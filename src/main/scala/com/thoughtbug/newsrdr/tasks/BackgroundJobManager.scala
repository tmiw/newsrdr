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
  val CLEANUP_JOB_NAME = "newsrdr_cleanup"
  
  var db : Database = _
  var dao : DataTables = _
  var scheduler : Scheduler = null
  
  def start(context: ServletContext) = {
    scheduler = context.getInitParameter(org.scalatra.EnvironmentKey) match {
      case "production" => {
        var temp = new StdSchedulerFactory()
        temp.initialize(context.getResourceAsStream("/WEB-INF/classes/quartz-production.properties"))
        temp.getScheduler()
      }
      case _ => StdSchedulerFactory.getDefaultScheduler()
    }
    
    if (scheduler.getJobDetail(new JobKey(CLEANUP_JOB_NAME)) == null)
    {
      // schedule cleanup job since it hasn't been done yet.
      val trigger = newTrigger()
    		.withIdentity(CLEANUP_JOB_NAME)
    		.startNow()
    		.withSchedule(simpleSchedule().withIntervalInHours(24).repeatForever())
    		.build()
      val job = newJob(classOf[ServerMaintenanceJob])
    		.withIdentity(CLEANUP_JOB_NAME)
    		.build()
    
      scheduler.scheduleJob(job, trigger)
    }
    
    scheduler.start()
  }
  
  def shutdown = {
    scheduler.shutdown(true)
    scheduler = null; // force GC of scheduler objects.
  }
  
  def unscheduleFeedJob(url: String) {
    val key = new JobKey(url)
    if (scheduler.checkExists(key)) {
      scheduler.deleteJob(key)
    }
  }
  
  def scheduleFeedJob(url: String) {
    if (!scheduler.checkExists(new JobKey(url))) {
      val trigger = newTrigger()
    		.withIdentity(url)
    		.startAt(futureDate(60, IntervalUnit.MINUTE))
    		.withSchedule(simpleSchedule().withIntervalInHours(1).repeatForever())
    		.build()
      val job = newJob(classOf[RssFetchJob])
    		.withIdentity(url)
    		.usingJobData("url", url)
    		.build()
    
      scheduler.scheduleJob(job, trigger)
    }
  }
  
  def rescheduleFeedJob(url: String, intervalInSeconds: Int) {
    val t = new TriggerKey(url)
    val oldTrigger = scheduler.getTrigger(t)
	val builder = oldTrigger.getTriggerBuilder()
	        
	val newTrigger = TriggerBuilder.newTrigger()
                                   .withIdentity(t.getName())
                                   .startAt(futureDate(intervalInSeconds, IntervalUnit.SECOND))
                                   .withSchedule(simpleSchedule().withIntervalInSeconds(intervalInSeconds).repeatForever())
                                   .build()

    scheduler.rescheduleJob(oldTrigger.getKey(), newTrigger)
  }
}
