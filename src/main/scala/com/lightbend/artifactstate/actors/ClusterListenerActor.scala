package com.lightbend.artifactstate.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior}
import akka.cluster.ClusterEvent.{ClusterDomainEvent, MemberRemoved, MemberUp, UnreachableMember}
import akka.cluster.typed.{Cluster, Subscribe}

object ClusterListenerActor {

  def apply(): Behavior[ClusterDomainEvent] =
    Behaviors.setup[ClusterDomainEvent] { context =>

      val cluster = Cluster(context.system)
      cluster.subscriptions ! Subscribe(context.self.ref, classOf[ClusterDomainEvent])

      context.log.info(s"started actor ${context.self.path} - (${context.self.getClass})")

      def running(): Behavior[ClusterDomainEvent] =
        Behaviors.receive { (context, message) =>
          message match {
            case MemberUp(member) =>
              context.log.info("Member is Up: {}", member.address)
              Behaviors.same
            case UnreachableMember(member) =>
              context.log.info("Member detected as unreachable: {}", member)
              Behaviors.same
            case MemberRemoved(member, previousStatus) =>
              context.log.info(
                "Member is Removed: {} after {}",
                member.address, previousStatus)
              Behaviors.same
            case _ =>
              Behaviors.same // ignore
          }
        }

      running()
    }

}