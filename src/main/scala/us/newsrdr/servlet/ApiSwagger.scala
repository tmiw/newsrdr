package us.newsrdr.servlet

import org.scalatra.swagger.{NativeSwaggerBase, Swagger}

import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import us.newsrdr.models._

import scala.slick.session.{Database, Session}

class ResourcesApp(implicit val swagger: Swagger) extends ScalatraServlet with NativeSwaggerBase {
  implicit override val jsonFormats: Formats = DefaultFormats
}

class ApiSwagger extends Swagger("1.0", "1")

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
    db withSession { implicit session: Session =>
      dao.getUserSession(sessionId, http.getRemoteAddr()).map(_.userId)
    }
  }
    
  def authenticationRequired[T](dao: DataTables, id: String, db: Database, http: javax.servlet.ServletRequest, f: => T, g: => T) : T = {
    db withSession { implicit session: Session =>
      dao.getUserSession(id, http.getRemoteAddr()) match {
        case Some(sess) => f
        case None => g
      }
    }
  }
}
