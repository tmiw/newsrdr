package us.newsrdr.tasks

import org.quartz.Job
import org.quartz.JobExecutionContext
import us.newsrdr.models._
import slick.jdbc.JdbcBackend.Database
import scala.xml._
import scala.xml.transform._

class RssFetchJob extends Job {
  def execute(ctxt: JobExecutionContext) {
    val isDown = BackgroundJobManager.dao.isSiteDown()(BackgroundJobManager.db)
    
    if (!isDown)
    {
      val feedUrl = ctxt.getMergedJobDataMap().getString("url").toString()
      fetch(None, feedUrl, true)
    }
  }
  
  private def preventDeadlock[T](f: Database => T)(implicit db : Database) : T = {
    // Allow up to three times to perform transaction.
    var count = 0
    while (count < 3)
    {
      try {
        return f(db)
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
  
  private def reduceFeedUpdateFrequency(today: java.sql.Timestamp, feedUrl: String) {
    // Hold off on updating again for 2x the previous interval (max 24 hours).
    // This will reduce the amount of load/bandwidth on the server if the feed
    // is not frequently updated.
    val feed = BackgroundJobManager.dao.getFeedFromUrl(feedUrl)(BackgroundJobManager.db)
    feed match {
      case Some(f) => {
        val timeDiff = 2 * (today.getTime() - f.lastUpdate.getTime()) / 1000
        val newInterval = 
          if (timeDiff > 60*60*24)
          {
            60*60*24
          }
          else
          {
            timeDiff
          }
        BackgroundJobManager.rescheduleFeedJob(feedUrl, newInterval.asInstanceOf[Int])
      }
      case _ => ()
    }
  }
  
  def fetch(userId: Option[Int], feedUrl: String, log: Boolean): NewsFeed = {
    val today = new java.sql.Timestamp(new java.util.Date().getTime())
    val currentFeed = BackgroundJobManager.dao.getFeedFromUrl(feedUrl)(BackgroundJobManager.db)
    val lastUpdatedTime = (
      if (currentFeed.isDefined) currentFeed.get.lastUpdate
      else new java.sql.Timestamp(0)
    ).getTime()
    
    try {
      val feed = try {
        XmlFeedFactory.load(feedUrl, lastUpdatedTime, false)
      } catch {
        case e : MultipleFeedsException => {
          // If logging (e.g. automated task), choose first feed from list.
          if (log) XmlFeedFactory.load(e.getFeedList(0).url, lastUpdatedTime, false)
          else throw e
        }
      }
      val ret = preventDeadlock { implicit db: Database =>
        // Update feed's contents with whatever we've fetched from the server.
        // If it doesn't already exist, create.
        if (userId.isDefined) BackgroundJobManager.dao.updateOrInsertFeed(userId.get, feedUrl, feed)
        else BackgroundJobManager.dao.updateOrInsertFeed(feedUrl, feed)
      }(BackgroundJobManager.db)
      
      BackgroundJobManager.rescheduleFeedJob(feedUrl, 60*60)
      ret
    }
    catch
    {
      case e:NotModifiedException => {
        // Not modified; this isn't an error, but we should probably check less
        // often if this feed is infrequently updated.
        val ret = if (currentFeed.isDefined) currentFeed.get else null
        ret
      }
      case e:Exception => {
        if (log == false) {
          throw e
        } else {
          // log to error log
          preventDeadlock { implicit db: Database =>
            val message = 
              if (e.isInstanceOf[HasNoFeedsException])
                e.getClass().toString()
              else e.toString()
            
            BackgroundJobManager.dao.logFeedFailure(feedUrl, message)
          }(BackgroundJobManager.db)
          reduceFeedUpdateFrequency(today, feedUrl)
          null
        }
      }
    }
  }
}
