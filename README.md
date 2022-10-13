# 基于akka的rpc框架

# All-In-One example
```scala
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext.Implicits._
import akka.actor.typed.scaladsl.ActorContext
import io.github.liewhite.rpc4s.*

case class Req(i: Int) extends TMessage
case class Res(i: Int) extends TMessage

class Service extends RpcMain {
  override def init(ctx: ActorContext[_]): Unit = {
    val api = Endpoint[Req, Res]("api1")
    api.init(ctx)
    // create a local endpoint
    api.startLocal(
      ctx,
      (ctx, req) => {
        println(s"receive local: $req")
        Res(123)
      }
    )
    // call local endpoint
    api
      .call(ctx, Req(1))
      .map(item => println(s"----call response-----\n $item"))
    // create a cluster endpoint
    api.startCluster(
      ctx,
      (ctx, id, req) => {
        println(s"receive cluster: $req")
        Res(req.i)
      }
    )
    // call the cluster endpoint
    api
      .callEntity(ctx, "1", Req(1))
      .map(item => println(s"----call entity response-----\n $item"))

    // async call
    api.tell(ctx, Req(2))
    api.tellEntity(ctx, "1", Req(2))
  }
}

@main def main() = {
    val s = Service()
    s.start("application_rpc.conf")
}
```