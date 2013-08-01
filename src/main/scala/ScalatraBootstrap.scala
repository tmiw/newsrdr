import com.thoughtbug.newsrdr._
import org.scalatra._
import javax.servlet.ServletContext
import com.mchange.v2.c3p0.ComboPooledDataSource
import org.slf4j.LoggerFactory
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import scala.slick.session.Database
import com.thoughtbug.newsrdr.models._;
import com.thoughtbug.newsrdr.tasks.BackgroundJobManager

class ScalatraBootstrap extends LifeCycle {
  val logger = LoggerFactory.getLogger(getClass)
  implicit val swagger = new ApiSwagger
  
  val cpds = new ComboPooledDataSource
  logger.info("Created c3p0 connection pool")
  
  override def init(context: ServletContext) {
    val db = Database.forDataSource(cpds)  // create a Database which uses the DataSource
    context.mount(new NewsReaderServlet(db), "/*")
    context.mount(new FeedServlet(db, swagger), "/feeds/*")
    context mount(new ResourcesApp, "/api-docs/*")
    
    db withSession {
      (Categories.ddl ++ NewsFeeds.ddl ++ NewsFeedCategories.ddl ++
          NewsFeedArticles.ddl ++ NewsFeedArticleCategories.ddl ++
          Users.ddl ++ UserArticles.ddl ++ UserFeeds.ddl).create
      
      // HACK: create single user that all API code will assume until
      // auth code is in place
      Users.insert(User(None, "admin", "xyz", ""))
    }
    
    // Start Quartz scheduler.
    BackgroundJobManager.db = db
    BackgroundJobManager.start
  }
  
  private def closeDbConnection() {
    logger.info("Closing c3po connection pool")
    cpds.close
  }

  override def destroy(context: ServletContext) {
    super.destroy(context)
    BackgroundJobManager.shutdown
    closeDbConnection
  }
}
