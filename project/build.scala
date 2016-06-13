import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._
import coffeescript.Plugin._

object NewsrdrBuild extends Build {
  val Organization = "us.newsrdr"
  val Name = "newsrdr"
  val Version = "0.2.0-SNAPSHOT"
  val ScalaVersion = "2.11.8"
  val ScalatraVersion = "2.4.0"

  lazy val project = Project (
    "newsrdr",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ scalateSettings ++ coffeeSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      resolvers += "Sonatype Releases"  at "http://oss.sonatype.org/content/repositories/releases",
      resolvers += "Google Maven Snapshot Repository" at "https://repository.jboss.org/nexus/content/repositories/thirdparty-uploads/",
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.scalatra" %% "scalatra-swagger"  % ScalatraVersion,
        "org.json4s"   %% "json4s-native" % "3.3.0",
        "javax.mail" % "mail" % "1.4.7",
        "com.lambdaworks" % "scrypt" % "1.4.0",
        "net.databinder.dispatch" %% "dispatch-core" % "0.11.3",
        "org.twitter4j" % "twitter4j-stream" % "4.0.4",
        "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1",
        "com.github.nscala-time" %% "nscala-time" % "2.12.0",
        "ch.qos.logback" % "logback-classic" % "1.1.7" % "runtime",
        "com.typesafe.slick" %% "slick" % "3.1.1",
        "org.slf4j" % "slf4j-nop" % "1.7.21",
        "com.h2database" % "h2" % "1.4.192",
        "mysql" % "mysql-connector-java" % "6.0.2",
        "com.mchange" % "c3p0" % "0.9.5.2",
        "org.quartz-scheduler" % "quartz" % "2.2.3",
        "xalan" % "xalan" % "2.7.2",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.18.v20150929" % "container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      ),
      (resourceManaged in (Compile, CoffeeKeys.coffee)) <<= (resourceManaged in Compile)(_ / "webapp" / "js"),
      unmanagedResourceDirectories in Compile <+= (resourceManaged in Compile)(_ / "webapp" ),
      scalateTemplateConfig in Compile <<= (sourceDirectory in Compile){ base =>
        Seq(
          TemplateConfig(
            base / "webapp" / "WEB-INF" / "templates",
            Seq.empty,  /* default imports should be added here */
            Seq(
              Binding("context", "_root_.org.scalatra.scalate.ScalatraRenderContext", importMembers = true, isImplicit = true)
            ),  /* add extra bindings here */
            Some("templates")
          )
        )
      }
    )
  )
}
