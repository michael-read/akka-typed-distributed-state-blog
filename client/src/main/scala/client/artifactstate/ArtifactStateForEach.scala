package client.artifactstate

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import com.lightbend.artifactstate.endpoint.{ArtifactAndUser, ArtifactCommand, ArtifactStateServiceClient, CommandResponse}

import scala.collection._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Random, Success}

// tag::clientForEach[]
object ArtifactStateForEach extends App {

    implicit val sys: ActorSystem[_] = ActorSystem(Behaviors.empty, "ArtifactStateClient")
    implicit val ec: ExecutionContext = sys.executionContext

    val client = ArtifactStateServiceClient(GrpcClientSettings.fromConfig("client.ArtifactStateService"))

    var lastnames = ListBuffer.empty[String]
    for (line <- scala.io.Source.fromFile("./test-data/lastnames.csv").getLines) {
        lastnames += line.replaceAll("[\t\n]", "")
    }

    val commands = List("SetArtifactReadByUser", "SetArtifactAddedToUserFeed", "SetArtifactRemovedFromUserFeed")

    var cnt: Int = 0
    val r = new Random()

    val replies = mutable.ListBuffer.empty[Future[CommandResponse]]
    for (cnt <- 1 to 1000) {
        val commandSelect = r.between(0, 3)
        val command = commands(commandSelect)
        val artifactId = r.between(0, 101)
        val userId = lastnames(r.between(1, 1001))
        replies += singleRequestReply(ArtifactAndUser(artifactId, userId), command, cnt)
    }
    println(s"requests sent ${replies.size}")
    Await.result(Future.sequence(replies), Duration(5, MINUTES))
    println("Done.")
    System.exit(0)

    def singleRequestReply(artifactAndUser: ArtifactAndUser, command: String, cnt: Int): Future[CommandResponse] = {
        println(s"transmitting data ($cnt) for artifactId ${artifactAndUser.artifactId} userId ${artifactAndUser.userId} command: ${command}")
        val reply = command match {
            case "SetArtifactReadByUser" =>
                client.setArtifactReadByUser(artifactAndUser)
            case "SetArtifactAddedToUserFeed" =>
                client.setArtifactReadByUser(artifactAndUser)
            case "SetArtifactRemovedFromUserFeed" =>
                client.setArtifactRemovedFromUserFeed(artifactAndUser)
            case _ =>
                Future.failed(new Throwable("Unsupported command encountered."))
        }
        reply.onComplete {
            case Success(commandResponse: CommandResponse) =>
                commandResponse match {
                    case commandResponse if commandResponse.success =>
                        println(s"reply received ($cnt): successful command ${command} on ${artifactAndUser}")
                    case commandResponse if !commandResponse.success =>
                        println(s"reply received ($cnt): failed command ${command} on ${artifactAndUser}")
                    case _ =>
                        println("unrecognized response")
                }

            case Failure(e) =>
                println(s"reply received ($cnt) a failure : ${e.getMessage} on ${command}")
        }
        reply
    }

}
// end::clientForEach[]