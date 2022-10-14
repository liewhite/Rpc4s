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

abstract class LocalEndpoint[I: ClassTag: Encoder: Decoder, O: Encoder: Decoder](name:String) extends AbstractEndpoint[I, O](name) {
    private var local: ActorRef[String] = null

    def tellJson(
        ctx: ActorContext[_],
        i: Json,
        customeRequestId: Option[String] = None
    ): Unit = {
        val requestId = customeRequestId match {
            case Some(id) => id
            case None     => UUID.randomUUID().toString()
        }
        local ! RequestWrapper(i, requestId, ctx.system.ignoreRef).toMsgString(ctx)
    }
    def tell(
        ctx: ActorContext[_],
        i: I,
        customeRequestId: Option[String] = None
    ): Unit = {
        tellJson(ctx,i.encode, customeRequestId)
    }

    def call(
        ctx: ActorContext[_],
        i: I,
        timeout: Duration = 30.seconds,
        customeRequestId: Option[String] = None
    ): Future[Try[O]] = {
        callJson(ctx,i.encode,timeout,customeRequestId)
    }

    // 幂等请求需要用户提供request id
    def callJson(
        ctx: ActorContext[_],
        i: Json,
        timeout: Duration = 30.seconds,
        customeRequestId: Option[String] = None
    ): Future[Try[O]] = {
        clientInit(ctx)
        val requestId = customeRequestId match {
            case Some(id) => id
            case None     => UUID.randomUUID().toString()
        }
        val promise = Promise[Try[O]]
        requests.addOne(
          (requestId, (ZonedDateTime.now().plusSeconds(timeout.toSeconds), promise))
        )
        local ! RequestWrapper(i, requestId, callbackActor).toMsgString(ctx)
        promise.future
    }

    def startLocal(
        ctx: ActorContext[_]
    ) = {
        this.synchronized {
            local = ctx.spawn(
              Behaviors.receive[String]((ctx, msg) => {
                  val req = RequestWrapper.fromMsgString[I](ctx, msg)
                  req match
                      case Left(value) => ctx.log.error(value.getMessage())
                      case Right(value) => {
                          // catch error
                          val result = Try(
                            localHandle(
                              ctx,
                              value.msg
                            )
                          )
                          value.replyTo ! ResponseWrapper(
                            result,
                            value.requestId
                          ).toMsgString(ctx)
                      }
                  Behaviors.same
              }),
              name
            )
        }
    }
    def localHandle(ctx: ActorContext[_], i: I): O
}
