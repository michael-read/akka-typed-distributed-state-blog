package com.lightbend.artifactstate.endpoint

import com.lightbend.artifactstate.endpoint.ArtifactStatePocAPI._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object JsonFormats  {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val userJsonFormat: RootJsonFormat[ArtifactAndUser] = jsonFormat2(ArtifactAndUser)
  implicit val psResponse: RootJsonFormat[ExtResponse] = jsonFormat4(ExtResponse)
  implicit val psResponseII: RootJsonFormat[AllStatesResponse] = jsonFormat5(AllStatesResponse)
  implicit val cmdResponse: RootJsonFormat[CommandResponse] = jsonFormat1(CommandResponse)

}

