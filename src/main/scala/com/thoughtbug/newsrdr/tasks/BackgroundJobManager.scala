package com.thoughtbug.newsrdr.tasks

import javax.servlet.ServletContext
import com.thoughtbug.newsrdr.models.DataTables
import scala.slick.session.Database
import org.quartz.impl.StdSchedulerFactory
import org.quartz.JobBuilder._
import org.quartz.TriggerBuilder._
import org.quartz.SimpleScheduleBuilder._
import org.quartz.DateBuilder._
import org.quartz._
import org.scalatra._

object BackgroundJobManager {
  var db : Database = _
  var dao : DataTables = _
  var scheduler : Scheduler = null
  
  def start(context: ServletContext) = {
    scheduler = context.getInitParameter(org.scalatra.EnvironmentKey) match {
      case "production" => {
        var temp = new StdSchedulerFactory()
        temp.initialize(context.getResourceAsStream("quartz-production.properties"))
        temp.getScheduler()
      }
      case _ => StdSchedulerFactory.getDefaultScheduler()
    }
    scheduler.start()
  }
  
  def shutdown = {
    scheduler.shutdown()
  }
  
  def scheduleFeedJob(url: String) {
    if (!scheduler.checkExists(new JobKey(url))) {
      val trigger = newTrigger()
    		.withIdentity(url)
    		.startAt(futureDate(60, IntervalUnit.MINUTE))
    		.startNow()
    		.withSchedule(simpleSchedule().withIntervalInHours(1).repeatForever())
    		.build()
      val job = newJob(classOf[RssFetchJob])
    		.withIdentity(url)
    		.usingJobData("url", url)
    		.build()
    
      scheduler.scheduleJob(job, trigger)
    }
  }
}