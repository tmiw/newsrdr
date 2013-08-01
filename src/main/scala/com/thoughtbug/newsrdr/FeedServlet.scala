package com.thoughtbug.newsrdr

import org.scalatra._
import scalate.ScalateSupport
import scala.slick.session.Database
import com.thoughtbug.newsrdr.models._
import com.thoughtbug.newsrdr.tasks._

// Use H2Driver to connect to an H2 database
import scala.slick.driver.H2Driver.simple._

// Use the implicit threadLocalSession
import Database.threadLocalSession

// JSON-related libraries
import org.json4s.{DefaultFormats, Formats}

// JSON handling support from Scalatra
import org.scalatra.json._

// Swagger support
import org.scalatra.swagger._

class FeedServlet(db: Database, implicit val swagger: Swagger) extends NewsrdrStack
  with NativeJsonSupport with SwaggerSupport {

  override protected val applicationName = Some("feeds")
  protected val applicationDescription = "The feeds API. This exposes operations for manipulating the feed list."
    
  // Sets up automatic case class to JSON output serialization
  protected implicit val jsonFormats: Formats = DefaultFormats

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }
  
  val getFeeds = 
    (apiOperation[List[NewsFeed]]("getFeeds")
        summary "Shows all feeds"
        notes "Returns the list of all feeds the currently logged-in user is subscribed to.")
        
  get("/", operation(getFeeds)) {        
    db withSession {
      // TODO: stop using hardcoded admin user.
      for { uf <- UserFeeds if uf.userId === 1 } yield uf
    }
  }
  
  val postFeeds =
    (apiOperation[NewsFeed]("postFeeds")
        summary "Adds a new feed"
        notes "Subscribes the currently logged-in user to the given feed. It will perform the initial fetch of the feed if it doesn't already exist, so fetching the unread posts for this feed would be a good idea."
        parameter queryParam[String]("url").description("The URL to the given feed to add."))
   
  post("/", operation(postFeeds)) {
    val url = params.getOrElse("url", halt(422))

    // TODO: handle possible exceptions and output error data.
    // We probably also want to return validation error info above.
    db withTransaction {
      // Grab feed from database, creating if it doesn't already exist.
      var fetchJob = new RssFetchJob
      var feed = fetchJob.fetch(url)
      
      // Add subscription at the user level.
      // TODO: stop using hardcoded admin user.
      var userFeed = for { uf <- UserFeeds if uf.userId === 1 && uf.feedId === feed.id } yield uf
      userFeed.firstOption match {
        case Some(uf) => ()
        case None => {
          UserFeeds.insert(UserFeed(None, feed.id.get, 1))
          ()
        }
      }
    }
  }
  
  val deleteFeeds =
    (apiOperation("deleteFeeds")
        summary "Unsubscribes from a feed"
        notes "Unsubscribes the currently logged in user from the given feed."
        parameter pathParam[Int]("id").description("The ID of the feed to unsubscribe from."))
        
  delete("/:id", operation(deleteFeeds)) {
    val id = params.getOrElse("id", halt(422))
    
    // TODO: handle possible exceptions and output error data.
    // We probably also want to return validation error info above.
    db withTransaction {
      // Remove subscription at the user level.
      // TODO: stop using hardcoded admin user.
      var userFeed = for { uf <- UserFeeds if uf.userId === 1 && uf.feedId === Integer.parseInt(id) } yield uf
      userFeed.delete
    }
  }
}