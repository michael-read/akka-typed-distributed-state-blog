package com.lightbend.artifactstate.app

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.typed.Cluster
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives.{concat, handle}
import akka.http.scaladsl.server.Route
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.typed.ReplicaId
import akka.{Done, NotUsed, actor}
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor.{ArtifactCommand, ArtifactStatesShardName}
import com.lightbend.artifactstate.actors.{ArtifactStateEntityActor, ClusterListenerActor}
import com.lightbend.artifactstate.endpoint.{ArtifactStateRoutes, ArtifactStateServiceHandler, GrpcArtifactStateServiceImpl}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContextExecutor, Future}

object StartNode {
  private val appConfig = ConfigFactory.load()

  def main(args: Array[String]): Unit = {
    val clusterName = appConfig.getString("clustering.cluster.name")
    val clusterPort = appConfig.getInt("clustering.port")
    val defaultPort = appConfig.getInt("clustering.defaultPort")
    val dataCenter = if (appConfig.hasPath("akka.cluster.multi-data-center.self-data-center")) {
      ReplicaId(appConfig.getString("akka.cluster.multi-data-center.self-data-center"))
    }
    else {
      ReplicaId("dc-default")
    }
    var dcsConfigs = Set[ReplicaId]()
    if (appConfig.hasPath("clustering.allDataCenters")) {
      appConfig.getString("clustering.allDataCenters").split(",").map { dc =>
        dcsConfigs += ReplicaId(dc)
      }
    }

    val queryPluginId = appConfig.getString("clustering.queryPluginId")


    if (appConfig.hasPath("clustering.ports")) {
      val clusterPorts = appConfig.getIntList("clustering.ports")
      clusterPorts.forEach { port =>
        startNode(RootBehavior(port, defaultPort, dataCenter, dcsConfigs, queryPluginId), clusterName)
      }
    }
    else {
      startNode(RootBehavior(clusterPort, defaultPort, dataCenter, dcsConfigs, queryPluginId), clusterName)
    }
  }

  private object RootBehavior {
    def apply(port: Int, defaultPort: Int, dataCenter: ReplicaId, allDataCenters: Set[ReplicaId], queryPluginId: String): Behavior[NotUsed] =
      Behaviors.setup { context =>

        context.log.info(s"init RootBehavior: data center: $dataCenter")
        context.log.info(s"init RootBehavior: all data centers: $allDataCenters")
        context.log.info(s"init RootBehavior: queryPluginId: $queryPluginId")

        implicit val classicSystem: actor.ActorSystem = TypedActorSystemOps(context.system).toClassic

        val TypeKey = EntityTypeKey[ArtifactCommand](ArtifactStatesShardName)

        val cluster = Cluster(context.system)

        context.log.info(s"starting node with roles:")
        cluster.selfMember.roles.foreach { role =>
          context.log.info(s"role : $role")
        }

        if (cluster.selfMember.hasRole("k8s")) {
          AkkaManagement(classicSystem).start()
          ClusterBootstrap(classicSystem).start()
        }

        if (cluster.selfMember.hasRole("sharded")) {
          ClusterSharding(context.system).init(Entity(TypeKey)
          (createBehavior = ctx => ArtifactStateEntityActor(ctx.entityId, dataCenter, allDataCenters, queryPluginId))
            .withSettings(ClusterShardingSettings(context.system).withRole("sharded").withDataCenter(dataCenter.id)))
        }
        else {
          if (cluster.selfMember.hasRole("endpoint")) {
            implicit val ec: ExecutionContextExecutor = context.system.executionContext
            val psCommandActor: ActorRef[ShardingEnvelope[ArtifactCommand]] =
              ClusterSharding(context.system).init(Entity(TypeKey)
              (createBehavior = ctx => ArtifactStateEntityActor(ctx.entityId, dataCenter, allDataCenters, queryPluginId))
                .withSettings(ClusterShardingSettings(context.system).withDataCenter(dataCenter.id)))

            lazy val routes: Route = new ArtifactStateRoutes(context.system, psCommandActor).psRoutes
            val httpPort = context.system.settings.config.getString("akka.http.server.default-http-port")
            val interface = if (cluster.selfMember.hasRole("docker") || cluster.selfMember.hasRole("k8s")) {
              "0.0.0.0"
            }
            else {
              "localhost"
            }

            // Create gRPC service handler
            val grpcService: HttpRequest => Future[HttpResponse] =
              ArtifactStateServiceHandler.withServerReflection(new GrpcArtifactStateServiceImpl(context.system, psCommandActor))

            // As a Route
            val grpcHandlerRoute: Route = handle(grpcService)

            val route = concat(routes, grpcHandlerRoute)

            // Both HTTP and gRPC Binding
            val binding = Http().newServerAt(interface, httpPort.toInt).bind(route)

            binding.foreach { binding => println(s"HTTP / gRPC Server online at ip ${binding.localAddress} port $httpPort") }
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
