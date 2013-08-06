package com.thoughtbug.newsrdr.models

import scala.xml._
import java.sql.Timestamp
import java.text.SimpleDateFormat
import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import org.joda.time.format._

case class Category(
    id: Option[Int],
    name: String
    )

case class NewsFeed(
    id: Option[Int],
    title: String,
    link: String,
    description: String,
    feedUrl: String,
    
    // Optional per RSS2.0 specification
    language: Option[String],
    copyright: Option[String],
    managingEditor: Option[String],
    webMaster: Option[String],
    pubDate: Option[Timestamp],
    lastBuildDate: Option[Timestamp],
    generator: Option[String],
    docs: Option[String],
    //cloud:
    ttl: Option[Int],
    imageUrl: Option[String],
    imageTitle: Option[String],
    imageLink: Option[String]
    //textInput
    //skipHours
    //skipDays
    )

case class NewsFeedInfo(
    feed: NewsFeed,
    numUnread: Integer
)

case class NewsFeedArticle(
    id: Option[Int],
    feedId: Int,
    title: String,
    link: String,
    description: String,
    
    // optional per RSS2.0 specification
    
    author: Option[String],
    // category
    comments: Option[String],
    enclosureUrl: Option[String],
    enclosureLength: Option[Int],
    enclosureType: Option[String],
    guid: Option[String],
    isGuidPermalink: Option[Boolean],
    pubDate: Option[Timestamp],
    source: Option[String]
    )

case class NewsFeedArticleInfo(
    article: NewsFeedArticle,
    unread: Boolean
)

case class User(
    id: Option[Int],
    username: String,
    password: String, // for future use
    email: String // for future use
    )

case class UserSession(
    userId: Int,
    sessionId: String
    )

case class UserArticle(
    id: Option[Int],
    userId: Int,
    articleId: Int,
    articleRead: Boolean)

case class UserFeed(
    id: Option[Int],
    userId: Int,
    feedId: Int)

trait XmlFeedParser {
  def fillFeedProperties(root: Elem, url: String)
}

object XmlFeedFactory {
  def load(url: String) : XmlFeed = {
    val xmlDoc = XML.load(url)
    var feed : XmlFeed = null
    
    if ((xmlDoc \\ "entry").count((x) => true) > 0)
    {
      // Atom feed
      feed = new AtomFeed
    }
    else
    {
      feed = new RSSFeed
    }
    
    feed.fillFeedProperties(xmlDoc, url)
    feed
  }
}

abstract class XmlFeed extends XmlFeedParser {
    var feedProperties : NewsFeed = _
    var feedCategories : List[String] = _
    var entries : List[(NewsFeedArticle, List[String])] = _
    
    protected def generateOptionValue(x: String) : Option[String] = {
        if (!x.isEmpty) { Some(x) }
        else { None }
    }
    
    protected def generateOptionValueTimestamp(x: String) : Option[Timestamp] = {
        if (!x.isEmpty) { 
           var parsers = List(
            org.joda.time.format.DateTimeFormat.forPattern("E, d MMM y HH:mm:ss Z").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E, d MMM y HH:mm:ss Z '('z')'").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E, d MMM y HH:mm:ss z").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("dd MMM y HH:mm:ss Z").getParser(),
            ISODateTimeFormat.dateTimeParser().getParser()
          ).toArray
          var destFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
          var date = DateTime.parse(x, new DateTimeFormatterBuilder().append(null, parsers).toFormatter()).toDate()
          Some(Timestamp.valueOf(destFormat.format(date)))
        }
        else { None }
    }
    
    protected def generateOptionValueInt(x: String) : Option[Int] = {
        if (!x.isEmpty) { Some(x.toInt) }
        else { None }
    }
    
    protected def generateOptionValueBool(x: String) : Option[Boolean] = {
        if (!x.isEmpty) { Some(x.toBoolean) }
        else { None }
    }
}

class RSSFeed extends XmlFeed {
    def fillFeedProperties(root: Elem, url: String) = {
        val channel = (root \\ "channel")
        
        feedProperties = NewsFeed(
            None,
            (channel \ "title").text,
            (channel \ "link").text,
            (channel \ "description").text,
            url,
            generateOptionValue((channel \ "language").text),
            generateOptionValue((channel \ "copyright").text),
            generateOptionValue((channel \ "managingEditor").text),
            generateOptionValue((channel \ "webMaster").text),
            generateOptionValueTimestamp((channel \ "pubDate").text),
            generateOptionValueTimestamp((channel \ "lastBuildDate").text),
            generateOptionValue((channel \ "generator").text),
            generateOptionValue((channel \ "docs").text),
            generateOptionValueInt((channel \ "ttl").text),
            generateOptionValue((channel \ "image" \ "url").text),
            generateOptionValue((channel \ "image" \ "title").text),
            generateOptionValue((channel \ "image" \ "link").text)
            )
        
        feedCategories = (channel \ "category").map((x) => x.text).toList
        
        entries = (root \\ "item").map(createArticle).toList
    }
    
    private def createArticle(x : Node) : (NewsFeedArticle, List[String]) = {
        var article = NewsFeedArticle(
            None,
            0,
            (x \\ "title").text,
            (x \\ "link").text,
            (x \\ "description").text,
            generateOptionValue((x \\ "author").text),
            generateOptionValue((x \\ "comments").text),
            generateOptionValue((x \\ "enclosure@url").text),
            generateOptionValueInt((x \\ "enclosure@length").text),
            generateOptionValue((x \\ "enclosure@type").text),
            generateOptionValue((x \\ "guid").text),
            generateOptionValueBool((x \\ "guid@isPermaLink").text),
            generateOptionValueTimestamp((x \\ "pubDate").text),
            generateOptionValue((x \\ "source").text)
        )
        
        var articleCategories = (x \\ "category").map((x) => x.text).toList
        
        (article, articleCategories)
    }
}

class AtomFeed extends XmlFeed {
    def fillFeedProperties(root: Elem, url: String) = {
        val channel = (root \\ "feed")
        
        feedProperties = NewsFeed(
            None,
            (channel \ "title").text,
            (channel \ "link" \ "@href").text,
            (channel \ "subtitle").text,
            url,
            None,
            generateOptionValue((channel \ "rights").text),
            generateOptionValue((channel \ "author" \ "name").text),
            None,
            generateOptionValueTimestamp((channel \ "published").text),
            generateOptionValueTimestamp((channel \ "updated").text),
            generateOptionValue((channel \ "generator").text),
            None,
            None,
            None,
            None,
            None
            )
        
        feedCategories = (channel \ "category").map((x) => x.text).toList
        
        entries = (root \\ "entry").map(createArticle).toList
    }
    
    private def createArticle(x : Node) : (NewsFeedArticle, List[String]) = {
        var article = NewsFeedArticle(
            None,
            0,
            (x \\ "title").text,
            (x \\ "link" \ "@href").take(1).text,
            (x \\ "content").text,
            generateOptionValue((x \\ "author" \ "name").text),
            None,
            None,
            None,
            None,
            generateOptionValue((x \\ "id").text),
            None,
            generateOptionValueTimestamp((x \\ "published").text),
            None
        )
        
        var articleCategories = (x \\ "category").map((x) => x.text).toList
        
        (article, articleCategories)
    }
}