package us.newsrdr.servlet

import org.scalatra._
import scalate.ScalateSupport
import us.newsrdr.models._
import scala.slick.session.{Database, Session}
import org.openid4java.consumer._
import org.openid4java.discovery._
import org.openid4java.message.ax._
import org.openid4java.message._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import dispatch._, Defaults._
import dispatch.url

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.RequestToken;

import javax.mail._
import javax.mail.internet._
import java.util.Properties

// Swagger support
import org.scalatra.swagger._

import scala.collection._

class NewsReaderServlet(dao: DataTables, db: Database, props: Properties) extends NewsrdrStack with AuthOpenId with GZipSupport {
  val manager = new ConsumerManager
  
  override protected def templateAttributes(implicit request: javax.servlet.http.HttpServletRequest): mutable.Map[String, Any] = {
    val sessionId = request.getSession().getId()
    db withSession { implicit session: Session =>
      super.templateAttributes ++ mutable.Map("loggedIn" -> dao.getUserSession(session, sessionId, request.getRemoteAddr()).isDefined)
    }
  }
  
  get("/") {
    contentType = "text/html"
      
    db withSession { implicit session: Session =>
      val randomPost = dao.getNewestRandomPost(session)
      val randomPostFeed = randomPost.map(p => dao.getFeedByPostId(session, p.article.id.get))
      ssp("/index", "title" -> "", "randomPost" -> randomPost, "randomPostFeed" -> randomPostFeed)
    }
  }
  
  get("/privacy_policy") {
    contentType = "text/html"
    ssp("/privacy_policy", "title" -> "privacy policy")
  }
  
  get("/auth/login/google") {
    val sId = session.getId()
    val setAttribute = (x : DiscoveryInformation) => session.setAttribute("discovered", x)
    
    if (session.getAttribute("redirectUrlOnLogin") == null)
    {
      session.setAttribute("redirectUrlOnLogin", "/news/")
    }
    val redirectUrl = session.getAttribute("redirectUrlOnLogin").toString
    
    db withSession { implicit session: Session =>
      dao.getUserSession(session, sId, request.getRemoteAddr()) match {
        case Some(sess) => redirect(redirectUrl)
        case None => {
          val discoveries = manager.discover("https://www.google.com/accounts/o8/id")
          val discovered = manager.associate(discoveries)
          setAttribute(discovered)
          val authReq = 
            manager.authenticate(
                discovered, 
                Constants.getAuthenticatedURL(request, "google"))
          val fetch = FetchRequest.createFetchRequest()
          fetch.addAttribute("email", "http://schema.openid.net/contact/email",true)
          fetch.addAttribute("firstname", "http://axschema.org/namePerson/first", true)
          fetch.addAttribute("lastname", "http://axschema.org/namePerson/last", true)
          authReq.addExtension(fetch)
          redirect(authReq.getDestinationUrl(true))    
        }
      }
    }
  }
  
  get("/auth/login/fb") {
    val sId = session.getId()
    
    if (session.getAttribute("redirectUrlOnLogin") == null)
    {
      session.setAttribute("redirectUrlOnLogin", "/news/")
    }
    val redirectUrl = session.getAttribute("redirectUrlOnLogin").toString
    
    db withSession { implicit session: Session =>
      dao.getUserSession(session, sId, request.getRemoteAddr()) match {
        case Some(sess) => redirect(redirectUrl)
        case None => {
          redirect(Constants.getFacebookLoginURL(request))    
        }
      }
    }
  }
  
  get("/auth/login/twitter") {
    val sId = session.getId()
    val userSession = session
    
    if (session.getAttribute("redirectUrlOnLogin") == null)
    {
      session.setAttribute("redirectUrlOnLogin", "/news/")
    }
    val redirectUrl = session.getAttribute("redirectUrlOnLogin").toString
    
    db withSession { implicit session: Session =>
      dao.getUserSession(session, sId, request.getRemoteAddr()) match {
        case Some(sess) => redirect(redirectUrl)
        case None => {
          val twitter = new TwitterFactory().getInstance()
          request.getSession().setAttribute("twitter", twitter)
          val requestToken = twitter.getOAuthRequestToken(Constants.getAuthenticatedURL(request, "twitter"))
          userSession.setAttribute("requestToken", requestToken)
          redirect(requestToken.getAuthenticationURL())
        }
      }
    }
  }
  
  get("/auth/logout") {
    try {
      val sId = session.getId()
      db withTransaction { implicit session: Session =>
        dao.invalidateSession(session, sId)
      }
    } catch {
      case _:Exception => () // ignore any exceptions here
    }
    
    val authService = session.getValue("authService")
    session.invalidate
    redirect("/")
  }
  
