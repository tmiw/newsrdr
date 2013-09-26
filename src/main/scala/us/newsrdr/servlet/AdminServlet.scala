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

// Swagger support
import org.scalatra.swagger._

class AdminServlet(dao: DataTables, db: Database) extends NewsrdrStack with AuthOpenId with GZipSupport {
  val manager = new ConsumerManager
  
  get("/") {
    contentType="text/html"
    val sess = session;
    val authService = if (session.getAttribute("authService") != null) {
      session.getAttribute("authService")
    } else {
      "google"
    }
    
    authenticationRequired(dao, session.getId, db, request, { 
      val userSession = session
      db withSession { implicit session: Session =>
        val sess = dao.getUserSessionById(session, userSession.getId())
        val userInfo = dao.getUserInfo(session, sess.userId)
        
        if (!userInfo.isAdmin && userInfo.id.get != 1)
        {
          redirect("/news")
        }
        else
        {
            val statistics = dao.getSiteStatistics(session)
            ssp("/admin",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "site admin", "siteStats" -> statistics)
        }
      }
    }, {
      session.setAttribute("redirectUrlOnLogin", request.getRequestURI())
      redirect(Constants.LOGIN_URI + "/" + authService)
    })
  }
  
  post("/blog/post") {
    val subject = params.get("subject").get
    val body = params.get("body").get
    
    val authService = if (session.getAttribute("authService") != null) {
      session.getAttribute("authService")
    } else {
      "google"
    }
    
    authenticationRequired(dao, session.getId, db, request, { 
      val userSession = session
      db withTransaction { implicit session: Session =>
        val sess = dao.getUserSessionById(session, userSession.getId())
        val userInfo = dao.getUserInfo(session, sess.userId)
        
        if (!userInfo.isAdmin && userInfo.id.get != 1)
        {
          redirect("/news")
        }
        else
        {
          dao.insertBlogPost(session, userInfo.id.get, subject, body)
        }
      }
      redirect("/admin")
    }, {
      session.setAttribute("redirectUrlOnLogin", request.getRequestURI())
      redirect(Constants.LOGIN_URI + "/" + authService)
    })
  }
}
