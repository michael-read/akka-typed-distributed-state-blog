package com.mread.artifactstate.tests

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.server.Route

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef

import scala.concurrent.duration._
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.cluster.typed.{Join, MultiNodeTypedClusterSpec}
import akka.persistence.Persistence
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor.{ARTIFACTSTATES_SHARDNAME, AllStates, ArtifactCommand, ArtifactResponse, CmdArtifactAddedToUserFeed, CmdArtifactRead, CmdArtifactRemovedFromUserFeed, QryGetAllStates}
import com.lightbend.artifactstate.endpoint.ArtifactStateRoutes
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import com.lightbend.artifactstate.endpoint.ArtifactStatePocAPI.ArtifactAndUser

object ArtifactStatesSpec extends MultiNodeConfig {
  val endpointTest = role("endpointTest")
  val persistNode1 = role("persist1")
  val persistNode2 = role("persist2")

  nodeConfig(persistNode1, persistNode2) {
    ConfigFactory.parseString(s"""
      akka.cluster.roles=["sharded"]
     sharding {
       role = "sharded"
     }
      """)
  }

  commonConfig(ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.actor.provider = cluster
    akka.cluster.metrics.enabled=off
    akka.actor.allow-java-serialization = on
    akka.actor.warn-about-java-serializer-usage = off
    akka.actor.serializers {
      myjson = "com.lightbend.artifactstate.serializer.JsonSerializer"
    }
    akka.actor.serialization-bindings {
      "com.lightbend.artifactstate.serializer.EventSerializeMarker" = myjson
    }
    akka.persistence {
      journal.plugin = "cassandra-journal"
      snapshot-store.plugin = "cassandra-snapshot-store"
    }
    cassandra-journal {
      contact-points = ["localhost"]
    }
    cassandra-snapshot-store {
      contact-points = ["localhost"]
    }
    """))
}

class PSSpecMultiJvmNode1 extends ArtifactStatesSpec
class PSSpecMultiJvmNode2 extends ArtifactStatesSpec
class PSSpecMultiJvmNode3 extends ArtifactStatesSpec

class ArtifactStatesSpec extends MultiNodeSpec(ArtifactStatesSpec)
  with ImplicitSender with MultiNodeTypedClusterSpec {

  import ArtifactStatesSpec._

  import akka.http.scaladsl.testkit.ScalatestUtils
  import org.scalatest.exceptions.TestFailedException
  import akka.http.scaladsl.testkit.RouteTest
  import akka.http.scaladsl.testkit.TestFrameworkInterface
  import akka.http.scaladsl.model._

  abstract class RouteTesting(psCommandActor: ActorRef[ShardingEnvelope[ArtifactCommand]]) extends ArtifactStateRoutes(typedSystem, psCommandActor) with RouteTest with TestFrameworkInterface
    with ScalaFutures with ScalatestUtils {

    override protected def createActorSystem(): akka.actor.ActorSystem = typedSystem.toUntyped
    override def failTest(msg: String): Nothing = throw new TestFailedException(msg, 11)

    lazy val routes: Route = psRoutes
  }

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {

      cluster.manager ! Join(node(to).address)

      startPersistentSharding()
    }
    enterBarrier(from.name + "-joined")
  }

  def startPersistentSharding(): ActorRef[ShardingEnvelope[ArtifactCommand]] = {
   val TypeKey = EntityTypeKey[ArtifactCommand](ARTIFACTSTATES_SHARDNAME)
   val artifactActorSupervisor: ActorRef[ShardingEnvelope[ArtifactCommand]] =
     ClusterSharding(system.toTyped).init(Entity(typeKey = TypeKey,
       createBehavior = ctx => ArtifactStateEntityActor.behavior(ctx.entityId))
       .withSettings(ClusterShardingSettings(system.toTyped).withRole("sharded")))
   artifactActorSupervisor
  }

  def startProxySharding(): ActorRef[ShardingEnvelope[ArtifactCommand]] = {
    val TypeKey = EntityTypeKey[ArtifactCommand](ARTIFACTSTATES_SHARDNAME)
    val artifactActorSupervisor: ActorRef[ShardingEnvelope[ArtifactCommand]] =
      ClusterSharding(system.toTyped).init(Entity(typeKey = TypeKey,
        createBehavior = ctx => ArtifactStateEntityActor.behavior(ctx.entityId)))
    artifactActorSupervisor
  }

  val artifactMember = ArtifactAndUser(1l, "Mike")

  "Sharded ArtifactState app" must {

    "join cluster" in within(20.seconds) {
      Persistence(system) // start the Persistence extension
      join(persistNode1, persistNode1) // join myself
      join(endpointTest, persistNode1)
      join(persistNode2, persistNode1)
      enterBarrier("after join cluster")
    }

    // these tests test state directly against the cluster

    "create, and retrieve Artifact State" in within(15.seconds) {

      runOn(persistNode1) {
        val region = startProxySharding()
        region ! ShardingEnvelope(artifactMember.userId, CmdArtifactRead(artifactMember.artifactId, artifactMember.userId))
        region ! ShardingEnvelope(artifactMember.userId, CmdArtifactAddedToUserFeed(artifactMember.artifactId, artifactMember.userId))
        awaitAssert {
          within(10.second) {
            val probe = TestProbe[ArtifactResponse]
            region ! ShardingEnvelope(artifactMember.userId, QryGetAllStates(probe.ref, artifactMember.artifactId, artifactMember.userId))
            probe.expectMessage(AllStates(artifactRead = true, artifactInUserFeed = true))
          }
        }
      }

      enterBarrier("after create, and retrieve Artifact State")
    }

    "edit and retrieve Artifact State" in within(15.seconds) {

      runOn(persistNode2) {
        val region = startProxySharding()
        region ! ShardingEnvelope(artifactMember.userId, CmdArtifactRemovedFromUserFeed(artifactMember.artifactId, artifactMember.userId))
        awaitAssert {
          within(10.second) {
            val probe = TestProbe[ArtifactResponse]
            region ! ShardingEnvelope(artifactMember.userId, QryGetAllStates(probe.ref, artifactMember.artifactId, artifactMember.userId))
            probe.expectMessage(AllStates(artifactRead = true, artifactInUserFeed = false))
          }
        }
      }
      enterBarrier("after edit and retrieve Artifact State" )
    }

    // these tests use the Akka RouteTesting facility to test Akka HTTP Endpoint
    
    // test artifact read state

    "set artifact read state (POST)" in within(15 seconds) {
      runOn(endpointTest) {

        val region = startProxySharding()

        new RouteTesting(region) {

          // test setting artifactstate
          val userEntity = Marshal(artifactMember).to[MessageEntity].futureValue // futureValue is from ScalaFutures

          // using the RequestBuilding DSL:
          val request1 = Post("/artifactState/setArtifactReadByUser").withEntity(userEntity)

          request1 ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and we know what message we're expecting back:
            entityAs[String] should ===("""{"artifactId":1,"userId":"Mike"}""")
          }

        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("endpointTest-artifactstate-set")
    }

    "validate that the artifact state is read (GET)" in within(15 seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          // test retrieve of new state
          val request2 = Get("/artifactState/isArtifactReadByUser?artifactId=1&userId=Mike")

          request2 ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[String] should ===("""{"answer":true,"artifactId":1,"userId":"Mike"}""")
          }

        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("endpointTest-artifactstate-get-read")
    }

    "validate that the artifact state is read (POST)" in within(15 seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        // this will only run on the 'first' node

        // You have to use such new RouteTesting { } block around the routing test code

        new RouteTesting(region) {

          val userEntity = Marshal(artifactMember).to[MessageEntity].futureValue // futureValue is from ScalaFutures

          val request = Post("/artifactState/isArtifactReadByUser").withEntity(userEntity)

          request ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[String] should ===("""{"answer":true,"artifactId":1,"userId":"Mike"}""")
          }

        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("endpointTest-artifactstate-post-read")
    }

    // artifact / member feed tests

    "set artifact in member feed (POST)" in within(15 seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          // test setting memberfeed
          val userEntity = Marshal(artifactMember).to[MessageEntity].futureValue // futureValue is from ScalaFutures

          // using the RequestBuilding DSL:
          val request1 = Post("/artifactState/setArtifactAddedToUserFeed").withEntity(userEntity)

          request1 ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and we know what message we're expecting back:
            entityAs[String] should ===("""{"artifactId":1,"userId":"Mike"}""")
          }

        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("endpointTest-memberfeed-set")
    }

    "validate that the artifact / member feed is read (GET)" in within(15 seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          // test retrieve of new state
          val request2 = Get("/artifactState/isArtifactInUserFeed?artifactId=1&userId=Mike")

          request2 ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[String] should ===("""{"answer":true,"artifactId":1,"userId":"Mike"}""")
          }

        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("endpointTest-memberfeed-get-read")
    }

    "validate that the artifact is in member feed (POST)" in within(15 seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          val userEntity = Marshal(artifactMember).to[MessageEntity].futureValue // futureValue is from ScalaFutures

          val request = Post("/artifactState/isArtifactInUserFeed").withEntity(userEntity)

          request ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[String] should ===("""{"answer":true,"artifactId":1,"userId":"Mike"}""")
          }

        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("endpointTest-memberfeed-post-read")
    }

    "remove artifact from member feed (POST)" in within(15 seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          // test setting memberfeed
          val userEntity = Marshal(artifactMember).to[MessageEntity].futureValue // futureValue is from ScalaFutures

          // using the RequestBuilding DSL:
          val request1 = Post("/artifactState/setArtifactRemovedFromUserFeed").withEntity(userEntity)

          request1 ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and we know what message we're expecting back:
            entityAs[String] should ===("""{"artifactId":1,"userId":"Mike"}""")
          }

        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("endpointTest-memberfeed-post-read")

    }

    "validate that the artifact has been removed from member feed (GET)" in within(15 seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          // test retrieve of new state
          val request2 = Get("/artifactState/isArtifactInUserFeed?artifactId=1&userId=Mike")

          request2 ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[String] should ===("""{"answer":false,"artifactId":1,"userId":"Mike"}""")
          }

        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("endpointTest-memberfeed-get-read-II")

    }

    // test getAllStates

    "validate getAllStates (artifactRead: true, artifactInFeed: false (GET)" in within(15 seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          // test retrieve of new state
          val request2 = Get("/artifactState/getAllStates?artifactId=1&userId=Mike")

          request2 ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[String] should ===("""{"artifactId":1,"artifactInUserFeed":false,"artifactRead":true,"userId":"Mike"}""")
          }

        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("endpointTest-memberfeed-get-read")
    }

    "validate getAllStates (artifactRead: true, artifactInFeed: false (POST)" in within(15 seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        // this will only run on the 'first' node

        // You have to use such new RouteTesting { } block around the routing test code
        new RouteTesting(region) {

          val userEntity = Marshal(artifactMember).to[MessageEntity].futureValue // futureValue is from ScalaFutures

          val request = Post("/artifactState/getAllStates").withEntity(userEntity)

          request ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[String] should ===("""{"artifactId":1,"artifactInUserFeed":false,"artifactRead":true,"userId":"Mike"}""")
          }

        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("endpointTest-memberfeed-post-read")
    }

  }

}
