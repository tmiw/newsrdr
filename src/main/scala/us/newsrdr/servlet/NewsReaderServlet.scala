package us.newsrdr.servlet

import org.scalatra._
import scalate.ScalateSupport
import us.newsrdr.models._
import slick.jdbc.JdbcBackend.{Database, Session}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import org.json4s.{DefaultFormats, Formats}
import org.json4s.JsonDSL._
import org.scalatra.json._
import javax.mail._
import javax.mail.internet._
import java.util.Properties
import org.scalatra.swagger._
import scala.collection._
import us.newsrdr.AuthenticationTools
import scala.concurrent._
import scala.concurrent.duration._

class NewsReaderServlet(dao: DataTables, db: Database, props: Properties) extends NewsrdrStack with NativeJsonSupport with AuthOpenId with GZipSupport {
  override protected def templateAttributes(implicit request: javax.servlet.http.HttpServletRequest): mutable.Map[String, Any] = {
    val sessionId = request.getSession().getId()
    super.templateAttributes ++ mutable.Map("loggedIn" -> dao.getUserSession(sessionId, request.getRemoteAddr())(db).isDefined)
  }
  
  get("/") {
    contentType = "text/html"
    ssp("/index", "title" -> "", "randomPost" -> None, "randomPostFeed" -> None)
  }
  
  get("/privacy_policy") {
    contentType = "text/html"
    ssp("/privacy_policy", "title" -> "privacy policy")
  }
  
  get("/extensions") {
    contentType = "text/html"
    ssp("/extensions", "title" -> "Browser Extensions")
  }
  
  post("/auth/login/newsrdr") {
    contentType = formats("json")
    
    val sId = session.getId()
    
    // newsrdr account login is different because login is handled via AJAX.
    // If true is returned here /auth/login will set location.href to the correct redirect URL.
    val username = params.getOrElse("username", halt(422, NoDataApiResult(false, Some("validation_failed"))))
    val password = params.getOrElse("password", halt(422, NoDataApiResult(false, Some("validation_failed"))))
        
    val userInfo = dao.getUserInfoByUsername(username)(db)
    if (userInfo.isEmpty || userInfo.get.password == null || userInfo.get.password.length() == 0) halt(401, NoDataApiResult(false, Some("auth_failed")))
    else {
      if (AuthenticationTools.validatePassword(userInfo.get, password)) {
          dao.startUserSession(sId, username, userInfo.get.email, request.getRemoteAddr(), userInfo.get.friendlyName)(db)
        session.setAttribute("authService", "newsrdr")
        NoDataApiResult(true, None)
      }
    }
  }
  
  get("/auth/login/google") {
    val sId = session.getId()
    
    if (session.getAttribute("redirectUrlOnLogin") == null)
    {
      session.setAttribute("redirectUrlOnLogin", "/news/")
    }
    val redirectUrl = session.getAttribute("redirectUrlOnLogin").toString
    
    dao.getUserSession(sId, request.getRemoteAddr())(db) match {
      case Some(sess) => redirect(redirectUrl)
      case None => {
        redirect(Constants.getGoogleLoginURL(request))   
      }
    }
  }
  
  get("/auth/logout") {
    try {
      val sId = session.getId()
      dao.invalidateSession(sId)(db)
    } catch {
      case _:Exception => () // ignore any exceptions here
    }
    
    val authService = session.getValue("authService")
    session.invalidate
    redirect("/")
  }
  