  get("/auth/authenticated/twitter") {
  val twitter = session.getAttribute("twitter").asInstanceOf[Twitter]
    val requestToken = session.getAttribute("requestToken").asInstanceOf[RequestToken]
    val verifier = request.getParameter("oauth_verifier")
    
    twitter.getOAuthAccessToken(requestToken, verifier);
    session.removeAttribute("requestToken");
    session.setAttribute("authService", "twitter")
    
    val sId = session.getId()
    db withTransaction { implicit session: Session =>
      dao.startUserSession(session, sId, "tw:" + twitter.getId(), "", twitter.getScreenName())
    }
    redirect("/auth/login/twitter")
  }
  
  get("/auth/authenticated/fb") {
    if (params.contains("error"))
    {
      // TODO: show error on home page
      redirect("/")
    }
    else
    {
      val codeToTokenSvc = dispatch.url("https://graph.facebook.com/oauth/access_token") <<? 
        Map("client_id" -> Constants.FB_CLIENT_ID,
            "redirect_uri" -> Constants.getAuthenticatedURL(request, "fb"),
            "client_secret" -> Constants.FB_CLIENT_SECRET,
            "code" -> params.get("code").get)
      val resultFuture = dispatch.Http(codeToTokenSvc OK as.String)
      val result = resultFuture()
      
      val tokenRegex = "access_token=([^&]+)&expires=(.+)".r
      val tokenRegex(t, e) = result
      
      session.setAttribute("authService", "fb")
      session.setAttribute("fbToken", t)
      session.setAttribute("fbTokenExpires", new java.util.Date().getTime() + e*1000)
      
      val getEmailSvc = dispatch.url("https://graph.facebook.com/me") <<?
        Map("access_token" -> t)
      val emailFuture = dispatch.Http(getEmailSvc OK as.String)
      val emailJsonString = emailFuture()
      
      implicit val formats = DefaultFormats 
      val emailJson = parse(emailJsonString)
      val email = (emailJson \\ "email").extract[String]
      val realName = (emailJson \\ "name").extract[String]
      
      val sId = session.getId()
      db withTransaction { implicit session: Session =>
        dao.startUserSession(session, sId, email, request.getRemoteAddr(), realName)
      }
      redirect("/auth/login/fb")
    }
  }
  
  get("/auth/authenticated/google") {
    val openidResp = new org.openid4java.message.ParameterList(request.getParameterMap())
    val discovered = session.getAttribute("discovered").asInstanceOf[DiscoveryInformation]
    val receivingURL = new StringBuffer(Constants.getAuthenticatedURL(request, "google")) //request.getRequestURL()
    val queryString = request.getQueryString()
    if (queryString != null && queryString.length() > 0)
        receivingURL.append("?").append(request.getQueryString())

    val verification = manager.verify(receivingURL.toString(), openidResp, discovered)
    val verified = verification.getVerifiedId()
    if (verified != null) {
      val authSuccess = verification.getAuthResponse().asInstanceOf[AuthSuccess]
      if (authSuccess.hasExtension(AxMessage.OPENID_NS_AX)){
        val fetchResp = authSuccess.getExtension(AxMessage.OPENID_NS_AX).asInstanceOf[FetchResponse]
        val emails = fetchResp.getAttributeValues("email")
        val email = emails.get(0).asInstanceOf[String]
        val firstName = fetchResp.getAttributeValue("firstname")
        val lastName = fetchResp.getAttributeValue("lastname")
        
        // email is username for now
        val sId = session.getId()
        session.setAttribute("authService", "google")
        db withTransaction { implicit session: Session =>
          dao.startUserSession(session, sId, email, request.getRemoteAddr(), firstName + " " + lastName)
        }
        redirect("/auth/login/google")
      }
    } else {
      "not verified"
    }
  }
  
  get("/auth/login") {
    // Show list of login choices
    contentType="text/html"
    ssp("/login", "title" -> "login" )
  }
  
  get("/about") {
    contentType="text/html"
    ssp("/about", "title" -> "About" )
  }
  
  get("/privacy_policy") {
    contentType="text/html"
    ssp("/privacy_policy", "title" -> "Privacy Policy" )
  }
  
  get("/developers/button") {
    contentType="text/html"
    ssp("/developers/button", "title" -> "Get the Button" )
  }
  
  get("/contact") {
    contentType="text/html"
    ssp("/contact", "title" -> "Contact", "invalid" -> false, "from" -> "", "subject" -> "", "body" -> "" )
  }
  
  private def isValidEmail(s: String) : Boolean = {
    try
    {
      new InternetAddress(s).validate()
      true
    }
    catch
    {
      case (_:AddressException) => false
    }
  }
  
