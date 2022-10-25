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
    def listen(server: Server, handler: I => Future[O], autoDelete: Boolean = false): Listen = {
        server.listen(
          route,
          args => {
              val result = args.parseToJson.flatMap(_.decode[I]).map(handler(_))
              result match {
                  case Left(value) => throw value
                  case Right(o)    => o.map(_.encode.noSpaces)
              }
          },
          None,
          autoDelete
        )
    }

    def tell(
        client: Client,
        param: I,
        mandatory: Boolean = true,
        timeout: Duration = 30.second,
    ): Future[Unit] = {
        client.tell(route, param.encode.noSpaces, "", mandatory, timeout)
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
    def listen(
        server: Server,
        queue: String,
        handler: I => Future[Unit],
        autoDelete: Boolean = false
    ): Listen = {
        server.listen(
          route,
          args => {
              val result = args.parseToJson.flatMap(_.decode[I]).map(handler(_))
              result match {
                  case Left(value) => throw value
                  case Right(o)    => o.map(_.encode.noSpaces)
              }
          },
          Some(queue),
          autoDelete
        )
    }

    // 广播可以选择是否在没有消费者时报错。 方便自动停止生产
    def broadcast(client: Client, param: I, mandatory: Boolean = false): Future[Unit] = {
        client.tell(route, param.encode.noSpaces, "amq.direct", mandatory)
    }
}
