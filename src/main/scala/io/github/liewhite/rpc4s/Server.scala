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
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import io.github.liewhite.json.codec.*
import io.github.liewhite.json.JsonBehavior.*
import cats.syntax.validated
import com.rabbitmq.client.Delivery

class Server(
    val connection: Connection
) {
    // 严格顺序处理， 用户如果有异步需求就单独开Future进行处理
    def listen(
        route: String,
        callback: String => Future[String],
        defaultQueue: Option[String] = None
    ) = {
        val queue = defaultQueue match {
            case None        => route
            case Some(value) => value
        }
        val ch = connection.connection.createChannel()
        ch.basicQos(1)
        ch.confirmSelect()
        // 如果是广播模式， 则每个endpoint都要使用单独的队列
        ch.queueDeclare(queue, true, false, false, Map.empty[String, String].asJava)
        if (queue != route) {
            ch.queueBind(queue, "amq.direct", route)
        }
        ch.basicConsume(
          queue,
          (_, msg) => {
              val replyTo = msg.getProperties().getReplyTo()
              // 这个是server的delivery tag, 不要和client的id搞混了
              val deliveryTag = msg.getEnvelope().getDeliveryTag()
              Try(callback(String(msg.getBody()))) match {
                  case f @ Failure(exception) => {
                      if (replyTo != null) {
                          reply(ch, msg, replyTo, f.encode.noSpaces.getBytes())
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
    }
    def reply(ch: Channel, msg: Delivery, replyTo: String, body: Array[Byte]) = {
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
