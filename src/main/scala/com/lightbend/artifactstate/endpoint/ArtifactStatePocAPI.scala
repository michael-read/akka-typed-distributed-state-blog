package com.lightbend.artifactstate.endpoint

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

}