package com.thoughtbug.newsrdr.tasks

import scala.slick.session.Database
import org.quartz.impl.StdSchedulerFactory

object BackgroundJobManager {
  var db : Database = _
  val scheduler = StdSchedulerFactory.getDefaultScheduler()
  
  def start = {
    scheduler.start()
  }
  
  def shutdown = {
    scheduler.shutdown()
  }
}