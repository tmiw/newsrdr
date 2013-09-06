package us.newsrdr.models

import us.newsrdr.tasks._
import scala.slick.driver.{ExtendedProfile, H2Driver, MySQLDriver}
import scala.slick.jdbc.meta.{MTable}
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import java.sql.Timestamp

class DataTables(val driver: ExtendedProfile) {
	import driver.simple._
	
	// The amount we need to subtract the add date by so we
	// don't end up getting posts that are years old in the
	// results.
	val OLDEST_POST_DIFFERENCE_MS = 1000 * 60 * 60 * 24 * 14
	val OLDEST_POST_DIFFERENCE_SEC = OLDEST_POST_DIFFERENCE_MS / 1000
	
	// UNIX_TIMESTAMP support
	val unixTimestampFn = SimpleFunction.unary[Timestamp, Int]("UNIX_TIMESTAMP")
	
	case class FeedFailureLog(
	  id: Option[Int],
      feedId: Int,
      failureDate: Timestamp,
      failureMessage: String)
    
    object FeedFailureLogs extends Table[FeedFailureLog]("FeedFailureLogs") {
	  def id = column[Option[Int]]("id", O.PrimaryKey, O.AutoInc)
	  def feedId = column[Int]("feedId")
	  def failureDate = column[Timestamp]("failureDate")
	  def failureMessage = column[String]("failureMessage")
	  
	  def * = id ~ feedId ~ failureDate ~ failureMessage <> (FeedFailureLog, FeedFailureLog.unapply _)
	  
	  def feed = foreignKey("feedIdentifierLogKey", feedId, NewsFeeds)(_.id)
	}
	
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
		def lastUpdate = column[Timestamp]("lastUpdate")
		
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
		  imageTitle ~ imageLink ~ lastUpdate <> (NewsFeed, NewsFeed.unapply _)
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
	  def friendlyName = column[String]("friendlyName")
	  def optOutSharing = column[Boolean]("optOutSharing")
	  
