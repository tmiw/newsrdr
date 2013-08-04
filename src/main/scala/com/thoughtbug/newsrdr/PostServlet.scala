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

class PostServlet(db: Database, implicit val swagger: Swagger) extends NewsrdrStack
  with NativeJsonSupport with SwaggerSupport {

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
        parameter queryParam[Option[Integer]]("page").description("The page of results to retrieve."))
        
  get("/", operation(getPosts)) {
    var offset = Integer.parseInt(params.getOrElse("page", "0")) * Constants.ITEMS_PER_PAGE
    
    db withSession {
      var feed_posts = for { 
          (nfa, ua) <- Query(NewsFeedArticles).sortBy(_.pubDate.desc) leftJoin UserArticles on (_.id === _.articleId)
          uf <- UserFeeds if uf.userId === 1 && nfa.feedId === uf.feedId} yield (nfa, ua.articleRead.?)
      
      params.get("unread_only") match {
        case Some(unread_only_string) if unread_only_string.toLowerCase() == "true" => {
          (for { (p, q) <- feed_posts.list if q.getOrElse(true) == true } yield NewsFeedArticleInfo(p, true)).drop(offset).take(Constants.ITEMS_PER_PAGE)
        }
        case _ => (for { (fp, fq) <- feed_posts.list } yield NewsFeedArticleInfo(fp, fq.getOrElse(true))).drop(offset).take(Constants.ITEMS_PER_PAGE)
      }
    }
  }
  
  val markReadCommand =
    (apiOperation[Unit]("markRead")
        summary "Marks the given post as read."
        notes "Marks the given post as read."
        parameter pathParam[Int]("pid").description("The ID of the post."))
        
  delete("/:pid", operation(markReadCommand)) {
    var pid = params.getOrElse("pid", halt(422))
    
    // TODO: stop using hardcoded admin user.
    db withTransaction {
      var feed_posts = for {
            (nfa, ua) <- NewsFeedArticles leftJoin UserArticles on (_.id === _.articleId)
            	if ua.articleId === Integer.parseInt(pid)
            uf <- UserFeeds if uf.userId === 1 && nfa.feedId === uf.feedId} yield ua
          feed_posts.firstOption match {
              case Some(x) => feed_posts.update(UserArticle(x.id, x.userId, x.articleId, true))
              case None => UserArticles.insert(UserArticle(None, 1, Integer.parseInt(pid), true))
      }
    }
  }
  
  val markUnreadCommand =
    (apiOperation[Unit]("markUnread")
        summary "Marks the given post as unread."
        notes "Marks the given post as unread."
        parameter pathParam[Int]("pid").description("The ID of the post."))
  put("/:pid", operation(markUnreadCommand)) {
    var pid = params.getOrElse("pid", halt(422))
    
    // TODO: stop using hardcoded admin user.
    db withTransaction {
      var feed_posts = for {
            (nfa, ua) <- NewsFeedArticles leftJoin UserArticles on (_.id === _.articleId)
            	if ua.articleId === Integer.parseInt(pid)
            uf <- UserFeeds if uf.userId === 1 && nfa.feedId === uf.feedId} yield ua
          feed_posts.firstOption match {
              case Some(x) => feed_posts.update(UserArticle(x.id, x.userId, x.articleId, false))
              case None => UserArticles.insert(UserArticle(None, 1, Integer.parseInt(pid), false))
      }
    }
  }
}