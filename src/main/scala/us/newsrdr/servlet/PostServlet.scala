package us.newsrdr.servlet

import org.scalatra._
import scalate.ScalateSupport
import us.newsrdr.models._
import us.newsrdr.tasks._

import slick.jdbc.JdbcBackend.{Database, Session}

// JSON-related libraries
import org.json4s.{DefaultFormats, Formats}

// JSON-related libraries
import org.json4s.{DefaultFormats, Formats}

// JSON handling support from Scalatra
import org.scalatra.json._

// Swagger support
import org.scalatra.swagger._
import scala.collection._

class PostServlet(dao: DataTables, db: Database, implicit val swagger: Swagger) extends NewsrdrStack
  with NativeJsonSupport with SwaggerSupport with AuthOpenId with GZipSupport {

  override protected val applicationName = Some("posts")
  protected val applicationDescription = "The posts API. This exposes operations for manipulating individual posts."
    
  // Sets up automatic case class to JSON output serialization
  protected implicit val jsonFormats: Formats = DefaultFormats

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }
  
  error {
    case e: Exception => {
      NoDataApiResult(false, Some("server_error"))
    }
  }
  
  override protected def templateAttributes(implicit request: javax.servlet.http.HttpServletRequest): mutable.Map[String, Any] = {
    val sessionId = request.getSession().getId()
    super.templateAttributes ++ mutable.Map("loggedIn" -> dao.getUserSession(sessionId, request.getRemoteAddr())(db).isDefined)
  }
  
  val getPosts =
    (apiOperation[List[NewsFeedArticleInfo]]("getPosts")
        summary "Retrieves posts for all feeds."
        notes "Retrieves posts for all feeds, sorted by post date."
        parameter queryParam[Option[Boolean]]("unread_only").description("Whether to only retrieve unread posts.")
        parameter queryParam[Option[Integer]]("page").description("The page of results to retrieve.")
        parameter queryParam[Option[Integer]]("latest_post_id").description("The ID of the newest post."))
        
  get("/", operation(getPosts)) {
    authenticationRequired(dao, session.getId, db, request, {
      val offset = Integer.parseInt(params.getOrElse("page", "0")) * Constants.ITEMS_PER_PAGE
      val userId = getUserId(dao, db, session.getId, request).get
      val feedList = multiParams("feeds").map(Integer.parseInt(_)).toList
      
      val latestPostDate = params.get("latest_post_date") match {
            case Some(x) if !x.isEmpty() => java.lang.Long.parseLong(x)
            case _ => Long.MaxValue
        }
      
      ArticleListApiResult(true, None, { 
        val unreadOnly = params.get("unread_only") match {
          case Some(unread_only_string) if unread_only_string.toLowerCase() == "true" => true
          case _ => false
        }
        
        val latestPostId = params.get("latest_post_id") match {
            //case Some(x) if !x.isEmpty() => java.lang.Long.parseLong(x)
            case _ => Long.MaxValue // dao.getMaxPostIdForAllFeeds(userId, unreadOnly, latestPostDate)
        }
        
        ArticleListWithMaxId(
            latestPostId,
            if (feedList.size > 0) dao.getPostsForFeeds(userId, feedList, unreadOnly, offset, Constants.ITEMS_PER_PAGE, latestPostDate, latestPostId)(db)
            else dao.getPostsForAllFeeds(userId, unreadOnly, offset, Constants.ITEMS_PER_PAGE, latestPostDate, latestPostId)(db)
        )
      })
    }, {
      halt(401, NoDataApiResult(false, Some("validation_failed")))
    })
  }
  
  get("/:pid/link") {
    val pid = Integer.parseInt(params.getOrElse("pid", halt(404, "not found")))
    try {
      redirect(dao.getLinkForPost(pid)(db))
    } catch {
      case e:Exception => {
        halt(404, "not found")
      }
    }
  }
  
  val markReadCommand =
    (apiOperation[Unit]("markRead")
        summary "Marks the given post as read."
        notes "Marks the given post as read."
        parameter pathParam[Int]("pid").description("The ID of the post."))
        
  delete("/:pid", operation(markReadCommand)) {
    authenticationRequired(dao, session.getId, db, request, {
      var pid = Integer.parseInt(params.getOrElse("pid", halt(422, NoDataApiResult(false, Some("validation_failed")))))
      var userId = getUserId(dao, db, session.getId, request).get
      
      dao.setPostStatus(userId, pid, false)(db) match {
        case true => ()
        case _ => halt(404)
      }
      
      NoDataApiResult(true, None)
    }, {
      halt(401, NoDataApiResult(false, Some("auth_failed")))
    })
  }
  
  val markAllReadCommand =
    (apiOperation[Unit]("markAllRead")
        summary "Marks all posts as read."
        notes "Marks all posts as read."
        parameter queryParam[Int]("upTo").description("The oldest date/time which to mark as read.")
        parameter queryParam[Int]("from").description("The newest date/time which to mark as read."))
        
  delete("/", operation(markAllReadCommand)) {
    authenticationRequired(dao, session.getId, db, request, {
      val userId = getUserId(dao, db, session.getId, request).get
      val upTo = Integer.parseInt(params.getOrElse("upTo", "0"))
      val from = Integer.parseInt(params.getOrElse("from", halt(422, NoDataApiResult(false, Some("validation_failed")))))
      
      dao.setPostStatusForAllPosts(userId, from, upTo, false)(db) match {
        case true => ()
        case _ => halt(404)
      }
      
      NoDataApiResult(true, None)
    }, {
      halt(401, NoDataApiResult(false, Some("auth_failed")))
    })
  }
  
  val markUnreadCommand =
    (apiOperation[Unit]("markUnread")
        summary "Marks the given post as unread."
        notes "Marks the given post as unread."
        parameter pathParam[Int]("pid").description("The ID of the post."))
  put("/:pid", operation(markUnreadCommand)) {
    authenticationRequired(dao, session.getId, db, request, {
      var pid = Integer.parseInt(params.getOrElse("pid", halt(422, NoDataApiResult(false, Some("validation_failed")))))
      var userId = getUserId(dao, db, session.getId, request).get
      
      dao.setPostStatus(userId, pid, true)(db) match {
        case true => ()
        case _ => halt(404)
      }
      
      NoDataApiResult(true, None)
    }, {
      halt(401, NoDataApiResult(false, Some("auth_failed")))
    })
  }
  
    
  val saveCommand =
    (apiOperation[Unit]("save")
        summary "Saves post."
        notes "Saves post"
        parameter pathParam[Int]("pid").description("The ID of the post."))
        
  put("/:pid/saved", operation(saveCommand)) {
    authenticationRequired(dao, session.getId, db, request, {
        val id = Integer.parseInt(params.getOrElse("feedId", halt(422, NoDataApiResult(false, Some("validation_failed")))))
        val pid = Integer.parseInt(params.getOrElse("pid", halt(422, NoDataApiResult(false, Some("validation_failed")))))
        val userId = getUserId(dao, db, session.getId, request).get
        
        dao.savePost(userId, id, pid)(db) match {
          case true => ()
          case _ => halt(404)
        }
        
        NoDataApiResult(true, None)
    }, {
      halt(401, NoDataApiResult(false, Some("auth_failed")))
    })
  }
  
  val unsaveCommand =
    (apiOperation[Unit]("unsave")
        summary "Unsaves post."
        notes "Unsaves post"
        parameter pathParam[Int]("pid").description("The ID of the post."))
        
  delete("/:pid/saved", operation(saveCommand)) {
    authenticationRequired(dao, session.getId, db, request, {
        val id = Integer.parseInt(params.getOrElse("feedId", halt(422, NoDataApiResult(false, Some("validation_failed")))))
        val pid = Integer.parseInt(params.getOrElse("pid", halt(422, NoDataApiResult(false, Some("validation_failed")))))
        val userId = getUserId(dao, db, session.getId, request).get
        
        dao.unsavePost(userId, id, pid)(db) match {
          case true => ()
          case _ => halt(404)
        }
        
        NoDataApiResult(true, None)
    }, {
      halt(401, NoDataApiResult(false, Some("auth_failed")))
    })
  }
}
