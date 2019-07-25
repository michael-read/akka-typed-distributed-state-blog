package com.lightbend.artifactstate.endpoint

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor.Okay
import com.lightbend.artifactstate.endpoint.ArtifactStatePocAPI._
import spray.json.DefaultJsonProtocol

trait JsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val userJsonFormat = jsonFormat2(ArtifactAndUser)
  implicit val psResponse = jsonFormat4(ExtResponse)
  implicit val psResponseII = jsonFormat5(AllStatesResponse)

}

