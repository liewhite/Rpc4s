package io.github.liewhite.rpc4s

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.AMQP.BasicProperties
import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.concurrent.Future

case class Req(i: Int)
case class Res(i: Int)
class Api         extends Endpoint[Req, Res]("api")
class NotFoundApi extends Endpoint[Req, Res]("notfound")
class Broad2      extends Broadcast[Req]("broadcast2")

class Broad       extends Broadcast[Req]("broadcast3")

@main def main = {
    val conn   = Connection("amqp://guest:guest@localhost")
    val server = Server(conn)
    val api    = Api()
    val api404 = NotFoundApi()

    api.listen(server, req => {
        println(s"api receive $req")
        Future(Res(req.i))
    })
    val lsn = Broad().listen(
      server,
      "q3",
      req => {
          println(s"q3 receive $req")
          Future(())
      },
    )
    // Broad().listen(
    //   server,
    //   "q2",
    //   req => {

    //       println(s"q2 receive $req")
    //       Future(())
    //   }
    // )

    val client = Client(conn)

    Range(0, 1000).foreach(i => {
        Future {
            // logger.info(s"$i")
            Broad().broadcast(client, Req(i),true).onComplete(status => println(s"broadcast send : $status"))
            // Broad().broadcast(client, Req(i),true).onComplete(status => println(s"broadcast send : $status"))
            api.tell(client, Req(i)).onComplete(_ => println(s"api send ok: $i"))
            // api.tell(client, Req(i)).onComplete(_ => println(s"api send ok: $i"))
            api.ask(client, Req(i)).onComplete(r => println(s"api receive ok: $r"))
            // api404.tell(client, Req(i)).onComplete(r => println(s"404 tell result : $r"))
            api404.ask(client, Req(i)).onComplete(r => println(s"404 ask result : $r"))
        }
        // if(i > 50) {
        //     lsn.shutdown()
        // }
        // Thread.sleep(100)
    })
}
