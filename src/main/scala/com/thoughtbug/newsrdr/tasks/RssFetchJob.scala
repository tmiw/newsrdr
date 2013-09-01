package com.thoughtbug.newsrdr.tasks

import org.quartz.Job
import org.quartz.JobExecutionContext
import com.thoughtbug.newsrdr.models._
import scala.slick.session.{Database, Session}
import scala.xml._
import scala.xml.transform._

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
  
  /*val htmlParser = XML.withSAXParser(new org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl().newSAXParser())
  object ImagePrefetch extends RewriteRule
  {
    override def transform(n: Node): Seq[Node] = n match {
      case Elem(prefix, "img", attribs, scope, _*) => {
        if (attribs.get("height").isEmpty && attribs.get("width").isEmpty)
        {
          val imageUrl = attribs.get("src").map(_.text).get
          try
          {
            val img = javax.imageio.ImageIO.read(new java.net.URL(imageUrl))
            n.asInstanceOf[Elem] % Attribute(None, "height", Text(img.getHeight().toString), Null) % 
                                   Attribute(None, "width", Text(img.getWidth().toString), Null) toSeq
          }
          catch
          {
            case _:Exception => {
              // ignore any exceptions, not being able to fix the <img> tags isn't
              // the end of the world.
              n
            }
          }
        }
        else
        {
          n
        }
      }
      case other => other
    }
  }*/
  
  def fetch(feedUrl: String, log: Boolean): NewsFeed = {
    val today = new java.sql.Timestamp(new java.util.Date().getTime())
    try {
      val feed = XmlFeedFactory.load(feedUrl)

      // Pre-fetch images listed in <img> tags and populate height= and width=
      // attributes. This is done so that page rendering on the client end 
      // is faster.
      /*object transform extends RuleTransformer(ImagePrefetch)
      feed.entries = feed.entries.par.map({ p: (NewsFeedArticle, List[String]) =>
        val dom = htmlParser.loadString(p._1.description)
        (NewsFeedArticle(
            p._1.id,
            p._1.feedId,
            p._1.title,
            p._1.link,
            transform(dom).toString,
            p._1.author,
            p._1.comments,
            p._1.enclosureUrl,
            p._1.enclosureLength,
            p._1.enclosureType,
            p._1.guid,
            p._1.isGuidPermalink,
            p._1.pubDate,
            p._1.source),
         p._2)
      }).toList*/
      
      val ret = preventDeadlock { implicit session: Session =>
        // Update feed's contents with whatever we've fetched from the server.
        // If it doesn't already exist, create.
        BackgroundJobManager.dao.updateOrInsertFeed(session, feedUrl, feed)
      }
      
      BackgroundJobManager.rescheduleFeedJob(feedUrl, 60*60)
      ret
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
          }
          
          // Hold off on updating again for 2x the previous interval (max 24 hours).
          // This will reduce the amount of load/bandwidth on the server if the outage
          // is longer than expected.
          BackgroundJobManager.db.withTransaction { implicit session: Session =>
            val feed = BackgroundJobManager.dao.getFeedFromUrl(session, feedUrl)
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
          
          null
        }
      }
    }
  }
}