package com.thoughtbug.newsrdr

import org.scalatra.swagger.{NativeSwaggerBase, Swagger}

import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import com.thoughtbug.newsrdr.models._

import scala.slick.session.{Database, Session}

import org.openid4java.consumer._
import org.openid4java.discovery._
import org.openid4java.message.ax._
import org.openid4java.message._

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
      dao.getUserSession(session, sessionId, http.getRemoteAddr()).map(_.userId)
    }
  }
    
  def authenticationRequired(dao: DataTables, id: String, db: Database, http: javax.servlet.ServletRequest, f: => Any, g: => Any) = {
    db withSession { implicit session: Session =>
      dao.getUserSession(session, id, http.getRemoteAddr()) match {
        case Some(sess) => f
        case None => g
      }
    }
  }
}