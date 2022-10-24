package io.github.liewhite.rpc4s

import java.time.ZonedDateTime
import java.util.UUID

import scala.jdk.CollectionConverters.*
import scala.concurrent.{Promise, Future}
import scala.reflect.ClassTag
import scala.util.Try
import scala.util.Success
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.cluster.typed.Cluster
import akka.cluster.sharding.typed.scaladsl.*

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import java.io.File
import com.typesafe.config.Config

case class ClusterConfig(
    hostname: String = "localhost",
    port: Int = 2551,
    role: String = "master",
    seedNodes: Vector[String] = Vector("akka://RPC@localhost:2551")
) {
    def toConfig: Config = {
        ConfigFactory.parseMap(
          Map(
            "akka.cluster.seed-nodes"               -> seedNodes.asJava,
            "akka.remote.artery.canonical.port"     -> port,
            "akka.remote.artery.canonical.hostname" -> hostname,
            "akka.cluster.roles"                    -> Vector(role).asJava
          ).asJava
        )
    }
}
abstract class ClusterNode(
    val config: ClusterConfig = ClusterConfig()
) {
    var worker: ActorSystem[?] = null

    def listen() = {
        val conf = defaultConfig.withFallback(config.toConfig)
        logger.info(s"node config: $conf")
        worker = ActorSystem(
          Behaviors
              .setup(ctx => {
                  declareEndpoints(ctx.system)
                  init(ctx.system)
                  Behaviors.same
              }),
          "RPC",
          conf
        )
    }

    // 初始化endpoints
    def declareEndpoints(system: ActorSystem[?], declaredRole: Set[String] = Set.empty): Unit = {
        val cluster = Cluster(system)
        // val noderoles = cluster.selfMember.roles
        if (!declaredRole.contains(config.role)) {
            serveEndpoints().map(ep => {
                logger.info(s"declare serve endpoint on node ${cluster.selfMember.uniqueAddress}")
                ep.init(system)
            })
            involveEndpoints().map(ep => {
                logger.info(s"declare involve endpoint on node ${cluster.selfMember.uniqueAddress}")
                ep.init(system)
                ep.typeKey.name
            })
            involveNode().foreach(node => {
                logger.info(s"declare dependent node's endpoints: ${config.role} -> ${node.config.role} ")
                node.declareEndpoints(system, declaredRole + config.role)
            })
        }
    }

    def defaultConfig: Config = {
        ConfigFactory.parseMap(
          Map(
            "akka.cluster.jmx.multi-mbeans-in-same-jvm" -> "on",
            "akka.actor.provider"                       -> "cluster",
            "akka.cluster.downing-provider-class" -> "akka.cluster.sbr.SplitBrainResolverProvider"
          ).asJava
        )
    }

    // 用户业务逻辑入口
    def init(system: ActorSystem[_]): Unit

    def serveEndpoints(): Vector[ClusterEndpoint[_, _]] = Vector.empty
    // 可能会调用哪些endpoint
    def involveEndpoints(): Vector[ClusterEndpoint[_, _]] = Vector.empty
    // 或者直接声明依赖服务
    // 不允许出现环
    def involveNode(): Vector[ClusterNode] = Vector.empty

    def shutdown() = {
        worker.terminate()
    }

}
