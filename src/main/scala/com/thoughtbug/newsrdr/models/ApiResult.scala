package com.thoughtbug.newsrdr.models

object Constants {
  var ITEMS_PER_PAGE = 10
}

class ApiResult(success: Boolean, error_string: Option[String])

case class NoDataApiResult(success: Boolean, error_string: Option[String])
	extends ApiResult(success, error_string)

case class FeedListApiResult(success: Boolean, error_string: Option[String], data: List[NewsFeedInfo]) 
	extends ApiResult(success, error_string)