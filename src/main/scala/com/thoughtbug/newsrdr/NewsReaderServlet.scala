package com.thoughtbug.newsrdr

import org.scalatra._
import scalate.ScalateSupport
import scala.slick.session.Database

import com.thoughtbug.newsrdr.models._

// Use H2Driver to connect to an H2 database
import scala.slick.driver.H2Driver.simple._

// Use the implicit threadLocalSession
import Database.threadLocalSession

import org.openid4java.consumer._
import org.openid4java.discovery._
import org.openid4java.message.ax._
import org.openid4java.message._

class NewsReaderServlet(db: Database) extends NewsrdrStack with AuthOpenId {
  val manager = new ConsumerManager
  
  get("/") {        
    <html>
      <body>
        <h1>test server</h1>
        Real website coming soon. For now, <a href="/news/">click here</a> to get to the main app.
      </body>
    </html>
  }
  
  get("/auth/login") {
    db withSession {
      var q = (for { sess <- UserSessions if sess.sessionId === session.getId } yield sess)
      q.firstOption match {
        case Some(sess) => redirect("/news/")
        case None => {
          val discoveries = manager.discover("https://www.google.com/accounts/o8/id")
          val discovered = manager.associate(discoveries)
          session.setAttribute("discovered", discovered)
          val authReq = 
            manager.authenticate(
                discovered, 
                Constants.getAuthenticatedURL(request))
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
  
  get("/auth/logout") {
    try {
      db withTransaction {
        var q = (for { sess <- UserSessions if sess.sessionId === session.getId } yield sess)
        q.firstOption match {
          case Some(s) => q.delete
          case None => ()
        }
      }
    } catch {
      case _:Exception => () // ignore any exceptions here
    }
    
    session.invalidate
    redirect("/")
  }
  
  get("/auth/authenticated") {
    val openidResp = new ParameterList(request.getParameterMap())
    val discovered = session.getAttribute("discovered").asInstanceOf[DiscoveryInformation]
    val receivingURL = request.getRequestURL()
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
        db withTransaction {
          val q = for { u <- Users if u.username === email } yield u
          var userId = q.firstOption match {
            case Some(u) => u.id.get
            case None => {
              Users returning Users.id insert User(None, email, "", email)
            }
          }
          UserSessions.insert(UserSession(userId, session.getId))
        }
        redirect("/auth/login")
      }
    } else
      "not verified"        
  }
  
  get("""^/news/?$""".r) {
    contentType="text/html"
    authenticationRequired(session.id, db, {
      ssp("/app")
    }, {
      redirect(Constants.LOGIN_URI)
    })
  }
}
