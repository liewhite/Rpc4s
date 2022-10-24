package io.github.liewhite.rpc4s

import scala.reflect.ClassTag
import scala.util.Try
import java.time.ZonedDateTime
import scala.concurrent.Promise
import scala.concurrent.Future
import scala.concurrent.duration._
import java.util.UUID
import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.cluster.sharding.typed.scaladsl.*
import scala.concurrent.ExecutionContext.Implicits.*
import scala.concurrent.duration.*
import io.github.liewhite.json.codec.*
import io.github.liewhite.json.JsonBehavior.*
import scala.util.*

sealed trait EndpointStatus
case object Same extends EndpointStatus
case object Exit extends EndpointStatus

case class ResponseWithStatus[T](res: T, status: EndpointStatus = Same)

abstract class AbstractEndpoint[I: ClassTag: Encoder: Decoder, O: Encoder: Decoder](
    val system: ActorSystem[?],
    val name: String,
) {
    def handler(
        i: I,
        entityId: Option[String]
    ): ResponseWithStatus[O]

    protected def handlerBehavior() = {
        Behaviors.receive[String]((ctx, msg) => {
            val req = RequestWrapper.fromMsgString(system, msg)
            req match
                case Left(value) => {
                    ctx.log.error(s"failed parse request msg: $value")
                    Behaviors.same
                }
                case Right(request) => {
                    val result = request.msg
                        .decode[I]
                        .map(i =>
                            Try(
                              handler(
                                i,
                                None
                              )
                            )
                        )
                    result match {
                        case Left(e) => {
                            ctx.log.error(
                              s"failed parse request for local endpoint: $name , ${request.msg}"
                            )
                            Behaviors.same
                        }
                        case Right(o) => {
                            o match {
                                case Failure(exception) => {
                                    ctx.log.error(s"exec handler result error $exception")
                                    Behaviors.same
                                }
                                case Success(ResponseWithStatus(res, status)) => {
                                    request.replyTo ! ResponseWrapper(
                                      Try(res).encode
                                    ).toMsgString()
                                    status match {
                                        case Exit => {
                                            ctx.log.info(
                                              s"endpoint exit: $name"
                                            )
                                            Behaviors.stopped
                                        }
                                        case Same => {
                                            Behaviors.same

                                        }
                                    }
                                }
                            }

                        }
                    }
                }
        })
    }
    def responseFromStringFuture(
        result: Future[String],
        endpointName: String
    ): Future[O] = {
        result.map(r => {
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

    }
}