	  def * = id.? ~ username ~ password ~ email ~ friendlyName ~ optOutSharing <> (User, User.unapply _)
	}
	
	object UserSessions extends Table[UserSession]("UserSessions") {
	  def userId = column[Int]("userId")
	  def sessionId = column[String]("sessionId")
	  def lastAccess = column[Timestamp]("lastAccess")
	  def lastAccessIp = column[String]("lastAccessIp")
	  
	  def * = userId ~ sessionId ~ lastAccess ~ lastAccessIp <> (UserSession, UserSession.unapply _)
	  def bIdx1 = index("userSessionKey", userId ~ sessionId, unique = true)
	  def user = foreignKey("userSessionUserKey", userId, Users)(_.id)
	}
	
	object UserArticles extends Table[UserArticle]("UserArticles") {
	  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	  def userId = column[Int]("userId")
	  def articleId = column[Int]("articleId")
	  def articleRead = column[Boolean]("articleRead")
	  def articleSaved = column[Boolean]("articleSaved")
	  
	  def * = id.? ~ userId ~ articleId ~ articleRead ~ articleSaved <> (UserArticle, UserArticle.unapply _)
	  
	  def article = foreignKey("userArticleIdKey", articleId, NewsFeedArticles)(_.id)
	  def user = foreignKey("userArticleUserIdKey", userId, Users)(_.id)
	}
	
	object UserFeeds extends Table[UserFeed]("UserFeeds") {
	  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	  def userId = column[Int]("userId")
	  def feedId = column[Int]("feedId")
	  def addedDate = column[Timestamp]("addedDate")
	  
	  def * = id.? ~ userId ~ feedId ~ addedDate <> (UserFeed, UserFeed.unapply _)
	  
	  def feed = foreignKey("userFeedIdKey", feedId, NewsFeeds)(_.id)
	  def user = foreignKey("userFeedUserIdKey", userId, Users)(_.id)
	}

	def create(implicit session: Session) = {
      if (!MTable.getTables.list.exists(_.name.name == Categories.tableName)) Categories.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == NewsFeeds.tableName)) NewsFeeds.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == NewsFeedCategories.tableName)) NewsFeedCategories.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == NewsFeedArticles.tableName)) NewsFeedArticles.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == NewsFeedArticleCategories.tableName)) NewsFeedArticleCategories.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == Users.tableName)) Users.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == UserArticles.tableName)) UserArticles.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == UserFeeds.tableName)) UserFeeds.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == UserSessions.tableName)) UserSessions.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == FeedFailureLogs.tableName)) FeedFailureLogs.ddl.create
	}

	def getSubscribedFeeds(implicit session: Session, userId: Int) : List[(NewsFeed, Int)] = {
	  val queryString = if (driver.isInstanceOf[H2Driver]) {
	    """
	      select "uf"."id", count(*) as unread 
	      from "UserFeeds" "uf"
	          inner join "NewsFeedArticles" "nfa" on "nfa"."feedId" = "uf"."feedId" 
	          left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
	      where "uf"."userId" = ? and
                ("ua"."articleRead" is null or "ua"."articleRead" = false) and
	            UNIX_TIMESTAMP("nfa"."pubDate") >= (UNIX_TIMESTAMP("uf"."addedDate") - (60*60*24*14)) and
		        UNIX_TIMESTAMP("nfa"."pubDate") < UNIX_TIMESTAMP(CURRENT_TIMESTAMP())
	      group by "uf"."id"
	    """
	  } else {
	    """
	      select uf.id, count(*) as unread 
	      from UserFeeds uf
	          inner join NewsFeedArticles nfa on nfa.feedId = uf.feedId 
	          left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
	      where uf.userId = ? and
                (ua.articleRead is null or ua.articleRead = false) and
	            UNIX_TIMESTAMP(nfa.pubDate) >= (UNIX_TIMESTAMP(uf.addedDate) - (60*60*24*14)) and
	            UNIX_TIMESTAMP(nfa.pubDate) < UNIX_TIMESTAMP(UTC_TIMESTAMP())
	      group by uf.id
	    """
	  }
	  val unreadCountQuery = Q.query[Int, (Int, Int)](queryString)
	  val q = unreadCountQuery.list(userId)
	  
	  val feedMap = Map() ++ q.map(x => Pair(x._1, x._2))
	  val feedIds = q.map(_._1)
	  
	  (for {
	    f <- NewsFeeds
	    uf <- UserFeeds if f.id === uf.feedId && uf.userId === userId
	  } yield (uf.id, f)).sortBy(_._2.title).list.map(x => (x._2, feedMap.getOrElse(x._1, 0)))
	}
	
	def getUnreadCountForFeed(implicit session: Session, userId: Int, feedId: Int) : Int = {
	  val today = new Timestamp(new java.util.Date().getTime())
	  
	  val feed_posts = for { 
		  (nfa, ua) <- Query(NewsFeedArticles) leftJoin UserArticles on (_.id === _.articleId)
            	       if nfa.feedId === feedId
          uf <- UserFeeds if uf.userId === userId && nfa.feedId === uf.feedId
      } yield (nfa, uf, ua.articleRead.?)
      
      val feed_posts2 = for {
	    (nfa, uf, read) <- feed_posts.list 
	    	if nfa.pubDate.getOrElse(today).compareTo(new Timestamp(uf.addedDate.getTime() - OLDEST_POST_DIFFERENCE_MS)) >= 0
	  } yield (nfa, read)
	  
      (for { (fp, fq) <- feed_posts2 if fq.getOrElse(false) == false } yield fp ).length
	}
	
	def getFeedFromUrl(implicit session: Session, url: String) : Option[NewsFeed] = {
	  val feedQuery = for { f <- NewsFeeds if f.feedUrl === url } yield f
	  feedQuery.firstOption
	}
	
	def addSubscriptionIfNotExists(implicit session: Session, userId: Int, feedId: Int) {	  
	  val userFeed = for { uf <- UserFeeds if uf.userId === userId && uf.feedId === feedId } yield uf
	  userFeed.firstOption match {
	    case Some(uf) => ()
	    case None => {
	      UserFeeds.insert(UserFeed(None, userId, feedId, new Timestamp(new java.util.Date().getTime())))
	      ()
	    }
	  }
	}
	
	def unsubscribeFeed(implicit session: Session, userId: Int, feedId: Int) {
	  val userFeed = for { uf <- UserFeeds if uf.userId === userId && uf.feedId === feedId } yield uf
	  userFeed.delete
	  
	  val numSubscribed = for { uf <- UserFeeds if uf.feedId === feedId } yield uf
	  if (numSubscribed.list.count(_ => true) == 0)
	  {
	    val feed = for { f <- NewsFeeds if f.id === feedId } yield f.feedUrl
	    BackgroundJobManager.unscheduleFeedJob(feed.list.head)
	  }
	}
	
	def getFeedByPostId(implicit session: Session, postId: Int) : NewsFeed = {
		val feed = for {
		  (nfa, nf) <- NewsFeedArticles innerJoin NewsFeeds on (_.feedId === _.id) if nfa.id === postId
		} yield nf
		return feed.first
	}
	
	def getNewestRandomPost(implicit session: Session) : Option[NewsFeedArticleInfo] = {
	  val today = new java.sql.Timestamp(new java.util.Date().getTime())
	  val yesterday = new java.sql.Timestamp(today.getTime() - 60*60*24*1000)
	  
	  val articleQuery = 
	    for {
	      nfa <- NewsFeedArticles if unixTimestampFn(nfa.pubDate.get) >= unixTimestampFn(yesterday)
	      nf <- NewsFeeds if nfa.feedId === nf.id
	      uf <- UserFeeds if nf.id === uf.feedId
	      u <- Users if u.optOutSharing === false && u.id === uf.userId
	    } yield nfa

	  val length = Query(articleQuery.length).first
	  if (length > 0) {
	    val rng = new util.Random()
	    val randId = rng.nextInt(length)
	    articleQuery.drop(randId).firstOption match {
	      case Some(a) => Some(NewsFeedArticleInfo(a, false, false))
	      case _ => None
	    }
	  } else None
	}
	
	def getPostsForFeed(implicit session: Session, userId: Int, feedId: Int, unreadOnly: Boolean, offset: Int, maxEntries: Int, latestPostId: Long) : List[NewsFeedArticleInfo] = {
	  implicit val getNewsFeedArticleResult = GetResult(r => NewsFeedArticle(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

	  val feed_posts = if (driver.isInstanceOf[H2Driver]) {
	    if (unreadOnly) {
	      Q.query[(Int, Int, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
	        select "nfa".*, (case when "ua"."articleRead" is null then 0 else "ua"."articleRead" end) as isRead,
	          (case when "ua"."articleSaved" is null then 0 else "ua"."articleSaved" end) as isSaved
	        from "NewsFeedArticles" "nfa"
	        inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
	        left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
	                  "uf"."feedId" = ? and
	                  unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
	                  unix_timestamp("nfa"."pubDate") <= ? and
	                  ("ua"."articleRead" is null or "ua"."articleRead" = 0)
	        order by "nfa"."pubDate" desc
	        limit ? offset ?""")
	    } else {
	      Q.query[(Int, Int, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
	        select "nfa".*, (case when "ua"."articleRead" is null then 0 else "ua"."articleRead" end) as isRead,
	          (case when "ua"."articleSaved" is null then 0 else "ua"."articleSaved" end) as isSaved
	        from "NewsFeedArticles" "nfa"
	        inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
	        left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
	                  "uf"."feedId" = ? and
	                  unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                      unix_timestamp("nfa"."pubDate") <= ?
	        order by "nfa"."pubDate" desc
	        limit ? offset ?""")
	    }
	  } else {
	    if (unreadOnly) {
	      Q.query[(Int, Int, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
	        select nfa.*, (case when ua.articleRead is null then 0 else ua.articleRead end) as isRead,
	          (case when ua.articleSaved is null then 0 else ua.articleSaved end) as isSaved
	        from NewsFeedArticles nfa
	        inner join UserFeeds uf on uf.feedId = nfa.feedId
	        left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and 
	                  uf.feedId = ? and
	                  unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                      unix_timestamp(nfa.pubDate) <= ? and
	                  (ua.articleRead is null or ua.articleRead = 0)
	        order by nfa.pubDate desc
	        limit ? offset ?""")
	    } else {
	      Q.query[(Int, Int, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
	        select nfa.*, (case when ua.articleRead is null then 0 else ua.articleRead end) as isRead,
	          (case when ua.articleSaved is null then 0 else ua.articleSaved end) as isSaved
	        from NewsFeedArticles nfa
	        inner join UserFeeds uf on uf.feedId = nfa.feedId
	        left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and
	                  uf.feedId = ? and
	                  unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                      unix_timestamp(nfa.pubDate) <= ?
	        order by nfa.pubDate desc
	        limit ? offset ?""")
	    }
	  }
	  
	  feed_posts.list((userId, feedId, latestPostId, maxEntries, offset)).map(x => {
	    NewsFeedArticleInfo(x._1, x._2 == false, x._3)
	  })
	}
	
	def getPostsForAllFeeds(implicit session: Session, userId: Int, unreadOnly: Boolean, offset: Int, maxEntries: Int, latestPostId: Long) : List[NewsFeedArticleInfo] = {
	  implicit val getNewsFeedArticleResult = GetResult(r => NewsFeedArticle(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

	  val feed_posts = if (driver.isInstanceOf[H2Driver]) {
	    if (unreadOnly) {
	      Q.query[(Int, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
	        select "nfa".*, (case when "ua"."articleRead" is null then 0 else "ua"."articleRead" end) as isRead,
	          (case when "ua"."articleSaved" is null then 0 else "ua"."articleSaved" end) as isSaved
	        from "NewsFeedArticles" "nfa"
	        inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
	        left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
	                  unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                      unix_timestamp("nfa"."pubDate") <= ? and
	                  ("ua"."articleRead" is null or "ua"."articleRead" = 0)
	        order by "nfa"."pubDate" desc
	        limit ? offset ?""")
	    } else {
	      Q.query[(Int, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
	        select "nfa".*, (case when "ua"."articleRead" is null then 0 else "ua"."articleRead" end) as isRead,
	          (case when "ua"."articleSaved" is null then 0 else "ua"."articleSaved" end) as isSaved
	        from "NewsFeedArticles" "nfa"
	        inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId" and "ua"."userId" = "uf"."userId"
	        left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" 
                where "uf"."userId" = ? and 
	                  unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                      unix_timestamp("nfa"."pubDate") <= ?
	        order by "nfa"."pubDate" desc
	        limit ? offset ?""")
	    }
	  } else {
	    if (unreadOnly) {
	      Q.query[(Int, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
	        select nfa.*, (case when ua.articleRead is null then 0 else ua.articleRead end) as isRead,
	          (case when ua.articleSaved is null then 0 else ua.articleSaved end) as isSaved
	        from NewsFeedArticles nfa
	        inner join UserFeeds uf on uf.feedId = nfa.feedId
	        left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and 
	                  unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                      unix_timestamp(nfa.pubDate) <= ? and
	                  (ua.articleRead is null or ua.articleRead = 0)
	        order by nfa.pubDate desc
	        limit ? offset ?""")
	    } else {
	      Q.query[(Int, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
	        select nfa.*, (case when ua.articleRead is null then 0 else ua.articleRead end) as isRead,
	          (case when ua.articleSaved is null then 0 else ua.articleSaved end) as isSaved
	        from NewsFeedArticles nfa
	        inner join UserFeeds uf on uf.feedId = nfa.feedId
	        left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and 
	                  unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                      unix_timestamp(nfa.pubDate) <= ?
	        order by nfa.pubDate desc
	        limit ? offset ?""")
	    }
	  }
	  
	  feed_posts.list((userId, latestPostId, maxEntries, offset)).map(x => {
	    NewsFeedArticleInfo(x._1, x._2 == false, x._3)
	  })
	}
	
	def getSavedPosts(implicit session: Session, userId: Int, offset: Int, maxEntries: Int, latestPostId: Long) : List[NewsFeedArticleInfo] = {
	  implicit val getNewsFeedArticleResult = GetResult(r => NewsFeedArticle(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

	  val feed_posts = if (driver.isInstanceOf[H2Driver]) {
	    Q.query[(Int, Long, Int, Int), (NewsFeedArticle, Boolean)]("""
	      select "nfa".*, (case when "ua"."articleRead" is null then 0 else "ua"."articleRead" end) as isRead
	      from "NewsFeedArticles" "nfa"
	      inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId" and "ua"."userId" = "uf"."userId"
	      left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" 
              where "uf"."userId" = ? and 
	                unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                    unix_timestamp("nfa"."pubDate") <= ? and
	                "ua"."articleSaved" = 1
	      order by "nfa"."pubDate" desc
	      limit ? offset ?""")
	  } else {
	    Q.query[(Int, Long, Int, Int), (NewsFeedArticle, Boolean)]("""
	      select nfa.*, (case when ua.articleRead is null then 0 else ua.articleRead end) as isRead
	      from NewsFeedArticles nfa
	      inner join UserFeeds uf on uf.feedId = nfa.feedId
	      left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
              where uf.userId = ? and 
	                unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                    unix_timestamp(nfa.pubDate) <= ? and
	                ua.articleSaved = 1
	      order by nfa.pubDate desc
	      limit ? offset ?""")
	  }
	  
	  feed_posts.list((userId, latestPostId, maxEntries, offset)).map(x => {
	    NewsFeedArticleInfo(x._1, false, true)
	  })
	}
	
	def setPostStatusForAllPosts(implicit session: Session, userId: Int, feedId: Int, from: Int, upTo: Int, unread: Boolean) : Boolean = {
	  val today = new java.sql.Timestamp(new java.util.Date().getTime())
	  val my_feed = for { uf <- UserFeeds if uf.feedId === feedId && uf.userId === userId } yield uf
      my_feed.firstOption match {
        case Some(_) => {
	      val feed_posts = for {
	        uf <- UserFeeds if uf.userId === userId && uf.feedId === feedId
	        (nfa, ua) <- NewsFeedArticles leftJoin UserArticles on ((x,y) => x.id === y.articleId && y.userId === userId)
	            	     if nfa.feedId === feedId && (unixTimestampFn(nfa.pubDate.get) >= upTo) &&
	            	        (unixTimestampFn(nfa.pubDate.get) <= from) &&
	            	        unixTimestampFn(nfa.pubDate.get) >= (unixTimestampFn(uf.addedDate) - 60*60*24*14) &&
	            	        (ua.articleRead.isNull || ua.articleRead === false)
	      } yield (nfa, (ua.id.?, ua.userId.?, ua.articleId.?))
	      feed_posts.list.foreach(x => {
	        x._2 match {
	          case (id, Some(uid), Some(aid)) => {
	            val single_feed_post = for { 
	              ua <- UserArticles if ua.userId === uid && ua.articleId === aid 
	            } yield (ua.userId ~ ua.articleId ~ ua.articleRead)
	            single_feed_post.update(uid, aid, !unread)
	          }
	          case _ => UserArticles.insert(UserArticle(None, userId, x._1.id.get, !unread, false))
	        }
	      })
	      true
        }
	    case _ => false
	  }
	}
	
	def setPostStatusForAllPosts(implicit session: Session, userId: Int, from: Int, upTo: Int, unread: Boolean) : Boolean = {
	  val today = new java.sql.Timestamp(new java.util.Date().getTime())
      val feed_posts = for {
	    uf <- UserFeeds if uf.userId === userId
	    (nfa, ua) <- NewsFeedArticles leftJoin UserArticles on 
	                   ((x,y) => x.id === y.articleId && y.userId === userId)
	                 if unixTimestampFn(nfa.pubDate.get) >= upTo && unixTimestampFn(nfa.pubDate.get) <= from &&
	            	    unixTimestampFn(nfa.pubDate.get) >= (unixTimestampFn(uf.addedDate) - 60*60*24*14) &&
	            	    (ua.articleRead.isNull || ua.articleRead === false) &&
	            	    nfa.feedId === uf.feedId
	  } yield (nfa, (ua.id.?, ua.userId.?, ua.articleId.?))
	  feed_posts.list.foreach(x => {
	    x._2 match {
	      case (id, Some(uid), Some(aid)) => {
	        val single_feed_post = for { 
	          ua <- UserArticles if ua.userId === uid && ua.articleId === aid 
	        } yield (ua.userId ~ ua.articleId ~ ua.articleRead)
	        single_feed_post.update(uid, aid, !unread)
	      }
	      case _ => UserArticles.insert(UserArticle(None, userId, x._1.id.get, !unread, false))
	    }
	  })
	  true
	}
	
	def setPostStatus(implicit session: Session, userId: Int, feedId: Int, postId: Int, unread: Boolean) : Boolean = {
	  val my_feed = for { uf <- UserFeeds if uf.feedId === feedId && uf.userId === userId } yield uf
      my_feed.firstOption match {
        case Some(_) => {
	      val feed_posts = for {
	        (nfa, ua) <- NewsFeedArticles leftJoin UserArticles on (_.id === _.articleId)
	            	     if nfa.feedId === feedId && ua.articleId === postId
	        uf <- UserFeeds if uf.userId === userId && nfa.feedId === uf.feedId && uf.userId === ua.userId
	      } yield ua
	      feed_posts.firstOption match {
	        case Some(x) => {
	          val single_feed_post = for { ua <- UserArticles if ua.userId === x.userId && ua.articleId === x.articleId } yield ua
	          single_feed_post.update(UserArticle(x.id, x.userId, x.articleId, !unread, x.articleSaved))
	        }
	        case None => UserArticles.insert(UserArticle(None, userId, postId, !unread, false))
	      }
	      true
	    }
	    case _ => false
	  }
	}
	
	def setPostStatus(implicit session: Session, userId: Int, postId: Int, unread: Boolean) : Boolean = {
	  val post_exists = for {
	    nfa <- NewsFeedArticles if nfa.id === postId
	    uf <- UserFeeds if uf.userId === userId && nfa.feedId === uf.feedId
	  } yield nfa
	  
	  post_exists.firstOption match {
	    case Some(article) => {
	      val feed_posts = for {
	        (nfa, ua) <- NewsFeedArticles leftJoin UserArticles on (_.id === _.articleId)
	            	     if ua.articleId === postId && ua.userId === userId
	      } yield ua
	      feed_posts.firstOption match {
	        case Some(x) => {
	          val single_feed_post = for { ua <- UserArticles if ua.userId === x.userId && ua.articleId === x.articleId } yield ua
	          single_feed_post.update(UserArticle(x.id, x.userId, x.articleId, !unread, x.articleSaved))
	        }
	        case None => UserArticles.insert(UserArticle(None, userId, postId, !unread, false))
	      }
	      true
	    }
	    case None => false
	  }
	}
	
	def savePost(implicit session: Session, userId: Int, feedId: Int, postId: Int) : Boolean = {
	  val my_feed = for { uf <- UserFeeds if uf.feedId === feedId && uf.userId === userId } yield uf
      my_feed.firstOption match {
        case Some(_) => {
	      val feed_posts = for {
	        (nfa, ua) <- NewsFeedArticles leftJoin UserArticles on (_.id === _.articleId)
	            	     if nfa.feedId === feedId && ua.articleId === postId
	        uf <- UserFeeds if uf.userId === userId && nfa.feedId === uf.feedId && uf.userId === ua.userId
	      } yield ua
	      feed_posts.firstOption match {
	        case Some(x) => {
	          val single_feed_post = for { ua <- UserArticles if ua.userId === x.userId && ua.articleId === x.articleId } yield ua
	          single_feed_post.update(UserArticle(x.id, x.userId, x.articleId, x.articleRead, true))
	        }
	        case None => UserArticles.insert(UserArticle(None, userId, postId, false, true))
	      }
	      true
	    }
	    case _ => false
	  }
	}
	
	def unsavePost(implicit session: Session, userId: Int, feedId: Int, postId: Int) : Boolean = {
	  val my_feed = for { uf <- UserFeeds if uf.feedId === feedId && uf.userId === userId } yield uf
      my_feed.firstOption match {
        case Some(_) => {
	      val feed_posts = for {
	        (nfa, ua) <- NewsFeedArticles leftJoin UserArticles on (_.id === _.articleId)
	            	     if nfa.feedId === feedId && ua.articleId === postId
	        uf <- UserFeeds if uf.userId === userId && nfa.feedId === uf.feedId && uf.userId === ua.userId
	      } yield ua
	      feed_posts.firstOption match {
	        case Some(x) => {
	          val single_feed_post = for { ua <- UserArticles if ua.userId === x.userId && ua.articleId === x.articleId } yield ua
	          single_feed_post.update(UserArticle(x.id, x.userId, x.articleId, x.articleRead, false))
	        }
	        case None => UserArticles.insert(UserArticle(None, userId, postId, false, false))
	      }
	      true
	    }
	    case _ => false
	  }
	}
	
	def getUserSession(implicit session: Session, sessionId: String, ip: String) : Option[UserSession] = {
	  val q = (for { sess <- UserSessions if sess.sessionId === sessionId } yield sess)
	  q.firstOption match {
	    case Some(s) => {
	      q.update(UserSession(s.userId, s.sessionId, new java.sql.Timestamp(new java.util.Date().getTime()), ip))
	      Some(s)
	    }
	    case None => None
	  }
	}
	
	def getUserName(implicit session: Session, userId: Int) : String = {
	  val q = for { u <- Users if u.id === userId } yield u.username
	  q.firstOption.getOrElse("")
	}
	
	def getUserInfo(implicit session: Session, userId: Int) : User = {
	  val q = for { u <- Users if u.id === userId } yield u
	  q.first
	}
	
	def setOptOut(implicit session: Session, userId: Int, optOut: Boolean) {
	  val user = getUserInfo(session, userId)
	  val q = for { u <- Users if u.id === userId } yield u
	  q.update(User(user.id, user.username, user.password, user.email, user.friendlyName, optOut))
	}
	
	def invalidateSession(implicit session: Session, sessionId: String) {
	  val q = (for { sess <- UserSessions if sess.sessionId === sessionId } yield sess)
	  q.firstOption match {
        case Some(s) => q.delete
        case None => ()
      }
	}
	
	def startUserSession(implicit session: Session, sessionId: String, email: String, ip: String, friendlyName: String) {
	 startUserSession(session, sessionId, email, email, ip, friendlyName) 
	}
	
	def startUserSession(implicit session: Session, sessionId: String, username: String, email: String, ip: String, friendlyName: String) {
	  val q = for { u <- Users if u.username === username } yield u
      val userId = q.firstOption match {
        case Some(u) => {
          q.update(User(u.id, u.username, u.password, email, friendlyName, false))
          u.id.get
        }
        case None => {
          Users returning Users.id insert User(None, username, "", email, friendlyName, false)
        }
      }
      UserSessions.insert(UserSession(userId, sessionId, new java.sql.Timestamp(new java.util.Date().getTime()), ip))
	}
	
	def updateOrInsertFeed(implicit session: Session, feedUrl: String, feed: XmlFeed) : NewsFeed = {
	  val feedQuery = Query(NewsFeeds)
      val newsFeed = 
        for { f <- NewsFeeds if f.feedUrl === feedUrl } yield
        (f.copyright ~ f.description ~ f.docs ~ f.generator ~ f.imageLink ~
         f.imageTitle ~ f.imageUrl ~ f.language ~ f.lastBuildDate ~ f.link ~
         f.managingEditor ~ f.pubDate ~ f.title ~ f.ttl ~ f.webMaster ~ f.lastUpdate)
      
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
		         feed.feedProperties.webMaster,
		         new java.sql.Timestamp(new java.util.Date().getTime())))
          }
          case None => {
            (NewsFeeds.feedUrl ~ NewsFeeds.copyright ~ NewsFeeds.description ~ NewsFeeds.docs ~ NewsFeeds.generator ~ NewsFeeds.imageLink ~
             NewsFeeds.imageTitle ~ NewsFeeds.imageUrl ~ NewsFeeds.language ~ NewsFeeds.lastBuildDate ~ NewsFeeds.link ~
             NewsFeeds.managingEditor ~ NewsFeeds.pubDate ~ NewsFeeds.title ~ NewsFeeds.ttl ~ NewsFeeds.webMaster ~ NewsFeeds.lastUpdate).insert(
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
		         feed.feedProperties.webMaster,
		         new java.sql.Timestamp(new java.util.Date().getTime())
            )
          }
        }
    
      val newsFeedId = (for { f <- NewsFeeds if f.feedUrl === feedUrl } yield f.id).first
            
      // Insert categories that don't exist, then refresh feed categories with the current
      // set.
      /*val categoryIds = feed.feedCategories.map(c => {
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
      }*/
      
      // Now update/insert each individual post in the feed.
      for { p <- feed.entries } insertOrUpdateEntry(session, newsFeedId, p)
      
      (for { f <- NewsFeeds if f.feedUrl === feedUrl } yield f).first
	}
	
	private def insertOrUpdateEntry(implicit session: Session, feedId: Int, p: (NewsFeedArticle, List[String])) {
      val newPost = p._1
    
      // Insert or update article as needed.
      val existingEntryId = for { 
        e <- NewsFeedArticles if e.feedId === feedId &&
        						 ((e.link === newPost.link && !newPost.link.isEmpty()) ||
                                 (e.guid =!= (None : Option[String]) && e.guid === newPost.guid) || 
                                 (e.title === newPost.title && e.link === newPost.link && e.description === newPost.description))
      } yield e.id
      val entry = for { 
        e <- NewsFeedArticles if e.feedId === feedId &&
        						 ((e.link === newPost.link && !newPost.link.isEmpty()) ||
                                 (e.guid =!= (None : Option[String]) && e.guid === newPost.guid) || 
                                 (e.title === newPost.title && e.link === newPost.link && e.description === newPost.description))
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
            ent._11,
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
      /*val categoryIds = p._2.map(c => {
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
      }*/
  }

  def logFeedFailure(implicit session: Session, feedUrl: String, message: String) {
    val feed = for { nf <- NewsFeeds if nf.feedUrl === feedUrl } yield nf
    feed.firstOption match {
      case Some(f) => {
        FeedFailureLogs.insert(
            FeedFailureLog(None, f.id.get, new java.sql.Timestamp(new java.util.Date().getTime()), message))
      }
      case None => ()
    }
  }
  
  def deleteOldSessions(implicit session: Session) {
    val oneWeekAgo = new java.sql.Timestamp(new java.util.Date().getTime() - 60*60*24*7*1000)
    val oldSessions = for { us <- UserSessions if unixTimestampFn(us.lastAccess) < unixTimestampFn(oneWeekAgo) } yield us
    oldSessions.delete
  }
  
  def deleteOldFailLogs(implicit session: Session) {
    val oneWeekAgo = new java.sql.Timestamp(new java.util.Date().getTime() - 60*60*24*7*1000)
    val oldFailLogs = for { fl <- FeedFailureLogs if unixTimestampFn(fl.failureDate) < unixTimestampFn(oneWeekAgo) } yield fl
    oldFailLogs.delete
  }
}