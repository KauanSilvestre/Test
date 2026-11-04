package app.sentinela.core.storage

import kotlinx.serialization.json.Json

object JsonFormats {
    /** Manifesto legível por humanos: é evidência e pode ser aberto num editor de texto. */
    val manifest: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    /** Uma entrada por linha no journal (JSON Lines). */
    val compact: Json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }
}
