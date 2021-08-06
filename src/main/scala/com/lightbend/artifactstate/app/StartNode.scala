package com.lightbend.artifactstate.app

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.typed.Cluster
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.{Done, NotUsed, actor}
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor.{ArtifactCommand, ArtifactStatesShardName}
import com.lightbend.artifactstate.actors.{ArtifactStateEntityActor, ClusterListenerActor}
import com.lightbend.artifactstate.endpoint.ArtifactStateRoutes
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContextExecutor, Future}

object StartNode {
  private val appConfig = ConfigFactory.load()

  def main(args: Array[String]): Unit = {
    val clusterName = appConfig.getString ("clustering.cluster.name")
    val clusterPort = appConfig.getInt ("clustering.port")
    val defaultPort = appConfig.getInt ("clustering.defaultPort")
    if (appConfig.hasPath("clustering.ports")) {
      val clusterPorts = appConfig.getIntList("clustering.ports")
      clusterPorts.forEach { port =>
        startNode(RootBehavior(port, defaultPort), clusterName)
      }
    }
    else {
      startNode(RootBehavior(clusterPort, defaultPort), clusterName)
    }
  }

  private object RootBehavior {
    def apply(port: Int, defaultPort: Int) : Behavior[NotUsed] =
      Behaviors.setup { context =>
        implicit val classicSystem: actor.ActorSystem =  TypedActorSystemOps(context.system).toClassic

        val TypeKey = EntityTypeKey[ArtifactCommand](ArtifactStatesShardName)

        val cluster = Cluster(context.system)
        context.log.info(s"starting node with roles: $cluster.selfMember.roles")

        if (cluster.selfMember.hasRole("k8s")) {
          AkkaManagement(classicSystem).start()
          ClusterBootstrap(classicSystem).start()
        }

        if (cluster.selfMember.hasRole("sharded")) {
          ClusterSharding(context.system).init(Entity(TypeKey)
          (createBehavior = ctx => ArtifactStateEntityActor(ctx.entityId))
            .withSettings(ClusterShardingSettings(context.system).withRole("sharded")))
        }
        else {
          if (cluster.selfMember.hasRole("endpoint")) {
            implicit val ec: ExecutionContextExecutor = context.system.executionContext
            val psEntities: ActorRef[ShardingEnvelope[ArtifactCommand]] =
              ClusterSharding(context.system).init(Entity(TypeKey)
              (createBehavior = ctx => ArtifactStateEntityActor(ctx.entityId)))
            val psCommandActor: ActorRef[ShardingEnvelope[ArtifactCommand]] = psEntities

            lazy val routes: Route = new ArtifactStateRoutes(context.system, psCommandActor).psRoutes
            val httpPort = context.system.settings.config.getString("akka.http.server.default-http-port")
            val interface = if (!cluster.selfMember.hasRole("docker") && !cluster.selfMember.hasRole("k8s")) {
              "localhost"
            }
            else {
              "0.0.0.0"
            }
            val binding = Http().newServerAt(interface, 8082).bind(routes)
            // report successful binding
            binding.foreach { binding => println(s"Server online inside container on ip ${binding.localAddress} port $httpPort") }
          }
        }

        if (port == defaultPort) {
          context.spawn(ClusterListenerActor(), "clusterListenerActor")
          context.log.info("started clusterListenerActor")
        }

        Behaviors.empty
      }
  }

  def startNode(behavior: Behavior[NotUsed], clusterName: String): Future[Done] = {
    val system = ActorSystem(behavior, clusterName, appConfig)
    system.whenTerminated // remove compiler warnings
  }
}
