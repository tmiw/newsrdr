package com.thoughtbug.newsrdr

import org.scalatra._
import scalate.ScalateSupport
import scala.slick.session.Database

class NewsReaderServlet(db: Database) extends NewsrdrStack {

  get("/") {        
    <html>
      <body>
        <h1>Hello, world!</h1>
        Say <a href="hello-scalate">hello to Scalate</a>.
      </body>
    </html>
  }
  
}
