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
    id: Integer,
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
  /**
     * This method ensures that the output String has only
     * valid XML unicode characters as specified by the
     * XML 1.0 standard. For reference, please see
     * <a href="http://www.w3.org/TR/2000/REC-xml-20001006#NT-Char">the
     * standard</a>. This method will return an empty
     * String if the input is null or empty.
     *
     * @param in The String whose non-valid characters we want to remove.
     * @return The in String, stripped of non-valid characters.
     */
    private def stripNonValidXMLCharacters(in : String) : String = {
      in.filter(c => {
          c == 0x9 ||
          c == 0xA || 
          c == 0xD || 
          (c >= 0x20 && c <= 0xD7FF) ||
          (c >= 0xE000 && c <= 0xFFFD) ||
          (c >= 0x10000 && c <= 0x10FFFF)
      })
    }    
    
  def load(url: String) : XmlFeed = {
    // We need to be really tolerant of bad Unicode, sadly.
    var text = ""
    try {
      text = io.Source.fromURL(url)(io.Codec("UTF-8")).mkString
    } catch {
      case _:java.nio.charset.MalformedInputException => try {
        text = io.Source.fromURL(url)(io.Codec("ISO-8859-1")).mkString
      } catch {
        case _:java.nio.charset.MalformedInputException => try {
          text = io.Source.fromURL(url)(io.Codec("ASCII")).mkString
        }
      }
    }
    val src = stripNonValidXMLCharacters(text)
    val xmlDoc = XML.loadString(src)
    var feed : XmlFeed = null
    
    if ((xmlDoc \\ "entry").count(x => true) > 0)
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
      val destFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        if (!x.isEmpty) { 
           val parsers = List(
            org.joda.time.format.DateTimeFormat.forPattern("E, d MMM y HH:mm:ss Z").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E, d MMM y HH:mm:ss Z '('z')'").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E, d MMM y HH:mm:ss z").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E,  d MMM y HH:mm:ss Z").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E,  d MMM y HH:mm:ss Z '('z')'").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E,  d MMM y HH:mm:ss z").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E, d MMM y HH:mm:ssZ").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E, d MMM y HH:mm:ssZ '('z')'").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E, d MMM y HH:mm:ssz").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E,  d MMM y HH:mm:ssZ").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E,  d MMM y HH:mm:ssZ '('z')'").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E,  d MMM y HH:mm:ssz").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E, d MMM y HH:mm:ss 0").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E, d MMM y HH:mm:ss  0").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E,  d MMM y HH:mm:ss 0").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("E,  d MMM y HH:mm:ss  0").getParser(),
            org.joda.time.format.DateTimeFormat.forPattern("dd MMM y HH:mm:ss Z").getParser(),
            ISODateTimeFormat.dateTimeParser().getParser()
          ).toArray
          
          val date = DateTime.parse(x, new DateTimeFormatterBuilder().append(null, parsers).toFormatter()).toDate()
          Some(Timestamp.valueOf(destFormat.format(date)))
        }
        else { 
          Some(Timestamp.valueOf(destFormat.format(new java.util.Date())))
        }
    }
    
    protected def generateOptionValueInt(x: String) : Option[Int] = {
        if (!x.isEmpty) { Some(x.toInt) }
        else { None }
    }
    
    protected def generateOptionValueBool(x: String) : Option[Boolean] = {
        if (!x.isEmpty) { Some(x.toBoolean) }
        else { None }
    }

    protected def useEitherOrString(x: String, orY: String) : String = {
        if (!x.isEmpty) x
        else orY
    }
}

class RSSFeed extends XmlFeed {
    def fillFeedProperties(root: Elem, url: String) = {
        val channel = (root \\ "channel")
        
        feedProperties = NewsFeed(
            None,
            useEitherOrString((channel \ "title").text, url),
            (channel \ "link").text,
            (channel \ "description").text,
            url,
            generateOptionValue((channel \ "language").text),
            generateOptionValue((channel \ "copyright").text),
            generateOptionValue((channel \ "managingEditor").text),
            generateOptionValue((channel \ "webMaster").text),
            generateOptionValueTimestamp((channel \ "pubDate").text.trim()),
            generateOptionValueTimestamp((channel \ "lastBuildDate").text.trim()),
            generateOptionValue((channel \ "generator").text),
            generateOptionValue((channel \ "docs").text),
            generateOptionValueInt((channel \ "ttl").text),
            generateOptionValue((channel \ "image" \ "url").text),
            generateOptionValue((channel \ "image" \ "title").text),
            generateOptionValue((channel \ "image" \ "link").text)
            )
        
        feedCategories = (channel \ "category").map(_.text).toList
        
        entries = (root \\ "item").map(createArticle).toList
    }
    
