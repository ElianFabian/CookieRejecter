package dev.macbuildssys.consentguard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        internal const val ACTION_OPEN_SCROLL_DELAY = "dev.cookierejecter.action.OPEN_SCROLL_DELAY"
    }

    private data class AppLogRow(val title: String, val icon: Drawable, val entries: List<ConsentActivityLog.Entry>)

    private val languageFlags = mapOf(
        "en" to "🇬🇧", "es" to "🇪🇸", "fr" to "🇫🇷", "it" to "🇮🇹",
        "pt" to "🇵🇹", "ja" to "🇯🇵", "ko" to "🇰🇷", "zh-Hans" to "🇨🇳",
        "id" to "🇮🇩", "de" to "🇩🇪", "ru" to "🇷🇺", "uk" to "🇺🇦"
    )

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        ConsentBlockerService.forgetOldHistory(this)

        findViewById<Button>(R.id.openSettingsButton).setOnClickListener {
            /* A few device makers ship without the accessibility settings
               screen as a separate page. The general settings are the next
               best place, and a crash is never right. */
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.settings_missing, Toast.LENGTH_LONG).show()
                }
            }
        }

        bindSettingsRow(R.id.openRulesButton, R.drawable.ic_home_skip, R.string.apps_to_skip) {
            startActivity(Intent(this, RulesActivity::class.java))
        }
        findViewById<Button>(R.id.openLogButton).setOnClickListener { showActivityLog() }
        bindSettingsRow(R.id.openLanguageButton, R.drawable.ic_home_language, R.string.app_language) {
            showLanguages()
        }
        bindSettingsRow(R.id.openScrollDelayButton, R.drawable.ic_home_speed, R.string.review_scroll_delay) {
            showScrollDelays()
        }
        updateScrollDelaySubtitle()
        bindSettingsRow(R.id.openCompletionAlertsButton, R.drawable.ic_completion_bell, R.string.completion_alerts) {
            try {
                startActivity(ConsentReadyNotification.settingsIntent(this))
            } catch (e: ActivityNotFoundException) {
                startActivity(ConsentReadyNotification.appDetailsIntent(this))
            }
        }
        openRequestedSettings(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequestedSettings(intent)
    }

    private fun openRequestedSettings(intent: Intent) {
        if (intent.action != ACTION_OPEN_SCROLL_DELAY) return
        intent.action = null
        showScrollDelays()
    }

    private fun updateScrollDelaySubtitle() {
        findViewById<View>(R.id.openScrollDelayButton).findViewById<TextView>(R.id.homeSettingSubtitle).apply {
            val delay = getString(R.string.review_delay_ms, ReviewSpeed.scrollDelay(this@MainActivity))
            text = "$delay\n${getString(R.string.review_scroll_delay_hint)}"
            visibility = View.VISIBLE
        }
    }

    private fun showScrollDelays() {
        val delays = ReviewSpeed.scrollDelays
        val labels = delays.map { getString(R.string.review_delay_ms, it) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.review_scroll_delay)
            .setSingleChoiceItems(labels, delays.indexOf(ReviewSpeed.scrollDelay(this))) { dialog, index ->
                ReviewSpeed.setScrollDelay(this, delays[index])
                updateScrollDelaySubtitle()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun bindSettingsRow(id: Int, icon: Int, title: Int, action: () -> Unit) {
        findViewById<View>(id).apply {
            findViewById<ImageView>(R.id.homeSettingIcon).setImageResource(icon)
            findViewById<TextView>(R.id.homeSettingTitle).setText(title)
            ViewCompat.setScreenReaderFocusable(this, true)
            setOnClickListener { action() }
        }
    }

    private fun languageName(tag: String): String {
        if (tag.isEmpty()) return getString(R.string.language_system)
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { char -> char.titlecase(locale) }
    }

    private fun showLanguages() {
        val tags = resources.getXml(R.xml.locales_config).use { parser ->
            buildList {
                while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "locale") {
                        parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")?.let(::add)
                    }
                }
            }
        }
        val options = listOf("") + tags
        val names = options.map { tag ->
            val name = languageName(tag)
            languageFlags[tag]?.let { "$it  $name" } ?: name
        }
        val selected = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_language)
            .setSingleChoiceItems(names.toTypedArray(), options.indexOf(selected)) { dialog, index ->
                dialog.dismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(options[index]))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showActivityLog() {
        val groups = ConsentActivityLog.recentByApp()
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.open_log)
            .setPositiveButton(android.R.string.ok, null)
        if (groups.isEmpty()) {
            dialog.setMessage(R.string.log_empty).show()
            return
        }

        val apps = groups.map { (app, entries) -> appLogRow(app, entries) }
        val duplicateNames = apps.groupingBy(AppLogRow::title).eachCount()
        val rows = groups.keys.zip(apps).map { (app, row) ->
            if (duplicateNames.getValue(row.title) > 1) {
                row.copy(title = getString(R.string.log_app_disambiguation, row.title, app))
            } else row
        }
        val adapter = object : ArrayAdapter<AppLogRow>(this, R.layout.item_log_app, rows) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_log_app, parent, false)
                val row = rows[position]
                view.findViewById<ImageView>(R.id.logAppIcon).setImageDrawable(row.icon)
                view.findViewById<TextView>(R.id.logAppTitle).text = row.title
                view.findViewById<TextView>(R.id.logAppCount).text = resources.getQuantityString(
                    R.plurals.log_app_entries, row.entries.size, row.entries.size
                )
                return view
            }
        }
        dialog.setAdapter(adapter) { _, index -> showAppActivity(rows[index]) }
        dialog.setNeutralButton(R.string.clear_log) { _, _ -> ConsentActivityLog.clear() }
        dialog.show()
    }

    private fun showAppActivity(app: AppLogRow) {
        val clock = DateFormat.getTimeFormat(this)
        val adapter = object : ArrayAdapter<ConsentActivityLog.Entry>(this, R.layout.item_log_event, app.entries) {
            override fun areAllItemsEnabled() = false
            override fun isEnabled(position: Int) = false

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: layoutInflater.inflate(R.layout.item_log_event, parent, false)
                val entry = app.entries[position]
                val subject = entry.subject
                val action = row.findViewById<TextView>(R.id.logEventAction)
                val detail = row.findViewById<TextView>(R.id.logEventDetail)
                row.findViewById<TextView>(R.id.logEventTime).text = clock.format(Date(entry.time))
                row.findViewById<TextView>(R.id.logEventTitle).text = subject
                    ?: entry.detail?.let { getString(entry.message, it) }
                    ?: getString(entry.message)
                action.text = if (subject != null) getString(entry.message) else null
                action.visibility = if (subject != null) View.VISIBLE else View.GONE
                val detailText = entry.durationMs?.let { reviewDurationText(it) }
                    ?: entry.detail.takeIf { subject != null }
                detail.text = detailText
                detail.visibility = if (detailText != null) View.VISIBLE else View.GONE
                return row
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(app.title)
            .setAdapter(adapter, null)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.log_all_apps) { _, _ -> showActivityLog() }
            .show()
    }

    private fun appLogRow(app: String, entries: List<ConsentActivityLog.Entry>): AppLogRow = try {
        val info = packageManager.getApplicationInfo(app, 0)
        AppLogRow(info.loadLabel(packageManager).toString().ifBlank { app }, info.loadIcon(packageManager), entries)
    } catch (_: PackageManager.NameNotFoundException) {
        AppLogRow(app, packageManager.defaultActivityIcon, entries)
    }

    override fun onResume() {
        super.onResume()
        statusText.setText(
            if (ConsentBlockerService.isEnabled(this)) R.string.status_enabled else R.string.status_disabled
        )
        resultText.setText(ConsentBlockerService.lastResult)
        findViewById<View>(R.id.openLanguageButton).findViewById<TextView>(R.id.homeSettingSubtitle).apply {
            text = languageName(AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty())
            visibility = View.VISIBLE
        }
    }
}
