package com.lightbend.artifactstate.endpoint

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.MethodDirectives.{get, post}
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.util.Timeout
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor._
import com.lightbend.artifactstate.endpoint.ArtifactStatePocAPI._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps

class ArtifactStateRoutes(system: ActorSystem[Nothing], psCommandActor: ActorRef[ShardingEnvelope[ArtifactCommand]]) {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import JsonFormats._

  // If ask takes more time than this to complete the request is failed
  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("app.routes.ask-timeout"))
  private implicit val ec: ExecutionContextExecutor = system.executionContext

  implicit val scheduler: Scheduler = system.scheduler

  def handleResponse(req: ArtifactAndUser, f: Future[ArtifactResponse]): Future[ExtResponse] = {
    f.map {
      case ArtifactReadByUser(artifactRead) =>
        ExtResponse(req.artifactId, req.userId, Some(artifactRead), None)
      case ArtifactInUserFeed(artifactInUserFeed) =>
        ExtResponse(req.artifactId, req.userId, Some(artifactInUserFeed), None)
      case _ =>
        ExtResponse(req.artifactId, req.userId, None, Some("Internal Query Error: this shouldn't happen."))
    }
  }

  def queryArtifactRead(req: ArtifactAndUser): Future[ExtResponse] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope(req.artifactId + req.userId, IsArtifactReadByUser(ref, req.artifactId, req.userId))
    }
    handleResponse(req, result)
  }

  def queryArtifactInUserFeed(req: ArtifactAndUser): Future[ExtResponse] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope(req.artifactId + req.userId, IsArtifactInUserFeed(ref, req.artifactId, req.userId))
    }
    handleResponse(req, result)
  }

  def queryAllStates(req: ArtifactAndUser): Future[AllStatesResponse] = {
    val f = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope(req.artifactId + req.userId, GetAllStates(ref, req.artifactId, req.userId))
    }
    f.map {
      case AllStates(artifactRead, artifactInUserFeed) =>
        AllStatesResponse(req.artifactId, req.userId, Some(artifactRead), Some(artifactInUserFeed), None)
      case _ =>
        AllStatesResponse(req.artifactId, req.userId, None, None, Some("Internal Error: this shouldn't happen."))
    }.recover {
      case ex: Exception =>
        system.log.error(ex.getMessage, ex)
        AllStatesResponse(req.artifactId, req.userId, None, None, Some(ex.getMessage))
    }
  }

  def handleCmdResponse(req: ArtifactAndUser, f: Future[ArtifactResponse]): Future[CommandResponse] = {
    f.map {
      case Okay(_) => CommandResponse(true)
      case _ =>
        system.log.error("Internal Command Error: this shouldn't happen.")
        CommandResponse(false)
    }.recover {
      case ex: Exception =>
        system.log.error(ex.getMessage, ex)
        CommandResponse(false)
    }
  }

  def cmdArtifactRead(req: ArtifactAndUser): Future[CommandResponse] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope(req.artifactId + req.userId, SetArtifactRead(ref, req.artifactId, req.userId))
    }
    handleCmdResponse(req, result)
  }

  def cmdArtifactAddedToUserFeed(req: ArtifactAndUser): Future[CommandResponse] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope(req.artifactId + req.userId, SetArtifactAddedToUserFeed(ref, req.artifactId, req.userId))
    }
    handleCmdResponse(req, result)
  }

  def cmdArtifactRemovedFromUserFeed(req: ArtifactAndUser): Future[CommandResponse] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope(req.artifactId + req.userId, SetArtifactRemovedFromUserFeed(ref, req.artifactId, req.userId))
    }
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
            concat(
              get {
                parameters(("artifactId".as[Long], "userId")) { (artifactId, userId) =>
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
        },
        pathPrefix("isArtifactInUserFeed") {
          concat(
            get {
              parameters((("artifactId").as[Long], "userId")) { (artifactId, userId) =>
                val req = ArtifactAndUser(artifactId, userId)
                complete(queryArtifactInUserFeed(req))
              }
            },
            post {
              entity(as[ArtifactAndUser]) { req =>
                complete(StatusCodes.OK, queryArtifactInUserFeed(req))
              }
            })
        },
        pathPrefix("getAllStates") {
          concat(
            get {
              parameters(("artifactId".as[Long], "userId")) { (artifactId, userId) =>
                val req = ArtifactAndUser(artifactId, userId)
                complete(queryAllStates(req))
              }
            },
            post {
              entity(as[ArtifactAndUser]) { req =>
                complete(StatusCodes.OK, queryAllStates(req))
              }
            })
        },

        // COMMANDS:
        pathPrefix("setArtifactReadByUser") {
          post {
            entity(as[ArtifactAndUser]) { req =>
              complete {
                cmdArtifactRead(req)
              }
            }
          }
        },
        pathPrefix("setArtifactAddedToUserFeed") {
          post {
            entity(as[ArtifactAndUser]) { req =>
              complete {
                cmdArtifactAddedToUserFeed(req)
              }
            }
          }
        },
        pathPrefix("setArtifactRemovedFromUserFeed") {
          post {
            entity(as[ArtifactAndUser]) { req =>
              complete {
                cmdArtifactRemovedFromUserFeed(req)
              }
            }
          }
        })
    }

}
