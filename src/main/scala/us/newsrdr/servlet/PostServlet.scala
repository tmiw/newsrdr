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
  
  val getPosts =
    (apiOperation[List[NewsFeedArticleInfo]]("getPosts")
        summary "Retrieves posts for all feeds."
        notes "Retrieves posts for all feeds, sorted by post date."
        parameter queryParam[Option[Boolean]]("unread_only").description("Whether to only retrieve unread posts.")
        parameter queryParam[Option[Integer]]("page").description("The page of results to retrieve.")
        parameter queryParam[Option[Integer]]("latest_post_date").description("The date of the oldest post."))
        
  get("/", operation(getPosts)) {
    authenticationRequired(dao, session.getId, db, request, {
	    val offset = Integer.parseInt(params.getOrElse("page", "0")) * Constants.ITEMS_PER_PAGE
	    val userId = getUserId(dao, db, session.getId, request).get
	    
	    val latestPostDate = params.get("latest_post_date") match {
            case Some(x) if !x.isEmpty() => Integer.parseInt(x)
            case _ => new java.util.Date().getTime()
        }
	    
	    db withSession { implicit session: Session =>
	      params.get("unread_only") match {
	        case Some(unread_only_string) if unread_only_string.toLowerCase() == "true" => {
	          dao.getPostsForAllFeeds(session, userId, true, offset, Constants.ITEMS_PER_PAGE, latestPostDate)
	        }
	        case _ => dao.getPostsForAllFeeds(session, userId, false, offset, Constants.ITEMS_PER_PAGE, latestPostDate)
	      }
	    }
    }, {
      halt(401)
    })
  }
  
  val markReadCommand =
    (apiOperation[Unit]("markRead")
        summary "Marks the given post as read."
        notes "Marks the given post as read."
        parameter pathParam[Int]("pid").description("The ID of the post."))
        
  delete("/:pid", operation(markReadCommand)) {
    authenticationRequired(dao, session.getId, db, request, {
	    var pid = Integer.parseInt(params.getOrElse("pid", halt(422)))
	    var userId = getUserId(dao, db, session.getId, request).get
	    
	    db withTransaction { implicit session: Session =>
	      dao.setPostStatus(session, userId, pid, false) match {
	        case true => ()
	        case _ => halt(404)
	      }
	    }
	    
	    NoDataApiResult(true, None)
    }, {
      halt(401)
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
	    val from = Integer.parseInt(params.getOrElse("from", halt(422)))
	    
	    db withTransaction { implicit session: Session =>
	      dao.setPostStatusForAllPosts(session, userId, from, upTo, false) match {
	        case true => ()
	        case _ => halt(404)
	      }
	    }
	    
	    NoDataApiResult(true, None)
    }, {
      halt(401)
    })
  }
  
  val markUnreadCommand =
    (apiOperation[Unit]("markUnread")
        summary "Marks the given post as unread."
        notes "Marks the given post as unread."
        parameter pathParam[Int]("pid").description("The ID of the post."))
  put("/:pid", operation(markUnreadCommand)) {
    authenticationRequired(dao, session.getId, db, request, {
	    var pid = Integer.parseInt(params.getOrElse("pid", halt(422)))
	    var userId = getUserId(dao, db, session.getId, request).get
	    
	    db withTransaction { implicit session: Session =>
	      dao.setPostStatus(session, userId, pid, true) match {
	        case true => ()
	        case _ => halt(404)
	      }
	    }
	    
	    NoDataApiResult(true, None)
    }, {
      halt(401)
    })
  }
}
