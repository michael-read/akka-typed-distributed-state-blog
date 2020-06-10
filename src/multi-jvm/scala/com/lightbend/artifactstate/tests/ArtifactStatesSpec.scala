package com.lightbend.artifactstate.tests

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.server.Route
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef

import scala.concurrent.duration._
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.cluster.typed.{Join, MultiNodeTypedClusterSpec}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.persistence.Persistence
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor.{AllStates, ArtifactCommand, ArtifactResponse, ArtifactStatesShardName, GetAllStates, Okay, SetArtifactAddedToUserFeed, SetArtifactRead, SetArtifactRemovedFromUserFeed}
import com.lightbend.artifactstate.endpoint.ArtifactStateRoutes
import com.typesafe.config.ConfigFactory
import com.lightbend.artifactstate.endpoint.ArtifactStatePocAPI.ArtifactAndUser
import org.scalatest.concurrent.ScalaFutures

import scala.language.postfixOps

object ArtifactStatesSpec extends MultiNodeConfig {
  val endpointTest: RoleName = role("endpointTest")
  val persistNode1: RoleName = role("persist1")
  val persistNode2: RoleName = role("persist2")

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
    akka.actor.serialization-bindings {
      "com.lightbend.artifactstate.serializer.EventSerializeMarker" = jackson-json
      "com.lightbend.artifactstate.serializer.MsgSerializeMarker" = jackson-json
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
    app {
      # If ask takes more time than this to complete the request is failed
      routes.ask-timeout = 5s
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
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import akka.http.scaladsl.model.StatusCodes
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.server.ExceptionHandler
  import com.lightbend.artifactstate.endpoint.JsonFormats._

  abstract class RouteTesting(psCommandActor: ActorRef[ShardingEnvelope[ArtifactCommand]]) extends ArtifactStateRoutes(typedSystem, psCommandActor) with RouteTest with TestFrameworkInterface
    with ScalaFutures with ScalatestUtils {

    override protected def createActorSystem(): akka.actor.ActorSystem = typedSystem.toClassic
    override def failTest(msg: String): Nothing = throw new TestFailedException(msg, 11)
    def testExceptionHandler = ExceptionHandler {
      case e =>
        e.printStackTrace()
        complete((StatusCodes.InternalServerError, e.getLocalizedMessage))
    }
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
   val TypeKey = EntityTypeKey[ArtifactCommand](ArtifactStatesShardName)
   val artifactActorSupervisor: ActorRef[ShardingEnvelope[ArtifactCommand]] =
     ClusterSharding(system.toTyped).init(entity = Entity(TypeKey)
     (createBehavior = ctx => ArtifactStateEntityActor(ctx.entityId))
       .withSettings(ClusterShardingSettings(system.toTyped).withRole("sharded")))
   artifactActorSupervisor
  }

  def startProxySharding(): ActorRef[ShardingEnvelope[ArtifactCommand]] = {
    val TypeKey = EntityTypeKey[ArtifactCommand](ArtifactStatesShardName)
    ClusterSharding(system.toTyped).init(Entity(TypeKey)
      (ctx => ArtifactStateEntityActor(ctx.entityId)))
  }

  val artifactMember: ArtifactAndUser = ArtifactAndUser(1L, "Mike")

  // fix flakey failure: "Request was neither completed nor rejected within 1 second (DynamicVariable.scala:59)"
  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(5.seconds)

  "Sharded ArtifactState app" must {

    "join cluster" in within(20.seconds) {
      Persistence(system) // start the Persistence extension
      join(persistNode1, persistNode1) // join myself
      join(endpointTest, persistNode1)
      join(persistNode2, persistNode1)
      enterBarrier("after join cluster")
    }

    // these tests test state directly against the cluster

    "set artifact read" in within (15.seconds) {
      awaitAssert {
        within(15.seconds) {
          val region = startProxySharding()
          val probe = TestProbe[ArtifactResponse]
          region ! ShardingEnvelope(artifactMember.userId, SetArtifactRead(probe.ref, artifactMember.artifactId, artifactMember.userId))
          probe.expectMessage(Okay())
        }
      }
      enterBarrier("after set artifact read")
    }

    "set artifact added to user feed" in within (15.seconds) {
      awaitAssert {
        within(15.seconds) {
          val region = startProxySharding()
          val probe = TestProbe[ArtifactResponse]
          region ! ShardingEnvelope(artifactMember.userId, SetArtifactAddedToUserFeed(probe.ref, artifactMember.artifactId, artifactMember.userId))
          probe.expectMessage(Okay())
        }
      }
      enterBarrier("after added to user feed")
    }

    "create, and retrieve Artifact State" in within(15.seconds) {
      runOn(persistNode1) {
        val region = startProxySharding()
        val probe = TestProbe[ArtifactResponse]
        region ! ShardingEnvelope(artifactMember.userId, SetArtifactRead(probe.ref, artifactMember.artifactId, artifactMember.userId))
        region ! ShardingEnvelope(artifactMember.userId, SetArtifactAddedToUserFeed(probe.ref, artifactMember.artifactId, artifactMember.userId))
        awaitAssert {
          within(15.seconds) {
            region ! ShardingEnvelope(artifactMember.userId, GetAllStates(probe.ref, artifactMember.artifactId, artifactMember.userId))
            probe.expectMessage(AllStates(artifactRead = true, artifactInUserFeed = true))
          }
        }
      }
      enterBarrier("after create, and retrieve Artifact State")
    }

    "edit and retrieve Artifact State" in within(15.seconds) {

      runOn(persistNode2) {
        val region = startProxySharding()
        val probe = TestProbe[ArtifactResponse]
        region ! ShardingEnvelope(artifactMember.userId, SetArtifactRemovedFromUserFeed(probe.ref, artifactMember.artifactId, artifactMember.userId))
        awaitAssert {
          within(15.seconds) {
            region ! ShardingEnvelope(artifactMember.userId, GetAllStates(probe.ref, artifactMember.artifactId, artifactMember.userId))
            probe.expectMessage(AllStates(artifactRead = true, artifactInUserFeed = false))
          }
        }
      }
      enterBarrier("after edit and retrieve Artifact State" )
    }

    // these tests use the Akka RouteTesting facility to test Akka HTTP Endpoint
    
    // test artifact read state

    "set artifact read state (POST)" in within(15.seconds) {
      runOn(endpointTest) {

        val region = startProxySharding()

        new RouteTesting(region) {
          PatienceConfig()
          // test setting artifactstate
          val userEntity: MessageEntity = Marshal(artifactMember).to[MessageEntity].futureValue // futureValue is from ScalaTest

          // using the RequestBuilding DSL:
          val request1: HttpRequest = Post("/artifactState/setArtifactReadByUser").withEntity(userEntity)

          request1 ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and we know what message we're expecting back:
            entityAs[String] should ===("""{"success":true}""")
          }

        }
      }
      // this will run on all nodes
      // use barrier to coordinate test steps
      enterBarrier("after set artifact read state (POST)" )
    }

    "validate that the artifact state is read (GET)" in within(15.seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          // test retrieve of new state
          val request2: HttpRequest = Get("/artifactState/isArtifactReadByUser?artifactId=1&userId=Mike")

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
      enterBarrier("after validate that the artifact state is read (GET)" )
    }

    "validate that the artifact state is read (POST)" in within(15.seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        // this will only run on the 'first' node

        // You have to use such new RouteTesting { } block around the routing test code

        new RouteTesting(region) {

          val userEntity: MessageEntity = Marshal(artifactMember).to[MessageEntity].futureValue // futureValue is from ScalaTest

          val request: HttpRequest = Post("/artifactState/isArtifactReadByUser").withEntity(userEntity)

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
      enterBarrier("after validate that the artifact state is read (POST)" )
    }

    // artifact / member feed tests

    "set artifact in member feed (POST)" in within(15.seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          // test setting memberfeed
          val userEntity: MessageEntity = Marshal(artifactMember).to[MessageEntity].futureValue // futureValue is from ScalaTest

          // using the RequestBuilding DSL:
          val request1: HttpRequest = Post("/artifactState/setArtifactAddedToUserFeed").withEntity(userEntity)

          request1 ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and we know what message we're expecting back:
            entityAs[String] should ===("""{"success":true}""")
          }

        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      enterBarrier("after set artifact in member feed (POST)" )
    }

    "validate that the artifact / member feed is read (GET)" in within(15.seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          // test retrieve of new state
          val request2: HttpRequest = Get("/artifactState/isArtifactInUserFeed?artifactId=1&userId=Mike")

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
      enterBarrier("after validate that the artifact / member feed is read (GET)" )
    }

    "validate that the artifact is in member feed (POST)" in within(15.seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          val userEntity: MessageEntity = Marshal(artifactMember).to[MessageEntity].futureValue // futureValue is from ScalaTest

          val request: HttpRequest = Post("/artifactState/isArtifactInUserFeed").withEntity(userEntity)

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
      enterBarrier("after validate that the artifact is in member feed (POST)" )
    }

    "remove artifact from member feed (POST)" in within(15.seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          // test setting memberfeed
          val userEntity: MessageEntity = Marshal(artifactMember).to[MessageEntity].futureValue // futureValue is from ScalaTest

          // using the RequestBuilding DSL:
          val request1: HttpRequest = Post("/artifactState/setArtifactRemovedFromUserFeed").withEntity(userEntity)

          request1 ~> routes ~> check {
            status should ===(StatusCodes.OK)

            // we expect the response to be json:
            contentType should ===(ContentTypes.`application/json`)

            // and we know what message we're expecting back:
            entityAs[String] should ===("""{"success":true}""")
          }

        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      enterBarrier("after remove artifact from member feed (POST)" )
    }

    "validate that the artifact has been removed from member feed (GET)" in within(15.seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          // test retrieve of new state
          val request2: HttpRequest = Get("/artifactState/isArtifactInUserFeed?artifactId=1&userId=Mike")

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
      enterBarrier("after validate that the artifact has been removed from member feed (GET)" )

    }

    // test getAllStates

    "validate getAllStates (artifactRead: true, artifactInFeed: false (GET)" in within(15.seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        new RouteTesting(region) {

          // test retrieve of new state
          val request2: HttpRequest = Get("/artifactState/getAllStates?artifactId=1&userId=Mike")

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
      enterBarrier("after validate getAllStates (artifactRead: true, artifactInFeed: false (GET)" )
    }

    "validate getAllStates (artifactRead: true, artifactInFeed: false (POST)" in within(15.seconds) {
      runOn(endpointTest) {
        val region = startProxySharding()

        // this will only run on the 'first' node

        // You have to use such new RouteTesting { } block around the routing test code
        new RouteTesting(region) {

          val userEntity: MessageEntity = Marshal(artifactMember).to[MessageEntity].futureValue // futureValue is from ScalaTest

          val request: HttpRequest = Post("/artifactState/getAllStates").withEntity(userEntity)

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
      enterBarrier("after validate getAllStates (artifactRead: true, artifactInFeed: false (POST)" )
    }

  }

}
