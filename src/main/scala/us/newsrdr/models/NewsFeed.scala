package us.newsrdr.models

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
    imageLink: Option[String],
    //textInput
    //skipHours
    //skipDays
    
    lastUpdate: Timestamp
    )

case class NewsFeedInfo(
    feed: NewsFeed,
    id: Integer,
    numUnread: Integer,
    errorsUpdating: Boolean
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
    unread: Boolean,
    saved: Boolean
)

case class NewsFeedArticleInfoWithFeed(
    article: NewsFeedArticle,
    feed: NewsFeed
)

case class User(
    id: Option[Int],
    username: String,
    password: String, // for future use
    email: String, // for future use
    friendlyName: String,
    optOutSharing: Boolean,
    isAdmin: Boolean
    )

case class UserSession(
    userId: Int,
    sessionId: String,
    lastAccess: Timestamp,
    lastAccessIp: String
    )

case class UserArticle(
    id: Option[Int],
    userId: Int,
    articleId: Int,
    articleRead: Boolean,
    articleSaved: Boolean)

case class UserFeed(
    id: Option[Int],
    userId: Int,
    feedId: Int,
    addedDate: Timestamp)

trait XmlFeedParser {
  def fillFeedProperties(root: Elem, url: String)
}

// necessary because SAX closes this on error even when the entire thing hasn't been read.
class ManualCloseBufferedStream(s: java.io.InputStream) extends java.io.BufferedInputStream(s)
{
  override def close() {
    // empty
  }
  
  def actualClose() {
    super.close()
  }
}

object XmlFeedFactory {
  val parser = XML.withSAXParser(new org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl().newSAXParser())
  val f = javax.xml.parsers.SAXParserFactory.newInstance()
  f.setNamespaceAware(false)
  f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
  val MyXML = XML.withSAXParser(f.newSAXParser())
  
  def load(url: String) : XmlFeed = {
    var count = 0
    var code = 0
    var currentUrl = url
    var conn : java.net.HttpURLConnection = null
    
    try {
      do {
        count = count + 1
        val urlObj = new java.net.URL(currentUrl)
      
        if (conn != null)
        {
          conn.disconnect()
        }
      
        conn = urlObj.openConnection().asInstanceOf[java.net.HttpURLConnection]
        conn.setInstanceFollowRedirects(true)
        conn.setRequestProperty("User-Agent", "newsrdr (http://newsrdr.us/)")
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
        throw new RuntimeException("Too many redirects!")
      }
      else if (code > 299 || code < 200)
      {
        throw new RuntimeException("Server error: %d %s".format(code, conn.getResponseMessage()))
      }
    } catch {
      case e:Exception => {
        if (conn != null)
        {
          conn.disconnect()
        }
        throw e
      }
    }
    
    val contentType = conn.getContentType()
    val contentSize = conn.getContentLength()
    val maxContentSize = 1024*1024*3
    
    if (contentSize > maxContentSize)
    {
      conn.disconnect()
      throw new RuntimeException("Feed too large.")
    }
    
    val contentStream = new ManualCloseBufferedStream(conn.getInputStream())
    contentStream.mark(maxContentSize)
        
    var xmlDoc : xml.Elem = null
    try {
      MyXML.synchronized {
        MyXML.parser.reset()
        xmlDoc = MyXML.load(contentStream)
      }
      contentStream.actualClose
    } catch {
      case _:Exception => {
        contentStream.reset()
        try {
          parser.synchronized {
            xmlDoc = parser.load(contentStream)
          }
        } finally {
          contentStream.actualClose
        }
      }
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
      val feed = 
        if ((xmlDoc \\ "entry").count(x => true) > 0)
        {
          // Atom feed
          new AtomFeed
        }
        else if (
            (xmlDoc \\ "rss").count(x => true) > 0 ||
            (xmlDoc \\ "rdf").count(x => true) > 0 ||
            (xmlDoc \\ "RDF").count(x => true) > 0)
        {
          new RSSFeed
        }
        else
        {
          throw new RuntimeException("not an RSS or Atom feed!")
        }
    
      feed.fillFeedProperties(xmlDoc, url)
      val now = new java.sql.Timestamp(new java.util.Date().getTime())
      feed.entries = 
        feed.entries.sortBy(_._1.pubDate.getOrElse(now).getTime())
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
            generateOptionValue((channel \ "image" \ "link").text),
            new java.sql.Timestamp(new java.util.Date().getTime())
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
            None,
            new java.sql.Timestamp(new java.util.Date().getTime())
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
