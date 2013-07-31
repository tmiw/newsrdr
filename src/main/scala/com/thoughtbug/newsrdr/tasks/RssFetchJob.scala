package com.thoughtbug.newsrdr.tasks

import org.quartz.Job
import org.quartz.JobExecutionContext
import com.thoughtbug.newsrdr.models
import com.thoughtbug.newsrdr.models.RSSFeed

class RssFetchJob extends Job {
  def execute(ctxt: JobExecutionContext) {
    var feedUrl = ctxt.get("feedUrl").toString()
    
    val feed = new RSSFeed
    feed.load(feedUrl)
    
    // TODO: insert into db
  }
}