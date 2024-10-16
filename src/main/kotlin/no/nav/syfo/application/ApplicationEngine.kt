package no.nav.syfo.application

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.routing
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
    embeddedServer(Netty, env.applicationPort) { routing { registerNaisApi(applicationState) } }
