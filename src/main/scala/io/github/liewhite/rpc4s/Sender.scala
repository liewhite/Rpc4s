package io.github.liewhite.rpc4s

import scala.concurrent.Promise

class Sender{
    // 客户端首先调用send， 待Promise success
    def send(route:String, msg: Array[Byte]): Promise[Unit] = {
        ???
    }
}