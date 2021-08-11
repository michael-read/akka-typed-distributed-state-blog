import com.lightbend.cinnamon.sbt.Cinnamon.CinnamonKeys.cinnamon
import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

lazy val akkaHttpVersion = "10.2.6"
lazy val akkaVersion     = "2.6.15"
lazy val logbackVersion  = "1.2.3"
lazy val akkaManagementVersion = "1.1.1"
lazy val akkaCassandraVersion  = "1.0.5"
lazy val jacksonVersion  = "3.6.6"
lazy val akkaEnhancementsVersion = "1.1.16"

name := "akka-typed-blog-distributed-state"
ThisBuild / version := "0.1.2"
ThisBuild / organization := "com.lightbend"
ThisBuild / scalaVersion := "2.13.6"
ThisBuild / scalacOptions += "-deprecation"

// we're relying on the new credential file format for lightbend.sbt as described
//  here -> https://www.lightbend.com/account/lightbend-platform/credentials, which
//  requires a commercial Lightbend Subscription.
val credentialFile = file("./lightbend.sbt")

def doesCredentialExist : Boolean = {
  import java.nio.file.Files
  val exists = Files.exists(credentialFile.toPath)
  println(s"doesCredentialExist: ($credentialFile) " + exists)
  exists
}

def commercialDependencies : Seq[ModuleID] = {
  import com.lightbend.cinnamon.sbt.Cinnamon.CinnamonKeys.cinnamon
  Seq(
    // BEGIN: this requires a commercial Lightbend Subscription
    Cinnamon.library.cinnamonAkkaHttp,
    Cinnamon.library.cinnamonAkka,
    Cinnamon.library.cinnamonAkkaGrpc,
    Cinnamon.library.cinnamonAkkaPersistence,
    Cinnamon.library.cinnamonJvmMetricsProducer,
    Cinnamon.library.cinnamonCHMetrics3,
    Cinnamon.library.cinnamonCHMetricsElasticsearchReporter,
    Cinnamon.library.cinnamonSlf4jEvents,
    Cinnamon.library.cinnamonPrometheus,
    Cinnamon.library.cinnamonPrometheusHttpServer,
    Cinnamon.library.jmxImporter,
    "com.lightbend.akka" %% "akka-diagnostics" % akkaEnhancementsVersion,
    // END: this requires a commercial Lightbend Subscription
  )
}

def ossDependencies : Seq[ModuleID] = {
  Seq(
    "com.typesafe.akka" %% "akka-remote" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-cassandra" % akkaCassandraVersion,
    "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
    "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
    "com.typesafe.akka" %% "akka-pki" % akkaVersion,
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
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,

    "commons-io" % "commons-io" % "2.4" % Test,
    "org.scalatest" %% "scalatest" % "3.0.8" % Test
  )
}

lazy val root = (project in file("."))
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(if (doesCredentialExist.booleanValue()) Cinnamon else Plugins.empty) // NOTE: Cinnamon requires a commercial Lightbend Subscription
  .enablePlugins(MultiJvmPlugin).configs(MultiJvm)
  .enablePlugins(AkkaGrpcPlugin)
  .settings(multiJvmSettings: _*)
  .settings(
    Docker / packageName := "akka-typed-blog-distributed-state/cluster",
    libraryDependencies ++= {
      if (doesCredentialExist.booleanValue()) {
        commercialDependencies ++ ossDependencies
      }
      else {
        ossDependencies
      }
    },
    Universal / javaOptions ++= Seq(
      "-Dcom.sun.management.jmxremote.port=8090 -Dcom.sun.management.jmxremote.rmi.port=8090 -Djava.rmi.server.hostname=127.0.0.1 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
    )
  )
  .settings(
    dockerBaseImage := "openjdk:11-slim",
    dockerExposedPorts ++= Seq(9200)
  )

run / cinnamon  := true

fork := true