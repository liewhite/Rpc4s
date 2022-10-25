package io.github.liewhite.rpc4s

import com.rabbitmq.client.ConnectionFactory

class Connection(val uri: String) {
    val factory = ConnectionFactory()
    factory.setUri(uri)
    factory.setAutomaticRecoveryEnabled(true)
    val connection = factory.newConnection()
}