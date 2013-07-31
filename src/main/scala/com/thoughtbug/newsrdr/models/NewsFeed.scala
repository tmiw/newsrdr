package com.thoughtbug.newsrdr.models

import scala.xml._
import java.sql.Timestamp;

// TODO: support drivers other than H2

// Use H2Driver to connect to an H2 database
import scala.slick.driver.H2Driver.simple._

// Use the implicit threadLocalSession
import Database.threadLocalSession

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
    articleId: Int,
    articleRead: Boolean)

object UserArticles extends Table[UserArticle]("UserArticles") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def articleId = column[Int]("articleId")
  def articleRead = column[Boolean]("articleRead")
  
  def * = id.? ~ articleId ~ articleRead <> (UserArticle, UserArticle.unapply _)
  
  def article = foreignKey("userArticleIdKey", articleId, NewsFeedArticles)(_.id)
}

class RSSFeed {
    var feedProperties : NewsFeed = _
    var feedCategories : List[String] = _
    var entries : List[(NewsFeedArticle, List[String])] = _
    
    def load(url: String) = {
        val xmlDoc = XML.load(url)
        
        feedProperties = NewsFeed(
            None,
            (xmlDoc \\ "title").text,
            (xmlDoc \\ "link").text,
            (xmlDoc \\ "description").text,
            url,
            generateOptionValue((xmlDoc \\ "language").text),
            generateOptionValue((xmlDoc \\ "copyright").text),
            generateOptionValue((xmlDoc \\ "managingEditor").text),
            generateOptionValue((xmlDoc \\ "webMaster").text),
            generateOptionValueTimestamp((xmlDoc \\ "pubDate").text),
            generateOptionValueTimestamp((xmlDoc \\ "lastBuildDate").text),
            generateOptionValue((xmlDoc \\ "generator").text),
            generateOptionValue((xmlDoc \\ "docs").text),
            generateOptionValueInt((xmlDoc \\ "ttl").text),
            generateOptionValue((xmlDoc \\ "image" \ "url").text),
            generateOptionValue((xmlDoc \\ "image" \ "title").text),
            generateOptionValue((xmlDoc \\ "image" \ "link").text)
            )
        
        feedCategories = (xmlDoc \\ "channel" \ "category").map((x) => x.text).toList
        
        entries = (xmlDoc \\ "item").map(createArticle).toList
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
    
    private def generateOptionValue(x: String) : Option[String] = {
        if (x.isEmpty) { Some(x) }
        else { None }
    }
    
    private def generateOptionValueTimestamp(x: String) : Option[Timestamp] = {
        if (x.isEmpty) { Some(Timestamp.valueOf(x)) }
        else { None }
    }
    
    private def generateOptionValueInt(x: String) : Option[Int] = {
        if (x.isEmpty) { Some(x.toInt) }
        else { None }
    }
    
    private def generateOptionValueBool(x: String) : Option[Boolean] = {
        if (x.isEmpty) { Some(x.toBoolean) }
        else { None }
    }
}