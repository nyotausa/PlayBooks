package com.goodwy.audiobook.misc

import java.util.concurrent.TimeUnit

fun formatTime(timeMs: Long, durationMs: Long = 0): String {
  val m = timeMs.minutes()
  val s = timeMs.seconds()
  val largerTime = maxOf(timeMs, durationMs)
  val hourDigits = largerTime.hours().digits()
  return if (hourDigits > 0) {
    val h = timeMs.hours()
    "%1\$0${hourDigits}d:%2\$02d:%3\$02d".format(h, m, s)
  } else {
    val minuteDigits = largerTime.minutes().digits()
    val pattern = if (minuteDigits == 0) {
      "%1\$d:%2\$02d"
    } else {
      "%1\$0${minuteDigits}d:%2\$02d"
    }
    pattern.format(m, s)
  }
}

fun formatTimeSeconds(timeMs: Long, durationMs: Long = 0): String {
  val m = timeMs.minutes()
  val s = timeMs.seconds()
  val largerTime = maxOf(timeMs, durationMs)
  val hourDigits = largerTime.hours().digits()
  return if (hourDigits > 0) {
    val h = timeMs.hours()
    "%1\$0${hourDigits}d:%2\$02d:%3\$02d".format(h, m, s)
  } else {
    val minuteDigits = largerTime.minutes().digits()
    val pattern = if (minuteDigits == 0) {
      "%2\$2d"
    } else {
      "60"
    }
    pattern.format(m, s)
  }
}

fun Long.digits(): Int {
  return if (this == 0L) {
    0
  } else {
    toString().length
  }
}

fun Long.hours() = TimeUnit.MILLISECONDS.toHours(this)

fun Long.minutes() = TimeUnit.MILLISECONDS.toMinutes(this) % 60

fun Long.seconds() = TimeUnit.MILLISECONDS.toSeconds(this) % 60
