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
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._

import scala.collection.concurrent.TrieMap
import io.circe.Json
import io.github.liewhite.json.JsonBehavior.*
import io.github.liewhite.json.codec.*

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.unmarshalling.Unmarshal.apply
import akka.http.scaladsl.unmarshalling.Unmarshal
import scala.concurrent.Await
import akka.http.impl.engine.parsing.HttpRequestParser

case class Req(i: Int) derives Encoder, Decoder
case class Res(i: Int) derives Encoder, Decoder

// 集群api, 可以从集群内任意节点访问,需要指定id访问对应的实体
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

// 集群worker api, 可以从集群任意节点上访问, 无需指定实体id， 所有实体逻辑相同
class WorkerPool() extends ClusterWorkerPoolEndpoint[Req, Res]("cluster-worker-1", 3, "api") {
    override def clusterHandle(
        ctx: ActorContext[_],
        entityId: String,
        i: Req
    ): ResponseWithStatus[Res] = {
        ctx.log.info(s"worker receive cluster: $i, ${entityId} node: ${ctx.system.address}")
        ResponseWithStatus(Res(i.i), false)
    }
}

// 本地api, 一般只在节点内访问
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

// 所有请求都从
class NodeB(config: String) extends RpcMain(config) {
    override def init(ctx: ActorContext[_]): Unit = {
        val api = WorkerPool().clientInit(ctx)

        Future {
            Thread.sleep(3000)
            Range(1, 3).foreach(i => {
                api.callWorker(ctx, Req(i))
                    .map(item => println(s"----call entity response-----\n $item"))
                Thread.sleep(1000)
            })
            Range(1, 3).foreach(i => {
                api.callWorkerJson(ctx, Req(i).encode)
                    .map(item => println(s"----call entity response-----\n $item"))
                Thread.sleep(1000)
            })
        }
    }

    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(WorkerPool())
    }
}

class NodeA(config: String) extends RpcMain(config) {
    override def init(ctx: ActorContext[_]): Unit = {
        println("-------------node a start----------")
    }
    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(WorkerPool())
    }
}

class NodeC(config: String) extends RpcMain(config) {
    override def init(ctx: ActorContext[_]): Unit = {
        println("-------------node c start----------")
    }
    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(WorkerPool())
    }
}
class NodeD(config: String) extends RpcMain(config) {
    override def init(ctx: ActorContext[_]): Unit = {
        println("-------------node d start----------")
    }
    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]] = {
        Vector(WorkerPool())
    }
}

@main def main = {
    val a = NodeA("conf/a.conf")
    val c = NodeC("conf/c.conf")
    val d = NodeD("conf/d.conf")
    val b = NodeB("conf/b.conf")
}
