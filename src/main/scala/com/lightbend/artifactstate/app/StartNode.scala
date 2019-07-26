package com.lightbend.artifactstate.app

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.stream.ActorMaterializer
import com.lightbend.artifactstate.actors.{ArtifactStateEntityActor, ClusterListenerActor}
import com.lightbend.artifactstate.actors.ArtifactStateEntityActor.{ARTIFACTSTATES_SHARDNAME, ArtifactCommand}
import com.lightbend.artifactstate.endpoint.ArtifactStateRoutes
import com.typesafe.config.{Config, ConfigFactory}

object StartNode {
  private val appConfig = ConfigFactory.load()

  def main(args: Array[String]): Unit = {
    val clusterName = appConfig.getString ("clustering.cluster.name")
    appConfig.getString("app.nodetype") match {

      // run local
      case nodeRole @ "localCluster" =>
        println(s"starting $nodeRole...")
        val clusterIp = appConfig.getString ("clustering.ip")
        val clusterPort = appConfig.getString ("clustering.port")
        clusterStartup (clusterName, false, clusterIp, clusterPort, Seq (clusterPort) )
      case nodeRole @ "localEndpoint" =>
        println(s"starting $nodeRole...")
        endpoint(clusterName, nodeRole, false)

      // run in docker
      case nodeRole @ "dockerCluster" =>
        println(s"starting $nodeRole...")
        val clusterIp = appConfig.getString ("clustering.ip")
        val clusterPort = appConfig.getString ("clustering.port")
        clusterStartup (clusterName, true, clusterIp, clusterPort, Seq (clusterPort) )
      case nodeRole @ "dockerEndpoint" =>
        println(s"starting $nodeRole...")
        endpoint(clusterName, nodeRole, true)

      // run in kubernetes
      case nodeRole @ "k8sCluster" =>
        println(s"starting $nodeRole...")
        clusterStartupK8s(appConfig, clusterName)
      case nodeRole @ "k8sEndpoint" =>
        println(s"starting $nodeRole...")
        endpoint(clusterName, nodeRole, true)

    }
  }

  def clusterStartup(clusterName: String, isDocker: Boolean, defaultIp: String, defaultPort: String, ports: Seq[String]): Unit = {

    ports foreach { port =>
      println(s"starting on ${port}")

      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
        withFallback(appConfig)

      val localBehavior : Behavior[NotUsed] =
        Behaviors.setup { context =>

          val TypeKey = EntityTypeKey[ArtifactCommand](ARTIFACTSTATES_SHARDNAME)
          ClusterSharding(context.system).init(Entity(typeKey = TypeKey,
            createBehavior = ctx => ArtifactStateEntityActor.behavior(ctx.entityId))
            .withSettings(ClusterShardingSettings(context.system).withRole("sharded")))

          if (port == defaultPort) {
            context.spawn(ClusterListenerActor.clusterListenerBehavior, "clusterListenerActor")
            context.log.info("started clusterListenerActor")
          }

          Behaviors.receiveSignal {
            case (_, Terminated(_)) =>
              Behaviors.stopped
          }
        }

      // Create an Akka system
      val system = ActorSystem(localBehavior, clusterName, config)
      system.whenTerminated // remove compiler warnings
    }

  }

  def clusterStartupK8s(config: Config, clusterName: String) : Unit = {

    val k8sBehavior : Behavior[NotUsed] =
      Behaviors.setup { context =>

        val untypedSystem = context.system.toUntyped
        AkkaManagement(untypedSystem).start()
        ClusterBootstrap(untypedSystem).start()

        val TypeKey = EntityTypeKey[ArtifactCommand](ARTIFACTSTATES_SHARDNAME)
        val artifactActorSupervisor: ActorRef[ShardingEnvelope[ArtifactCommand]] =
          ClusterSharding(context.system).init(Entity(typeKey = TypeKey,
            createBehavior = ctx => ArtifactStateEntityActor.behavior(ctx.entityId)))

        Behaviors.receiveSignal {
          case (_, Terminated(_)) =>
            Behaviors.stopped
        }
      }

    // Create an Akka system
    val system = ActorSystem(k8sBehavior, clusterName, config)
    system.whenTerminated // remove compiler warnings

  }

  def endpoint(clusterName: String, nodeRole: String, inContainer: Boolean) = {

    val main: Behavior[NotUsed] =
      Behaviors.setup { context =>

        implicit val unTypedSystem = context.system.toUntyped
        implicit val materializer: ActorMaterializer = ActorMaterializer()

        implicit val ec = context.system.executionContext
        implicit val scheduler = context.system.scheduler

        nodeRole match {
          case "k8sEndpoint" =>
            //#start-akka-management
            AkkaManagement(unTypedSystem).start()
            //#start-akka-cluster bootstrap
            ClusterBootstrap(unTypedSystem).start()
          case _ =>
        }

        val TypeKey = EntityTypeKey[ArtifactCommand](ARTIFACTSTATES_SHARDNAME)
        val psEntities: ActorRef[ShardingEnvelope[ArtifactCommand]] =
          ClusterSharding(context.system).init(Entity(typeKey = TypeKey,
            createBehavior = ctx => ArtifactStateEntityActor.behavior(ctx.entityId))
            .withSettings(ClusterShardingSettings(context.system)))

        var psCommandActor: ActorRef[ShardingEnvelope[ArtifactCommand]] = psEntities

        lazy val routes: Route = new ArtifactStateRoutes(context.system, psCommandActor).psRoutes

        inContainer match {
          case false =>
            Http().bindAndHandle(routes, "localhost")
            context.log.info(s"Server online at http://localhost:8082/")
          case _ =>
            // running inside of docker container
            Http().bindAndHandle(routes, "0.0.0.0", 8082)
            context.log.info(s"Server online inside container on port 8082")
        }

        Behaviors.receiveSignal {
          case (_, Terminated(_)) =>
            Behaviors.stopped
        }
      }

    val system = ActorSystem(main, clusterName, appConfig)
    system.whenTerminated // remove compiler warnings

  }
}
