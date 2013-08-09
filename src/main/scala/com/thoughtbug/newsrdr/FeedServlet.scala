package com.thoughtbug.newsrdr

import org.scalatra._
import scalate.ScalateSupport
import scala.slick.session.Database
import com.thoughtbug.newsrdr.models._
import com.thoughtbug.newsrdr.tasks._
import scala.slick.session.{Database, Session}

// JSON-related libraries
import org.json4s.{DefaultFormats, Formats}

// JSON handling support from Scalatra
import org.scalatra.json._

// Swagger support
import org.scalatra.swagger._

class FeedServlet(dao: DataTables, db: Database, implicit val swagger: Swagger) extends NewsrdrStack
  with NativeJsonSupport with SwaggerSupport with ApiExceptionWrapper with AuthOpenId {

  override protected val applicationName = Some("feeds")
  protected val applicationDescription = "The feeds API. This exposes operations for manipulating the feed list."
    
  // Sets up automatic case class to JSON output serialization
  protected implicit val jsonFormats: Formats = DefaultFormats

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }
  
  val getFeeds = 
    (apiOperation[FeedListApiResult]("getFeeds")
        summary "Shows all feeds"
        notes "Returns the list of all feeds the currently logged-in user is subscribed to."
        parameter queryParam[Option[Integer]]("page").description("The page of results to retrieve."))
        
  get("/", operation(getFeeds)) {
    authenticationRequired(dao, session.getId, db, {
      executeOrReturnError {
        var userId = getUserId(dao, db, session.getId).get
        
        db withSession { implicit session: Session =>
          FeedListApiResult(true, None, 
              dao.getSubscribedFeeds(session, userId).map(x => NewsFeedInfo(
            		  x, 
            		  x.id.get,
            		  dao.getUnreadCountForFeed(session, userId, x.id.get)
              )))
        }
      }
    }, {
      halt(401)
    })
  }
  
  val postFeeds =
    (apiOperation[NewsFeedInfo]("postFeeds")
        summary "Adds a new feed"
        notes "Subscribes the currently logged-in user to the given feed. It will perform the initial fetch of the feed if it doesn't already exist, so fetching the unread posts for this feed would be a good idea."
        parameter queryParam[String]("url").description("The URL to the given feed to add."))
   
  post("/", operation(postFeeds)) {
    authenticationRequired(dao, session.getId, db, {
	    val url = params.getOrElse("url", halt(422))
	    var userId = getUserId(dao, db, session.getId).get
	    
	    // TODO: handle possible exceptions and output error data.
	    // We probably also want to return validation error info above.
	    db withTransaction { implicit session: Session =>
	      // Grab feed from database, creating if it doesn't already exist.
	      var feed = dao.getFeedFromUrl(session, url) getOrElse {
	          var fetchJob = new RssFetchJob
	          var f = fetchJob.fetch(url)
	          
	          // Schedule periodic feed updates
	          BackgroundJobManager.scheduleFeedJob(url)
	          
	          f
	      }
	      
	      // Add subscription at the user level.
	      dao.addSubscriptionIfNotExists(session, userId, feed.id.get)
	      
	      NewsFeedInfo(
	    		  feed,
	    		  feed.id.get,
	    		  dao.getUnreadCountForFeed(session, userId, feed.id.get))
	    }
    }, {
      halt(401)
    })
  }
  
  val deleteFeeds =
    (apiOperation[Unit]("deleteFeeds")
        summary "Unsubscribes from a feed"
        notes "Unsubscribes the currently logged in user from the given feed."
        parameter pathParam[Int]("id").description("The ID of the feed to unsubscribe from."))
        
  delete("/:id", operation(deleteFeeds)) {
    authenticationRequired(dao, session.getId, db, {
	    val id = params.getOrElse("id", halt(422))
	    var userId = getUserId(dao, db, session.getId).get
	    
	    // TODO: handle possible exceptions and output error data.
	    // We probably also want to return validation error info above.
	    db withTransaction { implicit session: Session =>
	      // Remove subscription at the user level.
	      dao.unsubscribeFeed(session, userId, Integer.parseInt(id))
	    }
	    
	    NoDataApiResult(true, None)
    }, {
      halt(401)
    })
  }
  
  val getPostsForFeed =
    (apiOperation[List[NewsFeedArticleInfo]]("getPostsForFeed")
        summary "Retrieves posts for a feed"
        notes "Retrieves posts for the given feed ID."
        parameter pathParam[Int]("id").description("The ID of the feed to operate upon.")
        parameter queryParam[Option[Boolean]]("unread_only").description("Whether to only retrieve unread posts.")
        parameter queryParam[Option[Integer]]("page").description("The page of results to retrieve."))
        
  get("/:id/posts", operation(getPostsForFeed)) {
      authenticationRequired(dao, session.getId, db, {
	      var id = Integer.parseInt(params.getOrElse("id", halt(422)))
	      var offset = Integer.parseInt(params.getOrElse("page", "0")) * Constants.ITEMS_PER_PAGE
	      var userId = getUserId(dao, db, session.getId).get
	      
	      db withSession { implicit session: Session =>
	        params.get("unread_only") match {
	          case Some(unread_only_string) if unread_only_string.toLowerCase() == "true" =>
	            dao.getPostsForFeed(session, userId, id, true, offset, Constants.ITEMS_PER_PAGE)
	          case _ => dao.getPostsForFeed(session, userId, id, false, offset, Constants.ITEMS_PER_PAGE)
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
        parameter pathParam[Int]("id").description("The ID of the feed.")
        parameter pathParam[Int]("pid").description("The ID of the post."))
        
  delete("/:id/posts/:pid", operation(markReadCommand)) {
    authenticationRequired(dao, session.getId, db, {
	    var id = Integer.parseInt(params.getOrElse("id", halt(422)))
	    var pid = Integer.parseInt(params.getOrElse("pid", halt(422)))
	    var userId = getUserId(dao, db, session.getId).get
	    
	    db withTransaction { implicit session: Session =>
	      dao.setPostStatus(session, userId, id, pid, false) match {
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
        parameter pathParam[Int]("id").description("The ID of the feed.")
        parameter pathParam[Int]("pid").description("The ID of the post."))
        
  put("/:id/posts/:pid", operation(markUnreadCommand)) {
    authenticationRequired(dao, session.getId, db, {
	    var id = Integer.parseInt(params.getOrElse("id", halt(422)))
	    var pid = Integer.parseInt(params.getOrElse("pid", halt(422)))
	    var userId = getUserId(dao, db, session.getId).get
	    
	    db withTransaction { implicit session: Session =>
	      dao.setPostStatus(session, userId, id, pid, true) match {
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