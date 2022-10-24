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
import akka.actor.typed.scaladsl.AskPattern._

// Local endpoint 创建出来大概率是要调用的
abstract class LocalEndpoint[I: ClassTag: Encoder: Decoder, O: Encoder: Decoder](
    system: ActorSystem[?],
    name: String
) extends AbstractEndpoint[I, O](system,name) {
    private var local: ActorRef[String] = null
    private var init: Boolean           = false

    def tellJson(
        i: Json
    ): Unit = {
        local ! RequestWrapper(i, system.ignoreRef).toMsgString(system)
    }

    def tell(
        i: I
    ): Unit = {
        tellJson(i.encode)
    }

    def call(
        i: I,
        timeout: Duration = 30.seconds
    ): Future[O] = {
        callJson(i.encode, timeout)
    }

    // 幂等请求需要用户提供request id
    def callJson(
        i: Json,
        timeout: Duration = 30.seconds,
    ): Future[O] = {
        implicit val syst: ActorSystem[_] = system
        implicit val t: Timeout           = timeout.toSeconds.second

        val result = local
            .ask[String](ref => RequestWrapper(i, ref).toMsgString(system))(t, system.scheduler)
        responseFromStringFuture(result, name)
    }

    def listen(
    ): Unit = {
        this.synchronized {
            if (!init) {
                local = system.systemActorOf(handlerBehavior(), name)
                init = true
            }
        }
    }

}
