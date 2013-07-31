package com.thoughtbug.newsrdr.tasks

import org.quartz.Job
import org.quartz.JobExecutionContext
import com.thoughtbug.newsrdr.models._

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

class RssFetchJob extends Job {
  def execute(ctxt: JobExecutionContext) {
    var feedUrl = ctxt.get("feedUrl").toString()
    
    val feed = new RSSFeed
    feed.load(feedUrl)
    
    BackgroundJobManager.db withTransaction {
      // Update feed's contents with whatever we've fetched from the server.
      val feedQuery = Query(NewsFeeds)
      val newsFeedId = (for { f <- NewsFeeds if f.feedUrl === feedUrl } yield f.id).first
      val newsFeed = 
        for { f <- NewsFeeds if f.feedUrl === feedUrl } yield
        (f.copyright ~ f.description ~ f.docs ~ f.generator ~ f.imageLink ~
         f.imageTitle ~ f.imageUrl ~ f.language ~ f.lastBuildDate ~ f.link ~
         f.managingEditor ~ f.pubDate ~ f.title ~ f.ttl ~ f.webMaster)
      
      newsFeed.update(
        (feed.feedProperties.copyright, 
         feed.feedProperties.description,
         feed.feedProperties.docs, 
         feed.feedProperties.generator,
         feed.feedProperties.imageLink,
         feed.feedProperties.imageTitle, 
         feed.feedProperties.imageUrl, 
         feed.feedProperties.language, 
         feed.feedProperties.lastBuildDate, 
         feed.feedProperties.link,
         feed.feedProperties.managingEditor, 
         feed.feedProperties.pubDate, 
         feed.feedProperties.title, 
         feed.feedProperties.ttl, 
         feed.feedProperties.webMaster))
    
      // Insert categories that don't exist, then refresh feed categories with the current
      // set.
      val categoryIds = feed.feedCategories.map((c) => {
	    val feedQuery = for { fc <- Categories if fc.name === c } yield fc
	    feedQuery.firstOption match {
	      case Some(cat) => (newsFeedId, cat.id.get)
	      case None => { 
	        val newId = (Categories.name) returning Categories.id insert(c)
	        (newsFeedId, newId)
	      }
	    }
      })
      val newsFeedCategories = for { nfc <- NewsFeedCategories if nfc.feedId === newsFeedId } yield nfc
      newsFeedCategories.delete
      for { c <- categoryIds } {
        (NewsFeedCategories.feedId ~ NewsFeedCategories.categoryId).insert(c)
      }
      
      // Now update/insert each individual post in the feed.
      for { p <- feed.entries } insertOrUpdateEntry(newsFeedId, p)
    } 
  }
  
  private def insertOrUpdateEntry(feedId: Int, p: (NewsFeedArticle, List[String])) {
    val newPost = p._1
    
    // Insert or update article as needed.
    val existingEntryId = for { 
      e <- NewsFeedArticles if e.guid === newPost.guid || 
                              (e.title === newPost.title && e.link === newPost.link && e.description === newPost.description)
      } yield e.id
    val entry = for { 
      e <- NewsFeedArticles if e.guid === newPost.guid || 
                              (e.title === newPost.title && e.link === newPost.link && e.description === newPost.description)
      } yield (
          e.title ~ e.link ~ e.description ~ e.author ~ e.comments ~ e.enclosureLength ~ 
          e.enclosureType ~ e.enclosureUrl ~ e.guid ~ e.isGuidPermalink ~ e.pubDate ~
          e.source)
    val entryId = entry.firstOption match {
        case Some(ent) => {
          entry.update(
            newPost.title,
            newPost.link,
            newPost.description,
            newPost.author,
            newPost.comments,
            newPost.enclosureLength,
            newPost.enclosureType,
            newPost.enclosureUrl,
            newPost.guid,
            newPost.isGuidPermalink,
            newPost.pubDate,
            newPost.source)
          existingEntryId.first
        }
        case None => (
            NewsFeedArticles.feedId ~ NewsFeedArticles.title ~ NewsFeedArticles.link ~ NewsFeedArticles.description ~
            NewsFeedArticles.author ~ NewsFeedArticles.comments ~ NewsFeedArticles.enclosureLength ~
            NewsFeedArticles.enclosureType ~ NewsFeedArticles.enclosureUrl ~ NewsFeedArticles.guid ~
            NewsFeedArticles.isGuidPermalink ~ NewsFeedArticles.pubDate ~ NewsFeedArticles.source) returning NewsFeedArticles.id insert(
                feedId,
                newPost.title,
	            newPost.link,
	            newPost.description,
	            newPost.author,
	            newPost.comments,
	            newPost.enclosureLength,
	            newPost.enclosureType,
	            newPost.enclosureUrl,
	            newPost.guid,
	            newPost.isGuidPermalink,
	            newPost.pubDate,
	            newPost.source
            )
    }
      
    // Update feed categories.
    val categoryIds = p._2.map((c) => {
	    val feedQuery = for { fc <- Categories if fc.name === c } yield fc
	    feedQuery.firstOption match {
	      case Some(cat) => (entryId, cat.id.get)
	      case None => { 
	        val newId = (Categories.name) returning Categories.id insert(c)
	        (entryId, newId)
	      }
	    }
      })
      val postCategories = for { nfc <- NewsFeedArticleCategories if nfc.articleId === entryId } yield nfc
      postCategories.delete
      for { c <- categoryIds } {
        (NewsFeedArticleCategories.articleId ~ NewsFeedArticleCategories.categoryId).insert(c)
      }
  }
}