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

abstract class ClusterEndpoint[I: ClassTag: Encoder: Decoder, O: Encoder: Decoder](
    name: String,
    val role: String
) extends AbstractEndpoint[I, O](name) {
    val typeKey = EntityTypeKey[String](name)

    def declareEntity(ctx: ActorContext[_]) = {
        ctx.log.info(s"sharding init ${typeKey} on ${ctx.system.address}")
        ClusterSharding(ctx.system).init(
          Entity(typeKey)(createBehavior =
              entityContext =>
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
          ).withRole(role)
        )

    }
    def tellEntity(
        ctx: ActorContext[_],
        entityId: String,
        i: I,
        customeRequestId: Option[String] = None
    ): Unit = {
        val requestId = customeRequestId match {
            case Some(id) => id
            case None     => UUID.randomUUID().toString()
        }
        val entity: EntityRef[String] =
            ClusterSharding(ctx.system).entityRefFor(typeKey, entityId)
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
        val requestId = customeRequestId match {
            case Some(id) => id
            case None     => UUID.randomUUID().toString()
        }
        val promise = Promise[Try[O]]
        requests.addOne(
          (requestId, (ZonedDateTime.now().plusSeconds(timeout.toSeconds), promise))
        )
        val entity: EntityRef[String] = ClusterSharding(ctx.system).entityRefFor(typeKey, entityId)
        entity ! RequestWrapper(i, requestId, callbackActor).toMsgString(ctx)
        promise.future
    }

    def clusterHandle(
        ctx: ActorContext[_],
        entityId: String,
        i: I
    ): O
}
