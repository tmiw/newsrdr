package com.thoughtbug.newsrdr

import org.scalatra._
import scalate.ScalateSupport
import servlet.{MultipartConfig, SizeConstraintExceededException, FileUploadSupport}
import scala.slick.session.Database
import com.thoughtbug.newsrdr.models._
import com.thoughtbug.newsrdr.tasks._
import scala.slick.session.{Database, Session}

// JSON-related libraries
import org.json4s.{DefaultFormats, Formats}
import org.json4s._
import org.json4s.JsonDSL._

// JSON handling support from Scalatra
import org.scalatra.json._

// Swagger support
import org.scalatra.swagger._

// XML support
import scala.xml._

class FeedServlet(dao: DataTables, db: Database, implicit val swagger: Swagger) extends NewsrdrStack
  with NativeJsonSupport with SwaggerSupport with ApiExceptionWrapper with AuthOpenId with FileUploadSupport
  with GZipSupport {

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(3*1024*1024)))
  
  error {
  	case e: SizeConstraintExceededException => {
  	  contentType = "text/html"
  	  <script language="javascript">
        window.top.window.AppController.UploadForm.done({{success: "false", reason: "too_big"}});
      </script>
    }
  }
  
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
    authenticationRequired(dao, session.getId, db, request, {
      executeOrReturnError {
        val userId = getUserId(dao, db, session.getId, request).get
        val today = new java.util.Date().getTime()
        
        db withSession { implicit session: Session =>
          FeedListApiResult(true, None, 
              dao.getSubscribedFeeds(session, userId).map(x => NewsFeedInfo(
            		  x._1, 
            		  x._1.id.get,
            		  x._2,
            		  if ((today - x._1.lastUpdate.getTime()) > 60*60*24*1000) { true } else { false }
              )))
        }
      }
    }, {
      halt(401)
    })
  }
  
  val getFeedsOpml = 
    (apiOperation[String]("getFeedsOpml")
        summary "Exports the list of subscribed feeds into OPML format."
        notes "Returns the list of all feeds the currently logged-in user is subscribed to.")
  get("/export.opml", operation(getFeedsOpml)) {
    contentType = "application/xml"
    
    authenticationRequired(dao, session.getId, db, request, {
      executeOrReturnError {
        var sId = session.getId
        db withSession { implicit session: Session =>
          val userId = getUserId(dao, db, sId, request).get
          val userName = dao.getUserName(session, userId)
      
          val destFormat = new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z")
          val today = destFormat.format(new java.util.Date())
          val feedList = dao.getSubscribedFeeds(session, userId).toList

          // Force a download instead of displaying in the browser.
          response.addHeader("Content-Disposition", "attachment; filename=subscriptions.opml")
          
          <opml version="1.1">
            <head>
              <title>newsrdr</title>
              <dateCreated>{today}</dateCreated>
              <dateModified>{today}</dateModified>
              <ownerName>{userName}</ownerName>
              <ownerEmail>{userName}</ownerEmail>
            </head>
            <body>
              <outline type="Subscriptions" text="Subscriptions">
                {for ( x <- feedList ) yield
                  <outline type="rss" title={x._1.title} text={x._1.title} xmlUrl={x._1.feedUrl} htmlUrl={x._1.link} />}
              </outline>
            </body>
          </opml>
        }
      }
    }, {
      redirect("/auth/login")
    })
  }
  
  val postFeedsOpml =
    (apiOperation[String]("postFeedsOpml")
        summary "Processes an OPML file and returns a list of feeds."
        notes "Takes a XML file and returns a list of feed URLs.")
  
  private def attributeEquals(name: String, value: String)(node: Node) = node.attribute(name).filter(_.text==value).isDefined
  
  post("/import.opml", operation(postFeedsOpml)) {
    authenticationRequired(dao, session.getId, db, request, {
	    db withSession { implicit session: Session =>
	      // AJAX doesn't support file upload, so we have to do it the old-fashioned way.
          contentType = "text/html"
          val jsonResult = fileParams.get("feedFile") match {
            case Some(f) => {
              try {
                val xmlDom = xml.XML.load(f.getInputStream)
                compact(render(("success" -> true) ~ ("feeds" ->
                  (xmlDom \\ "outline").filter(attributeEquals("type", "rss"))
                                       .filter(_.attribute("xmlUrl").isDefined)
                                       .map(_.attribute("xmlUrl").get)
                                       .map(_.text))))
              } catch {
                case _:Exception => compact(render(("success" -> false) ~ ("reason" -> "cant_parse")))
              }
            } 
            case None => compact(render(("success" -> false) ~ ("reason" -> "forgot_file")))
          }
          <script language="javascript">
            window.top.window.AppController.UploadForm.done({xml.Unparsed(jsonResult)});
          </script>
	    }
    }, {
      <script language="javascript">
        window.top.window.AppController.UploadForm.done({{success: "false", reason: "not_authorized"}});
      </script>
    })
  }
  
  val postFeeds =
    (apiOperation[NewsFeedInfo]("postFeeds")
        summary "Adds a new feed"
        notes "Subscribes the currently logged-in user to the given feed. It will perform the initial fetch of the feed if it doesn't already exist, so fetching the unread posts for this feed would be a good idea."
        parameter queryParam[String]("url").description("The URL to the given feed to add."))
   
  post("/", operation(postFeeds)) {
    authenticationRequired(dao, session.getId, db, request, {
	    val url = params.getOrElse("url", halt(422))
	    val userId = getUserId(dao, db, session.getId, request).get
	    
	    // TODO: handle possible exceptions and output error data.
	    // We probably also want to return validation error info above.
	    db withTransaction { implicit session: Session =>
	      // Grab feed from database, creating if it doesn't already exist.
	      val feed = dao.getFeedFromUrl(session, url) getOrElse {
	          val fetchJob = new RssFetchJob
	          val f = fetchJob.fetch(url, false)
	          
	          // Schedule periodic feed updates
	          BackgroundJobManager.scheduleFeedJob(f.feedUrl)
	          
	          f
	      }
	      
	      // Add subscription at the user level.
	      dao.addSubscriptionIfNotExists(session, userId, feed.id.get)
	      
	      val today = new java.util.Date().getTime()
	      NewsFeedInfo(
	    		  feed,
	    		  feed.id.get,
	    		  dao.getUnreadCountForFeed(session, userId, feed.id.get),
	    		  if ((today - feed.lastUpdate.getTime()) > 60*60*24*1000) { true } else { false })
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
    authenticationRequired(dao, session.getId, db, request, {
	    val id = params.getOrElse("id", halt(422))
	    var userId = getUserId(dao, db, session.getId, request).get
	    
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
        parameter queryParam[Option[Integer]]("page").description("The page of results to retrieve.")
        parameter queryParam[Option[Integer]]("latest_post_date").description("The date of the oldest post."))
        
  get("/:id/posts", operation(getPostsForFeed)) {
      authenticationRequired(dao, session.getId, db, request, {
	      val id = Integer.parseInt(params.getOrElse("id", halt(422)))
	      val offset = Integer.parseInt(params.getOrElse("page", "0")) * Constants.ITEMS_PER_PAGE
	      val userId = getUserId(dao, db, session.getId, request).get
	      
	      val latestPostDate = params.get("latest_post_date") match {
            case Some(x) if !x.isEmpty() => Integer.parseInt(x)
            case _ => new java.util.Date().getTime()
          }
	      
	      db withSession { implicit session: Session =>
	        params.get("unread_only") match {
	          case Some(unread_only_string) if unread_only_string.toLowerCase() == "true" =>
	            dao.getPostsForFeed(session, userId, id, true, offset, Constants.ITEMS_PER_PAGE, latestPostDate)
	          case _ => dao.getPostsForFeed(session, userId, id, false, offset, Constants.ITEMS_PER_PAGE, latestPostDate)
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
    authenticationRequired(dao, session.getId, db, request, {
	    var id = Integer.parseInt(params.getOrElse("id", halt(422)))
	    var pid = Integer.parseInt(params.getOrElse("pid", halt(422)))
	    var userId = getUserId(dao, db, session.getId, request).get
	    
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
  
  val saveCommand =
    (apiOperation[Unit]("save")
        summary "Saves post."
        notes "Saves post"
        parameter pathParam[Int]("id").description("The ID of the feed.")
        parameter pathParam[Int]("pid").description("The ID of the post."))
        
  put("/:id/posts/:pid/saved", operation(saveCommand)) {
    authenticationRequired(dao, session.getId, db, request, {
	    val id = Integer.parseInt(params.getOrElse("id", halt(422)))
	    val pid = Integer.parseInt(params.getOrElse("pid", halt(422)))
	    val userId = getUserId(dao, db, session.getId, request).get
	    
	    db withTransaction { implicit session: Session =>
	      dao.savePost(session, userId, id, pid) match {
	        case true => ()
	        case _ => halt(404)
	      }
	    }
	    
	    NoDataApiResult(true, None)
    }, {
      halt(401)
    })
  }
  
  val unsaveCommand =
    (apiOperation[Unit]("unsave")
        summary "Unsaves post."
        notes "Unsaves post"
        parameter pathParam[Int]("id").description("The ID of the feed.")
        parameter pathParam[Int]("pid").description("The ID of the post."))
        
  delete("/:id/posts/:pid/saved", operation(saveCommand)) {
    authenticationRequired(dao, session.getId, db, request, {
	    val id = Integer.parseInt(params.getOrElse("id", halt(422)))
	    val pid = Integer.parseInt(params.getOrElse("pid", halt(422)))
	    val userId = getUserId(dao, db, session.getId, request).get
	    
	    db withTransaction { implicit session: Session =>
	      dao.unsavePost(session, userId, id, pid) match {
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
        parameter pathParam[Int]("id").description("The ID of the feed.")
        parameter queryParam[Int]("upTo").description("The oldest date/time which to mark as read.")
        parameter queryParam[Int]("from").description("The newest date/time which to mark as read."))
        
  delete("/:id/posts", operation(markAllReadCommand)) {
    authenticationRequired(dao, session.getId, db, request, {
	    val id = Integer.parseInt(params.getOrElse("id", halt(422)))
	    val userId = getUserId(dao, db, session.getId, request).get
	    val upTo = Integer.parseInt(params.getOrElse("upTo", "0"))
	    val from = Integer.parseInt(params.getOrElse("from", halt(422)))
	    
	    db withTransaction { implicit session: Session =>
	      dao.setPostStatusForAllPosts(session, userId, id, from, upTo, false) match {
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
    authenticationRequired(dao, session.getId, db, request, {
	    var id = Integer.parseInt(params.getOrElse("id", halt(422)))
	    var pid = Integer.parseInt(params.getOrElse("pid", halt(422)))
	    var userId = getUserId(dao, db, session.getId, request).get
	    
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