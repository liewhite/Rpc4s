import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext.Implicits._
import akka.actor.typed.scaladsl.ActorContext
import io.github.liewhite.rpc4s.*

case class Req(i: Int) extends TMessage
case class Res(i: Int) extends TMessage

class Service extends RpcMain {
  override def init(ctx: ActorContext[_]): Unit = {
    val api = Endpoint[Req, Res]("api1")
    api.init(ctx)
    api.startLocal(
      ctx,
      (ctx, req) => {
        println(s"receive local: $req")
        Res(123)
      }
    )
    api
      .call(ctx, Req(1))
      .map(item => println(s"----call response-----\n $item"))
    api.startCluster(
      ctx,
      (ctx, id, req) => {
        println(s"receive cluster: $req")
        Res(req.i)
      }
    )
    api
      .callEntity(ctx, "1", Req(1))
      .map(item => println(s"----call entity response-----\n $item"))
    // todo 测试oneway
    api.tell(ctx, Req(2))
    api.tellEntity(ctx, "1", Req(2))
  }
}

class MySuite extends munit.FunSuite {
  test("ok") {
    // val s = Service()
    // s.start("application_rpc.conf")
  }
}
