package io.github.liewhite.http

import akka.http.scaladsl.model.HttpRequest

case class Request(httpRequest: HttpRequest, kv: Map[String, Any]) {
    def withKv(k: String, v: Any): Request = {
        this.copy(kv = kv.updated(k,v))
    }
}