package dev.uint.qrserv.util

import kotlin.random.Random

object TokenGenerator {
    private const val DEFAULT_CHARS =
        "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    fun generate(characters: String = DEFAULT_CHARS, length: Int = 32): String {
        if (length <= 0) return ""
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(characters[Random.nextInt(characters.length)])
        }
        return sb.toString()
    }
}
