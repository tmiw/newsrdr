package com.thoughtbug.newsrdr.models

import scala.slick.driver.{ExtendedProfile, H2Driver, MySQLDriver}
import java.sql.Timestamp

class DataTables(val driver: ExtendedProfile) {
	import driver.simple._
	
	object Categories extends Table[Category]("Categories") {
		def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
		def name = column[String]("name")
	
		def * = id.? ~ name <> (Category, Category.unapply _)
	}
	
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
	
	object Users extends Table[User]("Users") {
	  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	  def username = column[String]("username")
	  def password = column[String]("password")
	  def email = column[String]("email")
	  
	  def * = id.? ~ username ~ password ~ email <> (User, User.unapply _)
	}
	
	object UserSessions extends Table[UserSession]("UserSessions") {
	  def userId = column[Int]("userId")
	  def sessionId = column[String]("sessionId")
	  def * = userId ~ sessionId <> (UserSession, UserSession.unapply _)
	  def bIdx1 = index("userSessionKey", userId ~ sessionId, unique = true)
	  def user = foreignKey("userSessionUserKey", userId, Users)(_.id)
	}
	
	object UserArticles extends Table[UserArticle]("UserArticles") {
	  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	  def userId = column[Int]("userId")
	  def articleId = column[Int]("articleId")
	  def articleRead = column[Boolean]("articleRead")
	  
	  def * = id.? ~ userId ~ articleId ~ articleRead <> (UserArticle, UserArticle.unapply _)
	  
	  def article = foreignKey("userArticleIdKey", articleId, NewsFeedArticles)(_.id)
	  def user = foreignKey("userArticleUserIdKey", userId, Users)(_.id)
	}
	
	object UserFeeds extends Table[UserFeed]("UserFeeds") {
	  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	  def userId = column[Int]("userId")
	  def feedId = column[Int]("feedId")
	  
	  def * = id.? ~ userId ~ feedId <> (UserFeed, UserFeed.unapply _)
	  
	  def feed = foreignKey("userFeedIdKey", feedId, NewsFeeds)(_.id)
	  def user = foreignKey("userFeedUserIdKey", userId, Users)(_.id)
	}
	
	def create(implicit session: Session) = {
	  (Categories.ddl ++ NewsFeeds.ddl ++ NewsFeedCategories.ddl ++
          NewsFeedArticles.ddl ++ NewsFeedArticleCategories.ddl ++
          Users.ddl ++ UserArticles.ddl ++ UserFeeds.ddl ++ UserSessions.ddl).create
	}
	
	def getSubscribedFeeds(implicit session: Session, userId: Int) : List[NewsFeed] = {
	  (for { 
		  uf <- UserFeeds if uf.userId === userId
          f <- NewsFeeds if f.id === uf.feedId
       } yield f).list
	}
	
	def getUnreadCountForFeed(implicit session: Session, userId: Int, feedId: Int) : Int = {
	  var feed_posts = for { 
		  (nfa, ua) <- Query(NewsFeedArticles) leftJoin UserArticles on (_.id === _.articleId)
            	       if nfa.feedId === feedId
          uf <- UserFeeds if uf.userId === userId && nfa.feedId === uf.feedId
      } yield (nfa, ua.articleRead.?)
      (for { (fp, fq) <- feed_posts.list if fq.getOrElse(false) == false } yield fp ).length
	}
	
	def getFeedFromUrl(implicit session: Session, url: String) : Option[NewsFeed] = {
	  var feedQuery = for { f <- NewsFeeds if f.feedUrl === url } yield f
	  feedQuery.firstOption
	}
	
	def addSubscriptionIfNotExists(implicit session: Session, userId: Int, feedId: Int) {
	  var userFeed = for { uf <- UserFeeds if uf.userId === userId && uf.feedId === feedId } yield uf
	  userFeed.firstOption match {
	    case Some(uf) => ()
	    case None => {
	      UserFeeds.insert(UserFeed(None, userId, feedId))
	      ()
	    }
	  }
	}
	
