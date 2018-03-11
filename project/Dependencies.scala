import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.4"
  lazy val sparkJava = "com.sparkjava" % "spark-core" % "2.7.2"
  lazy val json4sJackson = "org.json4s" %% "json4s-jackson" % "3.5.3"
}
