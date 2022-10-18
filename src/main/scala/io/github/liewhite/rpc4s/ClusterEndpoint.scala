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
import cats.syntax.validated

abstract class ClusterEndpoint[I: ClassTag: Encoder: Decoder, O: Encoder: Decoder](
    name: String,
    val role: String
) extends AbstractEndpoint(name) {
    val typeKey = EntityTypeKey[String](name)

    def declareEntity(system: ActorSystem[_]) = {
        logger.info(s"sharding init ${typeKey} on ${system.address}")
        ClusterSharding(system).init(
          Entity(typeKey)(createBehavior =
              entityContext =>
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
                                        clusterHandle(
                                          system,
                                          entityContext.entityId,
                                          i
                                        )
                                      )
                                  )
                              result match {
                                  case Left(e) => {
                                      ctx.log.error(
                                        s"failed parse request for $typeKey id: ${entityContext.entityId} , ${request.msg}"
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
                                              status match {
                                                  case Exit => {
                                                      ctx.log.info(
                                                        s"entity receive exit: $name id: ${entityContext.entityId}"
                                                      )
                                                      Behaviors.stopped
                                                  }
                                                  case Same => {
                                                      request.replyTo ! ResponseWrapper(
                                                        Try(res).encode
                                                      ).toMsgString()
                                                      Behaviors.same

                                                  }
                                              }
                                          }
                                      }

                                  }
                              }
                          }
                  })
          ).withRole(role)
        )
    }

    def tell(
        system: ActorSystem[_],
        entityId: String,
        i: I
    ): Unit = {
        tellJson(system, entityId, i.encode)
    }

    def tellJson(
        system: ActorSystem[_],
        entityId: String,
        i: Json
    ): Unit = {
        val entity: EntityRef[String] =
            ClusterSharding(system).entityRefFor(typeKey, entityId)
        entity ! RequestWrapper(i, system.ignoreRef).toMsgString(system)
    }

    def callJson(
        system: ActorSystem[_],
        entityId: String,
        i: Json,
        timeout: Duration = 30.seconds
    ): Future[O] = {
        implicit val syst: ActorSystem[_] = system
        implicit val t: Timeout           = timeout.toSeconds.second

        val entity: EntityRef[String] = ClusterSharding(system).entityRefFor(typeKey, entityId)
        val result = entity.ask[String](ref => RequestWrapper(i, ref).toMsgString(system))(t)
        val o = result.map(r => {
            ResponseWrapper.fromMsgString(system, r) match {
                case Left(value) =>
                    throw Exception(s"failed parse response ${typeKey}-${entityId}: ${r}")
                case Right(value) =>
                    value.response.decode[Try[O]] match {
                        case Left(err) => throw err
                        case Right(ok) => {
                            ok match {
                                case Failure(exception) => throw exception
                                case Success(value) => value
                            }
                        }
                    }
            }
        })
        o
    }

    // 幂等请求需要用户提供request id
    def call(
        system: ActorSystem[_],
        entityId: String,
        i: I,
        timeout: Duration = 30.seconds
    ): Future[O] = {
        callJson(system, entityId, i.encode, timeout)
    }
    def listen(system: ActorSystem[_]): Unit = {
        declareEntity(system)
    }

    def clusterHandle(
        ctx: ActorSystem[_],
        entityId: String,
        i: I
    ): ResponseWithStatus[O]

}
