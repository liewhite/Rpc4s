package io.github.liewhite.rpc4s

import com.devsisters.shardcake._
import com.devsisters.shardcake
import com.devsisters.shardcake.interfaces._
import zio._
import scala.util.*
import zio.json.ast.Json
import zio.json.*

object ShardManagerApp extends ZIOAppDefault {
  val seedNode = Server.run.provide(
    ZLayer.succeed(ManagerConfig.default),
    ZLayer.succeed(GrpcConfig.default),
    PodsHealth.local, // just ping a pod to see if it's alive
    GrpcPods.live, // use gRPC protocol
    Storage.memory, // store data in memory
    ShardManager.live // shard manager logic
  )
  val ep = Endpoint[Int,String]("xxx",1.second)

  val program =
    for {
      seed <- seedNode.fork
      _ <- ZIO.sleep(2.second)
      _ <- ep.registerSharding((id:String, in) => {
        // throw Exception("i'm dead ....")
        ZIO.fail(Exception("zio fail"))
      })
      _ <- Sharding.registerScoped
      r <- ep.ask(1)
      _ <- Console.printLine("result: " + r)
      _ <- ZIO.sleep(3.second)
      r <- ep.ask(3)
      _ <- Console.printLine("next result: " + r)
    } yield ()

  def run: Task[Unit] =
    ZIO
      .scoped(program)
      .provide(
        ZLayer.succeed(shardcake.Config.default),
        ZLayer.succeed(GrpcConfig.default),
        KryoSerialization.live,
        // Serialization.javaSerialization, // use java serialization for messages
        Storage.memory, // store data in memory
        ShardManagerClient.liveWithSttp, // client to communicate with the Shard Manager
        GrpcPods.live, // use gRPC protocol
        GrpcShardingService.live, // expose gRPC service
        Sharding.live // sharding logic
      )
}