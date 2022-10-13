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

abstract class Endpoint[I: ClassTag: Encoder: Decoder, O: Encoder: Decoder](
    name: String
) {
    private var callable: Boolean         = false
    private var clusterDeclared: Boolean  = false
    private var sharding: ClusterSharding = null
    private val TypeKey                   = EntityTypeKey[String](name)

    private var callbackActor: ActorRef[String] = null
    private var local: ActorRef[String]         = null

    // 处理中的请求
    private val requests: scala.collection.concurrent.TrieMap[
      String,
      (ZonedDateTime, Promise[Try[O]])
    ] = scala.collection.concurrent.TrieMap
        .empty[String, (ZonedDateTime, Promise[Try[O]])]

    def declareEntity(ctx: ActorContext[_])(using Decoder[I]) = {
        this.synchronized {
            if (!clusterDeclared) {
                clusterDeclared = true
                sharding = ClusterSharding(ctx.system)
                sharding.init(
                  Entity(TypeKey)(createBehavior =
                      entityContext =>
                          // Behaviors.receive[RequestWrapper[I, O]]((ctx, msg) => {
                          Behaviors.receive[String]((ctx, msg) => {
                              val req = RequestWrapper.fromMsgString[I](ctx, msg)
                              req match
                                  case Left(value) => ctx.log.error(value.getMessage())
                                  case Right(value) => {
                                      // catch error
                                      val result = Try(
                                        clusterHandle(
                                          ctx,
                                          entityContext.entityId,
                                          value.msg
                                        )
                                      )
                                      value.replyTo ! ResponseWrapper(
                                        result,
                                        value.requestId
                                      ).toMsgString(ctx)

                                  }
                              Behaviors.same

                          })
                  )
                )

            }
        }
    }

    private def clientInit(ctx: ActorContext[_]) = {
        this.synchronized {
            if (!callable) {
                createCallbackActor(ctx)
                callable = true
                Future {
                    while (true) {
                        val now      = ZonedDateTime.now()
                        val timeouts = requests.filter((_, item) => item._1.isBefore(now))
                        timeouts.foreach(item => {
                            item._2._2.tryFailure(Timeout)
                            requests.remove(item._1)
                        })
                        Thread.sleep(30000)
                    }
                }
            }
        }
    }

    def tell(
        ctx: ActorContext[_],
        i: I,
        customeRequestId: Option[String] = None
    ): Unit = {
        clientInit(ctx)
        val requestId = customeRequestId match {
            case Some(id) => id
            case None     => UUID.randomUUID().toString()
        }
        local ! RequestWrapper(i, requestId, ctx.system.ignoreRef).toMsgString(ctx)
    }

    // 幂等请求需要用户提供request id
    def call(
        ctx: ActorContext[_],
        i: I,
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
    def tellEntity(
        ctx: ActorContext[_],
        entityId: String,
        i: I,
        customeRequestId: Option[String] = None
    ): Unit = {
        declareEntity(ctx)
        val requestId = customeRequestId match {
            case Some(id) => id
            case None     => UUID.randomUUID().toString()
        }
        val entity: EntityRef[String] =
            sharding.entityRefFor(TypeKey, entityId)
        entity ! RequestWrapper(i, requestId, ctx.system.ignoreRef).toMsgString(ctx)
    }

    // 幂等请求需要用户提供request id
    def callEntity(
        ctx: ActorContext[_],
        entityId: String,
        i: I,
        timeout: Duration = 30.seconds,
        customeRequestId: Option[String] = None
    ): Future[Try[O]] = {
        clientInit(ctx)
        declareEntity(ctx)
        val requestId = customeRequestId match {
            case Some(id) => id
            case None     => UUID.randomUUID().toString()
        }
        val promise = Promise[Try[O]]
        requests.addOne(
          (requestId, (ZonedDateTime.now().plusSeconds(timeout.toSeconds), promise))
        )
        val entity: EntityRef[String] =
            sharding.entityRefFor(TypeKey, entityId)
        entity ! RequestWrapper(i, requestId, callbackActor).toMsgString(ctx)
        promise.future
    }

    def createCallbackActor(ctx: ActorContext[_]) = {
        ctx.log.info("creating callback actor for {}", name)
        callbackActor = ctx.spawn(
          Behaviors.receive[String]((ctx, msg) => {
              ResponseWrapper.fromMsgString[O](ctx, msg) match {
                  case Left(value) => ctx.log.error("not json msg: {}", msg)
                  case Right(msg) => {
                      requests.get(msg.requestId).map(_._2.trySuccess(msg.response))
                      requests.remove(msg.requestId)
                  }
              }
              Behaviors.same
          }),
          name + "_callback"
        )
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
    def startCluster(
        ctx: ActorContext[_]
    ) = {
        declareEntity(ctx)
    }

    def localHandle(ctx: ActorContext[_], i: I): O

    def clusterHandle(
        ctx: ActorContext[_],
        entityId: String,
        i: I
    ): O
}
