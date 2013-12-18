package us.newsrdr.servlet

import org.scalatra._
import scalate.ScalateSupport
import servlet.{MultipartConfig, SizeConstraintExceededException, FileUploadSupport}
import scala.slick.session.Database
import us.newsrdr.models._
import us.newsrdr.tasks._
import scala.slick.session.{Database, Session}

// JSON-related libraries
import org.json4s.{DefaultFormats, Formats}
import org.json4s._
import org.json4s.JsonDSL._

// JSON handling support from Scalatra
import org.scalatra.json._

// Swagger support
import org.scalatra.swagger._

// XML support
import scala.xml._

import scala.collection._

class UserServlet(dao: DataTables, db: Database, implicit val swagger: Swagger) extends NewsrdrStack
  with NativeJsonSupport with SwaggerSupport with ApiExceptionWrapper with AuthOpenId with FileUploadSupport
  with GZipSupport {

  override protected val applicationName = Some("users")
  protected val applicationDescription = "The users API. This exposes operations for manipulating the user profile."
    
  // Sets up automatic case class to JSON output serialization
  protected implicit val jsonFormats: Formats = DefaultFormats

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }
  
  override protected def templateAttributes(implicit request: javax.servlet.http.HttpServletRequest): mutable.Map[String, Any] = {
    val sessionId = request.getSession().getId()
    db withSession { implicit session: Session =>
      super.templateAttributes ++ mutable.Map("loggedIn" -> dao.getUserSession(sessionId, request.getRemoteAddr()).isDefined)
    }
  }
  
  val optOut =
    (apiOperation[Unit]("optOut")
        summary "Opts out of sharing"
        notes "Opts user out of sharing feeds with others.")
   
  post("/optout", operation(optOut)) {
    authenticationRequired(dao, session.getId, db, request, {
      val userId = getUserId(dao, db, session.getId, request).get
      
      db withTransaction { implicit session: Session =>
        dao.setOptOut(userId, true)
        NoDataApiResult(true, None)
      }
    }, {
      halt(401)
    })
  }
  
  val optIn =
    (apiOperation[Unit]("deleteFeeds")
        summary "Opts into sharing"
        notes "Opts user into sharing feeds with others.")
        
  delete("/optout", operation(optIn)) {
    authenticationRequired(dao, session.getId, db, request, {
      val userId = getUserId(dao, db, session.getId, request).get
      
      db withTransaction { implicit session: Session =>
        dao.setOptOut(userId, false)
        NoDataApiResult(true, None)
      }
    }, {
      halt(401)
    })
  }
}
