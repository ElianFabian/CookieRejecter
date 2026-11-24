package dev.macbuildssys.consentguard

import android.content.Context

internal object ReviewSpeed {
    private const val KEY_SCROLL_DELAY = "review_scroll_delay_ms"
    const val DEFAULT_DELAY_MS = 100
    val scrollDelays = listOf(1, 5, 10, 50, DEFAULT_DELAY_MS, 200, 300, 400, 500)

    private fun preferences(context: Context) =
        context.getSharedPreferences(ConsentBlockerService.PREFS_NAME, Context.MODE_PRIVATE)

    fun scrollDelay(context: Context): Int = preferences(context)
        .getInt(KEY_SCROLL_DELAY, DEFAULT_DELAY_MS)
        .let { if (it == 320) 300 else it }
        .takeIf { it in scrollDelays } ?: DEFAULT_DELAY_MS

    fun setScrollDelay(context: Context, delayMs: Int) {
        require(delayMs in scrollDelays)
        preferences(context).edit().putInt(KEY_SCROLL_DELAY, delayMs).apply()
    }
}
