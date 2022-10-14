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
    workerNum: Int,
    role: String
) extends ClusterEndpoint[I, O](name, role) {
    def tellWorker(
        ctx: ActorContext[_],
        i: I,
        customeRequestId: Option[String] = None
    ): Unit = {
        tellWorkerJson(ctx,i.encode,customeRequestId)
    }

    def tellWorkerJson(
        ctx: ActorContext[_],
        i: Json,
        customeRequestId: Option[String] = None
    ): Unit = {
        val requestId = requestIdFromOption(customeRequestId)
        val entityId = (i.hashCode() % workerNum).toString()
        val entity: EntityRef[String] =
            ClusterSharding(ctx.system).entityRefFor(typeKey, entityId)
        entity ! RequestWrapper(i, requestId, ctx.system.ignoreRef).toMsgString(ctx)
    }
    def callWorker(
        ctx: ActorContext[_],
        i: I,
        timeout: Duration = 30.seconds,
        customeRequestId: Option[String] = None
    ): Future[Try[O]] = {
        callWorkerJson(ctx, i.encode, timeout, customeRequestId)
    }

    def callWorkerJson(
        ctx: ActorContext[_],
        i: Json,
        timeout: Duration = 30.seconds,
        customeRequestId: Option[String] = None
    ): Future[Try[O]] = {
        clientInit(ctx)
        val requestId = requestIdFromOption(customeRequestId)
        val promise = prepareRequest(requestId,timeout)
        val entityId = (i.hashCode() % workerNum).toString()
        val entity: EntityRef[String] = ClusterSharding(ctx.system).entityRefFor(typeKey, entityId)
        entity ! RequestWrapper(i, requestId, callbackActor).toMsgString(ctx)
        promise.future
    }
}
