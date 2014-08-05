name := "tollbooth"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  cache,
  javaWs,
  "org.agmip.web.util" %% "rested-crowd" % "1.0-SNAPSHOT"
)
