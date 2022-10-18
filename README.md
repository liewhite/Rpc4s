# 基于akka的rpc框架

# All-In-One example
## Dependency
```scala
libraryDependencies += "io.github.liewhite" %% "rpc4s" % "0.1.1"
```

## Code
[Example](https://github.com/liewhite/Rpc4s/blob/main/src/main/scala/io/github/liewhite/rpc4s/main.scala)

## 核心概念解释
### Endpoint
endpoint是一个可调用对象， 可以理解为一个函数， 或者一个接口， 一个endpoint有参数和返回值


# 其他
Node创建时自动创建回调队列, ctx.spawn 只有这个时候才能访问， 在endpoint 的 callsite 是不能保证线程环境的
