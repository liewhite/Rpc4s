package io.github.liewhite.rpc4s

import scala.reflect.ClassTag
import scala.util.Try
import java.time.ZonedDateTime
import scala.concurrent.Promise
import scala.concurrent.Future
import java.util.UUID
import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.cluster.sharding.typed.scaladsl.*
import scala.concurrent.ExecutionContext.Implicits.*
import scala.concurrent.duration.*
import io.github.liewhite.json.codec.*
import io.github.liewhite.json.JsonBehavior.*
import io.circe.Json
import scala.util.Failure
import scala.util.Success
import akka.util.Timeout
import cats.syntax.validated

abstract class ClusterEndpoint[I: ClassTag: Encoder: Decoder, O: Encoder: Decoder](
    name: String,
    val role: String
) extends AbstractEndpoint[I, O](name) {
    val typeKey = EntityTypeKey[String](name)

    def listen(system: ActorSystem[_]): Unit = {
        init(system)
    }

    def init(system: ActorSystem[_]): Unit = {
        logger.info(s"sharding init ${typeKey} on ${system.address}")
        val shardRegion = ClusterSharding(system).init(
          Entity(typeKey)(createBehavior = entityContext => handlerBehavior(system)).withRole(role)
        )
    }

    def tell(
        system: ActorSystem[_],
        entityId: String,
        i: I
    ): Unit = {
        tellJson(system, entityId, i.encode)
    }

    def tellJson(
        system: ActorSystem[_],
        entityId: String,
        i: Json
    ): Unit = {
        val entity: EntityRef[String] =
            ClusterSharding(system).entityRefFor(typeKey, entityId)
        entity ! RequestWrapper(i, system.ignoreRef).toMsgString(system)
    }

    def callJson(
        system: ActorSystem[_],
        entityId: String,
        i: Json,
        timeout: Duration = 30.seconds
    ): Future[O] = {
        implicit val syst: ActorSystem[_] = system
        implicit val t: Timeout           = timeout.toSeconds.second

        val entity: EntityRef[String] = ClusterSharding(system).entityRefFor(typeKey, entityId)
        val result = entity.ask[String](ref => RequestWrapper(i, ref).toMsgString(system))(t)
        responseFromStringFuture(system, result, s"${typeKey}-${entityId}")
    }

    // 幂等请求需要用户提供request id
    def call(
        system: ActorSystem[_],
        entityId: String,
        i: I,
        timeout: Duration = 30.seconds
    ): Future[O] = {
        callJson(system, entityId, i.encode, timeout)
    }

}
