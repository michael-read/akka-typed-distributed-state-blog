package com.lightbend.artifactstate.endpoint

import com.lightbend.artifactstate.endpoint.ArtifactStatePocAPI._

import spray.json.DefaultJsonProtocol

object JsonFormats  {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val userJsonFormat = jsonFormat2(ArtifactAndUser)
  implicit val psResponse = jsonFormat4(ExtResponse)
  implicit val psResponseII = jsonFormat5(AllStatesResponse)
  implicit val cmdResponse = jsonFormat1(CommandResponse)

}

