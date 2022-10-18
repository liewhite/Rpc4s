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
import akka.actor.ProviderSelection.Local

case class Req(i: Int) derives Encoder, Decoder
case class Res(i: Int)

class Api extends ClusterEndpoint[Req, Res]("api", "api") {
    def handler(ctx: ActorSystem[?], i: Req, entityId: Option[String]): ResponseWithStatus[Res] = {
        if(i.i == 0) {
            ResponseWithStatus[Res](Res(i.i), status = Exit)
        }else {
            ResponseWithStatus[Res](Res(i.i))

        }
    }

}

// 集群worker api, 可以从集群任意节点上访问, 无需指定实体id， 所有实体逻辑相同
class WorkerPool() extends ClusterWorkerPoolEndpoint[Req, Res]("cluster-worker-1", 30, "api") {
    def handler(ctx: ActorSystem[?], i: Req, entityId: Option[String]): ResponseWithStatus[Res] = {
        if(i.i == 0) {
            ResponseWithStatus[Res](Res(i.i), status = Exit)
        }else {
            ResponseWithStatus[Res](Res(i.i))

        }
    }
}

// 本地api, 一般只在节点内访问
class LocalApi() extends LocalEndpoint[Req, Res]("local-api-1") {

    def handler(
        system: ActorSystem[?],
        i: Req,
        entityId: Option[String]
    ): ResponseWithStatus[Res] = {
        if(i.i == 0) {
            ResponseWithStatus[Res](Res(i.i), status = Exit)
        }else {
            ResponseWithStatus[Res](Res(i.i))

        }
    }

}

// 所有请求都从
class NodeB(config: String) extends RpcMain(config) {
    override def init(system: ActorSystem[?]): Unit = {
        val api     = Api()
        val workers = WorkerPool()
        val local   = LocalApi()
        Future {
            Thread.sleep(3000)
            local.listen(system)
            Range(1, 10).foreach(i => {
                workers
                    .callWorker(system, Req(i))
                    .map(item => println(s"----call pool response-----\n $item"))
                api.call(system, i.toString(), Req(i))
                    .map(item => println(s"----call entity response-----\n $item"))
                local
                    .call(system, Req(i))
                    .map(item => println(s"----call entity response-----\n $item"))

                Thread.sleep(1000)
            })
            workers
                .callWorkerJson(system, Req(0).encode)
                .map(item => println(s"----call pool response-----\n $item"))
            api.callJson(system, 0.toString(), Req(0).encode)
                .map(item => println(s"----call entity response-----\n $item"))
            local
                .call(system, Req(0))
                .map(item => println(s"----call entity response-----\n $item"))
            Thread.sleep(1000)
        }
    }

    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(Api(), WorkerPool())
    }
}

class NodeA(config: String) extends RpcMain(config) {

    override def init(system: ActorSystem[?]): Unit = {}

    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(Api(), WorkerPool())
    }
}

class NodeC(config: String) extends RpcMain(config) {
    override def init(system: ActorSystem[_]): Unit = {
        println("-------------node c start----------")
    }
    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(Api(), WorkerPool())
    }
}
class NodeD(config: String) extends RpcMain(config) {
    override def init(system: ActorSystem[_]): Unit = {
        println("-------------node d start----------")
    }
    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(Api(), WorkerPool())
    }
}

@main def main = {
    val a = NodeA("conf/a.conf")
    val c = NodeC("conf/c.conf")
    val d = NodeD("conf/d.conf")
    val b = NodeB("conf/b.conf")
}
