package com.thoughtbug.newsrdr.tasks

import org.quartz.Job
import org.quartz.JobExecutionContext
import com.thoughtbug.newsrdr.models._
import scala.slick.session.{Database, Session}
import scala.collection.JavaConversions._
import org.quartz.impl.matchers._
import org.quartz.impl.StdSchedulerFactory
import org.quartz.JobBuilder._
import org.quartz.TriggerBuilder._
import org.quartz.SimpleScheduleBuilder._
import org.quartz.DateBuilder._
import org.quartz._

class ServerMaintenanceJob extends Job {
	def execute(ctxt: JobExecutionContext) {
	  // rebalance jobs due to low resources on AWS VM.
	  val scheduler = BackgroundJobManager.scheduler
	  var startSeconds = 10
	  
	  scheduler.getTriggerGroupNames().foreach(g => {
	    scheduler.getTriggerKeys(GroupMatcher.groupEquals(g)).foreach(t => {
	      if (!t.getName().equals(BackgroundJobManager.CLEANUP_JOB_NAME)) {
	        val oldTrigger = scheduler.getTrigger(t)
	        val builder = oldTrigger.getTriggerBuilder()
	        
	        val newTrigger = TriggerBuilder.newTrigger()
    		                               .withIdentity(t.getName())
    		                               .startAt(futureDate(startSeconds, IntervalUnit.SECOND))
    		                               .withSchedule(simpleSchedule().withIntervalInHours(1).repeatForever())
    		                               .build()

	        scheduler.rescheduleJob(oldTrigger.getKey(), newTrigger)
	        startSeconds += 10;
	      }
	    })
	  })
	}
}