enablePlugins(JettyPlugin)

webappPostProcess := {
  webappDir: File =>
    def listFiles(level: Int)(f: File): Unit = {
      val indent = ((1 until level) map { _ => "  " }).mkString
      if (f.isDirectory) {
        streams.value.log.info(indent + f.getName + "/")
        f.listFiles foreach { listFiles(level + 1) }
      } else streams.value.log.info(indent + f.getName)
    }
    
    // XXX: hardcoding this is bad, m'kay?
    IO.copyDirectory(webappDir / ".." / "scala-2.11" / "classes" / "webapp" / "js", webappDir / "js")

    streams.value.log.info("webappDir: " + webappDir)
    listFiles(1)(webappDir)
}

//javaOptions in Jetty ++= Seq(
//  "-Xdebug",
//  "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
//)