package com.netxlab.app.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized fun log(msg: String) {
        lines.addLast("${fmt.format(Date())}  $msg")
        while (lines.size > 500) lines.removeFirst()
    }
    @Synchronized fun all(): List<String> = lines.toList()
    @Synchronized fun clear() = lines.clear()
}
