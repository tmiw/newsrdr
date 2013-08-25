package com.thoughtbug.newsrdr.tasks

import org.quartz.Job
import org.quartz.JobExecutionContext
import com.thoughtbug.newsrdr.models._
import scala.slick.session.{Database, Session}

class RssFetchJob extends Job {
  def execute(ctxt: JobExecutionContext) {
    val feedUrl = ctxt.getMergedJobDataMap().getString("url").toString()
    fetch(feedUrl, true)
  }
  
  private def preventDeadlock[T](f: Session => T) : T = {
    // Allow up to three times to perform transaction.
    var count = 0
    while (count < 3)
    {
      try {
        return BackgroundJobManager.db.withTransaction(f)
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
  
  def fetch(feedUrl: String, log: Boolean): NewsFeed = {
    try {
      val feed = XmlFeedFactory.load(feedUrl)

      return preventDeadlock { implicit session: Session =>
        // Update feed's contents with whatever we've fetched from the server.
        // If it doesn't already exist, create.
        BackgroundJobManager.dao.updateOrInsertFeed(session, feedUrl, feed)
      }
    }
    catch
    {
      case e:Exception => {
        if (log == false) {
          throw e
        } else {
          // log to error log
          preventDeadlock { implicit session: Session =>
            BackgroundJobManager.dao.logFeedFailure(session, feedUrl, e.getMessage())
            null
          }
        }
      }
    }
  }
}