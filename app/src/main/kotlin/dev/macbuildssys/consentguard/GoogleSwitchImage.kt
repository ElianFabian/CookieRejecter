package dev.macbuildssys.consentguard

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/** Reads only pixels inside a recognized Google consent switch. */
internal object GoogleSwitchImage {
    enum class State { ON, OFF, UNKNOWN }

    fun read(image: Bitmap, bounds: Rect): State {
        if (!canSample(image, bounds)) return State.UNKNOWN

        // The white thumb is left when off and right when on. Require the
        // opposite half to match the provider's track as a second signal.
        val left = sample(image, bounds, 0.27)
        val right = sample(image, bounds, 0.73)
        return when {
            left == Track.BLUE && right == Track.WHITE -> State.ON
            left == Track.WHITE && right == Track.GRAY -> State.OFF
            else -> State.UNKNOWN
        }
    }

    fun describeSamples(image: Bitmap, bounds: Rect): String {
        if (!canSample(image, bounds)) return "outside screenshot or unexpected switch dimensions"
        return "left=${sample(image, bounds, 0.27)}, right=${sample(image, bounds, 0.73)}"
    }

    private fun canSample(image: Bitmap, bounds: Rect): Boolean =
        bounds.left >= 0 && bounds.top >= 0 && bounds.right <= image.width && bounds.bottom <= image.height &&
            bounds.height() >= 12 && bounds.width() >= bounds.height() * 1.55 &&
            bounds.width() <= bounds.height() * 2.3

    private enum class Track { BLUE, GRAY, WHITE, UNKNOWN }

    private fun sample(image: Bitmap, bounds: Rect, fraction: Double): Track {
        val samples = IntArray(Track.entries.size)
        for (vertical in -1..1) {
            for (horizontal in -1..1) {
                val x = bounds.left + (bounds.width() * (fraction + horizontal * 0.05)).toInt()
                val y = bounds.centerY() + (bounds.height() * vertical * 0.10).toInt()
                val pixel = image.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                val track = when {
                    minOf(red, green, blue) >= 225 -> Track.WHITE
                    blue >= 135 && blue - red >= 65 && blue - green >= 30 -> Track.BLUE
                    red in 75..180 && maxOf(red, green, blue) - minOf(red, green, blue) <= 30 -> Track.GRAY
                    else -> Track.UNKNOWN
                }
                samples[track.ordinal]++
            }
        }
        return Track.entries.firstOrNull { samples[it.ordinal] >= 8 } ?: Track.UNKNOWN
    }
}
