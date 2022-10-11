package com.lightbend.artifactstate.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, Recovery, ReplicatedEventSourcing, ReplicationContext, RetentionCriteria}
import akka.persistence.typed.{PersistenceId, ReplicaId, ReplicationId, SnapshotSelectionCriteria}
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor.ArtifactCommand
import com.lightbend.artifactstate.serializer.{EventSerializeMarker, MsgSerializeMarker}

object ArtifactStateEntityActor {

  final val ArtifactStatesShardName = "ArtifactState"

  sealed trait BaseId extends MsgSerializeMarker {
    val artifactId: Long
    val userId: String
  }

  sealed trait ArtifactCommand extends BaseId

  sealed trait ArtifactQuery extends ArtifactCommand

  sealed trait ArtifactResponse extends MsgSerializeMarker

  // queries
  final case class IsArtifactReadByUser(replyTo: ActorRef[ArtifactReadByUser], artifactId: Long, userId: String) extends ArtifactQuery

  final case class IsArtifactInUserFeed(replyTo: ActorRef[ArtifactInUserFeed], artifactId: Long, userId: String) extends ArtifactQuery

  final case class GetAllStates(replyTo: ActorRef[AllStates], artifactId: Long, userId: String) extends ArtifactQuery

  // commands
  final case class SetArtifactRead(replyTo: ActorRef[Okay], artifactId: Long, userId: String) extends ArtifactCommand

  final case class SetArtifactAddedToUserFeed(replyTo: ActorRef[Okay], artifactId: Long, userId: String) extends ArtifactCommand

  final case class SetArtifactRemovedFromUserFeed(replyTo: ActorRef[Okay], artifactId: Long, userId: String) extends ArtifactCommand

  // responses
  final case class Okay(okay: String = "OK") extends ArtifactResponse

  final case class ArtifactReadByUser(artifactRead: Boolean) extends ArtifactResponse

  final case class ArtifactInUserFeed(artifactInUserFeed: Boolean) extends ArtifactResponse

  final case class AllStates(artifactRead: Boolean, artifactInUserFeed: Boolean) extends ArtifactResponse

  // events
  sealed trait ArtifactEvent extends EventSerializeMarker

  final case class ArtifactRead(mark: String) extends ArtifactEvent

  final case class ArtifactAddedToUserFeed() extends ArtifactEvent

  final case class ArtifactRemovedFromUserFeed() extends ArtifactEvent

  final case class CurrState(artifactRead: Boolean = false, artifactInUserFeed: Boolean = false) extends MsgSerializeMarker

  // this signature was for normal cluster operation
  /*  def apply(entityId: String): Behavior[ArtifactCommand] =
    EventSourcedBehavior[ArtifactCommand, ArtifactEvent, CurrState](
      persistenceId = PersistenceId(ArtifactStatesShardName, entityId),
      emptyState = CurrState(),
      commandHandler,
      eventHandler)*/

  // this signature is for operating with Multi-DC Replicated Event Sourcing
  def apply(
             entityId: String,
             replicaId: ReplicaId,
             allReplicas: Set[ReplicaId],
             queryPluginId: String,
             snapShotOnNrEvents: Int,
             keepNSnapshots: Int
           ): Behavior[ArtifactCommand] = Behaviors.setup[ArtifactCommand] { ctx =>
/*    ReplicatedEventSourcing.perReplicaJournalConfig(
      ReplicationId(ArtifactStatesShardName, entityId, replicaId),
      allReplicas) { replicationContext =>
      new ArtifactStateEntityActor(ctx, replicationContext, entityId: String, replicaId, allReplicas)
        .behavior()
    }*/
    ReplicatedEventSourcing.commonJournalConfig(
      ReplicationId(ArtifactStatesShardName, entityId, replicaId),
      allReplicas,
      queryPluginId) { replicationContext =>
      new ArtifactStateEntityActor(ctx, replicationContext, entityId: String,
        replicaId, allReplicas, snapShotOnNrEvents, keepNSnapshots)
        .behavior()
    }
  }

}

