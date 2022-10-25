package io.github.liewhite.rpc4s

import scala.collection.mutable.Queue
import scala.concurrent.Promise
import com.rabbitmq.client.Channel
import java.util.concurrent.ConcurrentLinkedQueue
import com.rabbitmq.client.Return
import com.rabbitmq.client.AMQP.BasicProperties
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.concurrent.Future
import java.time.ZonedDateTime
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global

class RpcErr(msg: String) extends Exception(msg)

class NoRouteErr(msg: String) extends RpcErr(s"no route err: $msg")
class NackErr(msg: String)    extends RpcErr(s"nack by broker err: $msg")
class TimeoutErr(msg: String) extends RpcErr(s"request timeout : $msg")

class BrokerResponse(msg: Array[Byte])
enum RequestType {
    case Tell
    case Ask
}

case class Request(
    rtype: RequestType,
    route: String,
    sendResult: Promise[Unit],
    response: Promise[Array[Byte]],
    expireAt: ZonedDateTime
)

// 发送时指定类型
class Client(
    val connection: Connection
) {
    var returnedMsg: Return = null

    // 所有已发送且未确认的请求 deliveryTag, Promise
    val requests = scala.collection.mutable.Map.empty[Long, Request]
    Future {
        while (true) {
            val now = ZonedDateTime.now()
            requests.synchronized {
                requests.filterInPlace((_, req) => {
                    if (req.expireAt.isBefore(now)) {
                        req.sendResult.tryFailure(TimeoutErr(req.route))
                        false
                    } else {
                        true
                    }
                })
            }
            Thread.sleep(5000)
        }
    }

    val ch = connection.connection.createChannel()

    ch.confirmSelect()

    ch.addReturnListener(msg => {
        returnedMsg = msg
    })

    ch.addConfirmListener(
      (deliveryTag, multiple) => {
          requests.synchronized {
              if (returnedMsg != null) {
                  returnedMsg == null
                  if (multiple) {
                      requests.filterInPlace((tag, req) => {
                          if (tag <= deliveryTag) {
                              req.sendResult.failure(NoRouteErr(req.route))
                              false
                          } else {
                              true
                          }
                      })
                  } else {
                      requests
                          .get(deliveryTag)
                          .map(req => req.sendResult.failure(NoRouteErr(req.route)))
                  }
              } else {
                  if (multiple) {
                      requests.filterInPlace((tag, req) => {
                          if (tag <= deliveryTag) {
                              req.sendResult.success(())
                              false
                          } else {
                              true
                          }
                      })
                  } else {
                      requests.get(deliveryTag).map(_.sendResult.success(()))
                  }
              }

          }
      },
      (deliveryTag, multiple) => {
          requests.synchronized {
              if (multiple) {
                  requests.filterInPlace((tag, req) => {
                      if (tag <= deliveryTag) {
                          req.sendResult.failure(NackErr(req.route))
                          false
                      } else {
                          true
                      }
                  })
              } else {
                  requests.get(deliveryTag).map(req => req.sendResult.failure(NackErr(req.route)))
              }

          }
      }
    )

    ch.basicConsume(
      "amq.rabbitmq.reply-to",
      true,
      (tag, msg) => {
          val id = msg.getProperties().getHeaders().get("deliveryTag").asInstanceOf[Long]
          requests.remove(id).map(_.response.success(msg.getBody()))
      },
      (reason) => {
          logger.error(s"callback consumer shutdown: $reason")
          System.exit(-1)
      }
    )

    // 不论tell还是ask， 都要确保消息已到达队列
    def tell(
        route: String,
        msg: String,
        timeout: Duration = 30.second
    ): Future[Unit] = {
        val deliveryTag = ch.getNextPublishSeqNo()
        val rtype       = RequestType.Tell
        val sendResult  = Promise[Unit]
        val response    = Promise[Array[Byte]]
        val req = Request(
          rtype,
          route,
          sendResult,
          response,
          ZonedDateTime.now().plusSeconds(timeout.toSeconds)
        )
        requests.synchronized {
            requests.addOne((deliveryTag, req))
        }
        val props = BasicProperties
            .Builder()
            .headers(Map("deliveryTag" -> deliveryTag).asJava)

        ch.basicPublish(
          "",
          route,
          true,
          props.build(),
          msg.getBytes()
        )
        sendResult.future
    }

    def ask(
        route: String,
        msg: String,
        timeout: Duration = 30.second
    ): Future[Array[Byte]] = {
        val deliveryTag = ch.getNextPublishSeqNo()
        val rtype       = RequestType.Ask
        val sendResult  = Promise[Unit]
        val response    = Promise[Array[Byte]]
        val req = Request(
          rtype,
          route,
          sendResult,
          response,
          ZonedDateTime.now().plusSeconds(timeout.toSeconds)
        )
        requests.synchronized {
            requests.addOne((deliveryTag, req))
        }
        val props = BasicProperties
            .Builder()
            .headers(Map("deliveryTag" -> deliveryTag).asJava)
            .replyTo("amq.rabbitmq.reply-to")
        ch.basicPublish(
          "",
          route,
          true,
          props.build(),
          msg.getBytes()
        )
        sendResult.future.flatMap(ok => response.future)
    }

}
