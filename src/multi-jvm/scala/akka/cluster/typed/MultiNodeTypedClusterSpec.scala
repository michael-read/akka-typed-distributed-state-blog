package akka.cluster.typed

import java.util.concurrent.ConcurrentHashMap
import akka.actor.{Address, Scheduler}
import akka.actor.typed.ActorSystem
import akka.remote.testkit.{MultiNodeSpec, STMultiNodeSpec}
import org.scalatest.Suite
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.ClusterEvent
import akka.remote.testconductor.RoleName
import org.scalatest.matchers.should.Matchers


import scala.language.implicitConversions

trait MultiNodeTypedClusterSpec
  extends Suite
      with STMultiNodeSpec
//      with WatchedByCoroner
      with Matchers {
    self: MultiNodeSpec =>

    override def initialParticipants: Int = roles.size

    implicit def typedSystem: ActorSystem[Nothing] = system.toTyped
    implicit def scheduler: Scheduler = system.scheduler

    private val cachedAddresses = new ConcurrentHashMap[RoleName, Address]

    def cluster: Cluster = Cluster(system.toTyped)

    def clusterView: ClusterEvent.CurrentClusterState = cluster.state

  }
