package io.github.liewhite.rpc4s

import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import io.github.liewhite.json.codec.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.*
import com.typesafe.config.ConfigFactory
import java.io.File
import com.typesafe.config.ConfigParseOptions
import io.github.liewhite.json.JsonBehavior.*
import io.github.liewhite.json.codec.*


case class Req(i: Int) derives Encoder, Decoder
case class Res(i: Int)

class Api extends ClusterEndpoint[Req, Res]("api", "api") {
    def clusterHandle(ctx: ActorSystem[?], entityId: String, i: Req): ResponseWithStatus[Res] = {
        ResponseWithStatus[Res](Res(i.i))
    }

}

// // 集群worker api, 可以从集群任意节点上访问, 无需指定实体id， 所有实体逻辑相同
// class WorkerPool() extends ClusterWorkerPoolEndpoint[Req, Res]("cluster-worker-1", 3, "api") {
//     override def clusterHandle(
//         ctx: ActorContext[_],
//         entityId: String,
//         i: Req
//     ): ResponseWithStatus[Res] = {
//         ctx.log.info(s"worker receive cluster: $i, ${entityId} node: ${ctx.system.address}")
//         ResponseWithStatus(Res(i.i), false)
//     }
// }

// // 本地api, 一般只在节点内访问
// class LocalApi() extends LocalEndpoint[Req, Res]("local-api-1") {
//     override def localHandle(ctx: ActorContext[?], i: Req): ResponseWithStatus[Res] = {
//         ctx.log.info(s"receive local: $i, node: ${ctx.system.address}")
//         if (i.i > 20) {
//             ResponseWithStatus(Res(i.i), true)
//         } else {
//             ResponseWithStatus(Res(i.i), false)
//         }
//     }

// }

// 所有请求都从
class NodeB(config: String) extends RpcMain(config) {
    override def init(system: ActorSystem[?]): Unit = {
        val api = Api()
        Future {
            Thread.sleep(3000)
            Range(1, 3).foreach(i => {
                api.call(system,i.toString(), Req(i))
                    .map(item => println(s"----call entity response-----\n $item"))
                Thread.sleep(1000)
            })
            Range(1, 3).foreach(i => {
                api.callJson(system,i.toString(), Req(i).encode)
                    .map(item => println(s"----call entity response-----\n $item"))
                Thread.sleep(1000)
            })
        }
    }

    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(Api())
    }
}

class NodeA(config: String) extends RpcMain(config) {

    override def init(system: ActorSystem[?]): Unit = {}

    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(Api())
    }
}

class NodeC(config: String) extends RpcMain(config) {
    override def init(system: ActorSystem[_]): Unit = {
        println("-------------node c start----------")
    }
    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(Api())
    }
}
class NodeD(config: String) extends RpcMain(config) {
    override def init(system: ActorSystem[_]): Unit = {
        println("-------------node d start----------")
    }
    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(Api())
    }
}

@main def main = {
    val a = NodeA("conf/a.conf")
    val c = NodeC("conf/c.conf")
    val d = NodeD("conf/d.conf")
    val b = NodeB("conf/b.conf")
}
