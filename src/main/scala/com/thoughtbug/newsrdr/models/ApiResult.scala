package com.thoughtbug.newsrdr.models

class ApiResult(success: Boolean, error_string: Option[String])

case class FeedListApiResult(success: Boolean, error_string: Option[String], data: List[NewsFeed]) 
	extends ApiResult(success, error_string)