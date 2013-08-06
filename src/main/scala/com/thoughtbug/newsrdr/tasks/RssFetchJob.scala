package com.thoughtbug.newsrdr.tasks

import org.quartz.Job
import org.quartz.JobExecutionContext
import com.thoughtbug.newsrdr.models._
import scala.slick.session.{Database, Session}

class RssFetchJob extends Job {
  def execute(ctxt: JobExecutionContext) {
    var feedUrl = ctxt.getMergedJobDataMap().getString("url").toString()
    fetch(feedUrl)
  }
  
  def fetch(feedUrl: String): NewsFeed = {    
    val feed = XmlFeedFactory.load(feedUrl)
    
    BackgroundJobManager.db withTransaction { implicit session: Session =>
      // Update feed's contents with whatever we've fetched from the server.
      // If it doesn't already exist, create.
      BackgroundJobManager.dao.updateOrInsertFeed(session, feedUrl, feed)
    } 
  }
}