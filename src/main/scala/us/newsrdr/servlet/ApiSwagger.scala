package us.newsrdr.servlet

import org.scalatra.swagger.{NativeSwaggerBase, Swagger, ApiInfo}
import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import us.newsrdr.models._

import slick.jdbc.JdbcBackend.Database

class ResourcesApp(implicit val swagger: Swagger) extends ScalatraServlet with NativeSwaggerBase {
  implicit override val jsonFormats: Formats = DefaultFormats
}

object NewsrdrApiInfo extends ApiInfo(
    "The newsrdr API",
    "Docs for the newsrdr API",
    "http://newsrdr.us",
    "webmaster@newsrdr.us",
    "BSD",
    "https://opensource.org/licenses/BSD-2-Clause")

class ApiSwagger extends Swagger("1.0", "1", NewsrdrApiInfo)

trait ApiExceptionWrapper {
  def executeOrReturnError(f: => Any) = {
    try {
      f
    }
    catch {
      case e:Throwable => new ApiResult(false, Some(e.getMessage()))
    }
  }
}

trait AuthOpenId {
  def getUserId(dao: DataTables, db: Database, sessionId: String, http: javax.servlet.ServletRequest) : Option[Int] = {
    dao.getUserSession(sessionId, http.getRemoteAddr())(db).map(_.userId)
  }
    
  def authenticationRequired[T](dao: DataTables, id: String, db: Database, http: javax.servlet.ServletRequest, f: => T, g: => T) : T = {
    dao.getUserSession(id, http.getRemoteAddr())(db) match {
      case Some(sess) => f
      case None => g
    }
  }
}
