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
    name: String
) extends AbstractEndpoint(name) {
    private var local: ActorRef[String] = null

    def tellJson(
        system: ActorSystem[_],
        i: Json
    ): Unit = {
        local ! RequestWrapper(i, system.ignoreRef).toMsgString(system)
    }

    def tell(
        system: ActorSystem[_],
        i: I
    ): Unit = {
        tellJson(system, i.encode)
    }

    def call(
        system: ActorSystem[_],
        i: I,
        timeout: Duration = 30.seconds
    ): Future[O] = {
        callJson(system, i.encode, timeout)
    }

    // 幂等请求需要用户提供request id
    def callJson(
        system: ActorSystem[_],
        i: Json,
        timeout: Duration = 30.seconds,
        customeRequestId: Option[String] = None
    ): Future[O] = {
        implicit val syst: ActorSystem[_] = system
        implicit val t: Timeout           = timeout.toSeconds.second

        val result = local
            .ask[String](ref => RequestWrapper(i, ref).toMsgString(system))(t, system.scheduler)
        val o = result.map(r => {
            ResponseWrapper.fromMsgString(system, r) match {
                case Left(value) =>
                    throw Exception(s"failed parse local actor response ${name}: ${r}")
                case Right(value) =>
                    value.response.decode[Try[O]] match {
                        case Left(err) => throw err
                        case Right(ok) => {
                            ok match {
                                case Failure(exception) => throw exception
                                case Success(value)     => value
                            }
                        }
                    }
            }
        })
        o
    }

    // def startLocal(
    //     ctx: ActorContext[_]
    // ): this.type = {
    //     val system = ctx.system
    //     local = ctx.spawn(
    //       Behaviors.receive[String]((ctx, msg) => {
    //           val req = RequestWrapper.fromMsgString(system, msg)
    //           req match {
    //               case Left(value) => {
    //                   system.log.error(s"failed parse request msg: $value")
    //                   Behaviors.same
    //               }
    //               case Right(request) => {
    //                   // catch error
    //                   val result = Try(
    //                     localHandle(
    //                       system,
    //                       request.msg
    //                     )
    //                   )
    //                   result match {
    //                       case Failure(exception) => {
    //                           system.log.error(s"exec handler result error $exception")
    //                           Behaviors.same
    //                       }
    //                       case Success(ResponseWithStatus(res, exit)) => {
    //                           if (exit) {
    //                               system.log.info(
    //                                 s"local actor exit: $name"
    //                               )
    //                               Behaviors.stopped
    //                           } else {
    //                               request.replyTo ! ResponseWrapper[O](
    //                                 Try(res),
    //                                 request.requestId
    //                               ).toMsgString(system)
    //                               Behaviors.same

    //                           }
    //                       }
    //                   }
    //               }

    //           }
    //       }),
    //       name
    //     )
    //     this
    // }

    def localHandle(system: ActorSystem[_], i: I): ResponseWithStatus[O]
}
