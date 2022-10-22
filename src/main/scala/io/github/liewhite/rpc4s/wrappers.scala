package io.github.liewhite.rpc4s

import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import io.github.liewhite.json.JsonBehavior.*
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import scala.util.Try
import io.github.liewhite.json.codec.*

case class RequestWrapper(
    msg: Json,
    replyTo: ActorRef[String],
) {
    def toMsgString(system: ActorSystem[_]): String = {
        val actorRefResolver = ActorRefResolver(system)
        val replyToStr       = actorRefResolver.toSerializationFormat(replyTo)
        Json
            .fromJsonObject(
              JsonObject(
                "msg"     -> msg,
                "replyTo" -> Json.fromString(replyToStr)
              )
            )
            .noSpaces
    }
}
object RequestWrapper {
    def fromMsgString(
        system: ActorSystem[_],
        msg: String
    ): Either[Throwable, RequestWrapper] = {
        parse(msg) match {
            case Left(value) => Left(value)
            case Right(value) => {
                val params = for {
                    obj    <- value.asObject
                    ref    <- obj("replyTo")
                    refStr <- ref.asString
                    msg    <- obj("msg")
                } yield (msg, refStr)
                params match {
                    case None => Left(Exception(s"parse request error: $msg"))
                    case Some(ps) => {
                        val actorRefResolver = ActorRefResolver(system)
                        val ref =
                            actorRefResolver.resolveActorRef[String](ps._2)
                        Right(RequestWrapper(ps._1, ref))
                    }
                }
            }
        }
    }
}

case class ResponseWrapper(
    response: Json
) {
    def toMsgString(): String = {
        val result = Json
            .fromJsonObject(
              JsonObject(
                "msg" -> response.encode
              )
            )
            .noSpaces
        result
    }
}

object ResponseWrapper {
    def fromMsgString(
        system: ActorSystem[_],
        msg: String
    ): Either[Throwable, ResponseWrapper] = {
        val result = parse(msg) match {
            case Left(value) => Left(value)
            case Right(value) => {
                val params = for {
                    obj <- value.asObject
                    msg <- obj("msg")
                } yield msg
                params match {
                    case None => Left(Exception(s"parse request error: $msg"))
                    case Some(ps) => {
                        Right(ResponseWrapper(ps))
                    }
                }
            }
        }
        val ss = result.map(_.toMsgString())
        result
    }
}
