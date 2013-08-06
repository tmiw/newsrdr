package com.thoughtbug.newsrdr.models

object Constants {
  var ITEMS_PER_PAGE = 10
  var LOGIN_URI = "/auth/login"
  var AUTHENTICATED_URI = "/auth/authenticated"
    
  def getAuthenticatedURL(request: javax.servlet.http.HttpServletRequest) : String = {
    var protocol = request.isSecure() match {
      case true => "https://"
      case _ => "http://"
    }
    
    (protocol + request.getServerName() + ":" + request.getServerPort().toString() + Constants.AUTHENTICATED_URI)
  }
}

class ApiResult(success: Boolean, error_string: Option[String])

case class NoDataApiResult(success: Boolean, error_string: Option[String])
	extends ApiResult(success, error_string)

case class FeedListApiResult(success: Boolean, error_string: Option[String], data: List[NewsFeedInfo]) 
	extends ApiResult(success, error_string)