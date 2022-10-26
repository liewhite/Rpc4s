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
    response: Option[Promise[Array[Byte]]],
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
            logger.info(s"clean expire requests, pending: ${requests.size}")
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
        val tag = msg.getProperties().getHeaders().get("deliveryTag").asInstanceOf[Long]
        // val threadId = Thread.currentThread().getId()
        logger.warn(
          s"no route message returned: $tag ${msg.getExchange()} -> ${msg.getRoutingKey()}"
        )
        returnedMsg = msg
    })

    ch.addConfirmListener(
      // acked消息， 如果returnedMsg != null, 则noroute
      (deliveryTag, multiple) => {

          if (returnedMsg != null) {
              logger.warn(
                s"ack with returned msg:  ${returnedMsg.getExchange()} - ${returnedMsg
                        .getRoutingKey()} id ${returnedMsg.getProperties().getHeaders().get("deliveryTag")}, current tag: ${deliveryTag}"
              )
              returnedMsg = null
              nack(deliveryTag, multiple, req => NoRouteErr(req.toString()))
          } else {
              ack(deliveryTag, multiple)
          }
      },
      (deliveryTag, multiple) => {
          nack(deliveryTag, multiple, req => NackErr(req.toString()))
      }
    )

    ch.basicConsume(
      "amq.rabbitmq.reply-to",
      true,
      (tag, msg) => {
          val id = msg.getProperties().getHeaders().get("deliveryTag").asInstanceOf[Long]
          requests.synchronized {
              if (!requests.contains(id)) {
                  logger.warn(s"id $id not found in request $requests")
              }
              requests.remove(id).map(_.response.map(_.success(msg.getBody())))
          }
      },
      (reason) => {
          logger.error(s"callback consumer shutdown: $reason")
          System.exit(-1)
      }
    )

    def tell(
        route: String,
        msg: String,
        exchange: String = "",
        mandatory: Boolean = true, // 广播无需确认，可能没有监听队列
        timeout: Duration = 30.second
    ): Future[Unit] = {
        ch.synchronized {
            val deliveryTag = ch.getNextPublishSeqNo()
            val rtype       = RequestType.Tell
            val sendResult  = Promise[Unit]
            val req = Request(
              rtype,
              route,
              sendResult,
              None,
              ZonedDateTime.now().plusSeconds(timeout.toSeconds)
            )
            requests.synchronized {
                requests.addOne((deliveryTag, req))
            }
            val props = BasicProperties
                .Builder()
                .headers(Map("deliveryTag" -> deliveryTag).asJava)

            ch.basicPublish(
              exchange,
              route,
              mandatory,
              props.build(),
              msg.getBytes()
            )
            sendResult.future
        }
    }

    def ask(
        route: String,
        msg: String,
        timeout: Duration = 30.second
    ): Future[Array[Byte]] = {
        ch.synchronized {
            val deliveryTag = ch.getNextPublishSeqNo()
            val rtype       = RequestType.Ask
            val sendResult  = Promise[Unit]
            val response    = Promise[Array[Byte]]
            val req = Request(
              rtype,
              route,
              sendResult,
              Some(response),
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

    def ack(deliveryTag: Long, multiple: Boolean) = {
        requests.synchronized {
            if (multiple) {
                requests.filterInPlace((tag, req) => {
                    if (tag <= deliveryTag) {
                        // 可能会有已经ack过的ask还存在， 所以要用try
                        req.sendResult.trySuccess(())

                        req.response.nonEmpty
                    } else {
                        true
                    }
                })
            } else {
                // ack的请求如果无需response则可以删除
                requests.get(deliveryTag).map(_.sendResult.trySuccess(()))
                if (requests(deliveryTag).response.isEmpty) {
                    requests.remove(deliveryTag)
                }
            }

        }
    }
    def nack(deliveryTag: Long, multiple: Boolean, err: Request => RpcErr) = {
        requests.synchronized {
            if (multiple) {
                requests.filterInPlace((tag, req) => {
                    if (tag <= deliveryTag) {
                        if (!req.sendResult.isCompleted) {
                            logger.warn(
                              s"message nacked $tag / $deliveryTag $multiple ,${err(req).getMessage()}"
                            )
                            req.sendResult.failure(err(req))
                            false
                        } else {
                            // tag 满足条件， 但是其实已经ack过了。
                            true
                        }
                    } else {
                        true
                    }
                })
            } else {
                // nack的请求直接remove
                requests
                    .remove(deliveryTag)
                    .map(req => {
                        logger.warn(
                          s"message nacked $deliveryTag $multiple  ,${err(req).getMessage()}"
                        )
                        req.sendResult.failure(err(req))
                    })
            }

        }
    }

}
