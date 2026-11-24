package dev.macbuildssys.consentguard

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit

internal fun Context.reviewDurationText(durationMs: Long): String {
    val seconds = durationMs / 1000
    val formatter = MeasureFormat.getInstance(resources.configuration.locales[0], MeasureFormat.FormatWidth.SHORT)
    val remainder = Measure(seconds % 60, MeasureUnit.SECOND)
    val duration = if (seconds < 60) {
        formatter.formatMeasures(remainder)
    } else {
        formatter.formatMeasures(Measure(seconds / 60, MeasureUnit.MINUTE), remainder)
    }
    return getString(R.string.review_completed_in, duration)
}
