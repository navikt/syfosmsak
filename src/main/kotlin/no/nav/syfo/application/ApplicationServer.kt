package no.nav.syfo.application

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.util.concurrent.TimeUnit

class ApplicationServer(
    private val applicationServer:
        EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>,
    private val applicationState: ApplicationState,
) {

    init {
        Runtime.getRuntime()
            .addShutdownHook(
                Thread {
                    this.applicationState.ready = false
                    this.applicationServer.stop(
                        TimeUnit.SECONDS.toMillis(10),
                        TimeUnit.SECONDS.toMillis(10)
                    )
                },
            )
    }

    fun start() {
        applicationServer.start(true)
    }
}
