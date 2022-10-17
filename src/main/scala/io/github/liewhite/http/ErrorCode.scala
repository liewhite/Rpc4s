package io.github.liewhite.http

case class HttpError[T](code: Int, msg: String, data: T)
