package us.newsrdr.servlet

import org.scalatra._
import scalate.ScalateSupport
import us.newsrdr.models._
import slick.jdbc.JdbcBackend.Database
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
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
    super.templateAttributes ++ mutable.Map("loggedIn" -> dao.getUserSession(sessionId, request.getRemoteAddr())(db).isDefined)
  }
  
  def adminWrapper[T](f: (Database, User) => T) : Any = {
    contentType="text/html"
    val sess = session;
    val qs = if (request.getQueryString() == null) { "" } else { "?" + request.getQueryString() }
    val authService = if (session.getAttribute("authService") != null) {
      session.getAttribute("authService")
    } else {
      session.setAttribute("redirectUrlOnLogin", request.getRequestURI() + qs)
      redirect("/auth/login")
    }
    
    authenticationRequired(dao, session.getId, db, request, { implicit db: Database =>
      val userSession = session
      val sess = dao.getUserSessionById(userSession.getId())
      val userInfo = dao.getUserInfo(sess.userId)
      
      if (!userInfo.isAdmin && userInfo.id.get != 1)
      {
        redirect("/news")
      }
      else
      {
        f(db, userInfo)
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
    adminWrapper((db: Database, userInfo: User) => {
        val statistics = dao.getSiteStatistics()(db)
        ssp("/admin",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "site admin", "siteStats" -> statistics)
    })
  }
  
  get("/maint/rebalance") {
    adminWrapper((db: Database, userInfo: User) => {
      us.newsrdr.tasks.BackgroundJobManager.scheduleRebalanceJob
      redirect("/admin/")
    })
  }
  
  get("/blog") {
    adminWrapper((db: Database, userInfo: User) => {
        val postList = dao.getBlogPosts(0)(db)
        ssp("/admin_blog",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "blog admin", "postList" -> postList, "offset" -> 0)
    })
  }
  
  get("/blog/page/:page") {
    adminWrapper((db: Database,  userInfo: User) => {
        val offset = Integer.parseInt(params.get("page").getOrElse("0"))
        val postList = dao.getBlogPosts(offset * Constants.ITEMS_PER_PAGE)(db)
        ssp("/admin_blog",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "blog admin", "postList" -> postList, "offset" -> offset)
    })
  }
  
  get("/blog/post/:id/delete") {
    val postId = Integer.parseInt(params.get("id").get)
      adminWrapper((db: Database, userInfo: User) => {
      dao.deleteBlogPost(postId)(db)
      redirect("/admin/blog")
    })
  }
  
  get("/blog/post/:id/edit") {
    adminWrapper((db: Database, userInfo: User) => {
      val post = dao.getBlogPostById(Integer.parseInt(params.get("id").get))(db)
      ssp("/admin_blog_edit",  "layout" -> "WEB-INF/templates/layouts/app.ssp", "title" -> "edit blog post", "post" -> post)
    })
  }
  
  post("/blog/post/:id/save") {
    adminWrapper((db: Database, userInfo: User) => {
        val subject = params.get("subject").get
        val body = params.get("body").get
        val postId = Integer.parseInt(params.get("id").get)
        dao.editBlogPost(postId, subject, body)(db)
        redirect("/admin/blog")
    })
  }
  
  post("/blog/post") {
    adminWrapper((db: Database, userInfo: User) => {
        val subject = params.get("subject").get
        val body = params.get("body").get
        dao.insertBlogPost(userInfo.id.get, subject, body)(db)
        redirect("/admin/blog")
    })
  }
}
