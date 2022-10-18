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

abstract class ClusterWorkerPoolEndpoint[I: ClassTag: Encoder: Decoder, O: Encoder: Decoder](
    name: String,
    maxPoolSize: Int,
    role: String,
) extends ClusterEndpoint[I, O](name, role) {

    def tellWorker(
        system: ActorSystem[_],
        i: I,
        customeRequestId: Option[String] = None
    ): Unit = {
        tellWorkerJson(system, i.encode, customeRequestId)
    }

    def tellWorkerJson(
        system: ActorSystem[_],
        i: Json,
        customeRequestId: Option[String] = None
    ): Unit = {
        val entityId  = entityIdFromReq(i)
        val entity: EntityRef[String] =
            ClusterSharding(system).entityRefFor(typeKey, entityId)
        entity ! RequestWrapper(i, system.ignoreRef).toMsgString(system)
    }
    def callWorker(
        system: ActorSystem[_],
        i: I,
        timeout: Duration = 30.seconds,
    ): Future[O] = {
        callWorkerJson(system, i.encode, timeout)
    }

    def callWorkerJson(
        system: ActorSystem[_],
        i: Json,
        timeout: Duration = 30.seconds,
    ): Future[O] = {
        val entityId                  = entityIdFromReq(i)
        callJson(system, entityId, i, timeout)
    }

    def entityIdFromReq(i: Object): String = {
        (i.hashCode().abs % maxPoolSize).toString()
    }
}