	def unsubscribeFeed(implicit session: Session, userId: Int, feedId: Int) {
	  var userFeed = for { uf <- UserFeeds if uf.userId === userId && uf.feedId === feedId } yield uf
	  userFeed.delete
	}
	
	def getPostsForFeed(implicit session: Session, userId: Int, feedId: Int, unreadOnly: Boolean, offset: Int, maxEntries: Int) : List[NewsFeedArticleInfo] = {
	  var feed_posts = for { 
	    (nfa, ua) <- Query(NewsFeedArticles).sortBy(_.pubDate.desc) leftJoin UserArticles on (_.id === _.articleId)
	                 if nfa.feedId === feedId
	    uf <- UserFeeds if uf.userId === userId && nfa.feedId === uf.feedId
	  } yield (nfa, ua.articleRead.?)
	    
	  unreadOnly match {
	    case true => 
	      (for { (p, q) <- feed_posts.list if q.getOrElse(false) == false } yield NewsFeedArticleInfo(p, true)).drop(offset).take(maxEntries)
	    case _ =>
	      (for { (fp, fq) <- feed_posts.list } yield NewsFeedArticleInfo(fp, fq.getOrElse(false) == false)).drop(offset).take(maxEntries)
	  }
	}
	
	def getPostsForAllFeeds(implicit session: Session, userId: Int, unreadOnly: Boolean, offset: Int, maxEntries: Int) : List[NewsFeedArticleInfo] = {
	  var feed_posts = for { 
	    (nfa, ua) <- NewsFeedArticles leftJoin UserArticles on (_.id === _.articleId)
	    uf <- UserFeeds if uf.userId === userId && nfa.feedId === uf.feedId
	  } yield (nfa, ua.articleRead.?)
	    
	  unreadOnly match {
	    case true => 
	      (for { (p, q) <- feed_posts.sortBy(_._1.pubDate.desc).list if q.getOrElse(false) == false } yield NewsFeedArticleInfo(p, true)).drop(offset).take(maxEntries)
	    case _ =>
	      (for { (fp, fq) <- feed_posts.sortBy(_._1.pubDate.desc).list } yield NewsFeedArticleInfo(fp, fq.getOrElse(false) == false)).drop(offset).take(maxEntries)
	  }
	}
	
	def setPostStatus(implicit session: Session, userId: Int, feedId: Int, postId: Int, unread: Boolean) : Boolean = {
	  var my_feed = for { uf <- UserFeeds if uf.feedId === feedId && uf.userId === userId } yield uf
      my_feed.firstOption match {
        case Some(_) => {
	      var feed_posts = for {
	        (nfa, ua) <- NewsFeedArticles leftJoin UserArticles on (_.id === _.articleId)
	            	     if nfa.feedId === feedId && ua.articleId === postId
	        uf <- UserFeeds if uf.userId === userId && nfa.feedId === uf.feedId
	      } yield ua
	      feed_posts.firstOption match {
	        case Some(x) => {
	          var single_feed_post = for { ua <- UserArticles if ua.userId === x.userId && ua.articleId === x.articleId } yield ua
	          single_feed_post.update(UserArticle(x.id, x.userId, x.articleId, !unread))
	        }
	        case None => UserArticles.insert(UserArticle(None, userId, postId, !unread))
	      }
	      true
	    }
	    case _ => false
	  }
	}
	
	def setPostStatus(implicit session: Session, userId: Int, postId: Int, unread: Boolean) : Boolean = {
	  var post_exists = for {
	    nfa <- NewsFeedArticles if nfa.id === postId
	    uf <- UserFeeds if uf.userId === userId && nfa.feedId === uf.feedId
	  } yield nfa
	  
	  post_exists.firstOption match {
	    case Some(article) => {
	      var feed_posts = for {
	        (nfa, ua) <- NewsFeedArticles leftJoin UserArticles on (_.id === _.articleId)
	            	     if ua.articleId === postId
	      } yield ua
	      feed_posts.firstOption match {
	        case Some(x) => {
	          var single_feed_post = for { ua <- UserArticles if ua.userId === x.userId && ua.articleId === x.articleId } yield ua
	          single_feed_post.update(UserArticle(x.id, x.userId, x.articleId, !unread))
	        }
	        case None => UserArticles.insert(UserArticle(None, userId, postId, !unread))
	      }
	      true
	    }
	    case None => false
	  }
	}
	