class ArtifactStateEntityActor(
      ctx: ActorContext[ArtifactCommand],
      replicationContext: ReplicationContext,
      entityId: String,
      replicaId: ReplicaId,
      allReplicas: Set[ReplicaId],
      snapShotOnNrEvents: Int,
      keepNSnapshots: Int
  ) {

  import ArtifactStateEntityActor._

  private def behavior() : EventSourcedBehavior[ArtifactCommand, ArtifactEvent, CurrState] = EventSourcedBehavior[ArtifactCommand, ArtifactEvent, CurrState](
    persistenceId = PersistenceId(ArtifactStatesShardName, entityId),
    emptyState = CurrState(),
    commandHandler,
    eventHandler)
    .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.latest))
    /*
      The R2DBC snapshot plugin only ever keeps *one* snapshot per persistence id in the database.
      If a `keepNSnapshots > 1` is specified for an `EventSourcedBehavior` that setting will be ignored.

      The reason for this is that there is no real benefit to keep multiple snapshots around on a relational
      database with a high consistency.
     */
    .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = snapShotOnNrEvents, keepNSnapshots = keepNSnapshots))

  private val commandHandler: (CurrState, ArtifactCommand) => Effect[ArtifactEvent, CurrState] = { (state, command) =>
    command match {
      case SetArtifactRead (replyTo, _, _) => artifactRead(replyTo, state)
      case SetArtifactAddedToUserFeed (replyTo, _, _) => artifactAddedToUserFeed(replyTo, state)
      case SetArtifactRemovedFromUserFeed (replyTo, _, _) => artifactRemovedFromUserFeed(replyTo, state)

      case IsArtifactReadByUser (replyTo, _, _) => getArtifactRead(replyTo, state)
      case IsArtifactInUserFeed (replyTo, _, _) => getAritfactInFeed (replyTo, state)
      case GetAllStates (replyTo, _, _) => getArtifactState (replyTo, state)
    }
  }

  private def artifactRead(replyTo: ActorRef[Okay], currState: CurrState): Effect[ArtifactEvent, CurrState] = {
    Effect.persist(ArtifactRead("Mike was here")).thenRun(_ => replyTo ! Okay())
  }

  private def artifactAddedToUserFeed(replyTo: ActorRef[Okay], currState: CurrState): Effect[ArtifactEvent, CurrState] = {
    Effect.persist(ArtifactAddedToUserFeed()).thenRun(_ => replyTo ! Okay())
  }

  private def artifactRemovedFromUserFeed(replyTo: ActorRef[Okay], currState: CurrState): Effect[ArtifactEvent, CurrState] = {
    Effect.persist(ArtifactRemovedFromUserFeed()).thenRun(_ => replyTo ! Okay())
  }

  private def getArtifactRead(replyTo: ActorRef[ArtifactReadByUser], currState: CurrState): Effect[ArtifactEvent, CurrState] = {
    replyTo ! ArtifactReadByUser(currState.artifactRead)
    Effect.none
  }

  private def getAritfactInFeed(replyTo: ActorRef[ArtifactInUserFeed], currState: CurrState): Effect[ArtifactEvent, CurrState] = {
    replyTo ! ArtifactInUserFeed(currState.artifactInUserFeed)
    Effect.none
  }

  private def getArtifactState(replyTo: ActorRef[AllStates], currState: CurrState): Effect[ArtifactEvent, CurrState] = {
    replyTo ! AllStates(currState.artifactRead, currState.artifactInUserFeed)
    Effect.none
  }

  private val eventHandler: (CurrState, ArtifactEvent) => CurrState = { (state, event) =>
    event match {
      case ArtifactRead(_) =>
        CurrState(artifactRead = true, artifactInUserFeed = state.artifactInUserFeed)

      case ArtifactAddedToUserFeed() =>
        CurrState(state.artifactRead, artifactInUserFeed = true)

      case ArtifactRemovedFromUserFeed() =>
        CurrState(state.artifactRead)

      case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
    }
  }
}