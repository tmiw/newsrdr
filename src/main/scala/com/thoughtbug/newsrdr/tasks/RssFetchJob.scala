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
    
    // Allow up to three times to perform transaction.
    var count = 0
    while (count < 3)
    {
      try {
        return BackgroundJobManager.db withTransaction { implicit session: Session =>
          // Update feed's contents with whatever we've fetched from the server.
          // If it doesn't already exist, create.
          BackgroundJobManager.dao.updateOrInsertFeed(session, feedUrl, feed)
        }
      } catch {
        case e:java.sql.SQLException => {
          if (e.toString().contains("Lock wait timeout exceeded"))
          {
            count = count + 1
          }
          else
          {
            throw e
          }
        }
      }
    }
    
    throw new RuntimeException("Could not break deadlock.")
  }
}