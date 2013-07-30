import com.thoughtbug.newsrdr._
import org.scalatra._
import javax.servlet.ServletContext
import com.mchange.v2.c3p0.ComboPooledDataSource
import org.slf4j.LoggerFactory
import scala.slick.session.Database

class ScalatraBootstrap extends LifeCycle {
  val logger = LoggerFactory.getLogger(getClass)
  
  val cpds = new ComboPooledDataSource
  logger.info("Created c3p0 connection pool")
  
  override def init(context: ServletContext) {
    val db = Database.forDataSource(cpds)  // create a Database which uses the DataSource
    context.mount(new NewsReaderServlet(db), "/*")
  }
  
  private def closeDbConnection() {
    logger.info("Closing c3po connection pool")
    cpds.close
  }

  override def destroy(context: ServletContext) {
    super.destroy(context)
    closeDbConnection
  }
}
