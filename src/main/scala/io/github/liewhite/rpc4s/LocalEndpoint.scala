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

// Local endpoint 创建出来大概率是要调用的
abstract class LocalEndpoint[I: ClassTag: Encoder: Decoder, O: Encoder: Decoder](
    name: String,
) extends AbstractEndpoint[I, O](name) {
    private var local: ActorRef[String] = null

    def tellJson(
        ctx: ActorContext[_],
        i: Json,
        customeRequestId: Option[String] = None
    ): Unit = {
        val requestId = requestIdFromOption(customeRequestId)
        local ! RequestWrapper(i, requestId, ctx.system.ignoreRef).toMsgString(ctx)
    }
    def tell(
        ctx: ActorContext[_],
        i: I,
        customeRequestId: Option[String] = None
    ): Unit = {
        tellJson(ctx, i.encode, customeRequestId)
    }

    def call(
        ctx: ActorContext[_],
        i: I,
        timeout: Duration = 30.seconds,
        customeRequestId: Option[String] = None
    ): Future[Try[O]] = {
        callJson(ctx, i.encode, timeout, customeRequestId)
    }

    // 幂等请求需要用户提供request id
    def callJson(
        ctx: ActorContext[_],
        i: Json,
        timeout: Duration = 30.seconds,
        customeRequestId: Option[String] = None
    ): Future[Try[O]] = {
        val requestId = requestIdFromOption(customeRequestId)
        val promise   = prepareRequest(requestId, timeout)
        local ! RequestWrapper(i, requestId, callbackActor).toMsgString(ctx)
        promise.future
    }

    def startLocal(
        ctx: ActorContext[_]
    ): this.type = {
        this.synchronized {
            clientInit(ctx)
            local = ctx.spawn(
              Behaviors.receive[String]((ctx, msg) => {
                  val req = RequestWrapper.fromMsgString[I](ctx, msg)
                  req match {
                      case Left(value) => {
                          ctx.log.error(s"failed parse request msg: $value")
                          Behaviors.same
                      }
                      case Right(request) => {
                          // catch error
                          val result = Try(
                            localHandle(
                              ctx,
                              request.msg
                            )
                          )
                          result match {
                              case Failure(exception) => {
                                  ctx.log.error(s"exec handler result error $exception")
                                  Behaviors.same
                              }
                              case Success(ResponseWithStatus(res, exit)) => {
                                  if (exit) {
                                      ctx.log.info(
                                        s"local actor exit: $name"
                                      )
                                      Behaviors.stopped
                                  } else {
                                      request.replyTo ! ResponseWrapper[O](
                                        Try(res),
                                        request.requestId
                                      ).toMsgString(ctx)
                                      Behaviors.same

                                  }
                              }
                          }
                      }

                  }
              }),
              name
            )
        }
        this
    }

    def localHandle(ctx: ActorContext[_], i: I): ResponseWithStatus[O]
}
