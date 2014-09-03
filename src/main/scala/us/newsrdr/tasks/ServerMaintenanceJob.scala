package us.newsrdr.tasks

import org.quartz.Job
import org.quartz.JobExecutionContext
import us.newsrdr.models._
import scala.slick.session.{Database, Session}
import scala.collection.JavaConversions._
import org.quartz.impl.matchers._
import org.quartz.impl.StdSchedulerFactory
import org.quartz.JobBuilder._
import org.quartz.TriggerBuilder._
import org.quartz.SimpleScheduleBuilder._
import org.quartz.DateBuilder._
import org.quartz._

class RebalanceJob extends Job {
  def rebalanceJobs() {
    // rebalance jobs due to low resources on AWS VM.
    val scheduler = BackgroundJobManager.scheduler
    val groupNames = scheduler.getTriggerGroupNames()
    val triggerList = collection.mutable.ListBuffer[TriggerKey]()
    
    // get list of triggers
    groupNames.foreach(g => {
      scheduler.getTriggerKeys(GroupMatcher.groupEquals(g)).foreach(triggerList += _)
    })
    
    val SECONDS_PER_HOUR = 60*60
    val START_TIME = 60
    val divisor = if (triggerList.length >= SECONDS_PER_HOUR) {
      1
    } else {
      SECONDS_PER_HOUR / triggerList.length
    }
    var startSeconds = START_TIME

    triggerList.toList.foreach(t => {
      if (!t.getName().equals(BackgroundJobManager.CLEANUP_JOB_NAME)) {
        val oldTrigger = scheduler.getTrigger(t)
        val builder = oldTrigger.getTriggerBuilder()
          
        val newTrigger = TriggerBuilder.newTrigger()
                                       .withIdentity(t.getName())
                                       .startAt(futureDate(startSeconds, IntervalUnit.SECOND))
                                       .withSchedule(simpleSchedule().withIntervalInHours(1).repeatForever())
                                       .build()

        scheduler.rescheduleJob(oldTrigger.getKey(), newTrigger)
        startSeconds = startSeconds + divisor;
          
        // The goal is to get all the jobs to run within an hour. 
        // If we run out of slots, restart from the top of the hour.
        if (startSeconds >= SECONDS_PER_HOUR)
        {
          startSeconds = START_TIME
        }
      }
    })
  }
  
  def execute(ctxt: JobExecutionContext) {
    rebalanceJobs
  }
}
class ServerMaintenanceJob extends Job {
  private def deleteOldSessions() {
    BackgroundJobManager.db withSession { implicit session: Session =>
      BackgroundJobManager.dao.deleteOldSessions
    }
  }
  
  private def deleteOldFailLogs {
    BackgroundJobManager.db withSession { implicit session: Session =>
      BackgroundJobManager.dao.deleteOldFailLogs
    }
  }
  
  def execute(ctxt: JobExecutionContext) {
    deleteOldSessions
    deleteOldFailLogs
  }
}
