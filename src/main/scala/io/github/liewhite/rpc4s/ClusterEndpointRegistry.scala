package io.github.liewhite.rpc4s

import akka.actor.typed.scaladsl.ActorContext
import scala.collection.mutable
import akka.cluster.typed.Cluster

// 所有集群内的sharding都要按role注册， node 按role 进行初始化 entity

class ClusterEndpointRegistry {
    val map = mutable.Map.empty[String, mutable.HashSet[ClusterEndpoint[_, _]]]

    def addEndpoint(ep: ClusterEndpoint[_, _]) = {
        this.synchronized {
            if (!map.contains(ep.role)) {
                map.addOne(ep.role, mutable.HashSet.empty)
            }
            map(ep.role).add(ep)
        }
    }

    def nodeInit(role: String, ctx: ActorContext[_]) = {
        // val cluster = Cluster(ctx.system)
        map.get(role)
            .map(es => {
                es.foreach(item => {
                    ctx.log.info(
                      s"---------init endpoint: ${item.typeKey.name} on node ${ctx.system.address}: ${ctx.system.address} with role: $role"
                    )
                    item.declareEntity(ctx)
                })
            })
    }

}
