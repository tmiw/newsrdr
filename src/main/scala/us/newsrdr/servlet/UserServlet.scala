package us.newsrdr.servlet

import org.scalatra._
import scalate.ScalateSupport
import servlet.{MultipartConfig, SizeConstraintExceededException, FileUploadSupport}
import scala.slick.session.Database
import us.newsrdr.models._
import us.newsrdr.tasks._
import us.newsrdr._
import scala.slick.session.{Database, Session}
import org.json4s.{DefaultFormats, Formats}
import org.json4s._
import org.json4s.JsonDSL._
import org.scalatra.json._
import org.scalatra.swagger._
import scala.xml._
import scala.collection._
import scala.util.matching.Regex
import javax.mail._
import javax.mail.internet._
import java.util.Properties

class UserServlet(dao: DataTables, db: Database,  props: Properties, implicit val swagger: Swagger) extends NewsrdrStack
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
  
  error {
    case e: Exception => {
      NoDataApiResult(false, Some("server_error"))
    }
  }
  
  override protected def templateAttributes(implicit request: javax.servlet.http.HttpServletRequest): mutable.Map[String, Any] = {
    val sessionId = request.getSession().getId()
    db withSession { implicit session: Session =>
      super.templateAttributes ++ mutable.Map("loggedIn" -> dao.getUserSession(sessionId, request.getRemoteAddr()).isDefined)
    }
  }
  
  val register =
    (apiOperation[Unit]("register")
        summary "Registers new account"
        notes "Registers a new account.")
        
  post("/", operation(register)) {
    val username = params.getOrElse("username", halt(422, NoDataApiResult(false, Some("validation_failed"))))
    val password = params.getOrElse("password", halt(422, NoDataApiResult(false, Some("validation_failed"))))
    val password2 = params.getOrElse("password2", halt(422, NoDataApiResult(false, Some("validation_failed"))))
    val email = params.getOrElse("email", halt(422, NoDataApiResult(false, Some("validation_failed"))))
    val sId = session.getId()
    
    if (username.length() == 0 || password.length() < 8 ||
        password2.length() == 0 || email.length() == 0) halt(422, NoDataApiResult(false, Some("validation_failed")))
    
    if (password != password2) halt(422, NoDataApiResult(false, Some("validation_failed")))
    
    if ("^[-0-9A-Za-z!#$%&'*+/=?^_`{|}~.]+@[-0-9A-Za-z!#$%&'*+/=?^_`{|}~.]+".r.findFirstIn(email).isEmpty) halt(422)
    
    db withTransaction { implicit s: Session => 
      val ret = dao.createUser(username, password, email)
      if (ret) {
        dao.startUserSession(sId, username, email, request.getRemoteAddr(), username)
        session.setAttribute("authService", "newsrdr")
      }
      NoDataApiResult(ret, None)
    }
  }
  
  val getProfile =
    (apiOperation[Unit]("getProfile")
        summary "Retrieves account profile."
        notes "Retrieves account profile.")
  get("/profile", operation(getProfile))
  {
    authenticationRequired(dao, session.getId, db, request, {
      val userId = getUserId(dao, db, session.getId, request).get
      
      // Only for newsrdr accounts.
      if (session.getAttribute("authService").toString() != "newsrdr") halt(401, NoDataApiResult(false, Some("auth_failed")))
      
      db withSession { implicit s: Session =>
        val email = dao.getUserInfo(userId).email
        StringDataApiResult(true, None, email)
      }
    }, {
      halt(401, NoDataApiResult(false, Some("auth_failed")))
    })
  }
  
  val updateProfile =
    (apiOperation[Unit]("updateProfile")
        summary "Updates account profile."
        notes "Updates account profile.")
  
  post("/profile", operation(updateProfile)) {
    authenticationRequired(dao, session.getId, db, request, {
      val userId = getUserId(dao, db, session.getId, request).get
      val password = params.get("password")
      val password2 = params.get("password2")
      val email = params.getOrElse("email", halt(422, NoDataApiResult(false, Some("validation_failed"))))
    
      // Only for newsrdr accounts.
      if (session.getAttribute("authService").toString() != "newsrdr") halt(401, NoDataApiResult(false, Some("auth_failed")))
      
      // Password is optional, but if provided, will be reset.
      if (password.isDefined)
      {
        if (password2.isEmpty || password.get != password2.get || (password.get.length < 8 && password.get.length > 0)) halt(422, NoDataApiResult(false, Some("validation_failed")))
      }
    
      db withTransaction { implicit s: Session => 
        dao.setEmail(userId, email)
        if (password.isDefined && password.get.length > 0) dao.setPassword(userId, password.get)
      }
      
      NoDataApiResult(true, None)
    }, {
      halt(401, NoDataApiResult(false, Some("auth_failed")))
    })
  }
  
  val resetPassword =
    (apiOperation[Unit]("resetPassword")
        summary "Resets password"
        notes "Resets password.")
  post("/resetPassword", operation(resetPassword)) {
    val username = params.getOrElse("username", halt(422, NoDataApiResult(false, Some("validation_failed"))))
    if (username.length() == 0) halt(422, NoDataApiResult(false, Some("validation_failed")))
    
    try {
      val newRandomPassword = AuthenticationTools.randomPassword
      val email = db withSession { implicit s: Session =>
        dao.getUserInfoByUsername(username).get.email
      }
      db withTransaction { implicit s: Session => 
        dao.setPassword(username, newRandomPassword)
      }
      
      // Send email to owner
      val session = Session.getDefaultInstance(props)
      val message = new MimeMessage(session)

      // TODO: i18n
      message.setFrom(new InternetAddress(props.get("us.newsrdr.email-to").toString()))
      message.setRecipients(javax.mail.Message.RecipientType.TO, email)
      message.setSubject("[newsrdr.us] Password reset")
      message.setText("Someone (probably you) has requested the password for your account be reset.\r\n" +
      		"Your new password is: " + newRandomPassword + "\r\n" +
      		"\r\n" +
      		"Visit http://newsrdr.us/auth/login to log in.")
   
      val tr = session.getTransport("smtp")
      if (props.get("mail.smtp.auth") == "true")
      {
        tr.connect(props.get("mail.smtp.user").toString(), props.get("mail.smtp.password").toString())
      }
      else
      {
        tr.connect()
      }
      message.saveChanges()
      tr.sendMessage(message, message.getAllRecipients())
      tr.close()
    } catch {
      case _:Exception => { /* ignore so we don't tip off hackers. */ }
    }
    
    NoDataApiResult(true, None)
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
      halt(401, NoDataApiResult(false, Some("auth_failed")))
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
      halt(401, NoDataApiResult(false, Some("auth_failed")))
    })
  }
}
