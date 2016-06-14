package us.newsrdr.models

import us.newsrdr.tasks._
import us.newsrdr._
import slick.driver.{JdbcDriver, JdbcProfile, H2Driver, MySQLDriver}
import slick.jdbc.meta.{MTable}
import slick.jdbc.GetResult
import java.sql.Timestamp
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

case class SiteStatistics(numUsers: Int, numFeeds: Int, numUsersInLastWeek: Int, numUsersInLastDay: Int)
case class BlogEntry(id: Option[Int], authorId: Int, postDate: Timestamp, subject: String, body: String)

class DataTables(val driver: JdbcProfile) {
  import driver.api._
  
  // The amount we need to subtract the add date by so we
  // don't end up getting posts that are years old in the
  // results.
  val OLDEST_POST_DIFFERENCE_MS : Long = 1000 * 60 * 60 * 24 * 14
  val OLDEST_POST_DIFFERENCE_SEC = OLDEST_POST_DIFFERENCE_MS / 1000
  
  // UNIX_TIMESTAMP support
  val unixTimestampFn = SimpleFunction.unary[Option[Timestamp], Long]("UNIX_TIMESTAMP")
  
  case class SiteSetting(
      id: Option[Int],
      isDown: Boolean)
  
  class SiteSettings(tag: Tag) extends Table[SiteSetting](tag, "SiteSettings") {
    def id = column[Option[Int]]("id", O.PrimaryKey, O.AutoInc)
    def isDown = column[Boolean]("isDown")
    
    def * = (id, isDown) <> (SiteSetting.tupled, SiteSetting.unapply)
  }
  val SiteSettings = TableQuery[SiteSettings]
  
  case class FeedFailureLog(
    id: Option[Int],
      feedId: Int,
      failureDate: Timestamp,
      failureMessage: String)
    
  class FeedFailureLogs(tag: Tag) extends Table[FeedFailureLog](tag, "FeedFailureLogs") {
    def id = column[Option[Int]]("id", O.PrimaryKey, O.AutoInc)
    def feedId = column[Int]("feedId")
    def failureDate = column[Timestamp]("failureDate")
    def failureMessage = column[String]("failureMessage")
    
    def * = (id, feedId, failureDate, failureMessage) <> (FeedFailureLog.tupled, FeedFailureLog.unapply)
    
    def feed = foreignKey("feedIdentifierLogKey", feedId, NewsFeeds)(_.id)
  }
  val FeedFailureLogs = TableQuery[FeedFailureLogs]
  
  class Categories(tag: Tag) extends Table[Category](tag, "Categories") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
  
