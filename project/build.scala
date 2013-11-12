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
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.10.2"
  val ScalatraVersion = "2.2.1"

  lazy val project = Project (
    "newsrdr",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ coffeeSettings ++ scalateSettings ++ Seq(
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
        "org.json4s"   %% "json4s-native" % "3.2.4",
        "javax.mail" % "mail" % "1.4.7",
        "net.databinder.dispatch" %% "dispatch-core" % "0.11.0",
        "org.twitter4j" % "twitter4j-stream" % "3.0.3",
        "org.openid4java" % "openid4java-consumer" % "0.9.6",
        "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2",
        "com.github.nscala-time" %% "nscala-time" % "0.4.2",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "com.typesafe.slick" %% "slick" % "1.0.0",
        "org.slf4j" % "slf4j-nop" % "1.6.4",
        "com.h2database" % "h2" % "1.3.166",
        "mysql" % "mysql-connector-java" % "5.1.26",
        "c3p0" % "c3p0" % "0.9.1.2",
        "org.quartz-scheduler" % "quartz" % "2.2.0",
        "xalan" % "xalan" % "2.7.1",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      ),
      (resourceManaged in (Compile, CoffeeKeys.coffee)) <<= (resourceManaged in Compile)(_ / "webapp" / "js"),
      com.github.siasia.PluginKeys.webappResources in Compile <+= (resourceManaged in Compile)(_ / "webapp" ),
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
  ).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
}