  post("/contact") {
    contentType="text/html"
    
    // Validate properties
    val from = params("from")
    val subject = params("subject")
    val body = params("body")
    
    if (from == null || from.length() == 0 || !isValidEmail(from) ||
        subject == null || subject.length() == 0 ||
        body == null || body.length() == 0)
    {
      ssp("/contact", "title" -> "Contact", "invalid" -> true, "from" -> from, "subject" -> subject, "body" -> body )
    }
    else
    {
      val session = Session.getDefaultInstance(props)
      val message = new MimeMessage(session)

      message.setFrom(new InternetAddress(params("from")))
      message.setRecipients(javax.mail.Message.RecipientType.TO, props.get("us.newsrdr.email-to").toString())
      message.setSubject(params("subject"))
      message.setText(params("body"))
   
      val tr = session.getTransport("smtp")
      if (props.get("mail.smtp.auth") == "true")
      {
        tr.connect(props.get("mail.smtp.user").toString(), props.get("mail.smtp.password").toString())
      }
      else
      {
        tr.connect()
      }
      message.saveChanges()
      tr.sendMessage(message, message.getAllRecipients())
      tr.close()

      ssp("/contact_success", "title" -> "Message Sent" )
    }
  }
  
  get("/_elb_health_check") {
    contentType="text/html"
    "it works"
  }
  
  get("""^/news(|/|/[A-Za-z]+.*)$""".r) {
    val qs = if (request.getQueryString() == null) { "" } else { "?" + request.getQueryString() }
    val authService = if (session.getAttribute("authService") != null) {
      session.getAttribute("authService")
    } else {
      session.setAttribute("redirectUrlOnLogin", request.getRequestURI() + qs)
      redirect("/auth/login")
    }
    
    authenticationRequired(dao, session.id, db, request, {
      val sid = session.getId
      db withSession { implicit session: Session =>
        val userId = getUserId(dao, db, sid, request).get
        val tail = if (multiParams("captures") == null) { "" } else { multiParams("captures").head }
        redirect("/news/" + userId.toString + tail + qs)
      }
    }, {
      session.setAttribute("redirectUrlOnLogin", request.getRequestURI() + qs)
      redirect(Constants.LOGIN_URI + "/" + authService)
    })
  }
  
  // Sets up automatic case class to JSON output serialization
  protected implicit val jsonFormats: Formats = DefaultFormats
  
  get("""^/news/([0-9]+)""".r) {
    contentType="text/html"
    val sess = session;
    val qs = if (request.getQueryString() == null) { "" } else { "?" + request.getQueryString() }
    val authService = if (session.getAttribute("authService") != null) {
      session.getAttribute("authService")
    } else {
      session.setAttribute("redirectUrlOnLogin", request.getRequestURI() + qs)
      redirect("/auth/login")
    }
    val uidAsString = multiParams("captures").head

    authenticationRequired(dao, session.id, db, request, {
      val sid = session.getId
      db withSession { implicit session: Session =>
        val userId = getUserId(dao, db, sid, request).get
        val user = dao.getUserInfo(session, userId)
        
        if (userId.toString != uidAsString)
        {
          sess.setAttribute("redirectUrlOnLogin", "/news/" + userId.toString + qs)
          redirect(Constants.LOGIN_URI + "/" + authService)
        }
        else
        {
          implicit val formats = Serialization.formats(NoTypeHints)
          val today = new java.util.Date().getTime()
          val bootstrappedFeeds = write(dao.getSubscribedFeeds(session, userId).map(x => {
            NewsFeedInfo(
                  x._1, 
                  x._1.id.get,
                  x._2,
                  if ((today - x._1.lastUpdate.getTime()) > 60*60*24*1000) { true } else { false }
            )
          }))
          ssp("/app", "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "", "bootstrappedFeeds" -> bootstrappedFeeds, "realName" -> user.friendlyName, "optOut" -> user.optOutSharing, "uid" -> userId )
        }
      }
    }, {
      if (request.getHeader("User-Agent") == "Mediapartners-Google") {
        db withSession { implicit session: Session =>
          val userId = Integer.parseInt(uidAsString)
          val user = dao.getUserInfo(session, userId)
          val savedPosts = dao.getLatestPostsForUser(session, userId).map(p =>
            NewsFeedArticleInfoWithFeed(p.article, dao.getFeedByPostId(session, p.article.id.get)))
          val bootstrappedPosts = write(savedPosts)
        
          ssp("/saved_posts",
              "title" -> "", 
              "bootstrappedPosts" -> bootstrappedPosts,
              "postList" -> savedPosts,
              "uid" -> userId)
        }
      } else {
        session.setAttribute("redirectUrlOnLogin", request.getRequestURI() + qs)
        redirect(Constants.LOGIN_URI + "/" + authService)
      }
    })
  }
}
