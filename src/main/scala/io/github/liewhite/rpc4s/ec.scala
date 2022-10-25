package io.github.liewhite.rpc4s

import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())