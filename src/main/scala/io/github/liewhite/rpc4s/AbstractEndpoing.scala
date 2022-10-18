package io.github.liewhite.rpc4s

import scala.reflect.ClassTag
import scala.util.Try
import java.time.ZonedDateTime
import scala.concurrent.Promise
import scala.concurrent.Future
import scala.concurrent.duration._
import java.util.UUID
import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.cluster.sharding.typed.scaladsl.*
import scala.concurrent.ExecutionContext.Implicits.*
import scala.concurrent.duration.*
import io.github.liewhite.json.codec.*

sealed trait EndpointStatus
case object Same extends EndpointStatus
case object Exit extends EndpointStatus

case class ResponseWithStatus[T](res: T, status: EndpointStatus = Same)

abstract class AbstractEndpoint[I,O](
    val name: String
) {
    def handler(
        ctx: ActorSystem[_],
        i: I,
        entityId: Option[String],
    ): ResponseWithStatus[O]

    // implement by subclass
    def listen(system: ActorSystem[_]): Unit
}
