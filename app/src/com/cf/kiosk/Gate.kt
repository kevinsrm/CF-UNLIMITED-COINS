/*
 * lang: Kotlin
 * file: Gate.kt
 * runs on: Android 7.0 (API 24) through Android 16 (API 36)
 * targets: PIN verification for CF Lock, no plaintext key on disk
 */
package com.cf.kiosk

import java.security.MessageDigest

object Gate {

    // four 64-bit limbs, each the matching limb of SHA-256(salt + key) XOR a fixed mask.
    // the key itself is stored nowhere in this package or in any resource.
    private const val SALT = "cf9f2a71"
    private val MASK = longArrayOf(
        0x5A17C3E9B4D02F61UL.toLong(),
        0x9C2D7B41E8F3A056UL.toLong(),
        0x37F9A2C60D15B84EUL.toLong(),
        0xE41B6897C3A2D50FUL.toLong()
    )
    private val VAULT = longArrayOf(
        0x780AE6FC8980BBD1UL.toLong(),
        0xF898D91275483FB8UL.toLong(),
        0x5ABC912A947BEFA7UL.toLong(),
        0xE97FE5B89AC96A4CUL.toLong()
    )

    fun accepts(input: String): Boolean {
        if (input.isEmpty() || input.length > 32) return false
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((SALT + ":" + input).toByteArray(Charsets.UTF_8))
        if (digest.size != 32) return false
        var diff = 0L
        for (i in 0 until 4) {
            var limb = 0L
            for (b in 0 until 8) {
                limb = (limb shl 8) or (digest[i * 8 + b].toLong() and 0xFFL)
            }
            diff = diff or ((limb xor MASK[i]) xor VAULT[i])
        }
        return diff == 0L
    }
}
