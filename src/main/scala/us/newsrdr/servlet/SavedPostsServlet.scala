package us.newsrdr.servlet

import scala.collection._
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

class SavedPostsServlet(dao: DataTables, db: Database, implicit val swagger: Swagger) extends NewsrdrStack
  with NativeJsonSupport with SwaggerSupport with AuthOpenId with GZipSupport
{
  override protected val applicationName = Some("saved")
  protected val applicationDescription = "The saved posts API. This exposes operations for viewing saved posts."
  
  // Sets up automatic case class to JSON output serialization
  protected implicit val jsonFormats: Formats = DefaultFormats

  override protected def templateAttributes(implicit request: javax.servlet.http.HttpServletRequest): mutable.Map[String, Any] = {
    val sessionId = request.getSession().getId()
    db withSession { implicit session: Session =>
      super.templateAttributes ++ mutable.Map("loggedIn" -> dao.getUserSession(sessionId, request.getRemoteAddr()).isDefined)
    }
  }
  
  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }
  
  error {
    case e: Exception => {
      NoDataApiResult(false, Some("server_error"))
    }
  }
  
  get("/:uid") {
    contentType="text/html"
    
    db withSession { implicit session: Session =>
      val userId = try {
        Integer.parseInt(params.get("uid").get)
      } catch {
        case _:Exception => halt(404)
      }
      
      if (dao.getUserName(userId).isEmpty())
      {
        halt(404)
      }
      else
      {
        val user = dao.getUserInfo(userId)
        val savedPosts = dao.getSavedPosts(userId, 0, 10, Long.MaxValue, Long.MaxValue).map(p =>
          NewsFeedArticleInfoWithFeed(p.article, dao.getFeedByPostId(p.article.id.get)))
        val bootstrappedPosts = write(savedPosts)
        
        // Render the posts directly in the HTML only if the AdSense bot visited.
        // Needed to produce relevant ads because AdSense can't grok JS.
        val postList = if (request.getHeader("User-Agent") == "Mediapartners-Google") {
          savedPosts
        } else {
          List[NewsFeedArticleInfoWithFeed]()
        }
        
        ssp("/saved_posts",
            "title" -> (user.friendlyName + "'s saved posts"), 
            "bootstrappedPosts" -> bootstrappedPosts,
            "postList" -> postList,
            "uid" -> userId)
      }
    }
  }
  
  get("/:uid/feed") {
    contentType="application/rss+xml"
    db withSession { implicit session: Session =>
      val userId = Integer.parseInt(params.get("uid").get)
      
      if (dao.getUserName(userId).isEmpty())
      {
        halt(404)
      }
      else
      {
        val user = dao.getUserInfo(userId)
        val posts = dao.getSavedPosts(userId, 0, 10, Long.MaxValue, Long.MaxValue)
        val dateFormatter = new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z")
        
        <rss version="2.0">
          <channel>
            <title>{(user.friendlyName + "'s saved posts")}</title>
            <link>{Constants.getURL(request, "/saved/" + userId.toString())}</link>
            <description>{(user.friendlyName + "'s saved posts")}</description>
            <lastBuildDate>{dateFormatter.format(new java.util.Date())}</lastBuildDate>
            {posts.map(p => {
              val permalink = p.article.isGuidPermalink.getOrElse(true).toString()
              <item>
                <title>{p.article.title}</title>
                <link>{p.article.link}</link>
                <description>{scala.xml.PCData(p.article.description)}</description>
                <pubDate>{dateFormatter.format(p.article.pubDate.getOrElse(new java.sql.Timestamp(new java.util.Date().getTime())))}</pubDate>
                <guid isPermaLink={permalink}>{p.article.guid.getOrElse("")}</guid>
              </item>
            })}
          </channel>
        </rss>
      }
    }
  }
  
  val getPosts =
    (apiOperation[List[NewsFeedArticleInfoWithFeed]]("getPosts")
        summary "Retrieves saved posts from the given user."
        notes "Retrieves saved posts from the given user, sorted by post date."
        parameter pathParam[Integer]("uid").description("The user's ID.")
        parameter queryParam[Option[Integer]]("page").description("The page of results to retrieve.")
        parameter queryParam[Option[Integer]]("latest_post_id").description("The ID of the newest post."))
        
  get("/:uid/posts", operation(getPosts)) {
    val offset = Integer.parseInt(params.getOrElse("page", "0")) * Constants.ITEMS_PER_PAGE
  val userId = try {
      Integer.parseInt(params.get("uid").get)
    } catch {
      case _:Exception => halt(404)
    }
      
  val latestPostId = params.get("latest_post_id") match {
      case Some(x) if !x.isEmpty() => java.lang.Long.parseLong(x)
      case _ => Long.MaxValue
    }
      
  val latestPostDate = params.get("latest_post_date") match {
      case Some(x) if !x.isEmpty() => java.lang.Long.parseLong(x)
      case _ => Long.MaxValue
    }
  db withSession { implicit session: Session =>
    SavedArticleListWithMaxId(
        latestPostId,
        dao.getSavedPosts(userId, offset, Constants.ITEMS_PER_PAGE, latestPostDate, latestPostId).map(p =>
          NewsFeedArticleInfoWithFeed(p.article, dao.getFeedByPostId(p.article.id.get))))
    }
  }
}
