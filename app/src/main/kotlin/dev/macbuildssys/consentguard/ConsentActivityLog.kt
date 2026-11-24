package dev.macbuildssys.consentguard

internal object ConsentActivityLog {
    data class Entry(
        val app: String,
        val time: Long,
        val message: Int,
        val detail: String?,
        val subject: String?,
        val durationMs: Long?
    )

    private const val MAX_ENTRIES = 300
    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun record(app: String, message: Int, detail: String? = null, subject: String? = null, durationMs: Long? = null) {
        if (entries.size == MAX_ENTRIES) entries.removeFirst()
        entries.addLast(Entry(app, System.currentTimeMillis(), message, detail, subject, durationMs))
    }

    @Synchronized
    fun recentByApp(): Map<String, List<Entry>> = entries.reversed().groupBy(Entry::app)

    @Synchronized
    fun clear() = entries.clear()
}
