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
    system: ActorSystem[?],
    name: String,
    maxPoolSize: Int,
    role: String,
) extends ClusterEndpoint[I, O](system, name, role) {

    def tellWorker(
        i: I,
        customeRequestId: Option[String] = None
    ): Unit = {
        tellWorkerJson(i.encode, customeRequestId)
    }

    def tellWorkerJson(
        i: Json,
        customeRequestId: Option[String] = None
    ): Unit = {
        val entityId  = entityIdFromReq(i)
        val entity: EntityRef[String] =
            ClusterSharding(system).entityRefFor(typeKey, entityId)
        entity ! RequestWrapper(i, system.ignoreRef).toMsgString(system)
    }
    def callWorker(
        i: I,
        timeout: Duration = 30.seconds,
    ): Future[O] = {
        callWorkerJson(i.encode, timeout)
    }

    def callWorkerJson(
        i: Json,
        timeout: Duration = 30.seconds,
    ): Future[O] = {
        val entityId                  = entityIdFromReq(i)
        callJson(entityId, i, timeout)
    }

    def entityIdFromReq(i: Object): String = {
        (i.hashCode().abs % maxPoolSize).toString()
    }
}
