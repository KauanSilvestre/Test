package app.sentinela.core.model

import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Random

/** Identificadores no formato `2026-11-03T21-14-18Z_A7F9`: ordenáveis e seguros como nome de pasta. */
object SessionIds {
    private val FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

    val PATTERN = Regex("""\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}Z_[0-9A-F]{4}""")

    private val defaultRandom: Random by lazy { SecureRandom() }

    fun newId(now: Instant = Instant.now(), random: Random = defaultRandom): String =
        FORMAT.format(now) + "_" + String.format(Locale.ROOT, "%04X", random.nextInt(0x10000))

    fun isValid(id: String): Boolean = PATTERN.matches(id)
}
