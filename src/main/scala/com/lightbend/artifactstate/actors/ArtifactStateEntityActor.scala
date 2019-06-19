package com.lightbend.artifactstate.actors

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import com.lightbend.artifactstate.serializer.{EventSerializeMarker, MsgSerializeMarker}

object ArtifactStateEntityActor {

  final val ARTIFACTSTATES_SHARDNAME = "ArtifactState"

  sealed trait BaseId extends MsgSerializeMarker {
    val artifactId: Long
    val userId: String
  }
  sealed trait ArtifactCommand extends BaseId
  sealed trait ArtifactResponse extends MsgSerializeMarker

  // queries
  final case class QryArtifactReadByUser(replyTo: ActorRef[ArtifactResponse], artifactId: Long, userId: String) extends ArtifactCommand
  final case class QryArtifactInUserFeed(replyTo: ActorRef[ArtifactResponse], artifactId: Long, userId: String) extends ArtifactCommand
  final case class QryGetAllStates(replyTo: ActorRef[ArtifactResponse], artifactId: Long, userId: String) extends ArtifactCommand

  // commands
  final case class CmdArtifactRead(artifactId: Long, userId: String) extends ArtifactCommand
  final case class CmdArtifactAddedToUserFeed(artifactId: Long, userId: String) extends ArtifactCommand
  final case class CmdArtifactRemovedFromUserFeed(artifactId: Long, userId: String) extends ArtifactCommand

  // responses
  final case class ArtifactReadByUser(artifactRead: Boolean) extends ArtifactResponse
  final case class ArtifactInUserFeed(artifactInUserFeed: Boolean) extends ArtifactResponse
  final case class AllStates(artifactRead: Boolean, artifactInUserFeed: Boolean) extends ArtifactResponse

  // events
  sealed trait ArtifactEvent extends EventSerializeMarker
  final case class ArtifactRead() extends ArtifactEvent
  final case class ArtifactAddedToUserFeed() extends ArtifactEvent
  final case class ArtifactRemovedFromUserFeed() extends ArtifactEvent

  sealed trait ArtifactState extends MsgSerializeMarker
  final case class CurrState(artifactRead: Boolean = false, artifactInUserFeed : Boolean = false) extends ArtifactState

  def behavior(entityId: String): Behavior[ArtifactCommand] =
    EventSourcedBehavior[ArtifactCommand, ArtifactEvent, ArtifactState](
      persistenceId = PersistenceId(entityId),
      emptyState = CurrState(),
      commandHandler,
      eventHandler)

  private val commandHandler: (ArtifactState, ArtifactCommand) => Effect[ArtifactEvent, ArtifactState] = { (state, command) =>
    state match {
      case currState: CurrState =>
        command match {
          case cmd@CmdArtifactRead (artifactId, userId) => artifactRead(cmd)
          case cmd@CmdArtifactAddedToUserFeed (artifactId, userId) => artifactAddedToUserFeed(cmd)
          case cmd@CmdArtifactRemovedFromUserFeed (artifactId, userId) => artifactRemovedFromUserFeed(cmd)

          case QryArtifactReadByUser (replyTo, artifactId, userId) => getArtifactRead(replyTo, currState)
          case QryArtifactInUserFeed (replyTo, artifactId, userId) => getAritfactInFeed (replyTo, currState)
          case QryGetAllStates (replyTo, artifactId, userId) => getArtifactState (replyTo, currState)

          case _ => Effect.unhandled
        }

      case _ =>
        Effect.unhandled
    }
  }

  private def artifactRead(cmd: ArtifactCommand): Effect[ArtifactEvent, ArtifactState] = {
    Effect.persist(ArtifactRead())
  }

  private def artifactAddedToUserFeed(cmd: ArtifactCommand): Effect[ArtifactEvent, ArtifactState] = {
    Effect.persist(ArtifactAddedToUserFeed())
  }

  private def artifactRemovedFromUserFeed(cmd: ArtifactCommand): Effect[ArtifactEvent, ArtifactState] = {
    Effect.persist(ArtifactRemovedFromUserFeed())
  }

  private def getArtifactRead(replyTo: ActorRef[ArtifactResponse], currState: CurrState): Effect[ArtifactEvent, ArtifactState] = {
    replyTo ! ArtifactReadByUser(currState.artifactRead)
    Effect.none
  }

  private def getAritfactInFeed(replyTo: ActorRef[ArtifactResponse], currState: CurrState): Effect[ArtifactEvent, ArtifactState] = {
    replyTo ! ArtifactInUserFeed(currState.artifactInUserFeed)
    Effect.none
  }

  private def getArtifactState(replyTo: ActorRef[ArtifactResponse], currState: CurrState): Effect[ArtifactEvent, ArtifactState] = {
    replyTo ! AllStates(currState.artifactRead, currState.artifactInUserFeed)
    Effect.none
  }

  private val eventHandler: (ArtifactState, ArtifactEvent) => ArtifactState = { (state, event) =>
    state match {
      case currState: CurrState =>
        event match {
          case ArtifactRead() =>
            CurrState(true, currState.artifactInUserFeed)

          case ArtifactAddedToUserFeed() =>
            CurrState(currState.artifactRead, true)

          case ArtifactRemovedFromUserFeed() =>
            CurrState(currState.artifactRead, false)

          case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
        }
      case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
    }
  }
}