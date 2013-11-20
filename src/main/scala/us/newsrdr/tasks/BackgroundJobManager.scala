package us.newsrdr.tasks

import javax.servlet.ServletContext
import us.newsrdr.models.DataTables
import scala.slick.session.Database
import org.quartz.impl.StdSchedulerFactory
import org.quartz.JobBuilder._
import org.quartz.TriggerBuilder._
import org.quartz.SimpleScheduleBuilder._
import org.quartz.CronScheduleBuilder._
import org.quartz.DateBuilder._
import org.quartz._
import org.scalatra._
import scala.slick.session.Session

class QuartzWatchdogThread extends java.lang.Thread {
    private var isExiting = false
    private val SCHEDULER_RESTART_TIME = 60*60*6 // every 6 hours
    
    override def run() = {
      var i = 0
      while (isExiting == false) {
        while (i < SCHEDULER_RESTART_TIME && isExiting == false)
        {
            java.lang.Thread.sleep(1000)
            i = i + 1
        }
        i = 0
        if (!isExiting)
        {
          BackgroundJobManager.scheduler.shutdown(true)
          BackgroundJobManager.scheduler.start()
        }
      }
    }
    
    def stopThread() = {
      isExiting = true
    }
}

object BackgroundJobManager {
  val CLEANUP_JOB_NAME = "newsrdr_cleanup"
  
  var db : Database = _
  var dao : DataTables = _
  var scheduler : Scheduler = null
  var watchdog : QuartzWatchdogThread = null
  
  def start(context: ServletContext) = {
    scheduler = sys.props.get(org.scalatra.EnvironmentKey) match {
      case Some("production") => {
        var temp = new StdSchedulerFactory()
        temp.initialize(context.getResourceAsStream("/WEB-INF/classes/quartz-production.properties"))
        temp.getScheduler()
      }
      case _ => StdSchedulerFactory.getDefaultScheduler()
    }
    
    val jobDetail = scheduler.getJobDetail(new JobKey(CLEANUP_JOB_NAME))
    if (jobDetail == null)
    {
      // schedule cleanup job since it hasn't been done yet.
      val trigger = newTrigger()
        .withIdentity(CLEANUP_JOB_NAME)
        .startNow()
        .withSchedule(dailyAtHourAndMinute(8, 0))
        .build()
      val job = newJob(classOf[ServerMaintenanceJob])
        .withIdentity(CLEANUP_JOB_NAME)
        .build()
    
      scheduler.scheduleJob(job, trigger)
    }
    else
    {
      // reschedule so it starts at 00:00 UTC-8
      val trigger = newTrigger()
        .withIdentity(CLEANUP_JOB_NAME)
        .startNow()
        .withSchedule(dailyAtHourAndMinute(8, 0))
        .build()
      scheduler.rescheduleJob(new TriggerKey(CLEANUP_JOB_NAME), trigger)
    }
    
    scheduler.start()
    
    // Start watchdog (shouldn't be necessary, but you know...)
    //watchdog = new QuartzWatchdogThread
    //watchdog.start
  }
  
  def shutdown = {
    //watchdog.stopThread
    //watchdog = null
    
    scheduler.shutdown(true)
    scheduler = null; // force GC of scheduler objects.
  }
  
  private def getJobName(url: String) : String = {
    if (url.length() > 200)
    {
      // Quartz has a maximum length that we have to follow. Encode the feed ID instead,
      // and make the background task retrieve the real feed URL.
      db.withSession { implicit session: Session =>
          "feed_fetch_" + dao.getFeedFromUrl(session, url).get.id.toString()
      }
    }
    else
    {
      url
    }
  }
  
  def unscheduleFeedJob(url: String) {
    val newName = getJobName(url)
    val key = new JobKey(newName)
    if (scheduler.checkExists(key)) {
      scheduler.deleteJob(key)
    }
  }
  
  def scheduleFeedJob(url: String) {
    val newName = getJobName(url)
    if (!scheduler.checkExists(new JobKey(newName))) {
      val trigger = newTrigger()
        .withIdentity(newName)
        .startAt(futureDate(60, IntervalUnit.MINUTE))
        .withSchedule(simpleSchedule().withIntervalInHours(1).repeatForever())
        .build()
      val job = newJob(classOf[RssFetchJob])
        .withIdentity(newName)
        .usingJobData("url", url)
        .build()
    
      scheduler.scheduleJob(job, trigger)
    }
  }
  
  def rescheduleFeedJob(url: String, intervalInSeconds: Int) {
    val newName = getJobName(url)
    val t = new TriggerKey(newName)
    val oldTrigger = scheduler.getTrigger(t)
    if (oldTrigger != null)
    {
      val builder = oldTrigger.getTriggerBuilder()
          
      val newTrigger = TriggerBuilder.newTrigger()
                                     .withIdentity(t.getName())
                                     .startAt(futureDate(intervalInSeconds, IntervalUnit.SECOND))
                                     .withSchedule(simpleSchedule().withIntervalInSeconds(intervalInSeconds).repeatForever())
                                     .build()

      scheduler.rescheduleJob(oldTrigger.getKey(), newTrigger)
    }
    else
    {
      scheduleFeedJob(url)
    }
  }
}
