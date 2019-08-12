package com.lightbend.artifactstate.endpoint

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.MethodDirectives.{get, post}
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.util.Timeout
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor._
import com.lightbend.artifactstate.endpoint.ArtifactStatePocAPI._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ArtifactStateRoutes(system: ActorSystem[Nothing], psCommandActor: ActorRef[ShardingEnvelope[ArtifactCommand]]) extends JsonSupport {

  // Required by the `ask` (?) method below
  implicit lazy val timeout = Timeout(5 seconds) // usually we'd obtain the timeout from the system's configuration
  implicit val scheduler = system.scheduler

  def handleResponse(req: ArtifactAndUser, f: Future[ArtifactResponse])(implicit ec: ExecutionContext): Future[ExtResponse] = {
    f.map {
      case ArtifactReadByUser(artifactRead) =>
        ExtResponse(req.artifactId, req.userId, Some(artifactRead), None)
      case ArtifactInUserFeed(artifactInUserFeed) =>
        ExtResponse(req.artifactId, req.userId, Some(artifactInUserFeed), None)
      case _ =>
        ExtResponse(req.artifactId, req.userId, None, Some("Internal Query Error: this shouldn't happen."))
    }.recover {
      case ex: Exception =>
        system.log.error(ex, ex.getMessage)
        ExtResponse(req.artifactId, req.userId, None, Some(ex.getMessage))
    }
  }

  def queryArtifactRead(req: ArtifactAndUser)(implicit ec: ExecutionContext): Future[ExtResponse] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope(req.artifactId + req.userId, QryArtifactReadByUser(ref, req.artifactId, req.userId))
    }.mapTo[ArtifactResponse]
    handleResponse(req, result)
  }

  def queryArtifactInUserFeed(req: ArtifactAndUser)(implicit ec: ExecutionContext): Future[ExtResponse] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope(req.artifactId + req.userId, QryArtifactInUserFeed(ref, req.artifactId, req.userId))
    }.mapTo[ArtifactResponse]
    handleResponse(req, result)
  }

  def queryAllStates(req: ArtifactAndUser)(implicit ec: ExecutionContext): Future[AllStatesResponse] = {
    val f = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope(req.artifactId + req.userId, QryGetAllStates(ref, req.artifactId, req.userId))
    }.mapTo[ArtifactResponse]
    f.map {
      case AllStates(artifactRead, artifactInUserFeed) =>
        AllStatesResponse(req.artifactId, req.userId, Some(artifactRead), Some(artifactInUserFeed), None)
      case _ =>
        AllStatesResponse(req.artifactId, req.userId, None, None, Some("Internal Error: this shouldn't happen."))
    }.recover {
      case ex: Exception =>
        system.log.error(ex, ex.getMessage)
        AllStatesResponse(req.artifactId, req.userId, None, None, Some(ex.getMessage))
    }
  }

  def handleCmdResponse(req: ArtifactAndUser, f: Future[ArtifactResponse])(implicit ec: ExecutionContext): Future[String] = {
    f.map {
      case Okay(result) => result
      case _ =>
        "Internal Command Error: this shouldn't happen."
    }.recover {
      case ex: Exception =>
        system.log.error(ex, ex.getMessage)
        ex.getMessage
    }
  }

  def cmdArtifactRead(req: ArtifactAndUser)(implicit ec: ExecutionContext): Future[String] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope(req.artifactId + req.userId, CmdArtifactRead(ref, req.artifactId, req.userId))
    }.mapTo[ArtifactResponse]
    handleCmdResponse(req, result)
  }

  def cmdArtifactAddedToUserFeed(req: ArtifactAndUser)(implicit ec: ExecutionContext): Future[String] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope(req.artifactId + req.userId, CmdArtifactAddedToUserFeed(ref, req.artifactId, req.userId))
    }.mapTo[ArtifactResponse]
    handleCmdResponse(req, result)
  }

  def cmdArtifactRemovedFromUserFeed(req: ArtifactAndUser)(implicit ec: ExecutionContext): Future[String] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope(req.artifactId + req.userId, CmdArtifactRemovedFromUserFeed(ref, req.artifactId, req.userId))
    }.mapTo[ArtifactResponse]
    handleCmdResponse(req, result)
  }

  implicit def myExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case ex: Exception =>
        extractUri { uri =>
          val msg = s"Request to $uri could not be handled normally: Exception: ${ex.getCause} : ${ex.getMessage}"
          system.log.error(msg)
          complete(HttpResponse(StatusCodes.InternalServerError, entity = msg))
        }
    }

  lazy val psRoutes: Route =
    pathPrefix("artifactState") {
      concat(
        // QUERIES:
        pathPrefix("isArtifactReadByUser") {
          extractExecutionContext { implicit executor =>
            concat(
              get {
                parameters('artifactId.as[Long], 'userId) { (artifactId, userId) =>
                  complete {
                    queryArtifactRead(ArtifactAndUser(artifactId, userId))
                  }
                }
              },
              post {
                entity(as[ArtifactAndUser]) { req =>
                  complete(StatusCodes.OK, queryArtifactRead(req))
                }
              })
          }
        },
        pathPrefix("isArtifactInUserFeed") {
          extractExecutionContext { implicit executor =>
            concat(
              get {
                parameters('artifactId.as[Long], 'userId) { (artifactId, userId) =>
                  val req = ArtifactAndUser(artifactId, userId)
                  complete(queryArtifactInUserFeed(req))
                }
              },
              post {
                entity(as[ArtifactAndUser]) { req =>
                  complete(StatusCodes.OK, queryArtifactInUserFeed(req))
                }
              })
          }
        },
        pathPrefix("getAllStates") {
          extractExecutionContext { implicit executor =>
            concat(
              get {
                parameters('artifactId.as[Long], 'userId) { (artifactId, userId) =>
                  val req = ArtifactAndUser(artifactId, userId)
                  complete(queryAllStates(req))
                }
              },
              post {
                entity(as[ArtifactAndUser]) { req =>
                  complete(StatusCodes.OK, queryAllStates(req))
                }
              })
          }
        },

        // COMMANDS:
        pathPrefix("setArtifactReadByUser") {
          extractExecutionContext { implicit executor =>
            post {
              entity(as[ArtifactAndUser]) { req =>
                complete {
                  cmdArtifactRead(req)
                }
              }
            }
          }
        },
        pathPrefix("setArtifactAddedToUserFeed") {
          extractExecutionContext { implicit executor =>
            post {
              entity(as[ArtifactAndUser]) { req =>
                complete {
                  cmdArtifactAddedToUserFeed(req)
                }
              }
            }
          }
        },
        pathPrefix("setArtifactRemovedFromUserFeed") {
          extractExecutionContext { implicit executor =>
            post {
              entity(as[ArtifactAndUser]) { req =>
                complete {
                  cmdArtifactRemovedFromUserFeed(req)
                }
              }
            }
          }
        })
    }

}
