ThisBuild / scalaVersion := "2.13.14"

lazy val gatlingVersion = "3.12.0"

enablePlugins(GatlingPlugin)

libraryDependencies ++= Seq(
  "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion,
  "io.gatling" % "gatling-test-framework" % gatlingVersion
)
