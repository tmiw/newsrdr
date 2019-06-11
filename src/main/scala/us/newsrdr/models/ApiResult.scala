package us.newsrdr.models

object Constants {
  val ITEMS_PER_PAGE = 10
  val LOGIN_URI = "/auth/login"
  val AUTHENTICATED_URI = "/auth/authenticated"
  val GOOGLE_CLIENT_ID = "51702830260-toudua1ufu12a2f7rbge9c0jpbhoqej2.apps.googleusercontent.com"
  val GOOGLE_CLIENT_SECRET = "INSERT SECRET HERE"
  
  def getURL(request: javax.servlet.http.HttpServletRequest, uri: String) : String = {
    ("https://" + request.getServerName() + uri)
  }
  
  def getAuthenticatedURL(request: javax.servlet.http.HttpServletRequest, service: String) : String = {
    ("https://" + request.getServerName() + Constants.AUTHENTICATED_URI + "/" + service)
  }
  
  def getGoogleLoginURL(request: javax.servlet.http.HttpServletRequest) : String = {
    // TODO: CSRF verification using state variable.
    "https://accounts.google.com/o/oauth2/auth?scope=email+profile&state=xyz&redirect_uri=" +
    getAuthenticatedURL(request, "google") +
    "&response_type=code&client_id=" + GOOGLE_CLIENT_ID + "&access_type=online"
  }
}

class ApiResult(success: Boolean, error_string: Option[String])

case class StringDataApiResult(success: Boolean, error_string: Option[String], data: String)
  extends ApiResult(success, error_string)

case class AddFeedListApiResult(success: Boolean, error_string: Option[String], data: List[AddFeedEntry])
  extends ApiResult(success, error_string)

case class NoDataApiResult(success: Boolean, error_string: Option[String])
  extends ApiResult(success, error_string)

case class FeedInfoApiResult(success: Boolean, error_string: Option[String], data: NewsFeedInfo) 
  extends ApiResult(success, error_string)

case class FeedListApiResult(success: Boolean, error_string: Option[String], data: List[NewsFeedInfo]) 
  extends ApiResult(success, error_string)

case class ArticleListWithMaxId(id: Long, list: List[NewsFeedArticleInfo])

case class SavedArticleListWithMaxId(id: Long, list: List[NewsFeedArticleInfoWithFeed])

case class ArticleListApiResult(success: Boolean, error_string: Option[String], data: ArticleListWithMaxId) 
    extends ApiResult(success, error_string)
