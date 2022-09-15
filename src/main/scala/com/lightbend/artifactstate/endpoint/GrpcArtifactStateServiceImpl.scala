package com.lightbend.artifactstate.endpoint

import akka.NotUsed
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor._
import com.lightbend.artifactstate.endpoint

import scala.concurrent.{ExecutionContextExecutor, Future}

class GrpcArtifactStateServiceImpl(system: ActorSystem[Nothing], psCommandActor: ActorRef[ShardingEnvelope[ArtifactCommand]]) extends ArtifactStateService {

  private implicit val ec: ExecutionContextExecutor = system.executionContext

  // If ask takes more time than this to complete the request is failed
  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("app.routes.ask-timeout"))
  implicit val scheduler: Scheduler = system.scheduler

  def handleResponse(req: ArtifactAndUser, f: Future[ArtifactResponse]): Future[ExtResponse] = {
    f.map {
      case ArtifactReadByUser(artifactRead) =>
        ExtResponse(req.artifactId, req.userId, artifactRead)
      case ArtifactInUserFeed(artifactInUserFeed) =>
        ExtResponse(req.artifactId, req.userId, artifactInUserFeed)
      case _ =>
        ExtResponse(req.artifactId, req.userId, failureMsg = "Internal Query Error: this shouldn't happen.")
    }
  }

  def handleCmdResponse(req: ArtifactAndUser, f: Future[ArtifactResponse]): Future[CommandResponse] = f.map {
    case Okay(_) => CommandResponse(success = true)
    case _ =>
      system.log.error("Internal Command Error: this shouldn't happen.")
      CommandResponse()
  }.recover {
    case ex: Exception =>
      system.log.error(s"failure on request user: ${req.userId} artifact id: ${req.artifactId} ${ex.getMessage}", ex)
      CommandResponse()
  }

  /**
   * queries
   */
  override def isArtifactReadByUser(req: ArtifactAndUser): Future[ExtResponse] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope("%d%s".format(req.artifactId, req.userId), IsArtifactReadByUser(ref, req.artifactId, req.userId))
    }
    handleResponse(req, result)
  }

  override def isArtifactInUserFeed(req: ArtifactAndUser): Future[ExtResponse] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope("%d%s".format(req.artifactId, req.userId), IsArtifactInUserFeed(ref, req.artifactId, req.userId))
    }
    handleResponse(req, result)
  }

  override def getAllStates(req: ArtifactAndUser): Future[AllStatesResponse] = {
    val f = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope("%d%s".format(req.artifactId, req.userId), GetAllStates(ref, req.artifactId, req.userId))
    }
    f.map {
      case AllStates(artifactRead, artifactInUserFeed) =>
        AllStatesResponse(req.artifactId, req.userId, artifactRead, artifactInUserFeed)
      case _ =>
        AllStatesResponse(req.artifactId, req.userId, failureMsg = "Internal Error: this shouldn't happen.")
    }.recover {
      case ex: Exception =>
        system.log.error(ex.getMessage, ex)
        AllStatesResponse(req.artifactId, req.userId, failureMsg = ex.getMessage)
    }
  }

  /**
   * commands
   */
  override def setArtifactReadByUser(req: ArtifactAndUser): Future[CommandResponse] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope("%d%s".format(req.artifactId, req.userId), SetArtifactRead(ref, req.artifactId, req.userId))
    }
    handleCmdResponse(req, result)
  }

  override def setArtifactAddedToUserFeed(req: ArtifactAndUser): Future[CommandResponse] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope("%d%s".format(req.artifactId, req.userId), SetArtifactAddedToUserFeed(ref, req.artifactId, req.userId))
    }
    handleCmdResponse(req, result)
  }

  override def setArtifactRemovedFromUserFeed(req: ArtifactAndUser): Future[CommandResponse] = {
    val result = psCommandActor.ask { ref : ActorRef[ArtifactResponse] =>
      ShardingEnvelope("%d%s".format(req.artifactId, req.userId), SetArtifactRemovedFromUserFeed(ref, req.artifactId, req.userId))
    }
    handleCmdResponse(req, result)
  }

  override def commandsStreamed(in: Source[endpoint.ArtifactCommand, NotUsed]): Source[StreamedResponse, NotUsed] = {
    val validCommands = Set("SetArtifactReadByUser", "SetArtifactAddedToUserFeed", "SetArtifactRemovedFromUserFeed")

    in.mapAsync(5) { command => // parallelism should be configurable
      // validate the command first
      if (validCommands.contains(command.command)) {
        val result = psCommandActor.ask { ref: ActorRef[ArtifactResponse] =>
          command.command match {
            case "SetArtifactReadByUser" =>
              ShardingEnvelope("%d%s".format(command.artifactId, command.userId), SetArtifactRead(ref, command.artifactId, command.userId))
            case "SetArtifactAddedToUserFeed" =>
              ShardingEnvelope("%d%s".format(command.artifactId, command.userId), SetArtifactAddedToUserFeed(ref, command.artifactId, command.userId))
            case "SetArtifactRemovedFromUserFeed" =>
              ShardingEnvelope("%d%s".format(command.artifactId, command.userId), SetArtifactRemovedFromUserFeed(ref, command.artifactId, command.userId))
          }
        }
        handleCmdResponse(ArtifactAndUser(command.artifactId, command.userId), result).map { response =>
          StreamedResponse(response.success, command = Some(command))
        }
      }
      else {
        val errMsg = s"invalid command received ${command.command} for user: ${command.userId} artifact id: ${command.artifactId}"
        system.log.error(errMsg)
        Future.successful(StreamedResponse(failureMsg = errMsg, command = Some(command)))
      }
    }
    .named("commandsStreamedIn")
  }
}