	def getUserSession(implicit session: Session, sessionId: String) : Option[UserSession] = {
	  var q = (for { sess <- UserSessions if sess.sessionId === sessionId } yield sess)
	  q.firstOption
	}
	
	def invalidateSession(implicit session: Session, sessionId: String) {
	  var q = (for { sess <- UserSessions if sess.sessionId === sessionId } yield sess)
	  q.firstOption match {
        case Some(s) => q.delete
        case None => ()
      }
	}
	
	def startUserSession(implicit session: Session, sessionId: String, email: String) {
	  val q = for { u <- Users if u.username === email } yield u
      var userId = q.firstOption match {
        case Some(u) => u.id.get
        case None => {
          Users returning Users.id insert User(None, email, "", email)
        }
      }
      UserSessions.insert(UserSession(userId, sessionId))
	}
	
	def updateOrInsertFeed(implicit session: Session, feedUrl: String, feed: XmlFeed) : NewsFeed = {
	  val feedQuery = Query(NewsFeeds)
      val newsFeed = 
        for { f <- NewsFeeds if f.feedUrl === feedUrl } yield
        (f.copyright ~ f.description ~ f.docs ~ f.generator ~ f.imageLink ~
         f.imageTitle ~ f.imageUrl ~ f.language ~ f.lastBuildDate ~ f.link ~
         f.managingEditor ~ f.pubDate ~ f.title ~ f.ttl ~ f.webMaster)
      
      newsFeed.firstOption match {
          case Some(fd) => {
            newsFeed.update(
		        (feed.feedProperties.copyright, 
		         feed.feedProperties.description,
		         feed.feedProperties.docs, 
		         feed.feedProperties.generator,
		         feed.feedProperties.imageLink,
		         feed.feedProperties.imageTitle, 
		         feed.feedProperties.imageUrl, 
		         feed.feedProperties.language, 
		         feed.feedProperties.lastBuildDate, 
		         feed.feedProperties.link,
		         feed.feedProperties.managingEditor, 
		         feed.feedProperties.pubDate, 
		         feed.feedProperties.title, 
		         feed.feedProperties.ttl, 
		         feed.feedProperties.webMaster))
          }
          case None => {
            (NewsFeeds.feedUrl ~ NewsFeeds.copyright ~ NewsFeeds.description ~ NewsFeeds.docs ~ NewsFeeds.generator ~ NewsFeeds.imageLink ~
             NewsFeeds.imageTitle ~ NewsFeeds.imageUrl ~ NewsFeeds.language ~ NewsFeeds.lastBuildDate ~ NewsFeeds.link ~
             NewsFeeds.managingEditor ~ NewsFeeds.pubDate ~ NewsFeeds.title ~ NewsFeeds.ttl ~ NewsFeeds.webMaster).insert(
                 feedUrl,
                 feed.feedProperties.copyright, 
		         feed.feedProperties.description,
		         feed.feedProperties.docs, 
		         feed.feedProperties.generator,
		         feed.feedProperties.imageLink,
		         feed.feedProperties.imageTitle, 
		         feed.feedProperties.imageUrl, 
		         feed.feedProperties.language, 
		         feed.feedProperties.lastBuildDate, 
		         feed.feedProperties.link,
		         feed.feedProperties.managingEditor, 
		         feed.feedProperties.pubDate, 
		         feed.feedProperties.title, 
		         feed.feedProperties.ttl, 
		         feed.feedProperties.webMaster
            )
          }
        }
    
      val newsFeedId = (for { f <- NewsFeeds if f.feedUrl === feedUrl } yield f.id).first
            
      // Insert categories that don't exist, then refresh feed categories with the current
      // set.
      val categoryIds = feed.feedCategories.map((c) => {
	    val feedQuery = for { fc <- Categories if fc.name === c } yield fc
	    feedQuery.firstOption match {
	      case Some(cat) => (newsFeedId, cat.id.get)
	      case None => { 
	        val newId = (Categories.name) returning Categories.id insert(c)
	        (newsFeedId, newId)
	      }
	    }
      })
      val newsFeedCategories = for { nfc <- NewsFeedCategories if nfc.feedId === newsFeedId } yield nfc
      newsFeedCategories.delete
      for { c <- categoryIds } {
        (NewsFeedCategories.feedId ~ NewsFeedCategories.categoryId).insert(c)
      }
      
      // Now update/insert each individual post in the feed.
      for { p <- feed.entries } insertOrUpdateEntry(session, newsFeedId, p)
      
      (for { f <- NewsFeeds if f.feedUrl === feedUrl } yield f).first
	}
	
