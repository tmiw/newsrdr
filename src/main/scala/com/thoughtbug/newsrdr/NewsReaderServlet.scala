package com.thoughtbug.newsrdr

import org.scalatra._
import scalate.ScalateSupport
import com.thoughtbug.newsrdr.models._
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

class NewsReaderServlet(dao: DataTables, db: Database) extends NewsrdrStack with AuthOpenId with GZipSupport {
  val manager = new ConsumerManager
  
  get("/") {
    contentType = "text/html"
    ssp("/index")
  }
  
  get("/auth/login/g+") {
    var sId = session.getId()
    var setAttribute = (x : DiscoveryInformation) => session.setAttribute("discovered", x)
    
    db withSession { implicit session: Session =>
      dao.getUserSession(session, sId) match {
        case Some(sess) => redirect("/news/")
        case None => {
          val discoveries = manager.discover("https://www.google.com/accounts/o8/id")
          val discovered = manager.associate(discoveries)
          setAttribute(discovered)
          val authReq = 
            manager.authenticate(
                discovered, 
                Constants.getAuthenticatedURL(request, "g+"))
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
    var sId = session.getId()
    
    db withSession { implicit session: Session =>
      dao.getUserSession(session, sId) match {
        case Some(sess) => redirect("/news/")
        case None => {
          redirect(Constants.getFacebookLoginURL(request))    
        }
      }
    }
  }
  
  get("/auth/login/twitter") {
    val sId = session.getId()
    val userSession = session
    
    db withSession { implicit session: Session =>
      dao.getUserSession(session, sId) match {
        case Some(sess) => redirect("/news/")
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
      var sId = session.getId()
      db withTransaction { implicit session: Session =>
        dao.invalidateSession(session, sId)
      }
    } catch {
      case _:Exception => () // ignore any exceptions here
    }
    
    session.invalidate
    redirect("/")
  }
  
  get("/auth/authenticated/twitter") {
	val twitter = session.getAttribute("twitter").asInstanceOf[Twitter]
    val requestToken = session.getAttribute("requestToken").asInstanceOf[RequestToken]
    val verifier = request.getParameter("oauth_verifier")
    
    twitter.getOAuthAccessToken(requestToken, verifier);
    session.removeAttribute("requestToken");
    
    val sId = session.getId()
    db withTransaction { implicit session: Session =>
      dao.startUserSession(session, sId, "tw:" + twitter.getId(), "")
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
      
      val sId = session.getId()
      db withTransaction { implicit session: Session =>
        dao.startUserSession(session, sId, email)
      }
      redirect("/auth/login/fb")
    }
  }
  
  get("/auth/authenticated/g+") {
    val openidResp = new ParameterList(request.getParameterMap())
    val discovered = session.getAttribute("discovered").asInstanceOf[DiscoveryInformation]
    val receivingURL = new StringBuffer(Constants.getAuthenticatedURL(request, "g+")) //request.getRequestURL()
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
        session.setAttribute("authService", "g+")
        db withTransaction { implicit session: Session =>
          dao.startUserSession(session, sId, email)
        }
        redirect("/auth/login/g+")
      }
    } else
      "not verified"        
  }
  
  get("/news*") {
    contentType="text/html"
    val authService = if (session.getAttribute("authService") != null) {
      session.getAttribute("authService")
    } else {
      "g+"
    }
    authenticationRequired(dao, session.id, db, {
      val sid = session.getId
      db withSession { implicit session: Session =>
        val userId = getUserId(dao, db, sid).get
        
        implicit val formats = Serialization.formats(NoTypeHints)
        val bootstrappedFeeds = write(dao.getSubscribedFeeds(session, userId).map(x => NewsFeedInfo(
            		  x._1, 
            		  x._1.id.get,
            		  x._2
              )))
        ssp("/app", "bootstrappedFeeds" -> bootstrappedFeeds )
      }
    }, {
      redirect(Constants.LOGIN_URI + "/" + authService)
    })
  }
}
