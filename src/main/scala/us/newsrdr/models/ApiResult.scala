package us.newsrdr.models

object Constants {
  val ITEMS_PER_PAGE = 10
  val LOGIN_URI = "/auth/login"
  val AUTHENTICATED_URI = "/auth/authenticated"
  val FB_CLIENT_ID = "1375893982640236"
  val FB_CLIENT_SECRET = "INSERT SECRET HERE"
  
  def getURL(request: javax.servlet.http.HttpServletRequest, uri: String) : String = {
    var protocol = request.isSecure() match {
      case true => "https://"
      case _ => "http://"
    }
    
    (protocol + request.getServerName() + ":" + request.getServerPort().toString() + uri)
  }
  
  def getAuthenticatedURL(request: javax.servlet.http.HttpServletRequest, service: String) : String = {
    var protocol = request.isSecure() match {
      case true => "https://"
      case _ => "http://"
    }
    
    (protocol + request.getServerName() + ":" + request.getServerPort().toString() + Constants.AUTHENTICATED_URI + "/" + service)
  }
  
  def getFacebookLoginURL(request: javax.servlet.http.HttpServletRequest) : String = {
    "https://www.facebook.com/dialog/oauth?client_id=" + FB_CLIENT_ID + 
    "&redirect_uri=" + getAuthenticatedURL(request, "fb") +
    "&response_type=code&scope=email"
  }
}

class ApiResult(success: Boolean, error_string: Option[String])

case class NoDataApiResult(success: Boolean, error_string: Option[String])
	extends ApiResult(success, error_string)

case class FeedListApiResult(success: Boolean, error_string: Option[String], data: List[NewsFeedInfo]) 
	extends ApiResult(success, error_string)

case class ArticleListApiResult(success: Boolean, error_string: Option[String], data: List[NewsFeedArticleInfo]) 
    extends ApiResult(success, error_string)
