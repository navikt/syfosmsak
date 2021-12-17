package no.nav.syfo.metrics

import io.prometheus.client.Counter

const val NAMESPACE = "syfosmsak"

val MELDING_LAGER_I_JOARK: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("melding_lagret_i_joark")
    .help("Meldinger som er lagret i joark")
    .register()

val CASES_CREATED: Counter = Counter.Builder()
    .namespace(NAMESPACE)
    .name("sak_lagret")
    .help("Antall saker som opprettes")
    .register()
