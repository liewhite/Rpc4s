package io.github.liewhite.rpc4s

import zio.json.*
import com.devsisters.shardcake.EntityType
import zio.Dequeue
import zio.*
import scala.util.*
import com.devsisters.shardcake.Replier
import com.devsisters.shardcake.Sharding
import zio.URIO
import zio.json.internal.Write
import java.io.PrintWriter
import java.io.StringWriter
import zio.json.internal.RetractReader
import zio.json.ast.Json
import cats.syntax.validated

case class Timeout() extends Exception("timeout")
given JsonDecoder[Replier[String]] = JsonDecoder.derived[Replier[String]]
given JsonEncoder[Replier[String]] = JsonEncoder.derived[Replier[String]]

given JsonEncoder[Throwable] = new JsonEncoder[Throwable] {

  override def unsafeEncode(
      a: Throwable,
      indent: Option[Int],
      out: Write
  ): Unit = {
    val brief = a.toString()
    val stackWriter = new PrintWriter(new StringWriter())
    val stack = a.printStackTrace(stackWriter)
    out.write(
      Map[String, String](
        "cause" -> brief,
        "stack" -> stackWriter.toString()
      ).toJson
    )
  }

}
given JsonDecoder[Throwable] = JsonDecoder
  .map[String, String]
  .map(item => {
    val e = new Exception(item("cause"))
    e
  })

private[rpc4s] case class Request(sender: Replier[String], req: Json)
    derives JsonDecoder,
      JsonEncoder
// private[rpc4s] case class Response[Out](res: In) derives JsonDecoder

class Endpoint[In: JsonDecoder: JsonEncoder, Out: JsonEncoder: JsonDecoder](
    val name: String,
    val keepAlive: zio.Duration = 30.second
) {

  def registerSharding(
      handler: (id: String, in: In) => Task[Out]
  ): ZIO[Scope & Sharding, Nothing, Unit] = {
    for {
      result <- Sharding.registerEntity(entityType, genHandler(handler))
    } yield result
  }

  def tell(in: In, id: String = "singleton"): ZIO[Sharding, Nothing, Unit] = {
    for {
      messenger <- Sharding.messenger[String](entityType)
      res <- messenger.sendDiscard(id)(getRequest(Replier(""), in))
    } yield res
  }

  def ask(in: In, id: String = "singleton"): ZIO[Sharding, Throwable, Out] = {
    val r = for {
      messenger <- Sharding.messenger[String](entityType)
      res <- messenger.send[String](id)(r => getRequest(r, in))
    } yield {
      val out =
        res.fromJson[Either[Throwable, Out]].left.map(Exception(_)).flatten
      ZIO.fromEither(out)
    }
    r.flatten
  }

  private def getRequest(replier: Replier[String], in: In): String = {
    Request(replier, in.toJsonAST.toOption.get).toJson
  }

  private def entityType = new EntityType[String](name) {}

  protected def genHandler(
      f: (id: String, in: In) => Task[Out]
  ): (id: String, q: Dequeue[String]) => RIO[Sharding, Nothing] = {
    (id: String, q: Dequeue[String]) =>
      {
        (for {
          str <- q.take.timeout(this.keepAlive)
          out <- {
            str match {
              case None => {
                for {
                  _ <- ZIO.logInfo(
                    s"entity timeout and exit... ${entityType.name} $id"
                  )
                  _ <- ZIO.interrupt
                } yield ()
              }
              case Some(s) => {
                val req = s
                  .fromJson[Request]
                  .left
                  .map(Exception(_).fillInStackTrace())
                req match {
                  case Left(e) => {
                    ZIO.logWarning(s"bad request: $str, err: $e")
                  }
                  case Right(value) => {

                    val result = value.req
                      .as[In]
                      .left
                      .map(Exception(_))
                      .flatMap(p => {
                        Try {
                          f(id, p)
                        }.toEither
                      })
                    val payload = result.fold(
                      e => {
                        val payload: Either[Throwable, Out] = Left(e)
                        ZIO.succeed(payload.toJson)
                      },
                      v => {
                        v.map(item => {
                          val payload: Either[Throwable, Out] = Right(item)
                          payload.toJson
                        }).catchAll(e => {
                          val payload: Either[Throwable, Out] = Left(e)
                          ZIO.succeed(payload.toJson)
                        })
                      }
                    )
                    val sender = value.sender
                    if (sender.id == "") {
                      ZIO.unit
                    } else {
                      for {
                        v <- payload
                        _ <- sender.reply(v)
                      } yield ()
                    }
                  }
                }
              }
            }
          }
        } yield {
          out
        }).forever
      }
  }
}
