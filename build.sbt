import Dependencies._

enablePlugins(PackPlugin)

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.4",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "revolut-test",
    libraryDependencies ++= Seq(
      sparkJava,
      json4sJackson,
      logback,
      scalaLogging,
      sttp % Test,
      scalaTest % Test
    )
  )

packMain := Map("startServer" -> "revolut.Server")