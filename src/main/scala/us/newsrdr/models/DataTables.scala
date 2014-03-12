package us.newsrdr.models

import us.newsrdr.tasks._
import us.newsrdr._
import scala.slick.driver.{ExtendedProfile, H2Driver, MySQLDriver}
import scala.slick.jdbc.meta.{MTable}
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import java.sql.Timestamp

case class SiteStatistics(numUsers: Int, numFeeds: Int, numUsersInLastWeek: Int, numUsersInLastDay: Int)
case class BlogEntry(id: Option[Int], authorId: Int, postDate: Timestamp, subject: String, body: String)

class DataTables(val driver: ExtendedProfile) {
  import driver.simple._
  
  // The amount we need to subtract the add date by so we
  // don't end up getting posts that are years old in the
  // results.
  val OLDEST_POST_DIFFERENCE_MS = 1000 * 60 * 60 * 24 * 14
  val OLDEST_POST_DIFFERENCE_SEC = OLDEST_POST_DIFFERENCE_MS / 1000
  
  // UNIX_TIMESTAMP support
  val unixTimestampFn = SimpleFunction.unary[Option[Timestamp], Long]("UNIX_TIMESTAMP")
  
  case class SiteSetting(
      id: Option[Int],
      isDown: Boolean)
  
  object SiteSettings extends Table[SiteSetting]("SiteSettings") {
    def id = column[Option[Int]]("id", O.PrimaryKey, O.AutoInc)
    def isDown = column[Boolean]("isDown")
    
    def * = id ~ isDown <> (SiteSetting, SiteSetting.unapply _)
  }
  
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
    
    def hash = column[String]("hash")
    
