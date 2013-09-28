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
  
  def adminWrapper[T](f: (Session, User) => T) : Any = {
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
          f(session, userInfo)
        }
      }
    }, {
      session.setAttribute("redirectUrlOnLogin", request.getRequestURI())
      redirect(Constants.LOGIN_URI + "/" + authService)
    })
  }
  
  get("/") {
    adminWrapper((session: Session, userInfo: User) => {
        val statistics = dao.getSiteStatistics(session)
        ssp("/admin",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "site admin", "siteStats" -> statistics)
    })
  }
  
  get("/blog") {
    adminWrapper((session: Session, userInfo: User) => {
        val postList = dao.getBlogPosts(session, 0)
        ssp("/admin_blog",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "blog admin", "postList" -> postList, "offset" -> 0)
    })
  }
  
  get("/blog/page/:page") {
    adminWrapper((session: Session, userInfo: User) => {
        val offset = Integer.parseInt(params.get("page").getOrElse("0"))
        val postList = dao.getBlogPosts(session, offset * Constants.ITEMS_PER_PAGE)
        ssp("/admin_blog",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "blog admin", "postList" -> postList, "offset" -> offset)
    })
  }
  
  get("/blog/post/:id/delete") {
    val postId = Integer.parseInt(params.get("id").get)
    adminWrapper((session: Session, userInfo: User) => {
      db withTransaction {
        dao.deleteBlogPost(session, postId)
      }
      redirect("/admin/blog")
    })
  }
  
  get("/blog/post/:id/edit") {
    adminWrapper((session: Session, userInfo: User) => {
      val post = dao.getBlogPostById(session, Integer.parseInt(params.get("id").get))
      ssp("/admin_blog_edit",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "edit blog post", "post" -> post)
    })
  }
  
  post("/blog/post/:id/save") {
    adminWrapper((session: Session, userInfo: User) => {
        val subject = params.get("subject").get
        val body = params.get("body").get
        val postId = Integer.parseInt(params.get("id").get)
        db withTransaction {
            dao.editBlogPost(session, postId, subject, body)
        }
        redirect("/admin/blog")
    })
  }
  
  post("/blog/post") {
    adminWrapper((session: Session, userInfo: User) => {
        val subject = params.get("subject").get
        val body = params.get("body").get
        db withTransaction {
            dao.insertBlogPost(session, userInfo.id.get, subject, body)
        }
        redirect("/admin/blog")
    })
  }
}
