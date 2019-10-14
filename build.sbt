import com.lightbend.cinnamon.sbt.Cinnamon.CinnamonKeys.cinnamon
import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import sbt.Keys.fork
import sbt.Resolver

lazy val akkaHttpVersion = "10.1.10"
lazy val akkaVersion     = "2.5.25"
lazy val logbackVersion  = "1.2.3"
lazy val akkaManagementVersion = "1.0.3"
lazy val akkaCassandraVersion  = "0.99"
lazy val jacksonVersion  = "3.6.6"

name := "akka-typed-blog-distributed-state"
version in ThisBuild := "0.1.0"
organization in ThisBuild := "com.lightbend"
scalaVersion in ThisBuild := "2.12.10"

lazy val root = (project in file("."))
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(Cinnamon)  // NOTE: this requires a commercial Lightbend Subscription
  .enablePlugins(MultiJvmPlugin).configs(MultiJvm)
  .settings(
    packageName in Docker := "akka-typed-blog-distributed-state/cluster",
    libraryDependencies ++= Seq(
      Cinnamon.library.cinnamonAkkaHttp,
      Cinnamon.library.cinnamonAkka,
      Cinnamon.library.cinnamonJvmMetricsProducer,
      Cinnamon.library.cinnamonCHMetrics3,
      Cinnamon.library.cinnamonCHMetricsElasticsearchReporter,
      Cinnamon.library.cinnamonSlf4jEvents,
      Cinnamon.library.cinnamonPrometheus,
      Cinnamon.library.cinnamonPrometheusHttpServer,
      Cinnamon.library.jmxImporter,

      "com.typesafe.akka" %% "akka-remote" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,

      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed"% akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % akkaCassandraVersion,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
      "com.lightbend.akka" %% "akka-diagnostics" % "1.1.9",

      "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % akkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-http" % akkaManagementVersion,
      
      "org.json4s" %% "json4s-jackson" % jacksonVersion,
      "org.json4s" %% "json4s-core" % jacksonVersion,
      
      //Logback
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion,

      // testing
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,

      "commons-io" % "commons-io" % "2.4" % Test,
      "org.scalatest" %% "scalatest" % "3.0.1" % Test

    ),
    javaOptions in Universal ++= Seq(
      "-Dcom.sun.management.jmxremote.port=8090 -Dcom.sun.management.jmxremote.rmi.port=8090 -Djava.rmi.server.hostname=127.0.0.1 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
    )
  )
  .settings(
    dockerBaseImage := "openjdk:8-slim",
    dockerExposedPorts ++= Seq(9200)
  )
cinnamon in run := true
cinnamon in test := false
