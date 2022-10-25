package io.github.liewhite.rpc4s

import io.github.liewhite.json.codec.*
import io.github.liewhite.json.JsonBehavior.*
import scala.util.Try
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure.apply
import scala.util.Failure

abstract class Endpoint[I:Encoder:Decoder,O:Encoder:Decoder](route: String) {
    def handler(i: I): Future[O]
    // 每个endpoint 创建一个单独的channel
    def listen(server: Server): Unit = {
        server.listen(route, args => {
            val result = args.parseToJson.flatMap(_.decode[I]).map(handler(_))
            result match {
                case Left(value) => throw value
                case Right(o) => o.map(_.encode.noSpaces)
            }
        })
    }

    def tell(client: Client, param: I): Future[Unit] = {
        client.tell(route,param.encode.noSpaces)
    }

    def ask(client: Client, param: I): Future[Try[O]] = {
        val result = client.ask(route,param.encode.noSpaces).map(bytes => {
            String(bytes).parseToJson.flatMap(_.decode[Try[String]])
        })
        result.map(item => {
            item match {
                case Left(value) =>  {
                    Failure(value)
                }
                case Right(value) => {
                    value.flatMap(_.parseToJson.flatMap(_.decode[O]).toTry)
                }
            }

        })
    }
}