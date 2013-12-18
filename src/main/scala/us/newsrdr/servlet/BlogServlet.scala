package us.newsrdr.servlet

import org.scalatra._
import scalate.ScalateSupport
import scala.slick.session.Database
import us.newsrdr.models._
import us.newsrdr.tasks._

import scala.slick.session.{Database, Session}

// JSON-related libraries
import org.json4s.{DefaultFormats, Formats}

// JSON-related libraries
import org.json4s.{DefaultFormats, Formats}

// JSON handling support from Scalatra
import org.scalatra.json._

// Swagger support
import org.scalatra.swagger._

import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}

import scala.collection._

class BlogServlet(dao: DataTables, db: Database, implicit val swagger: Swagger) extends NewsrdrStack
  with NativeJsonSupport with SwaggerSupport with AuthOpenId with GZipSupport
{
  override protected val applicationName = Some("blog")
  protected val applicationDescription = "The blog API. This exposes operations for viewing blog entries."
  
  // Sets up automatic case class to JSON output serialization
  protected implicit val jsonFormats: Formats = DefaultFormats

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }
  
  override protected def templateAttributes(implicit request: javax.servlet.http.HttpServletRequest): mutable.Map[String, Any] = {
    val sessionId = request.getSession().getId()
    db withSession { implicit session: Session =>
      super.templateAttributes ++ mutable.Map("loggedIn" -> dao.getUserSession(sessionId, request.getRemoteAddr()).isDefined)
    }
  }
  
  get("/") {
    contentType="text/html"
    
    db withSession { implicit session: Session =>
        val postList = dao.getBlogPosts(0)
        ssp("/blog",
            "title" -> "blog", 
            "postList" -> postList,
            "offset" -> 0)
    }
  }
  
  get("/page/:page") {
    contentType="text/html"
    
    db withSession { implicit session: Session =>
        val offset = Integer.parseInt(params.get("page").get)
        val postList = dao.getBlogPosts(offset * Constants.ITEMS_PER_PAGE)
        ssp("/blog",
            "title" -> "blog", 
            "postList" -> postList,
            "offset" -> offset)
    }
  }
  
  get("/post/:id") {
    contentType="text/html"
    
    db withSession { implicit session: Session =>
        val post = dao.getBlogPostById(Integer.parseInt(params.get("id").get))
        ssp("/blog_post",
            "title" -> post.subject, 
            "post" -> post)
    }
  }
  
  get("/feed") {
    contentType="application/rss+xml"
    db withSession { implicit session: Session =>
        val posts = dao.getBlogPosts(0)
        val dateFormatter = new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z")
        
        <rss version="2.0">
          <channel>
            <title>newsrdr blog</title>
            <link>{Constants.getURL(request, "/blog/")}</link>
            <description>newsrdr blog</description>
            <lastBuildDate>{dateFormatter.format(new java.util.Date())}</lastBuildDate>
            {posts.map(p => {
              <item>
                <title>{p.subject}</title>
                <link>{Constants.getURL(request, "/blog/post/" + p.id.get.toString)}</link>
                <description>{scala.xml.PCData(p.body)}</description>
                <pubDate>{dateFormatter.format(p.postDate)}</pubDate>
                <guid isPermaLink="false">{p.id}</guid>
              </item>
            })}
          </channel>
        </rss>
    }
  }
}
