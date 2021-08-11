package client.artifactstate

import akka.{Done, NotUsed}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.stream.scaladsl.Source
import com.lightbend.artifactstate.endpoint.{ArtifactCommand, ArtifactStateServiceClient, CommandResponse, StreamedResponse}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success}

// tag::clientStream[]
object ArtifactStateStream extends App {

    implicit val sys: ActorSystem[_] = ActorSystem(Behaviors.empty, "ArtifactStateClient")
    implicit val ec: ExecutionContext = sys.executionContext

    val client = ArtifactStateServiceClient(GrpcClientSettings.fromConfig("client.ArtifactStateService"))

    var lastnames = ListBuffer.empty[String]
    for (line <- scala.io.Source.fromFile("./test-data/lastnames.csv").getLines) {
        lastnames += line.replaceAll("[\t\n]", "")
    }

    streamArtifactState

    def streamArtifactState : Unit = {

        val r = new Random()

        val commands = List("SetArtifactReadByUser", "SetArtifactAddedToUserFeed", "SetArtifactRemovedFromUserFeed", "XYZZY")

        val requestStream: Source[ArtifactCommand, NotUsed] = Source(1 to 2000).map { i =>
            val commandSelect = r.between(0, 4)
            val command = commands(commandSelect)
            val artifactId = r.between(0, 101)
            val userId = lastnames(r.between(1, 1001))
            println(s"transmitting data for artifactId ${artifactId} userId ${userId} ($i) command: ${command}")
            ArtifactCommand(artifactId, userId, command)
        }

        val responseStream: Source[StreamedResponse, NotUsed] = client.commandsStreamed(requestStream)
        var cnt = 0
        val done: Future[Done] =
            responseStream.runForeach{ reply =>
                cnt = cnt + 1
                reply.success match {
                  case true =>
                    println(s"streaming reply received ($cnt): ${reply.success}")
                  case false =>
                      println(s"streaming reply received ($cnt) a failure : ${reply.failureMsg} on ${reply.command}")
                }
            }

        done.onComplete {
            case Success(_) =>
                println("streamingBroadcast done")
                System.exit(0)
            case Failure(e) =>
                println(s"Error streamingBroadcast: $e")
                System.exit(0)
        }
    }

}
// end::clientStream[]