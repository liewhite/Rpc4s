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
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import io.github.liewhite.json.codec.*
import io.github.liewhite.json.JsonBehavior.*
import cats.syntax.validated
import com.rabbitmq.client.Delivery

case class ServerConfig(
    exchange: String,
    route: String,
    queue: Option[String] = None,
    exclusive: Boolean = false,
    autoDelete: Boolean = true,
    durable: Boolean = false
)

case class Listen(ch: Channel, conf: ServerConfig) {
    def shutdown() = {
        logger.info("listen endpoint shutdown manually")
        ch.abort()
    }
}

class Server(
    val connection: Connection
) {
    // 严格顺序处理， 用户如果有异步需求就单独开Future进行处理
    def listen(
        conf: ServerConfig,
        callback: String => Future[String]
    ): Listen = {
        // 如果不指定queue就与route一致
        val queue = conf.queue match {
            case None        => conf.route
            case Some(value) => value
        }
        val ch = connection.connection.createChannel()
        ch.basicQos(1)
        ch.confirmSelect()
        // 如果是广播模式， 则每个endpoint都要使用单独的队列
        ch.queueDeclare(
          queue,
          conf.durable,
          conf.exclusive,
          conf.autoDelete,
          Map.empty[String, String].asJava
        )
        // if (queue != conf.route) {
        ch.queueBind(queue, conf.exchange, conf.route)
        // }else {

        // }
        ch.basicConsume(
          queue,
          (_, msg) => {
              logger.info(s"route [${conf.route}] queue [$queue] body:  ${String(msg.getBody())}")
              val replyTo = msg.getProperties().getReplyTo()
              // 这个是server的delivery tag, 不要和client的id搞混了
              val deliveryTag = msg.getEnvelope().getDeliveryTag()
              Try(callback(String(msg.getBody()))) match {
                  case f @ Failure(exception) => {
                      val fail: Try[String] = Failure(exception)
                      if (replyTo != null) {
                          reply(ch, msg, replyTo, fail.encode.noSpaces.getBytes())
                      }
                      ch.basicAck(deliveryTag, false)
                  }
                  case Success(value) => {
                      value.onComplete(value => {
                          if (replyTo != null) {
                              reply(ch, msg, replyTo, value.encode.noSpaces.getBytes())
                          }
                          ch.basicAck(deliveryTag, false)
                      })
                  }
              }

          },
          reason => {
              logger.error(s"endpoint unexpect terminated : $reason")
          }
        )
        Listen(ch, conf)
    }
    def shutdownListen(listen: Listen) = {
        listen.ch.abort()
    }

    private def reply(ch: Channel, msg: Delivery, replyTo: String, body: Array[Byte]) = {
        val id =
            msg.getProperties()
                .getHeaders()
                .get("deliveryTag")
                .asInstanceOf[Long]
        ch.basicPublish(
          "",
          replyTo,
          false,
          BasicProperties()
              .builder()
              .headers(Map("deliveryTag" -> id).asJava)
              .build(),
          body
        )
    }

}
