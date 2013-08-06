package com.thoughtbug.newsrdr

import org.scalatra._
import scalate.ScalateSupport
import com.thoughtbug.newsrdr.models._
import scala.slick.session.{Database, Session}

import org.openid4java.consumer._
import org.openid4java.discovery._
import org.openid4java.message.ax._
import org.openid4java.message._

class NewsReaderServlet(dao: DataTables, db: Database) extends NewsrdrStack with AuthOpenId {
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
        var sId = session.getId()
        db withTransaction { implicit session: Session =>
          dao.startUserSession(session, sId, email)
        }
        redirect("/auth/login")
      }
    } else
      "not verified"        
  }
  
  get("""^/news/?$""".r) {
    contentType="text/html"
    authenticationRequired(dao, session.id, db, {
      ssp("/app")
    }, {
      redirect(Constants.LOGIN_URI)
    })
  }
}