	private def insertOrUpdateEntry(implicit session: Session, feedId: Int, p: (NewsFeedArticle, List[String])) {
      val newPost = p._1
    
      // Insert or update article as needed.
      val existingEntryId = for { 
        e <- NewsFeedArticles if (e.guid =!= (None : Option[String]) && e.guid === newPost.guid) || 
                                 (e.title === newPost.title && e.link === newPost.link && e.description === newPost.description)
      } yield e.id
      val entry = for { 
        e <- NewsFeedArticles if (e.guid =!= (None : Option[String]) && e.guid === newPost.guid) || 
                                 (e.title === newPost.title && e.link === newPost.link && e.description === newPost.description)
      } yield (
          e.title ~ e.link ~ e.description ~ e.author ~ e.comments ~ e.enclosureLength ~ 
          e.enclosureType ~ e.enclosureUrl ~ e.guid ~ e.isGuidPermalink ~ e.pubDate ~
          e.source)
      val entryId = entry.firstOption match {
        case Some(ent) => {
          entry.update(
            newPost.title,
            newPost.link,
            newPost.description,
            newPost.author,
            newPost.comments,
            newPost.enclosureLength,
            newPost.enclosureType,
            newPost.enclosureUrl,
            newPost.guid,
            newPost.isGuidPermalink,
            newPost.pubDate,
            newPost.source)
          existingEntryId.first
        }
        case None => (
            NewsFeedArticles.feedId ~ NewsFeedArticles.title ~ NewsFeedArticles.link ~ NewsFeedArticles.description ~
            NewsFeedArticles.author ~ NewsFeedArticles.comments ~ NewsFeedArticles.enclosureLength ~
            NewsFeedArticles.enclosureType ~ NewsFeedArticles.enclosureUrl ~ NewsFeedArticles.guid ~
            NewsFeedArticles.isGuidPermalink ~ NewsFeedArticles.pubDate ~ NewsFeedArticles.source) returning NewsFeedArticles.id insert(
                feedId,
                newPost.title,
	            newPost.link,
	            newPost.description,
	            newPost.author,
	            newPost.comments,
	            newPost.enclosureLength,
	            newPost.enclosureType,
	            newPost.enclosureUrl,
	            newPost.guid,
	            newPost.isGuidPermalink,
	            newPost.pubDate,
	            newPost.source
            )
      }
      
      // Update feed categories.
      val categoryIds = p._2.map((c) => {
	    val feedQuery = for { fc <- Categories if fc.name === c } yield fc
	    feedQuery.firstOption match {
	      case Some(cat) => (entryId, cat.id.get)
	      case None => { 
	        val newId = (Categories.name) returning Categories.id insert(c)
	        (entryId, newId)
	      }
	    }
      })
      val postCategories = for { nfc <- NewsFeedArticleCategories if nfc.articleId === entryId } yield nfc
      postCategories.delete
      for { c <- categoryIds } {
        (NewsFeedArticleCategories.articleId ~ NewsFeedArticleCategories.categoryId).insert(c)
      }
  }
}