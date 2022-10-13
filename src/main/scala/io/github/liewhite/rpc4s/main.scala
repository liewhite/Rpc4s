package io.github.liewhite.rpc4s
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext.Implicits._
import akka.actor.typed.scaladsl.ActorContext
import io.github.liewhite.rpc4s.*
import io.github.liewhite.json.codec.*
import akka.actor.typed.ActorRef
import scala.concurrent.Future

case class Req(i: Int)
case class Res(i: Int)

class Api1 extends Endpoint[Req, Res]("api1") {
    override def localHandle(
        ctx: ActorContext[_],
        i: Req
    ): Res = {
        println(s"receive local: $i")
        Res(i.i)
    }

    override def clusterHandle(
        ctx: ActorContext[_],
        entityId: String,
        i: Req
    ): Res = {
        println(s"receive cluster: $i, node: ${ctx.system.address}")
        Res(i.i)
    }
}

class NodeA extends RpcMain {
    override def init(ctx: ActorContext[_]): Unit = {
        val api = Api1()
        api.declareEntity(
          ctx
        )
    }
}

class NodeB extends RpcMain {
    override def init(ctx: ActorContext[_]): Unit = {
        val api = Api1()
        api.callEntity(ctx, "1", Req(1))
            .map(item => println(s"----call entity response-----\n $item"))
        api.tellEntity(ctx, "1", Req(2))
        Future {
            Range(3, 100).foreach(i => {
                api.callEntity(ctx, i.toString(), Req(i))
                    .map(item => println(s"----call entity response-----\n $item"))
                Thread.sleep(1000)
            })
        }
    }
}

class NodeC extends RpcMain {
    override def init(ctx: ActorContext[_]): Unit = {
      val api = Api1()
      api.startCluster(ctx)
      println("-------------node c start----------")
    }
}

@main def main = {
    val a = NodeA()
    a.start("application_rpc_a.conf")

    val b = NodeB()
    b.start("application_rpc_b.conf")

    val c = NodeC()
    c.start("application_rpc_c.conf")

}
