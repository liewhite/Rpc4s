package io.github.liewhite.rpc4s

import java.time.ZonedDateTime
import java.util.UUID

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

abstract class RpcMain(
    clusterRegistry: ClusterEndpointRegistry,
    configName: String = "conf/config.conf",
    clusterName: String = "RPC"
) {
    val config  = Map()
    val worker = ActorSystem(
      Behaviors
          .setup(ctx => {
              Cluster(ctx.system).selfMember.roles.map(clusterRegistry.nodeInit(_, ctx))
              init(ctx)
              Behaviors.same
          }),
      clusterName,
      ConfigFactory.parseFile(File(configName), ConfigParseOptions.defaults().setSyntaxFromFilename(configName))
    )

    def shutdown() = {
        worker.terminate()
    }
    def init(ctx: ActorContext[_]): Unit
}
