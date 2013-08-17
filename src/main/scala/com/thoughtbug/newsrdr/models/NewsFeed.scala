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
    feedId: Int,
    addedDate: Timestamp)

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
      
      // fix for isolated unescaped ampersands
      val regex = "&(\\s+)"
      val replacement = "&amp;$1"
      in.replaceAll(regex, replacement)
    }    
    
  def load(url: String) : XmlFeed = {
    // We need to be really tolerant of bad Unicode, sadly.
    //var text = ""
    var count = 0
    var code = 0
    var currentUrl = url
    var conn : java.net.HttpURLConnection = null
    
    do {
      count = count + 1
      val urlObj = new java.net.URL(currentUrl)
      conn = urlObj.openConnection().asInstanceOf[java.net.HttpURLConnection]
      conn.setInstanceFollowRedirects(true)
      conn.setRequestProperty("User-Agent", "newsrdr (http://newsrdr.us/)")
      //conn.setRequestMethod("HEAD")
      conn.connect()
 
      code = conn.getResponseCode() 
      if (code >= 300 && code <= 399)
      {
        // Didn't automatically redirect (went from http->https or vice versa). Compensate here.
        currentUrl = conn.getHeaderField("Location")
      }
    } while (count < 5 && code >= 300 && code <= 399)
    
    if (count >= 5)
    {
      conn.disconnect()
      throw new RuntimeException("Too many redirects!")
    }
    else if (code > 299 || code < 200)
    {
      conn.disconnect()
      throw new RuntimeException("Server error.")
    }
    
    val contentType = conn.getContentType()
    val contentSize = conn.getContentLength()
    
    if (contentSize > 1024*1024*3)
    {
      conn.disconnect()
      throw new RuntimeException("Feed too large.")
    }
    
    var contentStream = conn.getInputStream()
    val s = new java.util.Scanner(contentStream).useDelimiter("\\A")
    val text = if (s.hasNext()) { s.next() } else { "" }
    conn.disconnect()
        
    val parser = XML.withSAXParser(new org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl().newSAXParser())
    var xmlDoc : xml.Elem = null
    try {
      xmlDoc = XML.loadString(text)
    } catch {
      case _:Exception => xmlDoc = parser.loadString(text)
    }
    
    val feedLinks = (xmlDoc \\ "link").filter(attributeEquals("rel", "alternate")(_))
                                      .filter(x => attributeEquals("type", "application/rss+xml")(x) ||
                                                   attributeEquals("type", "application/atom+xml")(x))
    if (feedLinks.count(_ => true) > 0 && !feedLinks.head.attribute("href").map(_.text).head.equals(url))
    {
      load(new java.net.URL(new java.net.URL(currentUrl), feedLinks.head.attribute("href").map(_.text).head).toString())
    }
    else
    {
      var feed : XmlFeed = null
      
      if ((xmlDoc \\ "entry").count(x => true) > 0)
      {
        // Atom feed
        feed = new AtomFeed
      }
      else if (
          (xmlDoc \\ "rss").count(x => true) > 0 ||
          (xmlDoc \\ "rdf").count(x => true) > 0 ||
          (xmlDoc \\ "RDF").count(x => true) > 0)
      {
        feed = new RSSFeed
      }
      else
      {
        throw new RuntimeException("not an RSS or Atom feed!")
      }
    
      feed.fillFeedProperties(xmlDoc, url)
      feed
    }
  }
  
  private def attributeEquals(name: String, value: String)(node: Node) = node.attribute(name).filter(_.text==value).isDefined
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
          Some(new Timestamp(new java.util.Date().getTime()))
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
        val link = useEitherOrString(getHtmlLink(x, "link"), (x \\ "id").text)
        
        val article = NewsFeedArticle(
            None,
            0,
            getHtmlContent(x, "title"),
            link,
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
    
    private def getHtmlLink(x : Node, name : String) : String = {
    	val node = x \\ name
    	val xhtmlSummary = node.filter(attributeEquals("type", "text/xhtml")).text
    	val htmlSummary = node.filter(attributeEquals("type", "text/html")).text
    	val textSummary = escapeText(node.filter(attributeEquals("type", "text/plain")).text)
    	
    	useEitherOrString(
    	    xhtmlSummary,
    	    useEitherOrString(
    	        htmlSummary,
    	        useEitherOrString(
    	            textSummary,
    	            "")))
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
