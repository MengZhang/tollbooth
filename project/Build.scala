import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "nadar"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
    javaCore,
    "org.agmip.web.util" %% "rested-crowd" % "1.0-SNAPSHOT"
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers    += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
  )

}
