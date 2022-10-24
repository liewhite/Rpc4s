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
import io.circe.Json

case class Req(i: Int) derives Encoder, Decoder
case class Res(i: Int)

class Api(system: ActorSystem[?]) extends ClusterEndpoint[Req, Res](system, "api", "api") {
    def handler(i: Req, entityId: Option[String]): ResponseWithStatus[Res] = {
        logger.info(s"node ${system.address} receive ${i.i}")
        if (i.i == 0) {
            ResponseWithStatus[Res](Res(i.i), status = Exit)
        } else {
            ResponseWithStatus[Res](Res(i.i))
        }
    }

}

// 集群worker api, 可以从集群任意节点上访问, 无需指定实体id， 所有实体逻辑相同
class WorkerPool(system: ActorSystem[?])
    extends ClusterWorkerPoolEndpoint[Req, Res](system, "cluster-worker-1", 30, "api") {
    def handler(i: Req, entityId: Option[String]): ResponseWithStatus[Res] = {
        if (i.i == 0) {
            ResponseWithStatus[Res](Res(i.i), status = Exit)
        } else {
            ResponseWithStatus[Res](Res(i.i))

        }
    }
}

// 本地api, 一般只在节点内访问
class LocalApi(system: ActorSystem[?]) extends LocalEndpoint[Req, Res](system, "local-api-1") {

    def handler(
        i: Req,
        entityId: Option[String]
    ): ResponseWithStatus[Res] = {
        if (i.i == 0) {
            ResponseWithStatus[Res](Res(i.i), status = Exit)
        } else {
            ResponseWithStatus[Res](Res(i.i))

        }
    }

}

class NodeB extends ClusterNode(ClusterConfig(port = 2551, role="b")) {
    def entryPoint(system: ActorSystem[_]): Unit = {
        val api = Api(system)
        // val workers = WorkerPool()
        // val local   = LocalApi()
        Future {
            // val p = new ClusterEndpoint[Json,Json]("api","api") {
            //     def handler(system: ActorSystem[?], i: Json, entityId: Option[String]): ResponseWithStatus[Json] = ???
            // }
            // p.call(system, "id", Req(100).encode).map(i => println("--------------------------------------ssssssssssssssssssssssssss"))

            Thread.sleep(3000)
            // local.listen(system)
            Range(1, 10).foreach(i => {
                api.call(system, "${user_id}", Req(i))
                    .map(item => println(s"----call entity response-----\n $item"))
                // workers
                //     .callWorker(system, Req(i))
                //     .map(item => println(s"----call pool response-----\n $item"))
                // local
                //     .call(system, Req(i))
                //     .map(item => println(s"----call entity response-----\n $item"))

                Thread.sleep(1000)
            })
            // workers
            //     .callWorkerJson(system, Req(0).encode)
            //     .map(item => println(s"----call pool response-----\n $item"))
            // api.callJson(system, 0.toString(), Req(0).encode)
            //     .map(item => println(s"----call entity response-----\n $item"))
            // local
            //     .call(system, Req(0))
            //     .map(item => println(s"----call entity response-----\n $item"))
            Thread.sleep(1000)
        }.onComplete(println(_))
    }
}

class NodeA extends ClusterNode(ClusterConfig(port = 2552, role = "a")) {

    def entryPoint(system: ActorSystem[_]): Unit = {}
}

class NodeC extends ClusterNode(ClusterConfig(port = 2553, role = "c")) {
    def entryPoint(system: ActorSystem[_]): Unit = {}
}

class NodeD extends ClusterNode(ClusterConfig(port = 2554, role = "api")) {
    def entryPoint(system: ActorSystem[_]): Unit = {
        Api(system)
    }
}

@main def main = {
    val a = NodeA().listen()
    val c = NodeC().listen()
    val d = NodeD().listen()
    val b = NodeB().listen()
    Thread.sleep(100000)
}
