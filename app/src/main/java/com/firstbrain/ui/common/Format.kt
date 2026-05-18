package com.firstbrain.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    .withZone(ZoneId.systemDefault())

private val DATE_TIME = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())

fun Instant.formatDate(): String = DATE.format(this)
fun Instant.formatDateTime(): String = DATE_TIME.format(this)

fun Double.formatPercent(): String = String.format(Locale.getDefault(), "%.1f%%", this * 100)
fun Double.formatScore(): String = String.format(Locale.getDefault(), "%.2f", this)
