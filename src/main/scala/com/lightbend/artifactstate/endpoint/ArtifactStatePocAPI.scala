package com.lightbend.artifactstate.endpoint

// these are just for the JSON formats/external protocol/api
object ArtifactStatePocAPI {

  final case class ArtifactAndUser(artifactId: Long, userId: String)

  sealed trait ExtResponses
  final case class ExtResponse(artifactId: Long, userId: String, answer: Option[Boolean], failureMsg: Option[String]) extends ExtResponses
  final case class AllStatesResponse(
                                      artifactId: Long,
                                      userId: String,
                                      artifactRead: Option[Boolean],
                                      artifactInUserFeed: Option[Boolean],
                                      failureMsg: Option[String]) extends ExtResponses
  final case class CommandResponse(success: Boolean) extends ExtResponses

}