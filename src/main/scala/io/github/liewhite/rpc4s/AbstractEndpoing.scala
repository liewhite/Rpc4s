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

abstract class AbstractEndpoint[I: ClassTag: Encoder: Decoder, O: Encoder: Decoder](name: String) {
    var callable: Boolean                         = false
    protected var callbackActor: ActorRef[String] = null

    // 处理中的请求
    protected val requests: scala.collection.concurrent.TrieMap[
      String,
      (ZonedDateTime, Promise[Try[O]])
    ] = scala.collection.concurrent.TrieMap
        .empty[String, (ZonedDateTime, Promise[Try[O]])]

    protected def clientInit(ctx: ActorContext[_]) = {
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

    private def createCallbackActor(ctx: ActorContext[_]) = {
        val actorName = s"${name}_callback_${UUID.randomUUID().toString()}"
        ctx.log.info(s"creating callback actor ${actorName} " )
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
          actorName
        )
    }
}
