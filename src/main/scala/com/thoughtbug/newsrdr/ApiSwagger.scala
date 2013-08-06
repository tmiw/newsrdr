package com.thoughtbug.newsrdr

import org.scalatra.swagger.{NativeSwaggerBase, Swagger}

import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import com.thoughtbug.newsrdr.models._

import scala.slick.session.Database

// Use H2Driver to connect to an H2 database
import scala.slick.driver.H2Driver.simple._

// Use the implicit threadLocalSession
import Database.threadLocalSession

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
  def getUserId(db: Database, sessionId: String) : Option[Int] = {
    db withSession {
      var q = (for { sess <- UserSessions if sess.sessionId === sessionId } yield sess)
      q.firstOption match {
        case Some(sess) => Some(sess.userId)
        case None => None
      }
    }
  }
    
  def authenticationRequired(id: String, db: Database, f: => Any, g: => Any) = {
    db withSession {
      var q = (for { sess <- UserSessions if sess.sessionId === id } yield sess)
      q.firstOption match {
        case Some(sess) => f
        case None => g
      }
    }
  }
}