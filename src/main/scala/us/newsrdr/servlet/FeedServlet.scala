package us.newsrdr.servlet

import org.scalatra._
import scalate.ScalateSupport
import servlet.{MultipartConfig, SizeConstraintExceededException, FileUploadSupport}
import scala.slick.session.Database
import us.newsrdr.models._
import us.newsrdr.tasks._
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

import scala.collection._

class FeedServlet(dao: DataTables, db: Database, implicit val swagger: Swagger) extends NewsrdrStack
  with NativeJsonSupport with SwaggerSupport with ApiExceptionWrapper with AuthOpenId with FileUploadSupport
  with GZipSupport with CorsSupport {

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(3*1024*1024)))
  
  error {
    case e: SizeConstraintExceededException => {
      contentType = "text/html"
      <script language="javascript">
        window.top.window.app.finishedUploadingFeedList({{success: "false", error_string: "too_big"}});
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
  
  override protected def templateAttributes(implicit request: javax.servlet.http.HttpServletRequest): mutable.Map[String, Any] = {
    val sessionId = request.getSession().getId()
    db withSession { implicit session: Session =>
      super.templateAttributes ++ mutable.Map("loggedIn" -> dao.getUserSession(sessionId, request.getRemoteAddr()).isDefined)
    }
  }
  
  options("/*") {
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    contentType = "text/plain"
  }
    
  val getFeeds = 
    (apiOperation[FeedListApiResult]("getFeeds")
        summary "Shows all feeds"
        notes "Returns the list of all feeds the currently logged-in user is subscribed to.")
        
  get("/", operation(getFeeds)) {
    val userId = request.getHeader("X-newsrdr-userId")
    if (userId != null && userId.length() > 0)
    {
      executeOrReturnError {
        db withSession { implicit session: Session =>
          FeedListApiResult(true, None, 
            List[NewsFeedInfo](
              NewsFeedInfo(
                  NewsFeedFuncs.CreateFakeFeed(),
                  0,
                  dao.getSubscribedFeeds(Integer.parseInt(userId)).foldRight(0)((b,a) => b._2 + a),
                  false)
              )
            )
        }
      }
    }
    else
    {
      authenticationRequired(dao, session.getId, db, request, {
        executeOrReturnError {
          val userId = getUserId(dao, db, session.getId, request).get
          val today = new java.util.Date().getTime()
        
          db withSession { implicit session: Session =>
            FeedListApiResult(true, None, 
                dao.getSubscribedFeeds(userId).map(x => NewsFeedInfo(
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
  }
  
  val generateRss = 
    (apiOperation[String]("generateRss")
        summary "Generates an RSS feed given XPaths to items within the document."
        notes "Generates an RSS feed given XPaths to items within the document."
        parameter queryParam[String]("url").description("The URL to the page.")
        parameter queryParam[Option[String]]("bodyXPath").description("XPath query for the post body.")
        parameter queryParam[String]("titleXPath").description("XPath query for the post title.")
        parameter queryParam[String]("linkXPath").description("XPath query for the post's URL."))
  get("/generate.rss", operation(generateRss)) {
    contentType="application/rss+xml"
    XmlFeedFactory.generate(
        params.get("url").get, 
        params.get("titleXPath").get, 
        params.get("linkXPath").get,
        params.get("bodyXPath"))
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
          val userName = dao.getUserName(userId)
      
          val destFormat = new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z")
          val today = destFormat.format(new java.util.Date())
          val feedList = dao.getSubscribedFeeds(userId).toList

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
                case _:Exception => compact(render(("success" -> false) ~ ("error_string" -> "cant_parse")))
              }
            } 
            case None => compact(render(("success" -> false) ~ ("error_string" -> "forgot_file")))
          }
          <script language="javascript">
            window.top.window.app.finishedUploadingFeedList({xml.Unparsed(jsonResult)});
          </script>
      }
    }, {
      <script language="javascript">
        window.top.window.app.finishedUploadingFeedList({{success: "false", error_string: "not_authorized"}});
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
        try
        {
          val feed = dao.getFeedFromUrl(url) getOrElse {
              val fetchJob = new RssFetchJob
              val f = fetchJob.fetch(url, false)
            
              f
          }
        
          // Schedule periodic feed updates
          BackgroundJobManager.scheduleFeedJob(feed.feedUrl)
              
          // Add subscription at the user level.
          dao.addSubscriptionIfNotExists(userId, feed.id.get)
        
          val today = new java.util.Date().getTime()
          FeedInfoApiResult(true, None, NewsFeedInfo(
            feed,
            feed.id.get,
            dao.getUnreadCountForFeed(userId, feed.id.get),
            if ((today - feed.lastUpdate.getTime()) > 60*60*24*1000) { true } else { false }))
        } catch {
          case e:HasNoFeedsException => {
            // Provide the HTML actually fetched by the server so that the caller
            // can provide workflow to create a feed from said site. We also
            // need to do this because of XSS restrictions on the client side.
            StringDataApiResult(false, Some("not_a_feed"), e.getMessage())
          }
          case e:MultipleFeedsException => {
            AddFeedListApiResult(false, Some("multiple_feeds_found"), e.getFeedList)
          }
        }
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
        dao.unsubscribeFeed(userId, Integer.parseInt(id))
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
        parameter queryParam[Option[Integer]]("latest_post_id").description("The id of the newest post."))
        
  get("/:id/posts", operation(getPostsForFeed)) {
      authenticationRequired(dao, session.getId, db, request, {
        val id = Integer.parseInt(params.getOrElse("id", halt(422)))
        val offset = Integer.parseInt(params.getOrElse("page", "0")) * Constants.ITEMS_PER_PAGE
        val userId = getUserId(dao, db, session.getId, request).get
        
        val latestPostDate = params.get("latest_post_date") match {
          case Some(x) if !x.isEmpty() => java.lang.Long.parseLong(x)
          case _ => Long.MaxValue
        }
        
        ArticleListApiResult(true, None, db withSession { implicit session: Session =>
          val unreadOnly = params.get("unread_only") match {
            case Some(unread_only_string) if unread_only_string.toLowerCase() == "true" => true
            case _ => false
          }
          
          val latestPostId = params.get("latest_post_id") match {
            //case Some(x) if !x.isEmpty() => java.lang.Long.parseLong(x)
            case _ => Long.MaxValue // dao.getMaxPostIdForFeed(userId, id, unreadOnly, latestPostDate)
          }
          
          ArticleListWithMaxId(
              latestPostId,
              dao.getPostsForFeed(userId, id, unreadOnly, offset, Constants.ITEMS_PER_PAGE, latestPostDate, latestPostId)
          )
        })
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
        dao.setPostStatusForAllPosts(userId, id, from, upTo, false) match {
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
