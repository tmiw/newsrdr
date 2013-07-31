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
    
    // TODO: insert into db
    BackgroundJobManager.db withSession {
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
    } 
  }
}