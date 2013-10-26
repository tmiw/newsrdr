import us.newsrdr.servlet._
import org.scalatra._
import javax.servlet.ServletContext
import com.mchange.v2.c3p0.ComboPooledDataSource
import org.slf4j.LoggerFactory

import scala.slick.driver.{ExtendedProfile, H2Driver, MySQLDriver}
import scala.slick.session.{Database, Session}
import scala.slick.jdbc.{StaticQuery => Q}

import us.newsrdr.models._;
import us.newsrdr.tasks.BackgroundJobManager

class ScalatraBootstrap extends LifeCycle {
  val logger = LoggerFactory.getLogger(getClass)
  implicit val swagger = new ApiSwagger
  
  var cpds : ComboPooledDataSource = null
  
  override def init(context: ServletContext) {
    val envVar = System.getenv("IS_PRODUCTION")
    
    if (envVar != null && envVar == "true" ) {
      // force envrionment to production mode
      System.setProperty(org.scalatra.EnvironmentKey, "production")
    } else if (sys.props.get(org.scalatra.EnvironmentKey) == None) {
      System.setProperty(org.scalatra.EnvironmentKey, "development")
    }
    
    context.initParameters("org.scalatra.cors.allowCredentials") = "true"
    
    val environment = sys.props.getOrElse(org.scalatra.EnvironmentKey, "development")
    System.setProperty("scalate.mode", environment)
    var dao = environment match {
      case "production" => new DataTables(MySQLDriver)
      case _ => new DataTables(H2Driver)
    } 
    
    cpds = new ComboPooledDataSource(environment)
    logger.info("Created c3p0 connection pool")
  
    val db = Database.forDataSource(cpds)  // create a Database which uses the DataSource
    context.mount(new NewsReaderServlet(dao, db), "/*")
    context.mount(new FeedServlet(dao, db, swagger), "/feeds/*")
    context.mount(new PostServlet(dao, db, swagger), "/posts/*")
    context.mount(new UserServlet(dao, db, swagger), "/user/*")
    context.mount(new AdminServlet(dao, db), "/admin/*")
    context.mount(new SavedPostsServlet(dao, db, swagger), "/saved/*")
    context.mount(new BlogServlet(dao, db, swagger), "/blog/*")
    context mount(new ResourcesApp, "/api-docs/*")
    
    if (dao.driver.isInstanceOf[H2Driver]) {
      // Add functions that are missing from H2 but exist in MySQL.
      var conn = cpds.getConnection()
      var stmt = conn.createStatement()
      stmt.execute("""CREATE ALIAS IF NOT EXISTS UNIX_TIMESTAMP AS $$
            long getSeconds(java.sql.Timestamp ts) {
            return ts.getTime() / 1000;
          } $$ """)
      conn.close()  
    }
    
    db withTransaction { implicit session: Session =>
      dao.create
    }
    
    // Start Quartz scheduler.
    BackgroundJobManager.dao = dao
    BackgroundJobManager.db = db
    BackgroundJobManager.start(context)
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
