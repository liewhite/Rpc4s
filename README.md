# 基于akka的rpc框架

system 启动的时候额外启动一个callback actor, 所有请求 的reply-to都填该actor
该actor控制一个全局的 concurrent map[string, promise], 用来标记所有请求。
发送方法附带一个 timeout 参数， callback actor会定时failure 超时的promise并删除
当响应到来， success 对应的promise并删除
其他actor 发送完请求后，获取promise.future