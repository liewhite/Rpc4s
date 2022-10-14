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
import io.circe.Json
import io.github.liewhite.json.JsonBehavior.*
import io.github.liewhite.json.codec.*

case class Req(i: Int) derives Encoder, Decoder
case class Res(i: Int) derives Encoder, Decoder

class ClusterApi() extends ClusterEndpoint[Req, Res]("cluster-api-1", "api") {
    override def clusterHandle(
        ctx: ActorContext[_],
        entityId: String,
        i: Req
    ): ResponseWithStatus[Res] = {
        ctx.log.info(s"receive cluster: $i, node: ${ctx.system.address}")
        if (entityId.toInt % 2 == 0) {
            ResponseWithStatus(Res(i.i), true)
        } else {
            ResponseWithStatus(Res(i.i), false)
        }
    }
}

class LocalApi() extends LocalEndpoint[Req, Res]("local-api-1") {
    override def localHandle(ctx: ActorContext[?], i: Req): ResponseWithStatus[Res] = {
        ctx.log.info(s"receive local: $i, node: ${ctx.system.address}")
        if (i.i > 20) {
           ResponseWithStatus(Res(i.i), true)
        } else {
            ResponseWithStatus(Res(i.i), false)
        }
    }

}

class NodeB(config: String) extends RpcMain(config) {
    override def init(ctx: ActorContext[_]): Unit = {
        val localApi = LocalApi().startLocal(ctx)

        val api = ClusterApi()
        api.call(ctx, "1", Req(1))
            .map(item => println(s"----call entity response-----\n $item"))
        api.tellJson(ctx, "2", Req(2).encode)
        Future {
            Thread.sleep(3000)
            Range(3, 100).foreach(i => {
                api.callJson(ctx, i.toString(), Req(i).encode)
                    .map(item => println(s"----call entity response-----\n $item"))
                val localResult = localApi
                    .call(ctx, Req(i))
                    .map(item => {
                        ctx.log.info(s"local response : ${item}")
                    })
                Thread.sleep(1000)
            })
            Range(3, 100).foreach(i => {
                api.callJson(ctx, i.toString(), Req(i).encode)
                    .map(item => println(s"----call entity response-----\n $item"))
                Thread.sleep(1000)
            })
        }
    }

    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(ClusterApi())
    }
}

class NodeA(config: String) extends RpcMain(config) {
    override def init(ctx: ActorContext[_]): Unit = {
        println("-------------node a start----------")
    }
    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(ClusterApi())
    }
}

class NodeC(config: String) extends RpcMain(config) {
    override def init(ctx: ActorContext[_]): Unit = {
        println("-------------node c start----------")
    }
    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(ClusterApi())
    }
}
class NodeD(config: String) extends RpcMain(config) {
    override def init(ctx: ActorContext[_]): Unit = {
        println("-------------node d start----------")
    }
    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(ClusterApi())
    }
}

@main def main = {
    val a = NodeA("conf/a.conf")
    val c = NodeC("conf/c.conf")
    val d = NodeD("conf/d.conf")
    val b = NodeB("conf/b.conf")
}
