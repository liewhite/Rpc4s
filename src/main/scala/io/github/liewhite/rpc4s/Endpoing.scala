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

trait TMessage {}

case class RequestWrapper[I, O](
    msg: I,
    requestId: String,
    replyTo: ActorRef[ResponseWrapper[O]]
) extends TMessage

case class ResponseWrapper[O](response: Try[O], requestId: String)
    extends TMessage

class Endpoint[I <: TMessage: ClassTag, O](name: String) {
  var inited: Boolean = false
  var sharding: ClusterSharding = null
  val TypeKey = EntityTypeKey[RequestWrapper[I, O]](name)

  var callbackActor: ActorRef[ResponseWrapper[O]] = null
  var local: ActorRef[RequestWrapper[I, O]] = null

  // 处理中的请求
  val requests: scala.collection.concurrent.TrieMap[
    String,
    (ZonedDateTime, Promise[Try[O]])
  ] = scala.collection.concurrent.TrieMap
    .empty[String, (ZonedDateTime, Promise[Try[O]])]

  def init(ctx: ActorContext[_]) = {
    this.synchronized {
      if (!inited) {
        createCallbackActor(ctx)
        inited = true
        Future {
          while (true) {
            val now = ZonedDateTime.now()
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
    val requestId = customeRequestId match {
      case Some(id) => id
      case None     => UUID.randomUUID().toString()
    }
    local ! RequestWrapper(i, requestId, ctx.system.ignoreRef)
  }

  // 幂等请求需要用户提供request id
  def call(
      ctx: ActorContext[_],
      i: I,
      timeout: Duration = 30.seconds,
      customeRequestId: Option[String] = None
  ): Future[Try[O]] = {
    val requestId = customeRequestId match {
      case Some(id) => id
      case None     => UUID.randomUUID().toString()
    }
    val promise = Promise[Try[O]]
    requests.addOne(
      (requestId, (ZonedDateTime.now().plusSeconds(timeout.toSeconds), promise))
    )
    local ! RequestWrapper(i, requestId, callbackActor)
    promise.future
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
    val entity: EntityRef[RequestWrapper[I, O]] =
      sharding.entityRefFor(TypeKey, entityId)
    entity ! RequestWrapper(i, requestId, ctx.system.ignoreRef)
  }

  // 幂等请求需要用户提供request id
  def callEntity(
      ctx: ActorContext[_],
      entityId: String,
      i: I,
      timeout: Duration = 30.seconds,
      customeRequestId: Option[String] = None
  ): Future[Try[O]] = {
    val requestId = customeRequestId match {
      case Some(id) => id
      case None     => UUID.randomUUID().toString()
    }
    val promise = Promise[Try[O]]
    requests.addOne(
      (requestId, (ZonedDateTime.now().plusSeconds(timeout.toSeconds), promise))
    )
    val entity: EntityRef[RequestWrapper[I, O]] =
      sharding.entityRefFor(TypeKey, entityId)
    entity ! RequestWrapper(i, requestId, callbackActor)
    promise.future
  }

  def createCallbackActor(ctx: ActorContext[_]) = {
    ctx.log.info("creating callback actor for {}", name)
    callbackActor = ctx.spawn(
      Behaviors.receive[ResponseWrapper[O]]((ctx, msg) => {
        requests.get(msg.requestId).map(_._2.trySuccess(msg.response))
        requests.remove(msg.requestId)
        Behaviors.same
      }),
      name + "_callback"
    )
  }

  def startLocal(
      ctx: ActorContext[_],
      f: (ActorContext[RequestWrapper[I, O]], I) => O
  ) = {
    this.synchronized {
      local = ctx.spawn(
        Behaviors.receive[RequestWrapper[I, O]]((ctx, msg) => {
          val result = Try(f(ctx, msg.msg))
          msg.replyTo ! ResponseWrapper(result, msg.requestId)
          Behaviors.same
        }),
        name
      )
    }
  }
  def startCluster(
      ctx: ActorContext[_],
      f: (ActorContext[RequestWrapper[I, O]], String, I) => O
  ) = {
    this.synchronized {
      if (sharding == null) {
        sharding = ClusterSharding(ctx.system)
        sharding.init(
          Entity(TypeKey)(createBehavior =
            entityContext =>
              Behaviors.receive[RequestWrapper[I, O]]((ctx, msg) => {
                // catch error
                val result = Try(f(ctx, entityContext.entityId, msg.msg))
                msg.replyTo ! ResponseWrapper(result, msg.requestId)
                Behaviors.same
              })
          )
        )
      }
    }
  }
}
