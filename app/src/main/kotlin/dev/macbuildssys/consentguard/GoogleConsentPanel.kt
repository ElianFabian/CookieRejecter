package dev.macbuildssys.consentguard

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Reads supported Google Funding Choices forms from the dialog's own labels. */
internal class GoogleConsentPanel private constructor(
    val kind: Kind,
    private val labels: Labels,
    val viewport: Rect,
    val body: AccessibilityNodeInfo,
    val controls: List<Control>,
    val visible: List<Control>,
    val top: Boolean,
    val vendorLink: Action?,
    val back: Action?,
    val confirm: Action
) {
    enum class Kind { PURPOSES, VENDORS }
    data class Action(val node: AccessibilityNodeInfo, val label: String)
    data class BannerAction(val node: AccessibilityNodeInfo, val reject: Boolean)
    data class Control(
        val index: Int,
        val name: String?,
        val label: String,
        val node: AccessibilityNodeInfo,
        val bounds: Rect
    )

    fun sameForm(other: GoogleConsentPanel) = labels == other.labels
    fun samePanel(other: GoogleConsentPanel) = kind == other.kind && sameForm(other)

    private data class BannerLabels(
        val manage: Set<String>,
        val consent: Set<String>,
        val reject: Set<String>,
        val anchor: String
    )

    private data class Labels(
        val purposesHeader: String,
        val purposesTitle: String,
        val vendorsHeader: String,
        val vendorsTitle: String,
        val confirm: Set<String>,
        val acceptAll: String,
        val back: Set<String>,
        val details: String,
        val privacyPolicy: String,
        val switches: Set<String>,
        val banner: BannerLabels? = null
    ) {
        fun panelKind(header: List<String>, title: String): Kind? = when {
            header.any { it.equals(vendorsHeader, ignoreCase = true) } ||
                title.equals(vendorsTitle, ignoreCase = true) -> Kind.VENDORS
            header.any { it.equals(purposesHeader, ignoreCase = true) } ||
                title.equals(purposesTitle, ignoreCase = true) -> Kind.PURPOSES
            else -> null
        }
    }

    companion object {
        private val forms = listOf(
            Labels(
                purposesHeader = "Data preferences",
                purposesTitle = "Manage your data",
                vendorsHeader = "Vendor preferences",
                vendorsTitle = "Confirm our vendors",
                confirm = setOf("Confirm choices"),
                acceptAll = "Accept all",
                back = setOf("Back"),
                details = "View details",
                privacyPolicy = "Privacy policy",
                switches = setOf("Consent", "Legitimate interest", "Opt in")
            ),
            Labels(
                purposesHeader = "Preferencias de datos",
                purposesTitle = "Gestionar tus datos",
                vendorsHeader = "Preferencias sobre proveedores",
                vendorsTitle = "Aceptar a nuestros proveedores",
                confirm = setOf("Confirmar opciones"),
                acceptAll = "Aceptar todo",
                back = setOf("Atrás", "Volver"),
                details = "Ver detalles",
                privacyPolicy = "Política de privacidad",
                switches = setOf("Consentimiento", "Interés legítimo", "Consentir")
            ),
            Labels(
                purposesHeader = "Préférences de données",
                purposesTitle = "Gérer vos données",
                vendorsHeader = "Préférences concernant les fournisseurs",
                vendorsTitle = "Confirmer nos fournisseurs",
                confirm = setOf("Confirmer les choix"),
                acceptAll = "Tout accepter",
                back = setOf("Retour"),
                details = "Afficher les détails",
                privacyPolicy = "Règles de confidentialité",
                switches = setOf("Consentement", "Intérêt légitime", "Autorisation"),
                banner = BannerLabels(setOf("Gérer les options", "Gérer vos préférences"), setOf("Autoriser"), setOf("Refuser"), "intérêt légitime")
            ),
            Labels(
                purposesHeader = "Dateneinstellungen",
                purposesTitle = "Meine Daten verwalten",
                vendorsHeader = "Anbieter-Einstellungen",
                vendorsTitle = "Unsere Anbieter bestätigen",
                confirm = setOf("Auswahl bestätigen", "Optionen bestätigen"),
                acceptAll = "Alle akzeptieren",
                back = setOf("Zurück"),
                details = "Details ansehen",
                privacyPolicy = "Datenschutzerklärung",
                switches = setOf("Einwilligung", "Berechtigtes Interesse", "Zustimmung"),
                banner = BannerLabels(setOf("Optionen verwalten"), setOf("Einwilligen"), setOf("Nicht einwilligen"), "berechtigtes Interesse")
            ),
            Labels(
                purposesHeader = "Preferenze sui dati",
                purposesTitle = "Gestisci i tuoi dati",
                vendorsHeader = "Preferenze relative ai fornitori",
                vendorsTitle = "Conferma i nostri fornitori",
                confirm = setOf("Conferma le scelte"),
                acceptAll = "Accetta tutto",
                back = setOf("Indietro"),
                details = "Visualizza dettagli",
                privacyPolicy = "Norme sulla privacy",
                switches = setOf("Consenso", "Interesse legittimo", "Adesione"),
                banner = BannerLabels(setOf("Gestisci le opzioni"), setOf("Consenso", "Consenti"), setOf("Nega il consenso"), "interesse legittimo")
            ),
            Labels(
                purposesHeader = "Preferências de dados",
                purposesTitle = "Gerenciar seus dados",
                vendorsHeader = "Preferências de fornecedores",
                vendorsTitle = "Confirmar nossos fornecedores",
                confirm = setOf("Confirmar opções"),
                acceptAll = "Aceitar tudo",
                back = setOf("Voltar", "Anterior"),
                details = "Ver detalhes",
                privacyPolicy = "Política de Privacidade",
                switches = setOf("Consentimento", "Interesse legítimo", "Adesão"),
                banner = BannerLabels(setOf("Gerenciar opções", "Gerenciamento de opções", "Gerir opções"), setOf("Consentimento", "Consentir"), setOf("Não consentir"), "interesse legítimo")
            ),
            Labels(
                purposesHeader = "データ設定",
                purposesTitle = "データを管理",
                vendorsHeader = "ベンダー設定",
                vendorsTitle = "ベンダーを確認",
                confirm = setOf("選択内容を確認"),
                acceptAll = "すべて承認",
                back = setOf("戻る"),
                details = "詳細を表示",
                privacyPolicy = "プライバシー ポリシー",
                switches = setOf("同意", "正当な利益", "オプトイン"),
                banner = BannerLabels(setOf("オプションを管理", "オプションの管理"), setOf("同意する"), setOf("同意しない"), "正当な利益")
            ),
            Labels(
                purposesHeader = "数据偏好设置",
                purposesTitle = "管理您的数据",
                vendorsHeader = "供应商偏好设置",
                vendorsTitle = "确认我们的供应商",
                confirm = setOf("确认已选择完毕"),
                acceptAll = "全部接受",
                back = setOf("返回"),
                details = "查看详情",
                privacyPolicy = "隐私权政策",
                switches = setOf("同意", "合法权益", "选择接受"),
                banner = BannerLabels(setOf("管理选项"), setOf("同意"), setOf("不同意"), "合法权益")
            ),
            Labels(
                purposesHeader = "Настройки использования данных",
                purposesTitle = "Управление данными",
                vendorsHeader = "Настройки доступа поставщиков",
                vendorsTitle = "Права наших поставщиков",
                confirm = setOf("Подтвердить"),
                acceptAll = "Принять все",
                back = setOf("Назад"),
                details = "Подробнее",
                privacyPolicy = "Политика конфиденциальности",
                switches = setOf("Согласие", "Законный интерес", "Включить"),
                banner = BannerLabels(setOf("Настройки"), setOf("Соглашаюсь", "Согласиться"), setOf("Отказаться"), "законн")
            ),
            Labels(
                purposesHeader = "Налаштування даних",
                purposesTitle = "Керувати даними",
                vendorsHeader = "Параметри постачальника",
                vendorsTitle = "Підтвердити постачальників",
                confirm = setOf("Підтвердити вибір"),
                acceptAll = "Прийняти всі",
                back = setOf("Назад"),
                details = "Переглянути деталі",
                privacyPolicy = "Політика конфіденційності",
                switches = setOf("Згода", "Законне право", "Законний інтерес"),
                banner = BannerLabels(setOf("Керувати налаштуваннями"), setOf("Погодитися", "Погоджуюся"), setOf("Не погодитися", "Не погоджуюся"), "законне право")
            )
        )

        fun read(root: AccessibilityNodeInfo, readControls: Boolean): GoogleConsentPanel? =
            findDialog(root) { fromDialog(it, readControls) }

        fun readBanner(root: AccessibilityNodeInfo): BannerAction? =
            findDialog(root, ::bannerFromDialog)

        private fun bannerFromDialog(dialog: AccessibilityNodeInfo): BannerAction? {
            val children = dialog.children()
            val buttons = children.filter { it.isClickable && it.isEnabled && it.isVisibleToUser }
            for (form in forms) {
                val labels = form.banner ?: continue
                val manage = buttons.singleOrNull { it.hasAnyLabel(labels.manage) } ?: continue
                if (buttons.none { it.hasAnyLabel(labels.consent) }) continue
                val hasAnchor = children.any { node ->
                    node.label().contains(labels.anchor, ignoreCase = true) ||
                        node.children().any { it.label().contains(labels.anchor, ignoreCase = true) }
                }
                if (!hasAnchor) continue
                val reject = buttons.singleOrNull { it.hasAnyLabel(labels.reject) }
                return if (reject != null) BannerAction(reject, true) else BannerAction(manage, false)
            }
            return null
        }

        private fun <T : Any> findDialog(root: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> T?): T? {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            repeat(40) {
                val node = queue.removeFirstOrNull() ?: return null
                val nodeClass = node.className?.toString()
                if (nodeClass == "android.app.AlertDialog") match(node)?.let { return it }
                for (i in 0 until minOf(node.childCount, 12)) {
                    val child = node.getChild(i) ?: continue
                    // Some WebViews expose the form container as a plain view.
                    // Require the same footer labels and form structure in either case.
                    if (nodeClass == "android.webkit.WebView" &&
                        child.className?.toString() == "android.view.View" && child.childCount in 3..12
                    ) match(child)?.let { return it }
                    queue.addLast(child)
                }
            }
            return null
        }

        private fun fromDialog(dialog: AccessibilityNodeInfo, readControls: Boolean): GoogleConsentPanel? {
            val children = dialog.children()
            val labels = forms.firstOrNull { form ->
                children.any { it.isClickable && it.hasAnyLabel(form.confirm) } &&
                    children.any { it.isClickable && it.hasLabel(form.acceptAll) }
            } ?: return null
            val header = children.firstOrNull { node ->
                node.childCount in 2..4 &&
                    node.children().any { child ->
                        child.hasLabel(labels.purposesHeader) || child.hasLabel(labels.vendorsHeader) ||
                            child.isClickable && labels.back.any { child.hasLabel(it) }
                    }
            } ?: return null
            val headerNodes = header.children()
            val headerText = headerNodes.map { it.label() }
            val body = children.firstOrNull { it.isScrollable }
                ?: children.singleOrNull { node ->
                    node.getChild(0)?.let {
                        it.hasLabel(labels.purposesTitle) || it.hasLabel(labels.vendorsTitle)
                    } == true
                } ?: return null
            val kind = labels.panelKind(headerText, dialog.label())
                ?: labels.panelKind(headerText, body.getChild(0)?.label().orEmpty()) ?: return null
            val confirm = children.singleOrNull { it.hasAnyLabel(labels.confirm) && it.isClickable } ?: return null
            val viewport = body.bounds()
            if (viewport.isEmpty) return null

            var name: String? = null
            var top = false
            var vendorLink: AccessibilityNodeInfo? = null
            val controls = mutableListOf<Control>()
            val detailsPrefix = "${labels.details}, "
            val detailsSuffix = ", ${labels.details}"
            val policySuffix = ", ${labels.privacyPolicy}"
            val title = if (kind == Kind.PURPOSES) labels.purposesTitle else labels.vendorsTitle
            val indices = if (readControls) {
                0 until body.childCount
            } else {
                listOf(0) + ((body.childCount - 5).coerceAtLeast(1) until body.childCount)
            }
            for (i in indices) {
                val row = body.getChild(i) ?: continue
                val label = row.label().ifEmpty {
                    if (kind == Kind.VENDORS && row.childCount == 1) row.getChild(0)?.label().orEmpty() else ""
                }
                when {
                    kind == Kind.PURPOSES && label.startsWith(detailsPrefix, ignoreCase = true) ->
                        name = label.drop(detailsPrefix.length)
                    kind == Kind.VENDORS && label.endsWith(detailsSuffix, ignoreCase = true) ->
                        name = label.dropLast(detailsSuffix.length)
                    kind == Kind.VENDORS && label.endsWith(policySuffix, ignoreCase = true) ->
                        name = label.dropLast(policySuffix.length)
                }
                if (label.equals(title, ignoreCase = true)) {
                    val bounds = row.bounds()
                    top = !bounds.isEmpty && viewport.contains(bounds)
                }
                if (kind == Kind.PURPOSES && row.hasLabel(labels.vendorsHeader) && row.isClickable &&
                    !row.bounds().isEmpty && viewport.contains(row.bounds())
                ) vendorLink = row
                if (!readControls) continue

                if (row.childCount !in 2..4) continue
                val parts = row.children()
                val control = parts.singleOrNull { it.className?.toString() == "android.widget.ToggleButton" } ?: continue
                val controlLabel = parts.first().label()
                val recognized = labels.switches.any { controlLabel.startsWith(it, ignoreCase = true) }
                val controlName = name.takeIf { recognized && control == parts.last() }
                controls.add(Control(i, controlName, controlLabel, control, control.bounds()))
            }
            val visible = controls.filter {
                it.node.isVisibleToUser && !it.bounds.isEmpty && viewport.contains(it.bounds)
            }
            return GoogleConsentPanel(
                kind, labels, viewport, body, controls, visible, top, vendorLink?.action(),
                headerNodes.singleOrNull { node ->
                    node.isClickable && labels.back.any { node.hasLabel(it) }
                }?.action(),
                confirm.action()
            )
        }

        private fun AccessibilityNodeInfo.children() =
            (0 until childCount).mapNotNull(::getChild)

        private fun AccessibilityNodeInfo.label() =
            text?.toString()?.trim().orEmpty().ifEmpty { contentDescription?.toString()?.trim().orEmpty() }
        private fun AccessibilityNodeInfo.hasLabel(expected: String) = label().equals(expected, ignoreCase = true)
        private fun AccessibilityNodeInfo.hasAnyLabel(expected: Set<String>): Boolean {
            val actual = label()
            return expected.any { actual.equals(it, ignoreCase = true) }
        }
        private fun AccessibilityNodeInfo.action() = Action(this, label())
        private fun AccessibilityNodeInfo.bounds() = Rect().also(::getBoundsInScreen)
    }
}
