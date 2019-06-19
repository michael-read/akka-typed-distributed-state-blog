package com.lightbend.artifactstate.serializer

import java.nio.charset.Charset

import akka.actor.ExtendedActorSystem
import akka.event.Logging
import akka.persistence.journal.{ Tagged, WriteEventAdapter }
import akka.serialization.Serializer
import org.json4s.DefaultFormats

class JsonSerializer(actorSystem: ExtendedActorSystem) extends Serializer {

  import org.json4s.jackson.Serialization.{ read, write }

  private val log = Logging.getLogger(actorSystem, this)

  val UTF8: Charset = Charset.forName("UTF-8")

  implicit val formats = DefaultFormats

  // Completely unique value to identify this implementation of Serializer, used to optimize network traffic.
  // Values from 0 to 16 are reserved for Akka internal usage.
  // Make sure this does not conflict with any other kind of serializer or you will have problems
  override def identifier: Int = 90020001

  override def includeManifest = true

  override def fromBinary(bytes: Array[Byte], manifestOpt: Option[Class[_]]): AnyRef = {
    implicit val manifest = manifestOpt match {
      case Some(x) ⇒ Manifest.classType(x)
      case None ⇒ Manifest.AnyRef
    }
    val str = new String(bytes, UTF8)
    val result = read(str)
    result
  }

  override def toBinary(o: AnyRef): Array[Byte] = {
    val jsonString = write(o)
    val dat = write(o).getBytes(UTF8)
    dat
  }
}

/*

class Tagger(actorSystem: ExtendedActorSystem) extends WriteEventAdapter {

  private val log = Logging.getLogger(actorSystem, this)

  override def toJournal(event: Any): Any = event match {
    case e: UserEvent ⇒
      log.debug("tagging UserEvent event")
      Tagged(e, Set("UserEvent"))
    case e: UserManagerEvent ⇒
      log.debug("tagging UserManagerEvent event")
      Tagged(e, Set("UserManagerEvent"))
  }

  override def manifest(event: Any): String = ""
}*/
