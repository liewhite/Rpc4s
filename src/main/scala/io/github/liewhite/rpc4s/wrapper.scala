package io.github.liewhite.rpc4s

import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import io.github.liewhite.json.JsonBehavior.*
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import scala.util.Try
import io.github.liewhite.json.codec.*

case class RequestWrapper[I](
    msg: I,
    requestId: String,
    replyTo: ActorRef[String]
) {
  def toMsgString(ctx: ActorContext[_])(using Encoder[I]): String = {
    val body = msg.encode
    val actorRefResolver = ActorRefResolver(ctx.system)
    val replyToStr = actorRefResolver.toSerializationFormat(replyTo)
    Json
      .fromJsonObject(
        JsonObject(
          "msg" -> body,
          "replyTo" -> Json.fromString(replyToStr),
          "requestId" -> Json.fromString(requestId)
        )
      )
      .noSpaces
  }
}
object RequestWrapper {
  def fromMsgString[I: Decoder](
      ctx: ActorContext[_],
      msg: String
  ): Either[Throwable, RequestWrapper[I]] = {
    parse(msg) match {
      case Left(value) => Left(value)
      case Right(value) => {
        val params = for {
          obj <- value.asObject
          ref <- obj("replyTo")
          refStr <- ref.asString
          id <- obj("requestId")
          idStr <- id.asString
          msg <- obj("msg")
        } yield (msg, idStr, refStr)
        params match {
          case None => Left(Exception(s"parse request error: $msg"))
          case Some(ps) => {
            val actorRefResolver = ActorRefResolver(ctx.system)
            val ref =
              actorRefResolver.resolveActorRef[String](ps._3)
            ps._1.decode[I].map(RequestWrapper(_, ps._2, ref))
          }
        }
      }
    }
  }
}

case class ResponseWrapper[O](
    response: Try[O],
    requestId: String
) {
  def toMsgString(ctx: ActorContext[_])(using Encoder[O]): String = {
    val body = response.encode
    Json
      .fromJsonObject(
        JsonObject(
          "msg" -> body,
          "requestId" -> Json.fromString(requestId)
        )
      )
      .noSpaces
  }
}

object ResponseWrapper {
  def fromMsgString[O: Decoder](
      ctx: ActorContext[_],
      msg: String
  ): Either[Throwable, ResponseWrapper[O]] = {
    parse(msg) match {
      case Left(value) => Left(value)
      case Right(value) => {
        val params = for {
          obj <- value.asObject
          id <- obj("requestId")
          idStr <- id.asString
          msg <- obj("msg")
        } yield (msg, idStr)
        params match {
          case None => Left(Exception(s"parse request error: $msg"))
          case Some(ps) => {
            ps._1.decode[Try[O]] match {
              case Left(value)  => Left(value)
              case Right(value) => Right(ResponseWrapper(value, ps._2))
            }
          }
        }
      }
    }
  }
}
