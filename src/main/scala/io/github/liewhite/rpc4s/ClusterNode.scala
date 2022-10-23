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

abstract class ClusterNode(
    configName: String = "conf/config.conf",
    clusterName: String = "RPC"
) {
    val worker = ActorSystem(
      Behaviors
          .setup(ctx => {
              val noderoles = Cluster(ctx.system).selfMember.roles
              clusterEndpoints().foreach(ep => {
                  if (noderoles.contains(ep.role)) {
                      ep.listen(ctx.system)
                  }
              })
              init(ctx.system)
              Behaviors.same
          }),
      clusterName,
      ConfigFactory.parseFile(
        File(configName),
        ConfigParseOptions.defaults().setSyntaxFromFilename(configName)
      ).withFallback(ConfigFactory.parseMap(Map("akka.remote.artery.canonical.hostname" -> sys.env.getOrElse("IP","")).asJava))
    )

    // 用户业务逻辑入口
    def init(system: ActorSystem[_]): Unit

    // 可能会在该node上创建的 cluster endpoint
    def clusterEndpoints(): Vector[ClusterEndpoint[_, _]]

    def shutdown() = {
        worker.terminate()
    }

}
