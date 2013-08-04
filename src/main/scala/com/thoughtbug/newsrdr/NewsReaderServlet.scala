package com.thoughtbug.newsrdr

import org.scalatra._
import scalate.ScalateSupport
import scala.slick.session.Database

class NewsReaderServlet(db: Database) extends NewsrdrStack {
  get("/") {        
    <html>
      <body>
        <h1>test server</h1>
        Real website coming soon. For now, <a href="/app">click here</a> to get to the main app.
      </body>
    </html>
  }
  
  get("/app") {
    contentType="text/html"
    ssp("/app")
  }
}
