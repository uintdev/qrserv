package dev.uint.qrserv.util

import java.security.SecureRandom

object TokenGenerator {
    private const val DEFAULT_CHARS =
        "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private val random = SecureRandom()

    fun generate(characters: String = DEFAULT_CHARS, length: Int = 32): String {
        if (length <= 0) return ""
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(characters[random.nextInt(characters.length)])
        }
        return sb.toString()
    }
}
