package com.lightbend.artifactstate.app

import com.typesafe.config.ConfigFactory

object TestConfig extends App {
  try {
    val appConfig = ConfigFactory.load()

    val clusterName = appConfig.getString("clustering.cluster.name")

    println(clusterName)
  }
  catch {
    case ex: Exception =>
      println(s"exception -> $ex.getMessage")
      ex.printStackTrace()
  }
}