    def * = 
      id.? ~ title ~ link ~ description ~ feedUrl ~ language ~ copyright ~ managingEditor ~ 
      webMaster ~ pubDate ~ lastBuildDate ~ generator ~ docs ~ ttl ~ imageUrl ~ 
      imageTitle ~ imageLink ~ lastUpdate ~ hash <> (NewsFeed, NewsFeed.unapply _)
  }
  
  object NewsFeedCategories extends Table[(Int, Int, Int)]("NewsFeedCategories") {
      def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
      def feedId = column[Int]("feedId")
      def categoryId = column[Int]("categoryId")
    
      def * = id ~ feedId ~ categoryId
    
      def feed = foreignKey("feedIdentifierKey", feedId, NewsFeeds)(_.id)
      def category = foreignKey("categoryIdKey", categoryId, Categories)(_.id)
  }
    
  object UserNewsFeedArticles extends Table[UserNewsFeedArticle]("UserNewsFeedArticles") {
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
      id.? ~ userId ~ feedId ~ title ~ link ~ description ~ author ~ comments ~
      enclosureUrl ~ enclosureLength ~ enclosureType ~ guid ~ isGuidPermalink ~
      pubDate ~ source ~ isRead ~ isSaved <> (UserNewsFeedArticle, UserNewsFeedArticle.unapply _)
      
    def feed = foreignKey("feedIdKey", feedId, NewsFeeds)(_.id)
    def user = foreignKey("userIdKey", userId, Users)(_.id)
  }
  
  object Users extends Table[User]("Users") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def username = column[String]("username")
    def password = column[String]("password")
    def email = column[String]("email")
    def friendlyName = column[String]("friendlyName")
    def optOutSharing = column[Boolean]("optOutSharing")
    def isAdmin = column[Boolean]("isAdmin")
    
    def * = id.? ~ username ~ password ~ email ~ friendlyName ~ optOutSharing ~ isAdmin <> (User, User.unapply _)
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
  
  object UserFeeds extends Table[UserFeed]("UserFeeds") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Int]("userId")
    def feedId = column[Int]("feedId")
    def addedDate = column[Timestamp]("addedDate")
    
    def * = id.? ~ userId ~ feedId ~ addedDate <> (UserFeed, UserFeed.unapply _)
    
    def feed = foreignKey("userFeedIdKey", feedId, NewsFeeds)(_.id)
    def user = foreignKey("userFeedUserIdKey", userId, Users)(_.id)
  }
    
  object BlogEntries extends Table[BlogEntry]("BlogEntries") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
      def authorId = column[Int]("authorId")
      def postDate = column[Timestamp]("postDate")
      def subject = column[String]("subject")
      def body = column[String]("body")
      
      def * = id.? ~ authorId ~ postDate ~ subject ~ body <> (BlogEntry, BlogEntry.unapply _)
      def user = foreignKey("blogEntryUserIdKey", authorId, Users)(_.id)
  }
  
  def create()(implicit session: Session) = {
      if (!MTable.getTables.list.exists(_.name.name == Categories.tableName)) Categories.ddl.create
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
      if (!MTable.getTables.list.exists(_.name.name == SiteSettings.tableName)) SiteSettings.ddl.create
  }

  def isSiteDown()(implicit session: Session) : Boolean = {
    val q = for { s <- SiteSettings } yield s.isDown
    q.firstOption.getOrElse(false)
  }
  
  def getSiteStatistics()(implicit session: Session) : SiteStatistics = {
    val today = new java.sql.Timestamp(new java.util.Date().getTime())
    val lastWeek = new java.sql.Timestamp(today.getTime() - 60*60*24*7*1000)
    val yesterday = new java.sql.Timestamp(today.getTime() - 60*60*24*1000)
    
    SiteStatistics(
        (for{t <- Users} yield t).list.count(_ => true), 
        (for{t <- NewsFeeds} yield t).list.count(_ => true),
        (for{t <- UserSessions if unixTimestampFn(t.lastAccess) >= unixTimestampFn(Some(lastWeek))} yield t.userId).groupBy(x=>x).map(_._1).list.count(_ => true),
        (for{t <- UserSessions if unixTimestampFn(t.lastAccess) >= unixTimestampFn(Some(yesterday))} yield t.userId).groupBy(x=>x).map(_._1).list.count(_ => true))
  }
  
  def getBlogPosts(offset: Int)(implicit session: Session) : List[BlogEntry] = {
    Query(BlogEntries).sortBy(_.postDate.desc).drop(offset).take(Constants.ITEMS_PER_PAGE).list
  }
  
  def getBlogPostById(id: Int)(implicit session: Session) : BlogEntry = {
      Query(BlogEntries).filter(_.id === id).first
    }
  
  def insertBlogPost(uid: Int, subject: String, body: String)(implicit session: Session) {
    BlogEntries.insert(BlogEntry(None, uid, new java.sql.Timestamp(new java.util.Date().getTime()), subject, body))
  }
  
  def deleteBlogPost(id: Int)(implicit session: Session) {
    val query = for { be <- BlogEntries if be.id === id } yield be
    query.delete
  }
  
  def editBlogPost(id: Int, subject: String, body: String)(implicit session: Session) {
      val query = for { be <- BlogEntries if be.id === id } yield be.subject ~ be.body
      query.update(subject, body)
    }
  
  def getSubscribedFeeds(userId: Int)(implicit session: Session) : List[(NewsFeed, Int)] = {
    implicit val getNewsFeedResult = GetResult(
        r => NewsFeed(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<,
                      r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))
    
    val queryString = if (driver.isInstanceOf[H2Driver]) {
       """
       select "nf".*, (
         select count("id") from "UserNewsFeedArticles" where "feedId" = "uf"."feedId" and "userId" = ? and "isRead" = false
       ) as unread 
       from "NewsFeeds" "nf"
       inner join "UserFeeds" "uf" on "nf"."id" = "uf"."feedId"
       where "uf"."userId" = ?
     """
   } else {
     """
       select nf.*, (
         select count(id) from UserNewsFeedArticles where feedId = uf.feedId and userId = ? and isRead = false
       ) as unread 
       from NewsFeeds nf
       inner join UserFeeds uf on nf.id = uf.feedId
       where uf.userId = ?
     """
   }
   val unreadCountQuery = Q.query[(Int, Int), (NewsFeed, Int)](queryString)
   unreadCountQuery.list(userId, userId)
  }
  
  def getUnreadCountForFeed(userId: Int, feedId: Int)(implicit session: Session) : Int = {
    val today = new Timestamp(new java.util.Date().getTime())
    
    val feed_posts = for { 
      nfa <- UserNewsFeedArticles if nfa.feedId === feedId
      uf <- UserFeeds if uf.userId === userId && nfa.feedId === uf.feedId && nfa.userId === uf.userId
    } yield (nfa, uf, nfa.isRead)
      
    val feed_posts2 = for {
      (nfa, uf, read) <- feed_posts.list 
        if nfa.pubDate.getOrElse(today).compareTo(new Timestamp(uf.addedDate.getTime() - OLDEST_POST_DIFFERENCE_MS)) >= 0
    } yield (nfa, read)
    
    (for { (fp, fq) <- feed_posts2 if fq == false } yield fp ).length
  }
  
  def getFeedFromUrl(url: String)(implicit session: Session) : Option[NewsFeed] = {
    val feedQuery = for { f <- NewsFeeds if f.feedUrl === url || f.link === url } yield f
    feedQuery.firstOption
  }
  
  def addSubscriptionIfNotExists(userId: Int, feedId: Int)(implicit session: Session) {    
    val userFeed = for { uf <- UserFeeds if uf.userId === userId && uf.feedId === feedId } yield uf
    userFeed.firstOption match {
      case Some(uf) => ()
      case None => {
        UserFeeds.insert(UserFeed(None, userId, feedId, new Timestamp(new java.util.Date().getTime())))
        ()
      }
    }
  }
  
  def unsubscribeFeed(userId: Int, feedId: Int)(implicit session: Session) {
    val userFeed = for { uf <- UserFeeds if uf.userId === userId && uf.feedId === feedId } yield uf
    userFeed.delete
    
    val numSubscribed = for { uf <- UserFeeds if uf.feedId === feedId } yield uf
    if (numSubscribed.list.count(_ => true) == 0)
    {
      val feed = for { f <- NewsFeeds if f.id === feedId } yield f.feedUrl
      BackgroundJobManager.unscheduleFeedJob(feed.list.head)
    }
  }
  
  def getFeedByPostId(postId: Long)(implicit session: Session) : NewsFeed = {
    val feed = for {
      (nfa, nf) <- UserNewsFeedArticles innerJoin NewsFeeds on (_.feedId === _.id) if nfa.id === postId
    } yield nf
    return feed.first
  }
  
  def getLatestPostsForUser(userId: Int)(implicit session: Session) : List[NewsFeedArticleInfo] = {
    val userOptedOut = Query(Users).filter(_.id === userId).first.optOutSharing
    
    val articleQuery = 
      if (userOptedOut) {
        for {
          (uf, u) <- UserFeeds innerJoin Users on (_.userId === _.id) if u.optOutSharing === false
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
      }
    
    articleQuery.take(Constants.ITEMS_PER_PAGE).list.map(x =>
      NewsFeedArticleInfo(NewsFeedArticle(x.id, x.feedId, x.title, x.link, x.description, x.author, x.comments,
                          x.enclosureUrl, x.enclosureLength, x.enclosureType, x.guid, x.isGuidPermalink,
                          x.pubDate, x.source), false, false))
  }
  
  def getLinkForPost(postId: Long)(implicit session: Session): String = {
    val query = for { unfa <- UserNewsFeedArticles if unfa.id === postId } yield unfa.link;
    query.first
  }
  
  def getPostsForFeeds(userId: Int, feedIds: List[Int], unreadOnly: Boolean, offset: Int, maxEntries: Int, latestPostDate: Long, latestPostId: Long)(implicit session: Session): List[NewsFeedArticleInfo] = {
    val feed_posts = if (unreadOnly) {
      for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.feedId.inSet(feedIds) && 
                                            unfa.id <= latestPostId && unixTimestampFn(unfa.pubDate) < latestPostDate &&
                                            unfa.isRead === false } yield unfa
    } else {
      for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.feedId.inSet(feedIds) &&
                                            unfa.id <= latestPostId && unixTimestampFn(unfa.pubDate) < latestPostDate
                                            } yield unfa
    }
    
    feed_posts.sortBy(_.pubDate.desc).drop(offset).take(maxEntries).list.map(x => {
      NewsFeedArticleInfo(
          NewsFeedArticle(x.id, x.feedId, x.title, x.link, x.description, x.author, x.comments,
                          x.enclosureUrl, x.enclosureLength, x.enclosureType, x.guid, x.isGuidPermalink,
                          x.pubDate, x.source),
          x.isRead == false, x.isSaved)
    })
  }
  
  def getPostsForFeed(userId: Int, feedId: Int, unreadOnly: Boolean, offset: Int, maxEntries: Int, latestPostDate: Long, latestPostId: Long)(implicit session: Session) : List[NewsFeedArticleInfo] = {
    val feed_posts = if (unreadOnly) {
      for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.feedId === feedId && 
                                            unfa.id <= latestPostId && unixTimestampFn(unfa.pubDate) < latestPostDate &&
                                            unfa.isRead === false } yield unfa
    } else {
      for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.feedId === feedId &&
                                            unfa.id <= latestPostId && unixTimestampFn(unfa.pubDate) < latestPostDate
                                            } yield unfa
    }
    
    feed_posts.sortBy(_.pubDate.desc).drop(offset).take(maxEntries).list.map(x => {
      NewsFeedArticleInfo(
          NewsFeedArticle(x.id, x.feedId, x.title, x.link, x.description, x.author, x.comments,
                          x.enclosureUrl, x.enclosureLength, x.enclosureType, x.guid, x.isGuidPermalink,
                          x.pubDate, x.source),
          x.isRead == false, x.isSaved)
    })
  }
  
  def getPostsForAllFeeds(userId: Int, unreadOnly: Boolean, offset: Int, maxEntries: Int, latestPostDate: Long, latestPostId: Long)(implicit session: Session) : List[NewsFeedArticleInfo] = {
    val feed_posts = if (unreadOnly) {
      for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.id <= latestPostId &&
                                            unixTimestampFn(unfa.pubDate) < latestPostDate &&
                                            unfa.isRead === false } yield unfa
    } else {
      for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.id <= latestPostId && 
                                            unixTimestampFn(unfa.pubDate) < latestPostDate
                                            } yield unfa
    }
    
    feed_posts.sortBy(_.pubDate.desc).drop(offset).take(maxEntries).list.map(x => {
      NewsFeedArticleInfo(
          NewsFeedArticle(x.id, x.feedId, x.title, x.link, x.description, x.author, x.comments,
                          x.enclosureUrl, x.enclosureLength, x.enclosureType, x.guid, x.isGuidPermalink,
                          x.pubDate, x.source),
          x.isRead == false, x.isSaved)
    })
  }
  
  def getSavedPosts(userId: Int, offset: Int, maxEntries: Int, latestPostDate: Long, latestPostId: Long)(implicit session: Session) : List[NewsFeedArticleInfo] = {
    val feed_posts = for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.id <= latestPostId &&
                                                           unixTimestampFn(unfa.pubDate) < latestPostDate &&
                                                           unfa.isSaved === true } yield unfa
    
    feed_posts.sortBy(_.pubDate.desc).drop(offset).take(maxEntries).list.map(x => {
      NewsFeedArticleInfo(
          NewsFeedArticle(x.id, x.feedId, x.title, x.link, x.description, x.author, x.comments,
                          x.enclosureUrl, x.enclosureLength, x.enclosureType, x.guid, x.isGuidPermalink,
                          x.pubDate, x.source),
          false, true)
    })
  }
  
  def setPostStatusForAllPosts(userId: Int, feedId: Int, from: Long, upTo: Long, unread: Boolean)(implicit session: Session) : Boolean = {
    val feedPostsQuery = for { unfa <- UserNewsFeedArticles if unfa.userId === userId && unfa.feedId === feedId && 
                                                            unixTimestampFn(unfa.pubDate) <= from &&
                                                            unixTimestampFn(unfa.pubDate) >= upTo &&
                                                            unfa.isRead === unread } yield unfa.isRead
    feedPostsQuery.update(!unread)
    true
  }
  
  def setPostStatusForAllPosts(userId: Int, from: Long, upTo: Long, unread: Boolean)(implicit session: Session) : Boolean = {
    val feedPostsQuery = for { unfa <- UserNewsFeedArticles if unfa.userId === userId &&
                                                            unixTimestampFn(unfa.pubDate) <= from &&
                                                            unixTimestampFn(unfa.pubDate) >= upTo &&
                                                            unfa.isRead === unread } yield unfa.isRead
    feedPostsQuery.update(!unread)
    true
  }
  
  def setPostStatus(userId: Int, feedId: Int, postId: Long, unread: Boolean)(implicit session: Session) : Boolean = {
    val my_feed = for { uf <- UserFeeds if uf.feedId === feedId && uf.userId === userId } yield uf
    my_feed.firstOption match {
      case Some(_) => {
        val feedPostsQuery = for { unfa <- UserNewsFeedArticles if unfa.userId === userId &&
                                                                   unfa.feedId === feedId &&
                                                                   unfa.id === postId &&
                                                                   unfa.isRead === unread } yield unfa.isRead
        feedPostsQuery.update(!unread)
        true
      }
      case _ => false
    }
  }
  
  def setPostStatus(userId: Int, postId: Long, unread: Boolean)(implicit session: Session) : Boolean = {
    val my_feed = for { uf <- UserFeeds if uf.userId === userId } yield uf
    my_feed.firstOption match {
      case Some(_) => {
        val feedPostsQuery = for { unfa <- UserNewsFeedArticles if unfa.userId === userId &&
                                                                   unfa.id === postId &&
                                                                   unfa.isRead === unread } yield unfa.isRead
        feedPostsQuery.update(!unread)
        true
      }
      case _ => false
    }
  }
  
  def savePost(userId: Int, feedId: Int, postId: Long)(implicit session: Session) : Boolean = {
    val my_feed = for { uf <- UserFeeds if uf.feedId === feedId && uf.userId === userId } yield uf
    my_feed.firstOption match {
      case Some(_) => {
        val feedPostsQuery = for { unfa <- UserNewsFeedArticles if unfa.userId === userId &&
                                                                   unfa.feedId === feedId &&
                                                                   unfa.id === postId  } yield unfa.isSaved
        feedPostsQuery.update(true)
        true
      }
      case _ => false
    }
  }
  
  def unsavePost(userId: Int, feedId: Int, postId: Long)(implicit session: Session) : Boolean = {
    val my_feed = for { uf <- UserFeeds if uf.feedId === feedId && uf.userId === userId } yield uf
    my_feed.firstOption match {
      case Some(_) => {
        val feedPostsQuery = for { unfa <- UserNewsFeedArticles if unfa.userId === userId &&
                                                                   unfa.feedId === feedId &&
                                                                   unfa.id === postId  } yield unfa.isSaved
        feedPostsQuery.update(false)
        true
      }
      case _ => false
    }
  }
  
  def getUserSessionById(sessionId: String)(implicit session: Session) : UserSession = {
    (for { sess <- UserSessions if sess.sessionId === sessionId } yield sess).first
  }
  
  def getUserSession(sessionId: String, ip: String)(implicit session: Session) : Option[UserSession] = {
    val q = (for { sess <- UserSessions if sess.sessionId === sessionId } yield sess)
    q.firstOption match {
      case Some(s) => {
        q.update(UserSession(s.userId, s.sessionId, new java.sql.Timestamp(new java.util.Date().getTime()), ip))
        Some(s)
      }
      case None => None
    }
  }
  
  def getUserName(userId: Int)(implicit session: Session) : String = {
    val q = for { u <- Users if u.id === userId } yield u.username
    q.firstOption.getOrElse("")
  }
  
  def getUserInfoByUsername(username: String)(implicit session: Session) : Option[User] = {
    val q = for { u <- Users if u.username === username } yield u
    q.firstOption
  }
  
  def getUserInfo(userId: Int)(implicit session: Session) : User = {
    val q = for { u <- Users if u.id === userId } yield u
    q.first
  }
  
  def setOptOut(userId: Int, optOut: Boolean)(implicit session: Session) {
    val user = getUserInfo(userId)
    val q = for { u <- Users if u.id === userId } yield u
    q.update(User(user.id, user.username, user.password, user.email, user.friendlyName, optOut, user.isAdmin))
  }
  
  def invalidateSession(sessionId: String)(implicit session: Session) {
    val q = (for { sess <- UserSessions if sess.sessionId === sessionId } yield sess)
    q.firstOption match {
        case Some(s) => q.delete
        case None => ()
      }
  }
  
  def startUserSession(sessionId: String, email: String, ip: String, friendlyName: String)(implicit session: Session) {
    startUserSession(sessionId, email, email, ip, friendlyName) 
  }
  
  def createUser(username: String, password: String, email: String)(implicit session: Session) = {
    val q = for { u <- Users if u.username === username } yield u
    if (q.firstOption.isDefined) { false }
    else {
      Users.insert(User(None, username, AuthenticationTools.hashPassword(password), email, username, false, false))
      true
    }
  }
  
  def setPassword(username: String, password: String)(implicit session: Session) = {
    val q = for { u <- Users if u.username === username } yield u.password
    q.update(AuthenticationTools.hashPassword(password))
  }
  
  def setPassword(uId: Int, password: String)(implicit session: Session) = {
    val q = for { u <- Users if u.id === uId } yield u.password
    q.update(AuthenticationTools.hashPassword(password))
  }
  
  def setEmail(uId: Int, email: String)(implicit session: Session) = {
    val q = for { u <- Users if u.id === uId } yield u.email
    q.update(email)
  }
  
  def startUserSession(sessionId: String, username: String, email: String, ip: String, friendlyName: String)(implicit session: Session) {
    val q = for { u <- Users if u.username === username } yield u
    val userId = q.firstOption match {
      case Some(u) => {
        q.update(User(u.id, u.username, u.password, email, friendlyName, u.optOutSharing, u.isAdmin))
        u.id.get
      }
      case None => {
        Users returning Users.id insert User(None, username, "", email, friendlyName, false, false)
      }
    }
    UserSessions.insert(UserSession(userId, sessionId, new java.sql.Timestamp(new java.util.Date().getTime()), ip))
  }
  
  private def updateOrInsertFeedInfo(feedUrl: String, feed: XmlFeed)(implicit session: Session) : NewsFeed = {
    val feedQuery = Query(NewsFeeds)
    val newsFeed = 
      for { f <- NewsFeeds if f.feedUrl === feedUrl } yield
      (f.copyright ~ f.description ~ f.docs ~ f.generator ~ f.imageLink ~
       f.imageTitle ~ f.imageUrl ~ f.language ~ f.lastBuildDate ~ f.link ~
       f.managingEditor ~ f.pubDate ~ f.title ~ f.ttl ~ f.webMaster ~ f.lastUpdate ~ f.hash)
      
    newsFeed.firstOption match {
      case Some(fd) => {
        if (fd._17 == feed.feedProperties.hash) throw new NotModifiedException
        
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
           new java.sql.Timestamp(new java.util.Date().getTime()),
           feed.feedProperties.hash))
        }
        case None => {
          (NewsFeeds.feedUrl ~ NewsFeeds.copyright ~ NewsFeeds.description ~ NewsFeeds.docs ~ NewsFeeds.generator ~ NewsFeeds.imageLink ~
           NewsFeeds.imageTitle ~ NewsFeeds.imageUrl ~ NewsFeeds.language ~ NewsFeeds.lastBuildDate ~ NewsFeeds.link ~
           NewsFeeds.managingEditor ~ NewsFeeds.pubDate ~ NewsFeeds.title ~ NewsFeeds.ttl ~ NewsFeeds.webMaster ~ NewsFeeds.lastUpdate ~ NewsFeeds.hash).insert(
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
            feed.feedProperties.hash
          )
        }
      }
    
    (for { f <- NewsFeeds if f.feedUrl === feedUrl } yield f).first
  }
  
  def updateOrInsertFeed(userId: Int, feedUrl: String, feed: XmlFeed)(implicit session: Session) : NewsFeed = {
    val f = updateOrInsertFeedInfo(feedUrl, feed)
    val newsFeedId = f.id.get
      
    // Now update/insert each individual post in the feed.
    for { p <- feed.entries } insertOrUpdateEntry(userId, newsFeedId, p)
    f
  }
  
  def updateOrInsertFeed(feedUrl: String, feed: XmlFeed)(implicit session: Session) : NewsFeed = {
    val f = updateOrInsertFeedInfo(feedUrl, feed)
    val newsFeedId = f.id.get
      
    // Now update/insert each individual post in the feed.
    val subscribed_users = for { 
      f <- NewsFeeds if f.feedUrl === feedUrl
      uf <- UserFeeds if uf.feedId === f.id
    } yield uf.userId
    subscribed_users.list.foreach(uid =>
      for { p <- feed.entries } insertOrUpdateEntry(uid, newsFeedId, p)
    )
    
    f
  }
  
  private def insertOrUpdateEntry(userId: Int, feedId: Int, p: (NewsFeedArticle, List[String]))(implicit session: Session) {
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
            UserNewsFeedArticles.userId ~ UserNewsFeedArticles.feedId ~ UserNewsFeedArticles.title ~ UserNewsFeedArticles.link ~ UserNewsFeedArticles.description ~
            UserNewsFeedArticles.author ~ UserNewsFeedArticles.comments ~ UserNewsFeedArticles.enclosureLength ~
            UserNewsFeedArticles.enclosureType ~ UserNewsFeedArticles.enclosureUrl ~ UserNewsFeedArticles.guid ~
            UserNewsFeedArticles.isGuidPermalink ~ UserNewsFeedArticles.pubDate ~ UserNewsFeedArticles.source ~
            UserNewsFeedArticles.isRead ~ UserNewsFeedArticles.isSaved) returning UserNewsFeedArticles.id insert(
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
            )
      }
  }

  def logFeedFailure(feedUrl: String, message: String)(implicit session: Session) {
    val feed = for { nf <- NewsFeeds if nf.feedUrl === feedUrl } yield nf
    feed.firstOption match {
      case Some(f) => {
        FeedFailureLogs.insert(
            FeedFailureLog(None, f.id.get, new java.sql.Timestamp(new java.util.Date().getTime()), message))
      }
      case None => ()
    }
  }
  
  def deleteOldSessions()(implicit session: Session) {
    val oneWeekAgo = new java.sql.Timestamp(new java.util.Date().getTime() - 60*60*24*7*1000)
    val oldSessions = for { us <- UserSessions if unixTimestampFn(us.lastAccess) < unixTimestampFn(Some(oneWeekAgo)) } yield us
    oldSessions.delete
  }
  
  def deleteOldFailLogs()(implicit session: Session) {
    val oneWeekAgo = new java.sql.Timestamp(new java.util.Date().getTime() - 60*60*24*7*1000)
    val oldFailLogs = for { fl <- FeedFailureLogs if unixTimestampFn(fl.failureDate) < unixTimestampFn(Some(oneWeekAgo)) } yield fl
    oldFailLogs.delete
  }
}
