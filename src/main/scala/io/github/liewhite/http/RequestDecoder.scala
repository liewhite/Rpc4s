package io.github.liewhite.http

import akka.http.scaladsl.model.HttpRequest


// from path, query, header, body, kv
trait RequestDecoder[T] {
    def fromRequest(req: Request): Either[Throwable, T]
}