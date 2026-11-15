package com.netxlab.app.core

import java.security.SecureRandom
import kotlin.math.ln
import kotlin.math.pow

data class PasswordReport(
    val length: Int, val poolSize: Int, val entropyBits: Double, val adjustedBits: Double,
    val rating: String, val findings: List<String>, val crackTime: String
)

object PasswordLab {
    // Small embedded list: this is NOT a complete dictionary. It catches only very common choices.
    val COMMON = setOf(
        "password", "123456", "12345678", "123456789", "qwerty", "abc123", "111111", "123123", "admin", "letmein",
        "welcome", "monkey", "dragon", "football", "iloveyou", "login", "passw0rd", "master", "hello", "freedom",
        "whatever", "qazwsx", "trustno1", "654321", "superman", "1q2w3e4r", "sunshine", "princess", "starwars",
        "wifi", "wifipassword", "internet", "router", "default", "guest", "changeme", "12345", "1234567890",
        "password1", "qwertyuiop", "asdfghjkl", "zxcvbnm", "00000000", "11111111", "987654321", "baseball", "shadow"
    )
    private val WALKS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890", "abcdefghijklmnopqrstuvwxyz", "0987654321")

    private fun deleet(s: String) = s.lowercase().map {
        when (it) { '0' -> 'o'; '1' -> 'l'; '3' -> 'e'; '4' -> 'a'; '5' -> 's'; '7' -> 't'; '@' -> 'a'; '$' -> 's'; '!' -> 'i'; else -> it }
    }.joinToString("")

    fun analyze(pw: String): PasswordReport {
        var pool = 0
        if (pw.any { it.isLowerCase() }) pool += 26
        if (pw.any { it.isUpperCase() }) pool += 26
        if (pw.any { it.isDigit() }) pool += 10
        if (pw.any { !it.isLetterOrDigit() }) pool += 33
        val raw = if (pool == 0) 0.0 else pw.length * ln(pool.toDouble()) / ln(2.0)
        var penalty = 0.0
        val f = mutableListOf<String>()
        val low = pw.lowercase()
        if (pw.length < 8) { f += "Shorter than 8 characters (WPA2/WPA3-Personal require 8–63)."; penalty += 10 }
        if (pw.length > 63) f += "Longer than 63 characters: not valid as a WPA passphrase."
        if (low in COMMON) { f += "Matches a very common password."; penalty += raw }
        else if (deleet(pw) in COMMON) { f += "Common password with simple character substitutions."; penalty += raw * 0.9 }
        else if (COMMON.any { low.contains(it) && it.length >= 5 }) { f += "Contains a very common word or pattern."; penalty += raw * 0.4 }
        if (Regex("(.)\\1{2,}").containsMatchIn(pw)) { f += "Repeated characters (aaa, 111)."; penalty += 8 }
        if (WALKS.any { w -> (0..w.length - 4).any { low.contains(w.substring(it, it + 4)) } }) { f += "Keyboard or alphabet sequence (qwer, 1234, abcd)."; penalty += 12 }
        if (Regex("(19|20)\\d{2}").containsMatchIn(pw)) { f += "Contains a year-like number."; penalty += 6 }
        if (pw.all { it.isDigit() }) { f += "Digits only."; penalty += 6 }
        if (pw.all { it.isLetter() } && pw.length < 12) f += "Letters only."
        val adj = (raw - penalty).coerceAtLeast(0.0)
        val rating = when { adj < 28 -> "Very weak"; adj < 40 -> "Weak"; adj < 60 -> "Fair"; adj < 80 -> "Strong"; else -> "Very strong" }
        if (f.isEmpty()) f += "No common patterns found (against this small built-in list only)."
        return PasswordReport(pw.length, pool, raw, adj, rating, f, crackTime(adj))
    }

    /** Assumes an offline attacker with 10^10 guesses/s (fast GPU rig against a quick hash). Rough estimate only. */
    fun crackTime(bits: Double): String {
        if (bits <= 0) return "instantly"
        val secs = 2.0.pow(bits - 1) / 1e10
        return when {
            secs < 1 -> "under a second"
            secs < 3600 -> "%.0f minutes".format(secs / 60)
            secs < 86400 -> "%.0f hours".format(secs / 3600)
            secs < 86400 * 365 -> "%.0f days".format(secs / 86400)
            secs < 86400 * 365 * 1e6 -> "%.0f years".format(secs / 86400 / 365)
            else -> "billions of years or more"
        }
    }

    fun generate(length: Int, symbols: Boolean): String {
        val r = SecureRandom()
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789" + if (symbols) "!@#%^&*-_+=?" else ""
        return (1..length.coerceIn(8, 63)).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }

    fun generatePassphrase(words: Int = 5): String {
        val r = SecureRandom()
        val syl = listOf("ba", "be", "bi", "bo", "bu", "da", "de", "di", "do", "du", "ka", "ke", "ki", "ko", "ku", "la", "le", "li", "lo", "lu",
            "ma", "me", "mi", "mo", "mu", "na", "ne", "ni", "no", "nu", "ra", "re", "ri", "ro", "ru", "sa", "se", "si", "so", "su", "ta", "te", "ti", "to", "tu", "va", "ve", "vi", "vo", "za")
        return (1..words).joinToString("-") { (1..3).joinToString("") { syl[r.nextInt(syl.size)] } }
    }
}
