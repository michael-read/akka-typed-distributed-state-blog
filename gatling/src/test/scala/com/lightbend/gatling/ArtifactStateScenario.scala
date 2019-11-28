package com.mread.gatling

import scala.concurrent.duration._
import scala.util.Random

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class ArtifactStateScenario
  extends Simulation {

  val namesFeeder = csv("lastnames.csv").random

  val artifactIds = Iterator.continually(
    // Random number will be accessible in session under variable "artifactId"
    Map("artifactId" -> Random.nextInt(500))
  )

  val httpConf = http
    .baseUrl("http://localhost:8082/artifactState")
    .acceptHeader("application/json")

  val artifactAndUser = StringBody("""{ "artifactId": ${artifactId}, "userId": "${name}" }""")

  // a scenario that simply runs through all the various state changes
  val scn = scenario("ArtifactStateScenario")
    .feed(namesFeeder)
    .feed(artifactIds)
    .exec(
      http("set_artifact_read")
      .post("/setArtifactReadByUser")
      .body(artifactAndUser).asJson
      .check(status.is(200))
    )

    .exec(
      http("is_artifact_read")
        .post("/isArtifactReadByUser")
        .body(artifactAndUser).asJson
        .check(status.is(200))
    )

    .exec(
      http("set_artifact_in_feed")
        .post("/setArtifactAddedToUserFeed")
        .body(artifactAndUser).asJson
        .check(status.is(200))
    )

    .exec(
      http("is_artifact_in_user_feed")
        .post("/isArtifactInUserFeed")
        .body(artifactAndUser).asJson
        .check(status.is(200))
    )

    .exec(
      http("set_artifact_removed_from_feed")
        .post("/setArtifactRemovedFromUserFeed")
        .body(artifactAndUser).asJson
        .check(status.is(200))
    )

    .exec(
      http("get_all_states")
        .post("/setArtifactReadByUser")
        .body(artifactAndUser).asJson
        .check(status.is(200))
    )

  setUp(
//    scn.inject(atOnceUsers(1))
//    scn.inject(rampUsers(100) during (3 minutes))
    scn.inject(rampUsers(1000) during (5 minutes))
    // simulation set up -> https://gatling.io/docs/current/general/simulation_setup/
/*
    scn.inject(
      nothingFor(4 seconds), // 1
      atOnceUsers(10), // 2
      rampUsers(10) during (5 seconds), // 3
      constantUsersPerSec(20) during (15 seconds), // 4
      constantUsersPerSec(20) during (15 seconds) randomized, // 5
      rampUsersPerSec(100) to 20 during (10 minutes), // 6
      rampUsersPerSec(100) to 20 during (10 minutes) randomized, // 7
      heavisideUsers(1000) during (20 seconds) // 8
    )
*/
    .protocols(httpConf)
  )
}
