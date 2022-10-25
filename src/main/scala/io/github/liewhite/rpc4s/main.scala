package io.github.liewhite.rpc4s

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.AMQP.BasicProperties
import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Req(i: Int)
case class Res(i: Int)
class Api extends Endpoint[Req,Res]("api") {

  override def handler(i: Req): Future[Res] = {
    Future(Res(i.i))
  }
}

@main def main = {
    val conn = Connection("amqp://guest:guest@localhost:5672")
    val server = Server(conn)
    val api = Api()
    api.listen(server)
    val client = Client(conn)
    val tell = api.tell(client,Req(1))
    println(Await.result(tell, 3.second))
    val result = api.ask(client,Req(1))
    println(Await.result(result, 3.second))
}
