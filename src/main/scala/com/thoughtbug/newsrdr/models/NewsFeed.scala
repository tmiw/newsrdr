package com.thoughtbug.newsrdr.models

// TODO: support drivers other than H2

// Use H2Driver to connect to an H2 database
import scala.slick.driver.H2Driver.simple._

// Use the implicit threadLocalSession
import Database.threadLocalSession

object NewsFeed extends Table[(Int, String, String)]("NewsFeed") {
	def feedId = column[Int]("FeedIdentifier", O.PrimaryKey)
	def feedName = column[String]("FeedName")
	def feedUrl = column[String]("FeedUrl")
	
	def * = feedId ~ feedName ~ feedUrl
}

/*object NewsFeedArticle extends Table[(Int, Int, String, String)]("NewsFeedArticle") {
	def articleId = column[Int]("ArticleIdentifier", O.PrimaryKey)
	def feedId = column[Int]("FeedIdentifier")
	def articleTitle = column[String]("ArticleTitle")
}*/