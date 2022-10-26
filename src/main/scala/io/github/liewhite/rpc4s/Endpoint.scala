package io.github.liewhite.rpc4s

import io.github.liewhite.json.codec.*
import io.github.liewhite.json.JsonBehavior.*
import scala.util.Try
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.concurrent.duration.*

class Endpoint[I: Encoder: Decoder, O: Encoder: Decoder](var route: String) {
    // 每个endpoint 创建一个单独的channel
    def listen(server: Server, handler: I => Future[O]): Unit = {
        server.listen(
          route,
          args => {
              val result = args.parseToJson.flatMap(_.decode[I]).map(handler(_))
              result match {
                  case Left(value) => throw value
                  case Right(o)    => o.map(_.encode.noSpaces)
              }
          }
        )
    }

    def tell(
        client: Client,
        param: I,
        async: Boolean = false,
        timeout: Duration = 30.second
    ): Future[Unit] = {
        client.tell(route, param.encode.noSpaces, "", !async, timeout)
    }

    def ask(client: Client, param: I, timeout: Duration = 30.second): Future[O] = {
        val result = client
            .ask(route, param.encode.noSpaces, timeout)
            .map(bytes => {
                String(bytes).parseToJson.flatMap(_.decode[Try[String]])
            })
        result.map(item => {
            item match {
                case Left(value) => {
                    throw value
                }
                case Right(value) => {
                    value match {
                        case Failure(exception) => throw exception
                        case Success(value) =>
                            value.parseToJson.flatMap(_.decode[O]) match {
                                case Left(value)  => throw value
                                case Right(value) => value
                            }
                    }
                }
            }
        })
    }
}
abstract class Broadcast[I: Encoder: Decoder](route: String) {
    def listen(server: Server, queue: String,handler: I => Future[Unit]): Unit = {
        server.listen(
          route,
          args => {
              val result = args.parseToJson.flatMap(_.decode[I]).map(handler(_))
              result match {
                  case Left(value) => throw value
                  case Right(o)    => o.map(_.encode.noSpaces)
              }
          },
          Some(queue)
        )
    }

    def broadcast(client: Client, param: I): Future[Unit] = {
        client.tell(route, param.encode.noSpaces, "amq.direct", false)
    }
}
