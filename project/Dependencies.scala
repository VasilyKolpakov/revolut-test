import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4"
  lazy val sparkJava = "com.sparkjava" % "spark-core" % "2.7.2"
  lazy val json4sJackson = "org.json4s" %% "json4s-jackson" % "3.5.3"
  lazy val sttp = "com.softwaremill.sttp" %% "core" % "1.1.6"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0"
}
