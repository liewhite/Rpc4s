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
import scala.collection.concurrent.TrieMap

case class Req(i: Int)
case class Res(i: Int)

class ClusterApi() extends ClusterEndpoint[Req, Res]("cluster-api-1", "api") {
    override def clusterHandle(
        ctx: ActorContext[_],
        entityId: String,
        i: Req
    ): Res = {
        println(s"receive cluster: $i, node: ${ctx.system.address}")
        Res(i.i)
    }
}

object Registry extends ClusterEndpointRegistry {
    addEndpoint(ClusterApi())
}

class NodeB(config: String) extends RpcMain(config, Registry) {
    override def init(ctx: ActorContext[_]): Unit = {
        val api = ClusterApi()
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

class NodeA(config: String) extends RpcMain(config, Registry) {
    override def init(ctx: ActorContext[_]): Unit = {
        println("-------------node a start----------")
    }
}

class NodeC(config: String) extends RpcMain(config, Registry) {
    override def init(ctx: ActorContext[_]): Unit = {
        println("-------------node c start----------")
    }
}
class NodeD(config: String) extends RpcMain(config, Registry) {
    override def init(ctx: ActorContext[_]): Unit = {
        println("-------------node d start----------")
    }
}

@main def main = {
    val a = NodeA("application_rpc_a.conf")
    val c = NodeC("application_rpc_c.conf")
    val d = NodeD("application_rpc_d.conf")
    val b = NodeB("application_rpc_b.conf")
}
