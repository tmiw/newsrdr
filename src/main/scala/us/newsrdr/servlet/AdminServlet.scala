package us.newsrdr.servlet

import org.scalatra._
import scalate.ScalateSupport
import us.newsrdr.models._
import scala.slick.session.{Database, Session}
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
import scala.collection._

class AdminServlet(dao: DataTables, db: Database) extends NewsrdrStack with AuthOpenId with GZipSupport {
  override protected def templateAttributes(implicit request: javax.servlet.http.HttpServletRequest): mutable.Map[String, Any] = {
    val sessionId = request.getSession().getId()
    db withSession { implicit session: Session =>
      super.templateAttributes ++ mutable.Map("loggedIn" -> dao.getUserSession(sessionId, request.getRemoteAddr()).isDefined)
    }
  }
  
  def adminWrapper[T](f: (Session, User) => T) : Any = {
    contentType="text/html"
    val sess = session;
    val qs = if (request.getQueryString() == null) { "" } else { "?" + request.getQueryString() }
    val authService = if (session.getAttribute("authService") != null) {
      session.getAttribute("authService")
    } else {
      session.setAttribute("redirectUrlOnLogin", request.getRequestURI() + qs)
      redirect("/auth/login")
    }
    
    authenticationRequired(dao, session.getId, db, request, { 
      val userSession = session
      db withSession { implicit session: Session =>
        val sess = dao.getUserSessionById(userSession.getId())
        val userInfo = dao.getUserInfo(sess.userId)
        
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
      if (authService != "newsrdr")
        redirect(Constants.LOGIN_URI + "/" + authService)
      else
        redirect(Constants.LOGIN_URI)
    })
  }
  
  get("/") {
    adminWrapper((session: Session, userInfo: User) => {
        val statistics = dao.getSiteStatistics()(session)
        ssp("/admin",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "site admin", "siteStats" -> statistics)
    })
  }
  
  get("/maint/rebalance") {
    adminWrapper((session: Session, userInfo: User) => {
      us.newsrdr.tasks.BackgroundJobManager.scheduleRebalanceJob
      redirect("/admin/")
    })
  }
  
  get("/blog") {
    adminWrapper((session: Session, userInfo: User) => {
        val postList = dao.getBlogPosts(0)(session)
        ssp("/admin_blog",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "blog admin", "postList" -> postList, "offset" -> 0)
    })
  }
  
  get("/blog/page/:page") {
    adminWrapper((session: Session, userInfo: User) => {
        val offset = Integer.parseInt(params.get("page").getOrElse("0"))
        val postList = dao.getBlogPosts(offset * Constants.ITEMS_PER_PAGE)(session)
        ssp("/admin_blog",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "blog admin", "postList" -> postList, "offset" -> offset)
    })
  }
  
  get("/blog/post/:id/delete") {
    val postId = Integer.parseInt(params.get("id").get)
    adminWrapper((session: Session, userInfo: User) => {
      db withTransaction {
        dao.deleteBlogPost(postId)(session)
      }
      redirect("/admin/blog")
    })
  }
  
  get("/blog/post/:id/edit") {
    adminWrapper((session: Session, userInfo: User) => {
      val post = dao.getBlogPostById(Integer.parseInt(params.get("id").get))(session)
      ssp("/admin_blog_edit",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "edit blog post", "post" -> post)
    })
  }
  
  post("/blog/post/:id/save") {
    adminWrapper((session: Session, userInfo: User) => {
        val subject = params.get("subject").get
        val body = params.get("body").get
        val postId = Integer.parseInt(params.get("id").get)
        db withTransaction {
            dao.editBlogPost(postId, subject, body)(session)
        }
        redirect("/admin/blog")
    })
  }
  
  post("/blog/post") {
    adminWrapper((session: Session, userInfo: User) => {
        val subject = params.get("subject").get
        val body = params.get("body").get
        db withTransaction {
            dao.insertBlogPost(userInfo.id.get, subject, body)(session)
        }
        redirect("/admin/blog")
    })
  }
}
