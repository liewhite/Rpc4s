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

class Server(
    val connection: Connection
) {

    def listen(queue: String, callback: String => Future[String]) = {
        val ch = connection.connection.createChannel()
        ch.basicQos(1)
        ch.confirmSelect()
        ch.queueDeclare(queue, true, false, false, Map.empty[String, String].asJava)
        ch.basicConsume(
          queue,
          (_, msg) => {
              val result  = callback(String(msg.getBody()))
              val replyTo = msg.getProperties().getReplyTo()
              val msgId   = msg.getProperties().getCorrelationId()
              val deliveryTag =msg.getEnvelope().getDeliveryTag()

              result.onComplete(value => {
                  if (replyTo != null) {
                      ch.basicPublish(
                        "",
                        replyTo,
                        false,
                        BasicProperties()
                            .builder()
                            .correlationId(msgId)
                            .headers(Map("deliveryTag" -> deliveryTag).asJava)
                            .build(),
                        value.encode.noSpaces.getBytes()
                      )
                  }
                  ch.basicAck(deliveryTag, false)
              })
          },
          reason => {
              logger.error(s"endpoint unexpect terminated : $reason")
          }
        )
    }

}
