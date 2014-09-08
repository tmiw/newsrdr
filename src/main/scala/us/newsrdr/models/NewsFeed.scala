package us.newsrdr.models

import scala.xml._
import java.sql.Timestamp
import java.text.SimpleDateFormat
import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import org.joda.time.format._
import javax.xml.xpath._
import javax.xml.transform._
import javax.xml.transform.dom._
import javax.xml.transform.sax._
import org.xml.sax.InputSource
import org.w3c.dom.NodeList
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks._
import javax.xml.transform.stream.StreamResult
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.HttpsURLConnection

case class Category(
    id: Option[Int],
    name: String
    )

object NewsFeedFuncs {
  def CreateFakeFeed() : NewsFeed = {
    NewsFeed(
        None,
        "all",
        "",
        "",
        "",
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        new java.sql.Timestamp(new java.util.Date().getTime()),
        ""
    )
  }
}
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
    
    lastUpdate: Timestamp,
    hash: String
    )

case class NewsFeedInfo(
    feed: NewsFeed,
    id: Integer,
    numUnread: Integer,
    errorsUpdating: Boolean
)

case class NewsFeedArticle(
    id: Option[Long],
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

case class UserNewsFeedArticle(
    id: Option[Long],
    userId: Int,
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
    source: Option[String],
    isRead: Boolean,
    isSaved: Boolean
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
    articleId: Long,
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

class HasNoFeedsException(text: String) extends Exception(text) { }
case class AddFeedEntry(title: String, url: String)
class MultipleFeedsException(feedList: List[AddFeedEntry]) extends Exception 
{ 
  def getFeedList = feedList
}

class NotModifiedException extends Exception

object XmlFeedFactory {
  val parser = XML.withSAXParser(new org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl().newSAXParser())
  val xpathParser = new org.ccil.cowan.tagsoup.Parser()
  xpathParser.setFeature(org.ccil.cowan.tagsoup.Parser.namespacesFeature, false)
  //xpathParser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
  
  val f = javax.xml.parsers.SAXParserFactory.newInstance()
  f.setNamespaceAware(false)
  f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
  val MyXML = XML.withSAXParser(f.newSAXParser())
  
  class NaiveTrustManager extends X509TrustManager {
    override def checkClientTrusted(cert: Array[X509Certificate], authType: String) {
    }

    override def checkServerTrusted(cert: Array[X509Certificate], authType: String) {
    }

    override def getAcceptedIssuers = null

  }
  
  private def fetch[T](url: String, lastUpdatedTime: Long, fn: java.io.InputStream => T) : (String, T) = {
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
        
        // Trusts all SSL certs.
        // XXX: we really shouldn't do this but instead emit an error to the 
        // user when the feed is first added.
        val trustAllCerts = Array[TrustManager](new NaiveTrustManager())
        val sslContext = SSLContext.getInstance( "SSL" )
        val socketFactory = sslContext.getSocketFactory()
        
        conn = urlObj.openConnection().asInstanceOf[java.net.HttpURLConnection]
        if (conn.isInstanceOf[HttpsURLConnection])
        {
          conn.asInstanceOf[HttpsURLConnection].setSSLSocketFactory(socketFactory)
        }
        
        conn.setInstanceFollowRedirects(true)
        conn.setRequestProperty("User-Agent", "newsrdr (http://newsrdr.us/)")
        conn.setIfModifiedSince(lastUpdatedTime)
        conn.setReadTimeout(60*1000)
        conn.setConnectTimeout(10*1000)
        conn.connect()
 
        code = conn.getResponseCode() 
        if (code >= 300 && code <= 399)
        {
          // Didn't automatically redirect (went from http->https or vice versa). Compensate here.
          currentUrl = conn.getHeaderField("Location")
        }
      } while (count < 5 && code >= 300 && code <= 399 && code != 304)
    
      if (count >= 5)
      {
        throw new RuntimeException("Too many redirects!")
      }
      else if (code == 304)
      {
        throw new NotModifiedException
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
    
    val stream = conn.getInputStream()
    try
    {
      val result = fn(stream)
      (currentUrl, result)
    }
    finally
    {
      stream.close()
    }
  }
  
  private def longestCommonPrefix(prefixes: List[String]) : String = {
    var prefix = ""
    
    if (prefixes.length > 0)
    {
      prefix = prefixes.head
    }
    
    for (i <- 1 to prefixes.length - 1) 
    {
      val s = prefixes.drop(i).head
      var tmp = 0
      breakable { for (j <- 0 to Math.min(prefix.length() - 1, s.length() - 1))
      {
        if(prefix.charAt(j) != s.charAt(j)) { break }
        tmp = j
      } }
      prefix = prefix.substring(0, tmp);
    }
    
    prefix
  }
  
  def generate(url: String, titleXPath: String, linkXPath: String, bodyXPath: Option[String]) : scala.xml.Elem = {
    val xpathFac = XPathFactory.newInstance()
    val xpath = xpathFac.newXPath()
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    
    val (currentUrl, htmlNode) = fetch(url, 0, (stream) => {
      val result = new DOMResult()
      val contentStream = new ManualCloseBufferedStream(stream)
      contentStream.mark(1024*1024*3)
      transformer.transform(
          new SAXSource(
              xpathParser, 
              new InputSource(contentStream)),
          result)
      
      result.getNode()
    })

    val listOfPrefixes = if (bodyXPath.isEmpty) {
      List[String](titleXPath, linkXPath)
    } else {
      List[String](titleXPath, linkXPath, bodyXPath.get)
    }
    val longestPrefix = longestCommonPrefix(listOfPrefixes)
    
    val tmp = titleXPath.replace(longestPrefix, ".")
    val compiledTitleXPath = xpath.compile(titleXPath.replace(longestPrefix, "."))
    val compiledLinkXPath = xpath.compile(linkXPath.replace(longestPrefix, "."))
    val compiledBodyXPath = if (!bodyXPath.isEmpty) { xpath.compile(bodyXPath.get.replace(longestPrefix, ".")) } else { null }
    val compiledPerItemXPath = xpath.compile(longestPrefix)
    
    val nodes = compiledPerItemXPath.evaluate(htmlNode, XPathConstants.NODESET).asInstanceOf[NodeList]
    val htmlTitle = xpath.evaluate("//title/text()", htmlNode, XPathConstants.STRING).asInstanceOf[String]
    val dateFormatter = new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z")
    
    val listOfArticles = new ListBuffer[(String, String, Option[String])]()
    for (a <- 0 to nodes.getLength() - 1) {
      val node = nodes.item(a)
      val titleNodes = compiledTitleXPath.evaluate(node, XPathConstants.NODESET).asInstanceOf[NodeList]
      val title = {
        val writer = new java.io.StringWriter()
        for (b <- 0 to titleNodes.getLength() - 1) {
          transformer.transform(new DOMSource(titleNodes.item(b)), new StreamResult(writer))
        }
        writer.toString()
      }
      val link = compiledLinkXPath.evaluate(node)
      val body = 
        if (bodyXPath.isEmpty) { None } 
        else { 
          val bodyNodes = compiledBodyXPath.evaluate(node, XPathConstants.NODESET).asInstanceOf[NodeList]
          val writer = new java.io.StringWriter()
          for (b <- 0 to bodyNodes.getLength() - 1) {
            transformer.transform(new DOMSource(bodyNodes.item(b)), new StreamResult(writer))
          }
          Some(writer.toString()) 
      }
      listOfArticles += ((title, link, body))
    }
    
    <rss version="2.0">
      <channel>
        <title>{if (htmlTitle.length() == 0) { currentUrl } else { htmlTitle }}</title>
        <link>{currentUrl}</link>
        <description>Generated by http://newsrdr.us/.</description>
        <lastBuildDate>{dateFormatter.format(new java.util.Date())}</lastBuildDate>
        {listOfArticles.toList.map(p => {
          <item>
            <title>{p._1}</title>
            <link>{new java.net.URL(new java.net.URL(currentUrl), p._2).toString()}</link>
            <description>{scala.xml.PCData(if (p._3.isEmpty) { "" } else { p._3.get })}</description>
            <pubDate>{dateFormatter.format(new java.sql.Timestamp(new java.util.Date().getTime()))}</pubDate>
            <guid isPermaLink="true">{new java.net.URL(new java.net.URL(currentUrl), p._2).toString()}</guid>
          </item>
        })}
      </channel>
    </rss>
  }
  
  def load(url: String, lastUpdatedTime: Long) : XmlFeed = {
    var doc : String = ""
    val (currentUrl, xmlDoc) = fetch(url, lastUpdatedTime, (stream) => {
      val contentStream = new ManualCloseBufferedStream(stream)
      contentStream.mark(1024*1024*3)
      
      var xmlDoc : xml.Elem = null
      var success = false
      
      try {
        MyXML.synchronized {
          MyXML.parser.reset()
          xmlDoc = MyXML.load(contentStream)
          success = true
        }
      } catch {
        case _:SAXException => {
          contentStream.reset()
          parser.synchronized {
            xmlDoc = parser.load(contentStream)
            success = true
          }
        }
      } finally {
        if (success)
        {
          contentStream.reset()
          val s = new java.util.Scanner(contentStream).useDelimiter("\\A")
          doc = if (s.hasNext()) { s.next() } else { "" }
        }
      }
      
      xmlDoc
    })
        
    
    val feedLinks = (xmlDoc \\ "link").filter(attributeEquals("rel", "alternate")(_))
                                      .filter(x => attributeEquals("type", "application/rss+xml")(x) ||
                                                   attributeEquals("type", "application/atom+xml")(x))
    val feedCount = feedLinks.count(_ => true)
    if (feedCount == 1 && !feedLinks.head.attribute("href").map(_.text).head.equals(url))
    {
      load(new java.net.URL(new java.net.URL(currentUrl), feedLinks.head.attribute("href").map(_.text).head).toString(), lastUpdatedTime)
    }
    else if (feedCount > 1)
    {
      // Return list of RSS feeds for user to choose from.
      throw new MultipleFeedsException(
          feedLinks.map(p => AddFeedEntry(
              p.attribute("title").map(_.text).head,
              p.attribute("href").map(_.text).head)).toList)
    }
    else
    {
      val md = java.security.MessageDigest.getInstance("SHA-1")
      val ha = new sun.misc.BASE64Encoder().encode(md.digest(doc.getBytes()))

      val feed = 
        if ((xmlDoc \\ "entry").count(x => true) > 0)
        {
          // Atom feed
          new AtomFeed(ha)
        }
        else if (
            (xmlDoc \\ "rss").count(x => true) > 0 ||
            (xmlDoc \\ "rdf").count(x => true) > 0 ||
            (xmlDoc \\ "RDF").count(x => true) > 0)
        {
          new RSSFeed(ha)
        }
        else
        {
          throw new HasNoFeedsException(doc)
        }
    
      feed.fillFeedProperties(xmlDoc, url)
      val now = new java.sql.Timestamp(new java.util.Date().getTime())
      feed.entries = 
        feed.entries.sortBy(_._1.pubDate.getOrElse(now).getTime())
      feed
    }
  }
  
  private def attributeEquals(name: String, value: String)(node: Node) = node.attribute(name).filter(_.text==value))))).isDefined
}

