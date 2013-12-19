package us.newsrdr.models

import us.newsrdr.tasks._
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
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
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
  
  object NewsFeedArticleCategories extends Table[(Int, Long, Int)]("NewsFeedArticleCategories") {
      def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
      def articleId = column[Long]("articleIdentifier")
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
  
  object UserArticles extends Table[UserArticle]("UserArticles") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Int]("userId")
    def articleId = column[Long]("articleId")
    def articleRead = column[Boolean]("articleRead")
    def articleSaved = column[Boolean]("articleSaved")
    
    def * = id.? ~ userId ~ articleId ~ articleRead ~ articleSaved <> (UserArticle, UserArticle.unapply _)
    def maybe = id.? ~ userId.? ~ articleId.? ~ articleRead.? ~ articleSaved.? <> (
        tupleToArticle _,
        (ua: Option[UserArticle]) => None)
        
    def tupleToArticle(uaTuple: (Option[Int], Option[Int], Option[Long], Option[Boolean], Option[Boolean])): Option[UserArticle] = 
    {
      uaTuple match {
        case (Some(id), Some(uId), Some(aId), Some(aRead), Some(aSaved)) => Some(UserArticle(Some(id), uId, aId, aRead, aSaved))
        case (_, _, _, _, _) => None
      }
    }
    
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
      if (!MTable.getTables.list.exists(_.name.name == NewsFeedArticles.tableName)) NewsFeedArticles.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == NewsFeedArticleCategories.tableName)) NewsFeedArticleCategories.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == Users.tableName)) Users.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == UserArticles.tableName)) UserArticles.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == UserFeeds.tableName)) UserFeeds.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == UserSessions.tableName)) UserSessions.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == FeedFailureLogs.tableName)) FeedFailureLogs.ddl.create
      if (!MTable.getTables.list.exists(_.name.name == BlogEntries.tableName)) BlogEntries.ddl.create
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
    val queryString = if (driver.isInstanceOf[H2Driver]) {
      """
        select "uf"."id", count(*) as unread 
        from "UserFeeds" "uf"
            inner join "NewsFeedArticles" "nfa" on "nfa"."feedId" = "uf"."feedId" 
            left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
        where "uf"."userId" = ? and
                ("ua"."articleRead" is null or "ua"."articleRead" = false) and
              UNIX_TIMESTAMP("nfa"."pubDate") >= (UNIX_TIMESTAMP("uf"."addedDate") - (60*60*24*14))
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
              UNIX_TIMESTAMP(nfa.pubDate) >= (UNIX_TIMESTAMP(uf.addedDate) - (60*60*24*14))
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
  
  def getUnreadCountForFeed(userId: Int, feedId: Int)(implicit session: Session) : Int = {
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
      (nfa, nf) <- NewsFeedArticles innerJoin NewsFeeds on (_.feedId === _.id) if nfa.id === postId
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
          nfa <- NewsFeedArticles if nfa.feedId === uf.feedId
        } yield nfa
      } else {
        for {
          nfa <- NewsFeedArticles
          nf <- NewsFeeds if nfa.feedId === nf.id
          uf <- UserFeeds if nf.id === uf.feedId
          u <- Users if u.id === uf.userId && u.id === userId
        } yield nfa
      }
    
    articleQuery.take(Constants.ITEMS_PER_PAGE).list.map(NewsFeedArticleInfo(_, false, false))
  }
  
  def getMaxPostIdForFeed(userId: Int, feedId: Int, unreadOnly: Boolean, latestPostDate: Long)(implicit session: Session) : Long = {
    implicit val getNewsFeedArticleResult = GetResult(r => NewsFeedArticle(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

    val feed_posts = if (driver.isInstanceOf[H2Driver]) {
      if (unreadOnly) {
        Q.query[(Int, Int, Long), Long]("""
          select max("nfa"."id")
          from "NewsFeedArticles" "nfa"
          inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
          left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
                    "uf"."feedId" = ? and
                    unix_timestamp("nfa"."pubDate") < ? and
                    unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                    ("ua"."articleRead" is null or "ua"."articleRead" = 0)""")
      } else {
        Q.query[(Int, Int, Long), Long]("""
          select max("nfa"."id")
          from "NewsFeedArticles" "nfa"
          inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
          left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
                    "uf"."feedId" = ? and
                    unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                    unix_timestamp("nfa"."pubDate") < ?""")
      }
    } else {
      if (unreadOnly) {
        Q.query[(Int, Int, Long), Long]("""
          select max(nfa.id)
          from NewsFeedArticles nfa
          inner join UserFeeds uf on uf.feedId = nfa.feedId
          left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and 
                    uf.feedId = ? and
                    unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                    unix_timestamp(nfa.pubDate) < ? and
                    (ua.articleRead is null or ua.articleRead = 0)
          order by nfa.pubDate desc""")
      } else {
        Q.query[(Int, Int, Long), Long]("""
          select max(nfa.id)
          from NewsFeedArticles nfa
          inner join UserFeeds uf on uf.feedId = nfa.feedId
          left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and
                    uf.feedId = ? and
                    unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                    unix_timestamp(nfa.pubDate) < ?
          order by nfa.pubDate desc""")
      }
    }
    
    feed_posts.list((userId, feedId, latestPostDate)).head
  }
  
  def getPostsForFeeds(userId: Int, feedIds: List[Int], unreadOnly: Boolean, offset: Int, maxEntries: Int, latestPostDate: Long, latestPostId: Long)(implicit session: Session): List[NewsFeedArticleInfo] = {
    val q = if (unreadOnly) {
      for {
        (nfa, (uf, ua)) <- NewsFeedArticles join (UserFeeds leftJoin UserArticles on ((f,a) => f.userId === a.userId && a.articleRead === false)) on ((na, ufa) => na.feedId === ufa._1.feedId && (ufa._2.articleId.isNull || ufa._2.articleId === na.id))
                           if nfa.id <= latestPostId && unixTimestampFn(nfa.pubDate) < latestPostDate &&
                              nfa.id <= latestPostId && unixTimestampFn(nfa.pubDate) < latestPostDate &&
                              uf.userId === userId && uf.feedId.inSet(feedIds) &&
                              unixTimestampFn(nfa.pubDate) > unixTimestampFn(uf.addedDate) - (60*60*24*14).toLong
      } yield (nfa, ua.maybe)
    } else {
      for {
        (nfa, (uf, ua)) <- NewsFeedArticles join (UserFeeds leftJoin UserArticles on ((f,a) => f.userId === a.userId)) on ((na, ufa) => na.feedId === ufa._1.feedId && (ufa._2.articleId.isNull || ufa._2.articleId === na.id))
                           if nfa.id <= latestPostId && unixTimestampFn(nfa.pubDate) < latestPostDate &&
                              uf.userId === userId && uf.feedId.inSet(feedIds) &&
                              unixTimestampFn(nfa.pubDate) > unixTimestampFn(uf.addedDate) - (60*60*24*14).toLong
      } yield (nfa, ua.maybe)
    }
    
    q.sortBy(p => p._1.pubDate.desc)
     .drop(offset)
     .take(maxEntries)
     .list
     .map(p => {
       if (p._2.isEmpty) NewsFeedArticleInfo(p._1, true, false)
       else NewsFeedArticleInfo(p._1, p._2.get.articleRead == false, p._2.get.articleSaved)
     })
  }
  
  def getPostsForFeed(userId: Int, feedId: Int, unreadOnly: Boolean, offset: Int, maxEntries: Int, latestPostDate: Long, latestPostId: Long)(implicit session: Session) : List[NewsFeedArticleInfo] = {
    implicit val getNewsFeedArticleResult = GetResult(r => NewsFeedArticle(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

    val feed_posts = if (driver.isInstanceOf[H2Driver]) {
      if (unreadOnly) {
        Q.query[(Int, Int, Long, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
          select "nfa".*, (case when "ua"."articleRead" is null then 0 else "ua"."articleRead" end) as isRead,
            (case when "ua"."articleSaved" is null then 0 else "ua"."articleSaved" end) as isSaved
          from "NewsFeedArticles" "nfa"
          inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
          left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
                    "uf"."feedId" = ? and
                    "nfa"."id" <= ? and
                    unix_timestamp("nfa"."pubDate") < ? and
                    unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                    ("ua"."articleRead" is null or "ua"."articleRead" = 0)
          order by "nfa"."pubDate" desc
          limit ? offset ?""")
      } else {
        Q.query[(Int, Int, Long, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
          select "nfa".*, (case when "ua"."articleRead" is null then 0 else "ua"."articleRead" end) as isRead,
            (case when "ua"."articleSaved" is null then 0 else "ua"."articleSaved" end) as isSaved
          from "NewsFeedArticles" "nfa"
          inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
          left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
                    "uf"."feedId" = ? and
                    "nfa"."id" <= ? and
                    unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                    unix_timestamp("nfa"."pubDate") < ?
          order by "nfa"."pubDate" desc
          limit ? offset ?""")
      }
    } else {
      if (unreadOnly) {
        Q.query[(Int, Int, Long, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
          select nfa.*, (case when ua.articleRead is null then 0 else ua.articleRead end) as isRead,
            (case when ua.articleSaved is null then 0 else ua.articleSaved end) as isSaved
          from NewsFeedArticles nfa
          inner join UserFeeds uf on uf.feedId = nfa.feedId
          left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and 
                    uf.feedId = ? and
                    nfa.id <= ? and
                    unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                    unix_timestamp(nfa.pubDate) < ? and
                    (ua.articleRead is null or ua.articleRead = 0)
          order by nfa.pubDate desc
          limit ? offset ?""")
      } else {
        Q.query[(Int, Int, Long, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
          select nfa.*, (case when ua.articleRead is null then 0 else ua.articleRead end) as isRead,
            (case when ua.articleSaved is null then 0 else ua.articleSaved end) as isSaved
          from NewsFeedArticles nfa
          inner join UserFeeds uf on uf.feedId = nfa.feedId
          left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and
                    uf.feedId = ? and
                    nfa.id <= ? and
                    unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                    unix_timestamp(nfa.pubDate) < ?
          order by nfa.pubDate desc
          limit ? offset ?""")
      }
    }
    
    feed_posts.list((userId, feedId, latestPostId, latestPostDate, maxEntries, offset)).map(x => {
      NewsFeedArticleInfo(x._1, x._2 == false, x._3)
    })
  }
  
  def getMaxPostIdForAllFeeds(userId: Int, unreadOnly: Boolean, latestPostDate: Long)(implicit session: Session) : Long = {
    implicit val getNewsFeedArticleResult = GetResult(r => NewsFeedArticle(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

    val feed_posts = if (driver.isInstanceOf[H2Driver]) {
      if (unreadOnly) {
        Q.query[(Int, Long), Long]("""
          select max("nfa"."id")
          from "NewsFeedArticles" "nfa"
          inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
          left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
                    unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                    unix_timestamp("nfa"."pubDate") < ? and
                    ("ua"."articleRead" is null or "ua"."articleRead" = 0)""")
      } else {
        Q.query[(Int, Long), Long]("""
          select max("nfa"."id")
          from "NewsFeedArticles" "nfa"
          inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
          left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
                    unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                    unix_timestamp("nfa"."pubDate") < ?""")
      }
    } else {
      if (unreadOnly) {
        Q.query[(Int, Long), Long]("""
          select max(nfa.id)
          from NewsFeedArticles nfa
          inner join UserFeeds uf on uf.feedId = nfa.feedId
          left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and 
                    unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                    unix_timestamp(nfa.pubDate) < ? and
                    (ua.articleRead is null or ua.articleRead = 0)""")
      } else {
        Q.query[(Int, Long), Long]("""
          select max(nfa.id)
          from NewsFeedArticles nfa
          inner join UserFeeds uf on uf.feedId = nfa.feedId
          left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and
                    unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                    unix_timestamp(nfa.pubDate) < ?""")
      }
    }
    
    feed_posts.list((userId, latestPostDate)).head
  }
  
  def getPostsForAllFeeds(userId: Int, unreadOnly: Boolean, offset: Int, maxEntries: Int, latestPostDate: Long, latestPostId: Long)(implicit session: Session) : List[NewsFeedArticleInfo] = {
    implicit val getNewsFeedArticleResult = GetResult(r => NewsFeedArticle(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

    val feed_posts = if (driver.isInstanceOf[H2Driver]) {
      if (unreadOnly) {
        Q.query[(Int, Long, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
          select "nfa".*, (case when "ua"."articleRead" is null then 0 else "ua"."articleRead" end) as isRead,
            (case when "ua"."articleSaved" is null then 0 else "ua"."articleSaved" end) as isSaved
          from "NewsFeedArticles" "nfa"
          inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
          left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
                    "nfa"."id" <= ? and
                    unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                    unix_timestamp("nfa"."pubDate") < ? and
                    ("ua"."articleRead" is null or "ua"."articleRead" = 0)
          order by "nfa"."pubDate" desc
          limit ? offset ?""")
      } else {
        Q.query[(Int, Long, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
          select "nfa".*, (case when "ua"."articleRead" is null then 0 else "ua"."articleRead" end) as isRead,
            (case when "ua"."articleSaved" is null then 0 else "ua"."articleSaved" end) as isSaved
          from "NewsFeedArticles" "nfa"
          inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
          left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
                    "nfa"."id" <= ? and
                    unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                    unix_timestamp("nfa"."pubDate") < ?
          order by "nfa"."pubDate" desc
          limit ? offset ?""")
      }
    } else {
      if (unreadOnly) {
        Q.query[(Int, Long, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
          select nfa.*, (case when ua.articleRead is null then 0 else ua.articleRead end) as isRead,
            (case when ua.articleSaved is null then 0 else ua.articleSaved end) as isSaved
          from NewsFeedArticles nfa
          inner join UserFeeds uf on uf.feedId = nfa.feedId
          left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and 
                    nfa.id <= ? and
                    unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                    unix_timestamp(nfa.pubDate) < ? and
                    (ua.articleRead is null or ua.articleRead = 0)
          order by nfa.pubDate desc
          limit ? offset ?""")
      } else {
        Q.query[(Int, Long, Long, Int, Int), (NewsFeedArticle, Boolean, Boolean)]("""
          select nfa.*, (case when ua.articleRead is null then 0 else ua.articleRead end) as isRead,
            (case when ua.articleSaved is null then 0 else ua.articleSaved end) as isSaved
          from NewsFeedArticles nfa
          inner join UserFeeds uf on uf.feedId = nfa.feedId
          left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and
                    nfa.id <= ? and
                    unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                    unix_timestamp(nfa.pubDate) < ?
          order by nfa.pubDate desc
          limit ? offset ?""")
      }
    }
    
    feed_posts.list((userId, latestPostId, latestPostDate, maxEntries, offset)).map(x => {
      NewsFeedArticleInfo(x._1, x._2 == false, x._3)
    })
  }
  
  def getSavedPosts(userId: Int, offset: Int, maxEntries: Int, latestPostDate: Long, latestPostId: Long)(implicit session: Session) : List[NewsFeedArticleInfo] = {
    implicit val getNewsFeedArticleResult = GetResult(r => NewsFeedArticle(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

    val feed_posts = if (driver.isInstanceOf[H2Driver]) {
      Q.query[(Int, Long, Long, Int, Int), (NewsFeedArticle, Boolean)]("""
        select "nfa".*, (case when "ua"."articleRead" is null then 0 else "ua"."articleRead" end) as isRead
        from "NewsFeedArticles" "nfa"
        inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId" and "ua"."userId" = "uf"."userId"
        left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" 
              where "uf"."userId" = ? and 
                  "nfa"."id" <= ? and
                  unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                    "nfa"."id" <= ? and
                  "ua"."articleSaved" = 1
        order by "nfa"."pubDate" desc
        limit ? offset ?""")
    } else {
      Q.query[(Int, Long, Long, Int, Int), (NewsFeedArticle, Boolean)]("""
        select nfa.*, (case when ua.articleRead is null then 0 else ua.articleRead end) as isRead
        from NewsFeedArticles nfa
        inner join UserFeeds uf on uf.feedId = nfa.feedId
        left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
              where uf.userId = ? and 
                  nfa.id <= ? and
                  unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                    nfa.id <= ? and
                  ua.articleSaved = 1
        order by nfa.pubDate desc
        limit ? offset ?""")
    }
    
    feed_posts.list((userId, latestPostDate, latestPostId, maxEntries, offset)).map(x => {
      NewsFeedArticleInfo(x._1, false, true)
    })
  }
  
  def setPostStatusForAllPosts(userId: Int, feedId: Int, from: Int, upTo: Int, unread: Boolean)(implicit session: Session) : Boolean = {
    val feedPostsQuery = if (driver.isInstanceOf[H2Driver]) {
      Q.query[(Int, Int, Long, Long), (Long, Option[Boolean])]("""
            select "nfa"."id", "ua"."articleRead"
            from "NewsFeedArticles" "nfa"
            inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
            left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
                      "uf"."feedId" = ? and
                      unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                      unix_timestamp("nfa"."pubDate") <= ? and
                    unix_timestamp("nfa"."pubDate") >= ? and
                      ("ua"."articleRead" is null or "ua"."articleRead" = 0)""")
    } else {
      Q.query[(Int, Int, Long, Long), (Long, Option[Boolean])]("""
            select nfa.id, ua.articleRead
            from NewsFeedArticles nfa
            inner join UserFeeds uf on uf.feedId = nfa.feedId
            left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and 
                      uf.feedId = ? and
                      unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                      unix_timestamp(nfa.pubDate) <= ? and
                      unix_timestamp(nfa.pubDate) >= ? and
                      (ua.articleRead is null or ua.articleRead = 0)""")
    }
    val feedPosts = feedPostsQuery.list(userId, feedId, from, upTo)
    val feedPostsToAdd = feedPosts.filter(_._2.isEmpty).map(_._1)
    val feedPostsToUpdate = feedPosts.filter(!_._2.isEmpty).map(_._1)
    
    val updateQuery = for { ua <- UserArticles if ua.articleId inSetBind feedPostsToUpdate } yield ua.articleRead
    updateQuery.update(!unread)
    feedPostsToAdd.foreach((p: Long) => UserArticles.insert(UserArticle(None, userId, p, !unread, false)))
    true
  }
  
  def setPostStatusForAllPosts(userId: Int, from: Int, upTo: Int, unread: Boolean)(implicit session: Session) : Boolean = {
      val feedPostsQuery = if (driver.isInstanceOf[H2Driver]) {
        Q.query[(Int, Long, Long), (Long, Option[Boolean])]("""
            select "nfa"."id", "ua"."articleRead"
            from "NewsFeedArticles" "nfa"
            inner join "UserFeeds" "uf" on "uf"."feedId" = "nfa"."feedId"
            left join "UserArticles" "ua" on "ua"."articleId" = "nfa"."id" and "ua"."userId" = "uf"."userId"
                where "uf"."userId" = ? and 
                      unix_timestamp("nfa"."pubDate") > (unix_timestamp("uf"."addedDate") - (60*60*24*14)) and
                      unix_timestamp("nfa"."pubDate") <= ? and
                      unix_timestamp("nfa"."pubDate") >= ? and
                      ("ua"."articleRead" is null or "ua"."articleRead" = 0)""")
      } else {
        Q.query[(Int, Long, Long), (Long, Option[Boolean])]("""
            select nfa.id, ua.articleRead
            from NewsFeedArticles nfa
            inner join UserFeeds uf on uf.feedId = nfa.feedId
            left join UserArticles ua on ua.articleId = nfa.id and ua.userId = uf.userId
                where uf.userId = ? and 
                      unix_timestamp(nfa.pubDate) > (unix_timestamp(uf.addedDate) - (60*60*24*14)) and
                      unix_timestamp(nfa.pubDate) <= ? and
                      unix_timestamp(nfa.pubDate) >= ? and
                      (ua.articleRead is null or ua.articleRead = 0)""")
      }
      val feedPosts = feedPostsQuery.list(userId, from, upTo)
      val feedPostsToAdd = feedPosts.filter(_._2.isEmpty).map(_._1)
      val feedPostsToUpdate = feedPosts.filter(!_._2.isEmpty).map(_._1)
      
      val updateQuery = for { ua <- UserArticles if ua.articleId inSetBind feedPostsToUpdate } yield ua.articleRead
      updateQuery.update(!unread)
      feedPostsToAdd.foreach((p: Long) => UserArticles.insert(UserArticle(None, userId, p, !unread, false)))
      true
    }
  
  def setPostStatus(userId: Int, feedId: Int, postId: Long, unread: Boolean)(implicit session: Session) : Boolean = {
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
  
  def setPostStatus(userId: Int, postId: Long, unread: Boolean)(implicit session: Session) : Boolean = {
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
            val single_feed_post = for { ua <- UserArticles if ua.userId === x.userId && ua.articleId === x.articleId } yield ua.articleRead
            single_feed_post.update(!unread)
          }
          case None => UserArticles.insert(UserArticle(None, userId, postId, !unread, false))
        }
        true
      }
      case None => false
    }
  }
  
  def savePost(userId: Int, feedId: Int, postId: Long)(implicit session: Session) : Boolean = {
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
  
  def unsavePost(userId: Int, feedId: Int, postId: Long)(implicit session: Session) : Boolean = {
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
  
  def startUserSession(sessionId: String, username: String, email: String, ip: String, friendlyName: String)(implicit session: Session) {
    val q = for { u <- Users if u.username === username } yield u
    val userId = q.firstOption match {
      case Some(u) => {
        q.update(User(u.id, u.username, u.password, email, friendlyName, false, u.isAdmin))
        u.id.get
      }
      case None => {
        Users returning Users.id insert User(None, username, "", email, friendlyName, false, false)
      }
    }
    UserSessions.insert(UserSession(userId, sessionId, new java.sql.Timestamp(new java.util.Date().getTime()), ip))
  }
  
  def updateOrInsertFeed(feedUrl: String, feed: XmlFeed)(implicit session: Session) : NewsFeed = {
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
      for { p <- feed.entries } insertOrUpdateEntry(newsFeedId, p)
      
      (for { f <- NewsFeeds if f.feedUrl === feedUrl } yield f).first
  }
  
  private def insertOrUpdateEntry(feedId: Int, p: (NewsFeedArticle, List[String]))(implicit session: Session) {
      val newPost = p._1
    
      // Insert or update article as needed.
      val existingEntryId = for { 
        e <- NewsFeedArticles if e.feedId === feedId &&
                     ((e.link === newPost.link && !newPost.link.isEmpty()) ||
                                 (e.guid =!= (None : Option[String]) && e.guid === newPost.guid) || 
                                 (e.title === newPost.title && e.description === newPost.description))
      } yield e.id
      val entry = for { 
        e <- NewsFeedArticles if e.feedId === feedId &&
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
