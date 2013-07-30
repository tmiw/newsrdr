package com.thoughtbug.newsrdr.models

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
    def feedId = column[Int]("feedIdentifier")
    def categoryId = column[Int]("categoryId")
  
    def * = id ~ feedId ~ categoryId
  
    def feed = foreignKey("feedIdentifier", id, NewsFeeds)(_.id)
    def category = foreignKey("feedCategory", id, Categories)(_.id)
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
	  
	def feed = foreignKey("feedIdentifier", id, NewsFeeds)(_.id)
}

object NewsFeedArticleCategories extends Table[(Int, Int, Int)]("NewsFeedArticleCategories") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def articleId = column[Int]("articleIdentifier")
    def categoryId = column[Int]("categoryId")
  
    def * = id ~ articleId ~ categoryId
  
    def article = foreignKey("articleIdentifier", id, NewsFeedArticles)(_.id)
    def category = foreignKey("articleCategory", id, Categories)(_.id)
}