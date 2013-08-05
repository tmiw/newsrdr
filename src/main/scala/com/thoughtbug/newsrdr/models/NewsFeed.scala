package com.thoughtbug.newsrdr.models

import scala.xml._
import java.sql.Timestamp
import java.text.SimpleDateFormat
import com.github.nscala_time.time.Imports._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import org.joda.time.DateTime

case class Category(
    id: Option[Int],
    name: String
    )
 
object Categories extends Table[Category]("Categories") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def name = column[String]("name")
	
	def * = id.? ~ name <> (Category, Category.unapply _)
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
    imageLink: Option[String]
    //textInput
    //skipHours
    //skipDays
    )
   
object NewsFeeds extends Table[NewsFeed]("NewsFeeds") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def title = column[String]("title")
	def link = column[String]("link")
	def description = column[String]("description")
	def feedUrl = column[String]("feedUrl")
	
	def language = column[Option[String]]("language")
	def copyright = column[Option[String]]("copyright")
	def managingEditor = column[Option[String]]("managingEditor")
	def webMaster = column[Option[String]]("webMaster")
	def pubDate = column[Option[Timestamp]]("pubDate")
	def lastBuildDate = column[Option[Timestamp]]("lastBuildDate")
	def generator = column[Option[String]]("generator")
	def docs = column[Option[String]]("docs")
	def ttl = column[Option[Int]]("ttl")
	def imageUrl = column[Option[String]]("imageUrl")
	def imageTitle = column[Option[String]]("imageTitle")
	def imageLink = column[Option[String]]("imageLink")
	
	def * = 
	  id.? ~ title ~ link ~ description ~ feedUrl ~ language ~ copyright ~ managingEditor ~ 
	  webMaster ~ pubDate ~ lastBuildDate ~ generator ~ docs ~ ttl ~ imageUrl ~ 
	  imageTitle ~ imageLink <> (NewsFeed, NewsFeed.unapply _)
}

case class NewsFeedInfo(
    feed: NewsFeed,
    numUnread: Integer
)

object NewsFeedCategories extends Table[(Int, Int, Int)]("NewsFeedCategories") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def feedId = column[Int]("feedId")
    def categoryId = column[Int]("categoryId")
  
    def * = id ~ feedId ~ categoryId
  
    def feed = foreignKey("feedIdentifierKey", feedId, NewsFeeds)(_.id)
    def category = foreignKey("categoryIdKey", categoryId, Categories)(_.id)
}

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

object NewsFeedArticles extends Table[NewsFeedArticle]("NewsFeedArticles") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def feedId = column[Int]("feedId")
	def title = column[String]("title")
	def link = column[String]("link")
	def description = column[String]("description")
	
	def author = column[Option[String]]("author")
	def comments = column[Option[String]]("comments")
	def enclosureUrl = column[Option[String]]("enclosureUrl")
	def enclosureLength = column[Option[Int]]("enclosureLength")
	def enclosureType = column[Option[String]]("enclosureType")
	def guid = column[Option[String]]("guid")
	def isGuidPermalink = column[Option[Boolean]]("isGuidPermalink")
	def pubDate = column[Option[Timestamp]]("pubDate")
	def source = column[Option[String]]("source")
	
	def * = 
	  id.? ~ feedId ~ title ~ link ~ description ~ author ~ comments ~
	  enclosureUrl ~ enclosureLength ~ enclosureType ~ guid ~ isGuidPermalink ~
	  pubDate ~ source <> (NewsFeedArticle, NewsFeedArticle.unapply _)
	  
	def feed = foreignKey("feedIdKey", feedId, NewsFeeds)(_.id)
}

object NewsFeedArticleCategories extends Table[(Int, Int, Int)]("NewsFeedArticleCategories") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def articleId = column[Int]("articleIdentifier")
    def categoryId = column[Int]("categoryId")
  
    def * = id ~ articleId ~ categoryId
  
    def article = foreignKey("articleIdentifierKey", articleId, NewsFeedArticles)(_.id)
    def category = foreignKey("categoryFeedIdKey", categoryId, Categories)(_.id)
}

case class User(
    id: Option[Int],
    username: String,
    password: String,
    google_api_token: String)
   
object Users extends Table[User]("Users") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def username = column[String]("username")
  def password = column[String]("password")
  def google_api_token = column[String]("google_api_token")
  
  def * = id.? ~ username ~ password ~ google_api_token <> (User, User.unapply _)
}

case class UserArticle(
    id: Option[Int],
    userId: Int,
    articleId: Int,
    articleRead: Boolean)

object UserArticles extends Table[UserArticle]("UserArticles") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def userId = column[Int]("userId")
  def articleId = column[Int]("articleId")
  def articleRead = column[Boolean]("articleRead")
  
  def * = id.? ~ userId ~ articleId ~ articleRead <> (UserArticle, UserArticle.unapply _)
  
  def article = foreignKey("userArticleIdKey", articleId, NewsFeedArticles)(_.id)
  def user = foreignKey("userArticleUserIdKey", userId, Users)(_.id)
}

case class UserFeed(
    id: Option[Int],
    userId: Int,
    feedId: Int)

object UserFeeds extends Table[UserFeed]("UserFeeds") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def userId = column[Int]("userId")
  def feedId = column[Int]("feedId")
  
  def * = id.? ~ userId ~ feedId <> (UserFeed, UserFeed.unapply _)
  
  def feed = foreignKey("userFeedIdKey", feedId, NewsFeeds)(_.id)
  def user = foreignKey("userFeedUserIdKey", userId, Users)(_.id)
}

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
          var destFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
          var date = DateTime.parse(x).toDate()
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