abstract class XmlFeed(base64Hash: String) extends XmlFeedParser {
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

class RSSFeed(base64Hash: String) extends XmlFeed(base64Hash) {
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
            new java.sql.Timestamp(new java.util.Date().getTime()),
            base64Hash
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

class AtomFeed(base64Hash: String) extends XmlFeed(base64Hash) {
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
            new java.sql.Timestamp(new java.util.Date().getTime()),
            base64Hash
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
      val altFn = (p : Node) => {
        val a = p.attribute("rel")
        a.isEmpty || a.get.text == "alternate"
      }
      val altNode = node.filter(altFn)
      val xhtmlSummary = (altNode.filter(attributeEquals("type", "text/xhtml")) \ "@href").text
      val htmlSummary = (altNode.filter(attributeEquals("type", "text/html")) \ "@href").text
      val textSummary = (escapeText((altNode.filter(attributeEquals("type", "text/plain")) \ "@href").text))
      val otherSummary = (altNode.filter(_.attribute("href").isEmpty) \ "@href").text
      
      useEitherOrString(
          xhtmlSummary,
          useEitherOrString(
              htmlSummary,
              useEitherOrString(
                  textSummary,
                  otherSummary)))
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
    
    private def attributeEquals(name: String, value: String)(node: Node) = node.attribute(name).map(_.text).getOrElse("") == value

    private def escapeText(x : String) : String = {
      x.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\\r|\\n|\\r\\n", "<br>\r\n")
    }
}
