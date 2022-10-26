package io.github.liewhite.rpc4s

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.AMQP.BasicProperties
import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Req(i: Int)
case class Res(i: Int)
class Api extends Endpoint[Req, Res]("api") {

    override def handler(i: Req): Future[Res] = {
        throw Exception("xxxxxxxxx")
        Future(Res(i.i))
    }
}
class NotFoundApi extends Endpoint[Req, Res]("api") {

    override def handler(i: Req): Future[Res] = {
        ???
    }
}

class Broad extends Broadcast[Req]("broadcast1") {
    override def handler(i: Req): Future[Unit] = {
        println(s"receive broad1 $i")
        Future(Res(i.i))
    }
}
class Broad2 extends Broadcast[Req]("broadcast2") {
    override def handler(i: Req): Future[Unit] = {
        println(s"receive broad2 cast $i")
        Future(Res(i.i))
    }
}

@main def main = {
    val conn   = Connection("amqp://guest:guest@localhost:5672")
    val server = Server(conn)
    val api    = Api()
    val api404 = NotFoundApi()

    api.listen(server)
    Broad().listen(server, "q1")
    Broad().listen(server, "q2")


    val client = Client(conn)

    Range(0, 100).foreach(i => {
        Future {
            Broad().broadcast(client, Req(i)).onComplete(_ => println(s"broadcast send : $i"))
            api.tell(client, Req(i)).onComplete(_ => println(s"api send ok: $i"))
            api.tell(client, Req(i)).onComplete(_ => println(s"api send ok: $i"))
            // tell 发送的id， 居然带有replyTo
            api.ask(client, Req(i)).onComplete(r => println(s"api receive ok: $r"))

            // api404.tell(client, Req(i))
            // api404.ask(client, Req(i)).onComplete(r => println(s"404 receive : $r"))
        }
        Thread.sleep(1000)
    })
}