  get("/auth/authenticated/google") {
      val codeToTokenSvc = scalaj.http.Http("https://www.googleapis.com/oauth2/v3/token").postForm(Seq(
          "client_id" -> Constants.GOOGLE_CLIENT_ID,
          "redirect_uri" -> Constants.getAuthenticatedURL(request, "google"),
          "client_secret" -> Constants.GOOGLE_CLIENT_SECRET,
          "code" -> params.get("code").get,
          "grant_type" -> "authorization_code")).asString
      val result = codeToTokenSvc.body
      
      val tokenJson = parse(result)
      val t = (tokenJson \\ "access_token").extract[String]
      val e = (tokenJson \\ "expires_in").extract[Integer]
      
      session.setAttribute("authService", "google")
      session.setAttribute("googleToken", t)
      session.setAttribute("googleTokenExpires", new java.util.Date().getTime() + e*1000)
      
      val idTokenComponents = (tokenJson \\ "id_token").extract[String].split('.')
      val decodedIdToken = java.util.Base64.getDecoder().decode(idTokenComponents(1))
      val idTokenAsString = new String(decodedIdToken, "UTF-8")
      val jsonIdToken = parse(idTokenAsString)
      val email = (jsonIdToken \\ "email").extract[String]
      val realName = 
        try {
          (jsonIdToken \\ "name").extract[String]
        } catch {
          case e : Exception => email
        }
      val sId = session.getId()
      dao.startUserSession(sId, email, request.getRemoteAddr(), realName)(db)
      redirect("/auth/login/google")
  }
  
  get("/auth/login") {
    // Show list of login choices
    contentType="text/html"
      
    if (session.getAttribute("redirectUrlOnLogin") == null)
    {
      session.setAttribute("redirectUrlOnLogin", "/news/")
    }
    val redirectUrl = session.getAttribute("redirectUrlOnLogin").toString
    
    ssp("/login", "title" -> "login", "redirectUrl" -> redirectUrl )
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
      val userId = getUserId(dao, db, sid, request).get
      val tail = if (multiParams("captures") == null) { "" } else { multiParams("captures").head }
      redirect("/news/" + userId.toString + tail + qs)
    }, {
      session.setAttribute("redirectUrlOnLogin", request.getRequestURI() + qs)
      if (authService != "newsrdr")
        redirect(Constants.LOGIN_URI + "/" + authService)
      else
        redirect(Constants.LOGIN_URI)
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
      val userId = getUserId(dao, db, sid, request).get
      val user = dao.getUserInfo(userId)(db)
      
      if (userId.toString != uidAsString)
      {
        sess.setAttribute("redirectUrlOnLogin", "/news/" + userId.toString + qs)
        if (authService != "newsrdr")
          redirect(Constants.LOGIN_URI + "/" + authService)
        else
          redirect(Constants.LOGIN_URI)
      }
      else
      {
        implicit val formats = Serialization.formats(NoTypeHints)
        val today = new java.util.Date().getTime()
        val email = if (authService == "newsrdr") {
          user.email
        } else {
          ""
        }
        val bootstrappedFeeds = write(dao.getSubscribedFeeds(userId)(db).map(x => {
          NewsFeedInfo(
                x._1, 
                x._1.id.get,
                x._2,
                if ((today - x._1.lastUpdate.getTime()) > 60*60*24*1000) { true } else { false }
          )
        }))
        ssp("/app", "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "", "bootstrappedFeeds" -> bootstrappedFeeds, "realName" -> user.friendlyName, "optOut" -> user.optOutSharing, "uid" -> userId, "email" -> email )
      }
    }, {
      if (request.getHeader("User-Agent") == "Mediapartners-Google") {
        val userId = Integer.parseInt(uidAsString)
        val user = dao.getUserInfo(userId)(db)
        val savedPosts = dao.getLatestPostsForUser(userId)(db).map(p =>
          NewsFeedArticleInfoWithFeed(p.article, dao.getFeedByPostId(p.article.id.get)(db)))
        val bootstrappedPosts = write(savedPosts)
      
        ssp("/saved_posts",
            "title" -> "", 
            "bootstrappedPosts" -> bootstrappedPosts,
            "postList" -> savedPosts,
            "uid" -> userId)
      } else {
        session.setAttribute("redirectUrlOnLogin", request.getRequestURI() + qs)
        if (authService != "newsrdr")
            redirect(Constants.LOGIN_URI + "/" + authService)
          else
            redirect(Constants.LOGIN_URI)
      }
    })
  }
}