    private def createArticle(x : Node) : (NewsFeedArticle, List[String]) = {
    	var articleText = useEitherOrString((x \\ "encoded").filter(_.prefix == "content").take(1).text, (x \\ "description").take(1).text)
        var article = NewsFeedArticle(
            None,
            0,
            (x \\ "title").take(1).text,
            (x \\ "link").take(1).text,
            articleText,
            generateOptionValue((x \\ "author").text),
            generateOptionValue((x \\ "comments").text),
            generateOptionValue((x \\ "enclosure@url").text),
            generateOptionValueInt((x \\ "enclosure@length").text),
            generateOptionValue((x \\ "enclosure@type").text),
            generateOptionValue((x \\ "guid").text),
            generateOptionValueBool((x \\ "guid@isPermaLink").text),
            generateOptionValueTimestamp((x \\ "pubDate").text.trim()),
            generateOptionValue((x \\ "source").text)
        )
        
        var articleCategories = (x \\ "category").map(_.text).toList
        
        (article, articleCategories)
    }
}

class AtomFeed extends XmlFeed {
    def fillFeedProperties(root: Elem, url: String) = {
        val channel = (root \\ "feed")
        
        feedProperties = NewsFeed(
            None,
            useEitherOrString((channel \ "title").text, url),
            (channel \ "link" \ "@href").text,
            (channel \ "subtitle").text,
            url,
            None,
            generateOptionValue((channel \ "rights").text),
            generateOptionValue((channel \ "author" \ "name").text),
            None,
            generateOptionValueTimestamp((channel \ "published").text.trim()),
            generateOptionValueTimestamp((channel \ "updated").text.trim()),
            generateOptionValue((channel \ "generator").text),
            None,
            None,
            None,
            None,
            None
            )
        
        feedCategories = (channel \ "category").map(_.text).toList
        
        entries = (root \\ "entry").map(createArticle).toList
    }
    
    private def createArticle(x : Node) : (NewsFeedArticle, List[String]) = {
        val content = useEitherOrString(getHtmlContent(x, "content"), getHtmlContent(x, "summary"))
        val pubTime = useEitherOrString((x \\ "published").text.trim(), (x \\ "updated").text.trim())
        val article = NewsFeedArticle(
            None,
            0,
            getHtmlContent(x, "title"),
            (x \\ "link" \ "@href").take(1).text,
            content,
            generateOptionValue((x \\ "author" \ "name").text),
            None,
            None,
            None,
            None,
            generateOptionValue((x \\ "id").text),
            None,
            generateOptionValueTimestamp(pubTime),
            None
        )
        
        val articleCategories = (x \\ "category").map(_.text).toList
        
        (article, articleCategories)
    }
    
    private def getHtmlContent(x : Node, name : String) : String = {
    	val node = x \\ name
    	val xhtmlSummary = node.filter(attributeEquals("type", "xhtml")).text
    	val htmlSummary = node.filter(attributeEquals("type", "html")).text
    	val textSummary = escapeText(node.filter(attributeEquals("type", "text")).text)
    	val defaultSummary = escapeText(node.text)
    	
    	useEitherOrString(
    	    xhtmlSummary,
    	    useEitherOrString(
    	        htmlSummary,
    	        useEitherOrString(
    	            textSummary,
    	            defaultSummary)))
    }
    
    private def attributeEquals(name: String, value: String)(node: Node) = node.attribute(name).filter(_.text==value).isDefined

    private def escapeText(x : String) : String = {
    	x.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\\r|\\n|\\r\\n", "<br>\r\n")
    }
}