    def * = (id.?, name) <> (Category.tupled, Category.unapply)
  }
  val Categories = TableQuery[Categories]
  
  class NewsFeeds(tag: Tag) extends Table[NewsFeed](tag, "NewsFeeds") {
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
    
    def hash = column[String]("hash")
    
    def * = 
      (id.?, title, link, description, feedUrl, language, copyright, managingEditor, 
      webMaster, pubDate, lastBuildDate, generator, docs, ttl, imageUrl, 
      imageTitle, imageLink, lastUpdate, hash) <> (NewsFeed.tupled, NewsFeed.unapply)
  }
  val NewsFeeds = TableQuery[NewsFeeds]
  
  class NewsFeedCategories(tag: Tag) extends Table[(Int, Int, Int)](tag, "NewsFeedCategories") {
      def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
      def feedId = column[Int]("feedId")
      def categoryId = column[Int]("categoryId")
    
      def * = (id, feedId, categoryId)
    
      def feed = foreignKey("feedIdentifierKey", feedId, NewsFeeds)(_.id)
      def category = foreignKey("categoryIdKey", categoryId, Categories)(_.id)
  }
  val NewsFeedCategories = TableQuery[NewsFeedCategories]
  
  class UserNewsFeedArticles(tag: Tag) extends Table[UserNewsFeedArticle](tag, "UserNewsFeedArticles") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Int]("userId")
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
    
    def isRead = column[Boolean]("isRead")
    def isSaved = column[Boolean]("isSaved")
    
    def * = 
      (id.?, userId, feedId, title, link, description, author, comments,
      enclosureUrl, enclosureLength, enclosureType, guid, isGuidPermalink,
      pubDate, source, isRead, isSaved) <> (UserNewsFeedArticle.tupled, UserNewsFeedArticle.unapply)
      
    def feed = foreignKey("feedIdKey", feedId, NewsFeeds)(_.id)
    def user = foreignKey("userIdKey", userId, Users)(_.id)
  }
  val UserNewsFeedArticles = TableQuery[UserNewsFeedArticles]
  
  class Users(tag: Tag) extends Table[User](tag, "Users") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def username = column[String]("username")
    def password = column[String]("password")
    def email = column[String]("email")
    def friendlyName = column[String]("friendlyName")
    def optOutSharing = column[Boolean]("optOutSharing")
    def isAdmin = column[Boolean]("isAdmin")
    
    def * = 
      (id.?, username, password, email, friendlyName, optOutSharing, isAdmin) <> (User.tupled, User.unapply)
  }
  val Users = TableQuery[Users]
  
  class UserSessions(tag: Tag) extends Table[UserSession](tag, "UserSessions") {
    def userId = column[Int]("userId")
    def sessionId = column[String]("sessionId")
    def lastAccess = column[Timestamp]("lastAccess")
    def lastAccessIp = column[String]("lastAccessIp")
    
    def * = (userId, sessionId, lastAccess, lastAccessIp) <> (UserSession.tupled, UserSession.unapply)
    def bIdx1 = index("userSessionKey", (userId, sessionId), unique = true)
    def user = foreignKey("userSessionUserKey", userId, Users)(_.id)
  }
  val UserSessions = TableQuery[UserSessions]
  
  class UserFeeds(tag: Tag) extends Table[UserFeed](tag, "UserFeeds") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Int]("userId")
    def feedId = column[Int]("feedId")
    def addedDate = column[Timestamp]("addedDate")
    
    def * = (id.?, userId, feedId, addedDate) <> (UserFeed.tupled, UserFeed.unapply)
    
    def feed = foreignKey("userFeedIdKey", feedId, NewsFeeds)(_.id)
    def user = foreignKey("userFeedUserIdKey", userId, Users)(_.id)
  }
  val UserFeeds = TableQuery[UserFeeds]
  
  class BlogEntries(tag: Tag) extends Table[BlogEntry](tag, "BlogEntries") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
      def authorId = column[Int]("authorId")
      def postDate = column[Timestamp]("postDate")
      def subject = column[String]("subject")
      def body = column[String]("body")
      
      def * = (id.?, authorId, postDate, subject, body) <> (BlogEntry.tupled, BlogEntry.unapply)
      def user = foreignKey("blogEntryUserIdKey", authorId, Users)(_.id)
  }
  val BlogEntries = TableQuery[BlogEntries]
  
  private def executeNow[R](query: slick.dbio.DBIOAction[R, slick.dbio.NoStream, Nothing])(implicit db: Database) : R = {
    Await.result(db.run(query), 60 second)
  }
  
  def create()(implicit db: Database) = {
    val setup = DBIO.seq(
        (Categories.schema ++ NewsFeeds.schema ++ NewsFeedCategories.schema ++
         Users.schema ++ UserNewsFeedArticles.schema ++ UserFeeds.schema ++
         UserSessions.schema ++ FeedFailureLogs.schema ++ BlogEntries.schema ++
         SiteSettings.schema).create)
    executeNow(setup)
/*      if (!MTable.getTables.list.exists(_.name.name == Categories.tableName)) Categories.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == NewsFeeds.tableName)) NewsFeeds.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == NewsFeedCategories.tableName)) NewsFeedCategories.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == Users.tableName)) Users.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == UserNewsFeedArticles.tableName)) UserNewsFeedArticles.ddl.create
      //if (!MTable.getTables.list.exists(_.name.name == NewsFeedArticleCategories.tableName)) NewsFeedArticleCategories.ddl.create
      //if (!MTable.getTables.list.exists(_.name.name == UserArticles.tableName)) UserArticles.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == UserFeeds.tableName)) UserFeeds.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == UserSessions.tableName)) UserSessions.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == FeedFailureLogs.tableName)) FeedFailureLogs.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == BlogEntries.tableName)) BlogEntries.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == SiteSettings.tableName)) SiteSettings.ddl.create*/
  }

  def isSiteDown()(implicit db: Database) : Boolean = {
    val q = for { s <- SiteSettings } yield s.isDown
    executeNow(q.result.map { x => x.headOption }).getOrElse(false)
  }
  
  def getSiteStatistics()(implicit database: Database) : SiteStatistics = {
    val today = new java.sql.Timestamp(new java.util.Date().getTime())
    val lastWeek = new java.sql.Timestamp(today.getTime() - 60*60*24*7*1000)
    val yesterday = new java.sql.Timestamp(today.getTime() - 60*60*24*1000)
    
    val userCount = executeNow((for{t <- Users} yield t).result.map { x => x.length })
    val feedCount = executeNow((for{t <- NewsFeeds} yield t).result.map { x => x.length })
    val usersSinceLastWeek = executeNow((for{t <- UserSessions if unixTimestampFn(t.lastAccess) >= unixTimestampFn(Some(lastWeek))} yield t.userId).groupBy(x=>x).map(_._1).result.map { x => x.length })
    val usersSinceYesterday = executeNow((for{t <- UserSessions if unixTimestampFn(t.lastAccess) >= unixTimestampFn(Some(yesterday))} yield t.userId).groupBy(x=>x).map(_._1).result.map { x => x.length })
    SiteStatistics(
        userCount, 
        feedCount,
        usersSinceLastWeek,
        usersSinceYesterday)
  }
  
  def getBlogPosts(offset: Int)(implicit db: Database) : List[BlogEntry] = {
    val query = BlogEntries.sortBy(_.postDate.desc)
                           .drop(offset)
                           .take(Constants.ITEMS_PER_PAGE).to[List].result
    executeNow(query)
  }
  
  def getBlogPostById(id: Int)(implicit db: Database) : BlogEntry = {
    executeNow(BlogEntries.filter(_.id === id).result.map { x => x.head })
  }
  
  def insertBlogPost(uid: Int, subject: String, body: String)(implicit db: Database) {
    executeNow(BlogEntries += BlogEntry(None, uid, new java.sql.Timestamp(new java.util.Date().getTime()), subject, body))
  }
  
  def deleteBlogPost(id: Int)(implicit db: Database) {
    val query = for { be <- BlogEntries if be.id === id } yield be
    executeNow(query.delete)
  }
  
  def editBlogPost(id: Int, subject: String, body: String)(implicit db: Database) {
    val query = for { be <- BlogEntries if be.id === id } yield (be.subject, be.body)
    executeNow(query.update(subject, body))
  }
  
  def getSubscribedFeeds(userId: Int)(implicit db: Database) : List[(NewsFeed, Int)] = {
    implicit val getNewsFeedResult = GetResult(
        r => NewsFeed(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<,
                      r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))
    
    val queryString = if (driver.isInstanceOf[H2Driver]) {
       sql"""
       select "nf".*, (
         select count("id") from "UserNewsFeedArticles" where "feedId" = "uf"."feedId" and "userId" = $userId and "isRead" = false
       ) as unread 
       from "NewsFeeds" "nf"
       inner join "UserFeeds" "uf" on "nf"."id" = "uf"."feedId"
       where "uf"."userId" = $userId
     """
   } else {
     sql"""
       select nf.*, (
         select count(id) from UserNewsFeedArticles where feedId = uf.feedId and userId = $userId and isRead = false
       ) as unread 
       from NewsFeeds nf
       inner join UserFeeds uf on nf.id = uf.feedId
       where uf.userId = $userId
     """
   }
   val unreadCountQuery = queryString.as[(NewsFeed, Int)]
   executeNow(unreadCountQuery.map { x => x.toList })
  }
  
  def getUnreadCountForFeed(userId: Int, feedId: Int)(implicit db: Database) : Int = {
    val today = new Timestamp(new java.util.Date().getTime())
    
    val feed_posts = for { 
      nfa <- UserNewsFeedArticles if nfa.feedId === feedId
      uf <- UserFeeds if uf.userId === userId && nfa.feedId === uf.feedId && nfa.userId === uf.userId
    } yield (nfa, uf, nfa.isRead)
      
    val feed_posts2 = for {
      (nfa, uf, read) <- feed_posts 
        if unixTimestampFn(nfa.pubDate.getOrElse(today)) >= (unixTimestampFn(uf.addedDate) - OLDEST_POST_DIFFERENCE_MS)
    } yield (nfa, read)
    
    val resultQuery = (for { (fp, fq) <- feed_posts2 if fq == false } yield fp )
    executeNow(resultQuery.result.map { x => x.length })
  }
  
  def getFeedFromUrl(url: String)(implicit db: Database) : Option[NewsFeed] = {
    val feedQuery = for { f <- NewsFeeds if f.feedUrl === url || f.link === url } yield f
    executeNow(feedQuery.result.map { x => x.headOption })
  }
  
  def addSubscriptionIfNotExists(userId: Int, feedId: Int)(implicit db: Database) {    
    val userFeed = for { uf <- UserFeeds if uf.userId === userId && uf.feedId === feedId } yield uf
    val result : Option[UserFeed] = executeNow(userFeed.result.map { x => x.headOption })
    result match {
      case Some(uf) => ()
      case None => {
        executeNow(UserFeeds += UserFeed(None, userId, feedId, new Timestamp(new java.util.Date().getTime())))
        ()
      }
    }
  }
  
  def unsubscribeFeed(userId: Int, feedId: Int)(implicit db: Database) {
    val userFeed = for { uf <- UserFeeds if uf.userId === userId && uf.feedId === feedId } yield uf
    executeNow(userFeed.delete)
    
    val subscribedQuery = for { uf <- UserFeeds if uf.feedId === feedId } yield uf
    val numSubscribed = executeNow(subscribedQuery.length.result)
    if (numSubscribed == 0)
    {
      val feedQuery = for { f <- NewsFeeds if f.id === feedId } yield f.feedUrl
      val feed = executeNow(feedQuery.result.map { x => x.head })
      BackgroundJobManager.unscheduleFeedJob(feed)
    }
  }
  
  def getFeedByPostId(postId: Long)(implicit db: Database) : NewsFeed = {
    val feed = for {
      (nfa, nf) <- UserNewsFeedArticles join NewsFeeds on (_.feedId === _.id) if nfa.id === postId
    } yield nf
    return executeNow(feed.result.map { x => x.head })
  }
  
  def getLatestPostsForUser(userId: Int)(implicit db: Database) : List[NewsFeedArticleInfo] = {
    val userOptedOut = executeNow(Users.filter(_.id === userId).result.map { x => x.head.optOutSharing })
    
    val articleQuery = 
      if (userOptedOut) {
        for {
          (uf, u) <- UserFeeds join Users on (_.userId === _.id) if u.optOutSharing === false
          nf <- NewsFeeds if uf.feedId === nf.id
          nfa <- UserNewsFeedArticles if nfa.feedId === uf.feedId
        } yield nfa
      } else {
        for {
          nfa <- UserNewsFeedArticles
          nf <- NewsFeeds if nfa.feedId === nf.id
          uf <- UserFeeds if nf.id === uf.feedId
          u <- Users if u.id === uf.userId && u.id === userId
        } yield nfa
      }.take(Constants.ITEMS_PER_PAGE)
    
    executeNow(articleQuery.result.map { x => x.map(y =>
      NewsFeedArticleInfo(
          NewsFeedArticle(y.id, y.feedId, y.title, y.link, y.description, y.author, 
                          y.comments, y.enclosureUrl, y.enclosureLength, y.enclosureType, 
                          y.guid, y.isGuidPermalink, y.pubDate, y.source), false, false)).toList })
  }
  
  def getLinkForPost(postId: Long)(implicit db: Database): String = {
    val query = for { unfa <- UserNewsFeedArticles if unfa.id === postId } yield unfa.link;
    executeNow(query.result.map { x => x.head })
  }
  
  def getPostsForFeeds(userId: Int, feedIds: List[Int], unreadOnly: Boolean, offset: Int, maxEntries: Int, latestPostDate: Long, latestPostId: Long)(implicit db: Database): List[NewsFeedArticleInfo] = {
    val feed_posts = if (unreadOnly) {
      for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.feedId.inSet(feedIds) && 
                                            unfa.id <= latestPostId && unixTimestampFn(unfa.pubDate) < latestPostDate &&
                                            unfa.isRead === false } yield unfa
    } else {
      for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.feedId.inSet(feedIds) &&
                                            unfa.id <= latestPostId && unixTimestampFn(unfa.pubDate) < latestPostDate
                                            } yield unfa
    }
    
    val feedPostQuery = feed_posts.sortBy(_.pubDate.desc).drop(offset).take(maxEntries)
    executeNow(feedPostQuery.result.map { x => x.map(y =>
      NewsFeedArticleInfo(
          NewsFeedArticle(y.id, y.feedId, y.title, y.link, y.description, y.author, 
                          y.comments, y.enclosureUrl, y.enclosureLength, y.enclosureType, 
                          y.guid, y.isGuidPermalink, y.pubDate, y.source),
          y.isRead == false, y.isSaved)).toList })
  }
  
  def getPostsForFeed(userId: Int, feedId: Int, unreadOnly: Boolean, offset: Int, maxEntries: Int, latestPostDate: Long, latestPostId: Long)(implicit db: Database) : List[NewsFeedArticleInfo] = {
    val feed_posts = if (unreadOnly) {
      for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.feedId === feedId && 
                                            unfa.id <= latestPostId && unixTimestampFn(unfa.pubDate) < latestPostDate &&
                                            unfa.isRead === false } yield unfa
    } else {
      for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.feedId === feedId &&
                                            unfa.id <= latestPostId && unixTimestampFn(unfa.pubDate) < latestPostDate
                                            } yield unfa
    }
    
    val feedPostQuery = feed_posts.sortBy(_.pubDate.desc).drop(offset).take(maxEntries)
    executeNow(feedPostQuery.result.map{ x => x.map(y =>
      NewsFeedArticleInfo(
          NewsFeedArticle(y.id, y.feedId, y.title, y.link, y.description, y.author, y.comments,
                          y.enclosureUrl, y.enclosureLength, y.enclosureType, y.guid, y.isGuidPermalink,
                          y.pubDate, y.source),
          y.isRead == false, y.isSaved)).toList })
  }
  
  def getPostsForAllFeeds(userId: Int, unreadOnly: Boolean, offset: Int, maxEntries: Int, latestPostDate: Long, latestPostId: Long)(implicit db: Database) : List[NewsFeedArticleInfo] = {
    val feed_posts = if (unreadOnly) {
      for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.id <= latestPostId &&
                                            unixTimestampFn(unfa.pubDate) < latestPostDate &&
                                            unfa.isRead === false } yield unfa
    } else {
      for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.id <= latestPostId && 
                                            unixTimestampFn(unfa.pubDate) < latestPostDate
                                            } yield unfa
    }
    
    val feedQuery = feed_posts.sortBy(_.pubDate.desc).drop(offset).take(maxEntries)
    executeNow(feedQuery.result.map { x => x.map(y =>
      NewsFeedArticleInfo(
          NewsFeedArticle(y.id, y.feedId, y.title, y.link, y.description, y.author, y.comments,
                          y.enclosureUrl, y.enclosureLength, y.enclosureType, y.guid, y.isGuidPermalink,
                          y.pubDate, y.source),
          y.isRead == false, y.isSaved)).toList })
  }
  
  def getSavedPosts(userId: Int, offset: Int, maxEntries: Int, latestPostDate: Long, latestPostId: Long)(implicit db: Database) : List[NewsFeedArticleInfo] = {
    val feed_posts = for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.id <= latestPostId &&
                                                           unixTimestampFn(unfa.pubDate) < latestPostDate &&
                                                           unfa.isSaved === true } yield unfa
    
    val feedQuery = feed_posts.sortBy(_.pubDate.desc).drop(offset).take(maxEntries)
    executeNow(feedQuery.result.map { x => x.map(y =>
      NewsFeedArticleInfo(
          NewsFeedArticle(y.id, y.feedId, y.title, y.link, y.description, y.author, y.comments,
                          y.enclosureUrl, y.enclosureLength, y.enclosureType, y.guid, y.isGuidPermalink,
                          y.pubDate, y.source),
          false, true)).toList })
  }
  
  def setPostStatusForAllPosts(userId: Int, feedId: Int, from: Long, upTo: Long, unread: Boolean)(implicit db: Database) : Boolean = {
    val feedPostsQuery = for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.feedId === feedId && 
                                                            unixTimestampFn(unfa.pubDate) <= from &&
                                                            unixTimestampFn(unfa.pubDate) >= upTo &&
                                                            unfa.isRead === unread } yield unfa.isRead
    executeNow(feedPostsQuery.update(!unread))
    true
  }
  
  def setPostStatusForAllPosts(userId: Int, from: Long, upTo: Long, unread: Boolean)(implicit db: Database) : Boolean = {
    val feedPostsQuery = for { unfa <- UserNewsFeedArticles if unfa.userId === userId &&
                                                            unixTimestampFn(unfa.pubDate) <= from &&
                                                            unixTimestampFn(unfa.pubDate) >= upTo &&
                                                            unfa.isRead === unread } yield unfa.isRead
    executeNow(feedPostsQuery.update(!unread))
    true
  }
  
  def setPostStatus(userId: Int, feedId: Int, postId: Long, unread: Boolean)(implicit db: Database) : Boolean = {
    val my_feed = for { uf <- UserFeeds if uf.feedId === feedId && uf.userId === userId } yield uf
    val foundFeed : Option[UserFeed] = executeNow(my_feed.result.map { x => x.headOption })
    foundFeed match {
      case Some(_) => {
        val feedPostsQuery = for { unfa <- UserNewsFeedArticles if unfa.userId === userId &&
                                                                   unfa.feedId === feedId &&
                                                                   unfa.id === postId &&
                                                                   unfa.isRead === unread } yield unfa.isRead
        executeNow(feedPostsQuery.update(!unread))
        true
      }
      case _ => false
    }
  }
  
  def setPostStatus(userId: Int, postId: Long, unread: Boolean)(implicit db: Database) : Boolean = {
    val my_feed = for { uf <- UserFeeds if uf.userId === userId } yield uf
    val foundFeed : Option[UserFeed] = executeNow(my_feed.result.map { x => x.headOption })
    foundFeed match {
      case Some(_) => {
        val feedPostsQuery = for { unfa <- UserNewsFeedArticles if unfa.userId === userId &&
                                                                   unfa.id === postId &&
                                                                   unfa.isRead === unread } yield unfa.isRead
        executeNow(feedPostsQuery.update(!unread))
        true
      }
      case _ => false
    }
  }
  
  def savePost(userId: Int, feedId: Int, postId: Long)(implicit db: Database) : Boolean = {
    val my_feed = for { uf <- UserFeeds if uf.feedId === feedId && uf.userId === userId } yield uf
    val foundFeed : Option[UserFeed] = executeNow(my_feed.result.map { x => x.headOption })
    foundFeed match {
      case Some(_) => {
        val feedPostsQuery = for { unfa <- UserNewsFeedArticles if unfa.userId === userId &&
                                                                   unfa.feedId === feedId &&
                                                                   unfa.id === postId  } yield unfa.isSaved
        executeNow(feedPostsQuery.update(true))
        true
      }
      case _ => false
    }
  }
  
  def unsavePost(userId: Int, feedId: Int, postId: Long)(implicit db: Database) : Boolean = {
    val my_feed = for { uf <- UserFeeds if uf.feedId === feedId && uf.userId === userId } yield uf
    val foundFeed : Option[UserFeed] = executeNow(my_feed.result.map { x => x.headOption })
    foundFeed match {
      case Some(_) => {
        val feedPostsQuery = for { unfa <- UserNewsFeedArticles if unfa.userId === userId &&
                                                                   unfa.feedId === feedId &&
                                                                   unfa.id === postId  } yield unfa.isSaved
        executeNow(feedPostsQuery.update(false))
        true
      }
      case _ => false
    }
  }
  
  def getUserSessionById(sessionId: String)(implicit db: Database) : UserSession = {
    val query = (for { sess <- UserSessions if sess.sessionId === sessionId } yield sess)
    executeNow(query.result.map { x => x.head })
  }
  
  def getUserSession(sessionId: String, ip: String)(implicit db: Database) : Option[UserSession] = {
    val q = (for { sess <- UserSessions if sess.sessionId === sessionId } yield sess)
    val foundResult : Option[UserSession] = executeNow(q.result.map { x => x.headOption })
    foundResult match {
      case Some(s) => {
        executeNow(q.update(UserSession(s.userId, s.sessionId, new java.sql.Timestamp(new java.util.Date().getTime()), ip)))
        Some(s)
      }
      case None => None
    }
  }
  
  def getUserName(userId: Int)(implicit db: Database) : String = {
    val q = for { u <- Users if u.id === userId } yield u.username
    executeNow(q.result.map { x => x.headOption.getOrElse("") })
  }
  
  def getUserInfoByUsername(username: String)(implicit db: Database) : Option[User] = {
    val q = for { u <- Users if u.username === username } yield u
    executeNow(q.result.map { x => x.headOption })
  }
  
  def getUserInfo(userId: Int)(implicit db: Database) : User = {
    val q = for { u <- Users if u.id === userId } yield u
    executeNow(q.result.map { x => x.head })
  }
  
  def setOptOut(userId: Int, optOut: Boolean)(implicit db: Database) {
    val user = getUserInfo(userId)
    val q = for { u <- Users if u.id === userId } yield u
    executeNow(q.update(User(user.id, user.username, user.password, user.email, user.friendlyName, optOut, user.isAdmin)))
  }
  
  def invalidateSession(sessionId: String)(implicit db: Database) {
    val q = (for { sess <- UserSessions if sess.sessionId === sessionId } yield sess)
    val session : Option[UserSession] = executeNow(q.result.map { x => x.headOption })
    session match {
        case Some(s) => executeNow(q.delete)
        case None => ()
      }
  }
  
  def startUserSession(sessionId: String, email: String, ip: String, friendlyName: String)(implicit db: Database) {
    startUserSession(sessionId, email, email, ip, friendlyName) 
  }
  
  def createUser(username: String, password: String, email: String)(implicit db: Database) = {
    val q = for { u <- Users if u.username === username } yield u
    val isUserDefined = executeNow(q.result.map { x => x.headOption }).isDefined
    if (isUserDefined) { false }
    else {
      executeNow(Users += User(None, username, AuthenticationTools.hashPassword(password), email, username, false, false))
      true
    }
  }
  
  def setPassword(username: String, password: String)(implicit db: Database) = {
    val q = for { u <- Users if u.username === username } yield u.password
    executeNow(q.update(AuthenticationTools.hashPassword(password)))
  }
  
  def setPassword(uId: Int, password: String)(implicit db: Database) = {
    val q = for { u <- Users if u.id === uId } yield u.password
    executeNow(q.update(AuthenticationTools.hashPassword(password)))
  }
  
  def setEmail(uId: Int, email: String)(implicit db: Database) = {
    val q = for { u <- Users if u.id === uId } yield u.email
    executeNow(q.update(email))
  }
  
  def startUserSession(sessionId: String, username: String, email: String, ip: String, friendlyName: String)(implicit db: Database) {
    val q = for { u <- Users if u.username === username } yield u
    var queryResult : Option[User] = executeNow(q.result.map { x => x.headOption })
    val userId = queryResult match {
      case Some(u) => {
        executeNow(q.update(User(u.id, u.username, u.password, email, friendlyName, u.optOutSharing, u.isAdmin)))
        u.id.get
      }
      case None => {
        executeNow(Users returning Users.map(_.id) += User(None, username, "", email, friendlyName, false, false))
      }
    }
    executeNow(UserSessions += UserSession(userId, sessionId, new java.sql.Timestamp(new java.util.Date().getTime()), ip))
  }
  
  private def updateOrInsertFeedInfo(feedUrl: String, feed: XmlFeed)(implicit db: Database) : NewsFeed = {
    val newsFeed = 
      for { f <- NewsFeeds if f.feedUrl === feedUrl } yield 
      (f.copyright, f.description, f.docs, f.generator, f.imageLink,
       f.imageTitle, f.imageUrl, f.language, f.lastBuildDate, f.link,
       f.managingEditor, f.pubDate, f.title, f.ttl, f.webMaster, f.lastUpdate, f.hash)
    val newsFeedOption = executeNow(newsFeed.result.map { x => x.headOption })  
    newsFeedOption match {
      case Some(fd) => {
        if (fd._17 == feed.feedProperties.hash) throw new NotModifiedException
        
        executeNow(newsFeed.update(
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
           new java.sql.Timestamp(new java.util.Date().getTime()),
           feed.feedProperties.hash)))
        }
        case None => {
          executeNow(NewsFeeds.map(x => (x.feedUrl, x.copyright, x.description, x.docs, x.generator, 
                              x.imageLink, x.imageTitle, x.imageUrl, x.language, x.lastBuildDate, 
                              x.link, x.managingEditor, x.pubDate, x.title, x.ttl, x.webMaster, 
                              x.lastUpdate, x.hash)) += (
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
            new java.sql.Timestamp(new java.util.Date().getTime()),
            feed.feedProperties.hash))
        }
      }
    
    val resultQuery = (for { f <- NewsFeeds if f.feedUrl === feedUrl } yield f)
    executeNow(resultQuery.result.map { x => x.head })
  }
  
  def updateOrInsertFeed(userId: Int, feedUrl: String, feed: XmlFeed)(implicit db: Database) : NewsFeed = {
    val f = updateOrInsertFeedInfo(feedUrl, feed)
    val newsFeedId = f.id.get
      
    // Now update/insert each individual post in the feed.
    for { p <- feed.entries } insertOrUpdateEntry(userId, newsFeedId, p)
    f
  }
  
  def updateOrInsertFeed(feedUrl: String, feed: XmlFeed)(implicit db: Database) : NewsFeed = {
    val f = updateOrInsertFeedInfo(feedUrl, feed)
    val newsFeedId = f.id.get
      
    // Now update/insert each individual post in the feed.
    val subscribed_users = for { 
      f <- NewsFeeds if f.feedUrl === feedUrl
      uf <- UserFeeds if uf.feedId === f.id
    } yield uf.userId
    
    val subscribedUsersList = executeNow(subscribed_users.result.map { x => x.toList })
    subscribedUsersList.foreach(uid =>
      for { p <- feed.entries } insertOrUpdateEntry(uid, newsFeedId, p)
    )
    
    f
  }
  
  private def insertOrUpdateEntry(userId: Int, feedId: Int, p: (NewsFeedArticle, List[String]))(implicit db: Database) {
    val newPost = p._1
  
    // Insert or update article as needed.
    val existingEntryId = for { 
      e <- UserNewsFeedArticles if e.feedId === feedId && e.userId === userId && 
                   ((e.link === newPost.link && !newPost.link.isEmpty()) ||
                               (e.guid =!= (None : Option[String]) && e.guid === newPost.guid) || 
                               (e.title === newPost.title && e.description === newPost.description))
    } yield e.id
    val entry = for { 
      e <- UserNewsFeedArticles if e.feedId === feedId && e.userId === userId && 
                   ((e.link === newPost.link && !newPost.link.isEmpty()) ||
                               (e.guid =!= (None : Option[String]) && e.guid === newPost.guid) || 
                               (e.title === newPost.title && e.description === newPost.description))
    } yield (
        e.title, e.link, e.description, e.author, e.comments, e.enclosureLength, 
        e.enclosureType, e.enclosureUrl, e.guid, e.isGuidPermalink, e.pubDate,
        e.source)
    val entryResult = executeNow(entry.result.map { x => x.headOption })
    val entryId = entryResult match {
      case Some(ent) => {
        executeNow(entry.update(
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
          newPost.source))
        executeNow(existingEntryId.result.map { x => x.head })
      }
      case None => (
          executeNow(UserNewsFeedArticles.map(p =>
            (p.userId, p.feedId, p.title, p.link, p.description,
             p.author, p.comments, p.enclosureLength, p.enclosureType, 
             p.enclosureUrl, p.guid, p.isGuidPermalink, p.pubDate, p.source,
             p.isRead, p.isSaved)) returning UserNewsFeedArticles.map(_.id) += (
            userId,
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
            newPost.source,
            false,
            false
          )))
      }
  }

  def logFeedFailure(feedUrl: String, message: String)(implicit db: Database) {
    val feed = for { nf <- NewsFeeds if nf.feedUrl === feedUrl } yield nf
    val feedOption = executeNow(feed.result.map { x => x.headOption })
    feedOption match {
      case Some(f) => {
        executeNow(FeedFailureLogs += (
            FeedFailureLog(None, f.id.get, new java.sql.Timestamp(new java.util.Date().getTime()), message)))
      }
      case None => ()
    }
  }
  
  def deleteOldPosts()(implicit db: Database) {
    val threeMonthsAgo = new java.sql.Timestamp(new java.util.Date().getTime() - 60*60*24*30*3*1000)
    val matchingOldPosts = for { unfa <- UserNewsFeedArticles if unixTimestampFn(unfa.pubDate) < unixTimestampFn(Some(threeMonthsAgo)) && unfa.isSaved === false } yield unfa
    executeNow(matchingOldPosts.delete)
  }
  
  def deleteOldSessions()(implicit db: Database) {
    val oneWeekAgo = new java.sql.Timestamp(new java.util.Date().getTime() - 60*60*24*7*1000)
    val oldSessions = for { us <- UserSessions if unixTimestampFn(us.lastAccess) < unixTimestampFn(Some(oneWeekAgo)) } yield us
    executeNow(oldSessions.delete)
  }
  
  def deleteOldFailLogs()(implicit db: Database) {
    val oneWeekAgo = new java.sql.Timestamp(new java.util.Date().getTime() - 60*60*24*7*1000)
    val oldFailLogs = for { fl <- FeedFailureLogs if unixTimestampFn(fl.failureDate) < unixTimestampFn(Some(oneWeekAgo)) } yield fl
    executeNow(oldFailLogs.delete)
  }
}
