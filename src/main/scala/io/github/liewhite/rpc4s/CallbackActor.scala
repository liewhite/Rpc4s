import akka.actor.typed._
import akka.actor.typed.scaladsl._
import scala.concurrent.Promise
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import scala.reflect.ClassTag
import scala.concurrent.Future
import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import akka.cluster.sharding.typed.scaladsl.EntityRef
import com.typesafe.config.ConfigFactory
import scala.util.Try
import scala.util.Success

abstract class RpcMain() {
  var worker: ActorSystem[_] = null

  def start(configName: String, clusterName: String = "RPC") = {
    worker = ActorSystem(
      Behaviors
        .setup(ctx => {
          init(ctx)
          Behaviors.empty
        }),
      clusterName,
      ConfigFactory.load(configName)
    )
  }
  def shutdown() = {
    worker.terminate()
  }
  def init(ctx: ActorContext[_]): Unit
}
