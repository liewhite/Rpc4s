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
    seedNodes: Vector[String] = Vector("localhost:2551")
) {
    def toConfig: Config = {
        ConfigFactory.parseMap(
          Map(
            "akka.cluster.seed-nodes"               -> seedNodes.map(i => "akka://RPC@" + i).asJava,
            "akka.remote.artery.canonical.port"     -> port,
            "akka.remote.artery.canonical.hostname" -> hostname,
            "akka.cluster.roles"                    -> Vector(role).asJava
          ).asJava
        )
    }
}
abstract class ClusterNode(
    val config: ClusterConfig
) {
    var worker: ActorSystem[?] = null
    def listen() = {
        val conf = defaultConfig.withFallback(config.toConfig)
        logger.info(s"node config: $conf")
        worker = ActorSystem(
          Behaviors
              .setup(ctx => {
                  entryPoint(ctx.system)
                  Behaviors.same
              }),
          "RPC",
          conf
        )
    }

    def defaultConfig: Config = {
        ConfigFactory.parseMap(
          Map(
            // "akka.cluster.jmx.multi-mbeans-in-same-jvm" -> "on",
            "akka.actor.provider"                       -> "cluster",
            "akka.remote.artery.bind.hostname"     -> "0.0.0.0",
            "akka.cluster.downing-provider-class" -> "akka.cluster.sbr.SplitBrainResolverProvider"
          ).asJava
        )
    }

    // 用户业务逻辑入口
    def entryPoint(system: ActorSystem[_]): Unit

    def shutdown() = {
        if (worker != null) {
            worker.terminate()
        }
    }

}
