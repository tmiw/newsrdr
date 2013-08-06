import com.thoughtbug.newsrdr._
import org.scalatra._
import javax.servlet.ServletContext
import com.mchange.v2.c3p0.ComboPooledDataSource
import org.slf4j.LoggerFactory

import scala.slick.driver.{ExtendedProfile, H2Driver, MySQLDriver}
import scala.slick.session.{Database, Session}

import com.thoughtbug.newsrdr.models._;
import com.thoughtbug.newsrdr.tasks.BackgroundJobManager

class ScalatraBootstrap extends LifeCycle {
  val logger = LoggerFactory.getLogger(getClass)
  implicit val swagger = new ApiSwagger
  
  var cpds : ComboPooledDataSource = null
  
  override def init(context: ServletContext) {
    var environment = context.getInitParameter(org.scalatra.EnvironmentKey)
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
    context mount(new ResourcesApp, "/api-docs/*")
    
    db withTransaction { implicit session: Session =>
      dao.create
    }
    
    // Start Quartz scheduler.
    BackgroundJobManager.dao = dao
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