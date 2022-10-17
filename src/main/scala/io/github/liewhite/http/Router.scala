package io.github.liewhite.http

import scala.collection.mutable
import akka.http.scaladsl.model.HttpRequest
import scala.concurrent.Future
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.Uri
import io.github.liewhite.json.codec.Encoder
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Route

case class RouterTree(
    node: Router[_, _,_],
    children: mutable.Map[String, RouterTree]
)

// 从root向下依次注册
// root保存了所有的router, 所以在添加子节点时必须向上传递, 这样root能收集到所有的router信息， 并生成api文档
// I 为交集， O为并集，即入参必须满足各级router的要求， 但是出参只可能是其中一个, 比如账户验证失败直接就返回403了
// todo schema typeclass
// I 要单独实现typeclass： fromRequest
// O和E只需要json
// 返回值定义一个case class， 设置隐式转换， 可以从多个形式转换到该class
abstract class Router[I, O, E]( // 参数，响应，异常(多半是union type)
    val path: String,
    val method: HttpMethod // 不支持多方法
) {
    var children                        = mutable.HashMap.empty[String, Router[_, _, _]]
    var parent: Option[Router[_, _, _]] = None

    given [T](using encoder: Encoder[T]): Conversion[T, HttpResponse] =
        new Conversion[T, HttpResponse] {
            def apply(x: T): HttpResponse = {
                HttpResponse(200, Seq.empty, encoder.encode(x).noSpaces)
            }
        }
    def register[I2, O2,E2](router: Router[I2, O2,E2]) = {
        router.parent = Some(this)
        if (children.contains(path)) {
            throw Exception(s"path ${router.path} already register on $path")
        }
        children.update(path, router)
    }
    def toDirective(): Directive[_]
}


// 继承given可用
class S extends Router[Int,Int,Int]("",HttpMethods.GET) {

  override def toDirective(): Directive[?] = ???

  val s: HttpResponse = 123
}

class D extends Directive[Unit] {

  override def tapply(f: Unit => Route): Route = ???


}