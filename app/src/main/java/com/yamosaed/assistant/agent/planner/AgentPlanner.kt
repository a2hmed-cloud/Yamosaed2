package com.yamosaed.assistant.agent.planner

import com.yamosaed.assistant.agent.Arabic
import com.yamosaed.assistant.agent.action.AgentAction
import com.yamosaed.assistant.agent.discovery.AppDiscoveryService
import com.yamosaed.assistant.agent.vision.ScreenSnapshot

data class PlannedStep(
    val description: String,
    val expectedOutcome: String,
    val actionBuilder: (ScreenSnapshot) -> AgentAction?
)

data class ExecutionPlan(
    val goal: String,
    val targetAppPackage: String?,
    val steps: List<PlannedStep>
)

class AgentPlanner(
    private val appDiscoveryService: AppDiscoveryService
) {

    /**
     * تحليل طلب المستخدم وبناء خطة متعددة الخطوات قابلة للتحقق
     */
    fun createPlan(query: String, initialScreen: ScreenSnapshot): ExecutionPlan? {
        val norm = Arabic.norm(query).lowercase().trim()

        // 1. حالة Google Keep (إنشاء ملاحظة بعنوان ومحتوى)
        // مثال: "افتح Google Keep واعمل ملاحظة بعنوان مذاكرة واكتب فيها عندي كيمياء بكرة"
        if (norm.contains("كيب") || norm.contains("keep") || (norm.contains("ملاحظ") && norm.contains("عنوان"))) {
            val keepApp = appDiscoveryService.findBestAppMatch("Google Keep")
                ?: appDiscoveryService.findBestAppMatch("Keep")
                ?: appDiscoveryService.findBestAppMatch("Notes")

            val title = extractKeepTitle(query)
            val content = extractKeepContent(query)

            val steps = mutableListOf<PlannedStep>()

            // خطوة 1: فتح التطبيق
            if (keepApp != null) {
                steps.add(
                    PlannedStep(
                        description = "فتح تطبيق Google Keep",
                        expectedOutcome = "ظهور شاشة الملاحظات الرئيسية",
                        actionBuilder = { AgentAction.LaunchApp(keepApp.packageName, keepApp.label) }
                    )
                )
            }

            // خطوة 2: النقر على زر إنشاء ملاحظة جديدة
            steps.add(
                PlannedStep(
                    description = "الضغط على زر إضافة ملاحظة جديدة",
                    expectedOutcome = "فتح شاشة كتابة الملاحظة الفارغة",
                    actionBuilder = { screen ->
                        // أزرار إنشاء الملاحظة الشائعة في Keep
                        val addBtn = screen.findElementByText("إنشاء ملاحظة")
                            ?: screen.findElementByText("ملاحظة جديدة")
                            ?: screen.findElementByText("New note")
                            ?: screen.findClickableByViewId("new_note_button")
                            ?: screen.findClickableByViewId("floating_action_button")
                        if (addBtn != null) {
                            AgentAction.Click(text = addBtn.text.ifBlank { null }, viewId = addBtn.viewIdResourceName, bounds = addBtn.bounds, description = "زر إضافة ملاحظة")
                        } else {
                            // محاولة النقر على زر (+) أو النقر في الأسفل
                            AgentAction.Click(text = "+", description = "زر +")
                        }
                    }
                )
            )

            // خطوة 3: كتابة العنوان إن وجد
            if (title.isNotBlank()) {
                steps.add(
                    PlannedStep(
                        description = "كتابة عنوان الملاحظة: \"$title\"",
                        expectedOutcome = "ظهور العنوان في حقل العنوان",
                        actionBuilder = { screen ->
                            val titleField = screen.findElementByText("العنوان")
                                ?: screen.findElementByText("Title")
                                ?: screen.findEditableField()
                            AgentAction.TypeText(textToType = title, targetQuery = titleField?.text, clearFirst = true)
                        }
                    )
                )
            }

            // خطوة 4: كتابة محتوى الملاحظة
            if (content.isNotBlank()) {
                steps.add(
                    PlannedStep(
                        description = "كتابة محتوى الملاحظة: \"$content\"",
                        expectedOutcome = "ظهور المحتوى في نص الملاحظة",
                        actionBuilder = { screen ->
                            val noteField = screen.findElementByText("ملاحظة")
                                ?: screen.findElementByText("Note")
                                ?: screen.findEditableField()
                            AgentAction.TypeText(textToType = content, targetQuery = noteField?.text, clearFirst = false)
                        }
                    )
                )
            }

            // خطوة 5: حفظ الملاحظة بالرجوع
            steps.add(
                PlannedStep(
                    description = "حفظ الملاحظة والرجوع",
                    expectedOutcome = "العودة للقائمة وظهور الملاحظة المحفوظة",
                    actionBuilder = { AgentAction.NavigateBack }
                )
            )

            return ExecutionPlan(
                goal = "إنشاء ملاحظة بعنوان \"$title\" ومحتوى \"$content\" في Google Keep",
                targetAppPackage = keepApp?.packageName,
                steps = steps
            )
        }

        // 2. حالة WhatsApp (فتح المحادثة وكتابة رسالة مع التأكيد)
        // مثال: "افتح واتساب وابحث عن محادثة أحمد واكتب له أني وصلت"
        if (norm.contains("واتساب") || norm.contains("واتس") || norm.contains("whatsapp")) {
            val waApp = appDiscoveryService.findBestAppMatch("WhatsApp") ?: appDiscoveryService.findBestAppMatch("واتساب")
            val contactName = extractWhatsAppContact(query)
            val messageText = extractWhatsAppMessage(query)

            val steps = mutableListOf<PlannedStep>()

            if (waApp != null) {
                steps.add(
                    PlannedStep(
                        description = "فتح تطبيق واتساب",
                        expectedOutcome = "ظهور قائمة محادثات واتساب",
                        actionBuilder = { AgentAction.LaunchApp(waApp.packageName, waApp.label) }
                    )
                )
            }

            if (contactName.isNotBlank()) {
                // البحث عن المحادثة
                steps.add(
                    PlannedStep(
                        description = "الضغط على أيقونة البحث في واتساب",
                        expectedOutcome = "ظهور حقل البحث في المحادثات",
                        actionBuilder = { screen ->
                            val searchBtn = screen.findElementByText("بحث")
                                ?: screen.findElementByText("Search")
                                ?: screen.findClickableByViewId("menuitem_search")
                            AgentAction.Click(text = searchBtn?.text, viewId = searchBtn?.viewIdResourceName, description = "أيقونة البحث")
                        }
                    )
                )
                steps.add(
                    PlannedStep(
                        description = "كتابة اسم المستلم: \"$contactName\"",
                        expectedOutcome = "ظهور جهة الاتصال في نتائج البحث",
                        actionBuilder = { AgentAction.TypeText(textToType = contactName, clearFirst = true) }
                    )
                )
                steps.add(
                    PlannedStep(
                        description = "اختيار محادثة \"$contactName\"",
                        expectedOutcome = "فتح شاشة الدردشة للمستلم",
                        actionBuilder = { screen ->
                            val contactItem = screen.findElementByText(contactName)
                            AgentAction.Click(text = contactItem?.text ?: contactName, description = "جهة اتصال $contactName")
                        }
                    )
                )
            }

            if (messageText.isNotBlank()) {
                steps.add(
                    PlannedStep(
                        description = "كتابة الرسالة في حقل الإدخال: \"$messageText\"",
                        expectedOutcome = "ظهور الرسالة جاهزة في صندوق الكتابة",
                        actionBuilder = { screen ->
                            val inputField = screen.findElementByText("رسالة")
                                ?: screen.findElementByText("Message")
                                ?: screen.findEditableField()
                            AgentAction.TypeText(textToType = messageText, targetQuery = inputField?.text, clearFirst = true)
                        }
                    )
                )
                // خطوة أمان إنساني إلزامية: طلب تأكيد المستخدم قبل الإرسال
                steps.add(
                    PlannedStep(
                        description = "طلب تأكيد المستخدم قبل الإرسال الفعلي للرسالة",
                        expectedOutcome = "تأكيد صريح من المستخدم",
                        actionBuilder = {
                            AgentAction.RequestConfirmation(
                                message = "هل تريد مني إرسال: \"$messageText\" إلى $contactName الآن؟",
                                pendingAction = AgentAction.Click(text = "إرسال", description = "زر إرسال الرسالة")
                            )
                        }
                    )
                )
            }

            return ExecutionPlan(
                goal = "إرسال رسالة إلى $contactName في واتساب",
                targetAppPackage = waApp?.packageName,
                steps = steps
            )
        }

        // 3. حالة Chrome (فتح وبحث)
        // مثال: "افتح Chrome وابحث عن طريقة حل المعادلة دي"
        if (norm.contains("كروم") || norm.contains("chrome") || norm.contains("متصفح")) {
            val chromeApp = appDiscoveryService.findBestAppMatch("Chrome")
            val searchQuery = extractSearchQuery(query)

            val steps = mutableListOf<PlannedStep>()
            if (chromeApp != null) {
                steps.add(
                    PlannedStep(
                        description = "فتح متصفح Google Chrome",
                        expectedOutcome = "ظهور واجهة المتصفح وشريط العنوان",
                        actionBuilder = { AgentAction.LaunchApp(chromeApp.packageName, chromeApp.label) }
                    )
                )
            }
            if (searchQuery.isNotBlank()) {
                steps.add(
                    PlannedStep(
                        description = "النقر على شريط البحث وكتابة: \"$searchQuery\"",
                        expectedOutcome = "إدخال مصطلح البحث",
                        actionBuilder = { screen ->
                            val urlBar = screen.findElementByText("البحث")
                                ?: screen.findElementByText("Search")
                                ?: screen.findClickableByViewId("url_bar")
                                ?: screen.findEditableField()
                            AgentAction.TypeText(textToType = searchQuery, targetQuery = urlBar?.text, clearFirst = true)
                        }
                    )
                )
            }
            return ExecutionPlan(
                goal = "البحث في Chrome عن \"$searchQuery\"",
                targetAppPackage = chromeApp?.packageName,
                steps = steps
            )
        }

        // 4. حالة الإعدادات (فتح والبحث في الإعدادات)
        if (norm.contains("اعدادات") || norm.contains("الإعدادات") || norm.contains("settings")) {
            val settingsApp = appDiscoveryService.findBestAppMatch("Settings")
                ?: appDiscoveryService.findBestAppMatch("إعدادات")
            val settingQuery = norm.replace(Regex("^(افتح|ادخل|هات)\\s*(الإعدادات|الاعدادات|إعدادات|اعدادات)?\\s*(و)?\\s*(ابحث عن|دور على)?"), "").trim()

            val steps = mutableListOf<PlannedStep>()
            if (settingsApp != null) {
                steps.add(
                    PlannedStep(
                        description = "فتح إعدادات الجهاز",
                        expectedOutcome = "ظهور قائمة إعدادات النظام",
                        actionBuilder = { AgentAction.LaunchApp(settingsApp.packageName, settingsApp.label) }
                    )
                )
            }
            if (settingQuery.isNotBlank()) {
                steps.add(
                    PlannedStep(
                        description = "البحث في الإعدادات عن: \"$settingQuery\"",
                        expectedOutcome = "ظهور نتائج إعدادات النظام المطابقة",
                        actionBuilder = { screen ->
                            val searchBtn = screen.findElementByText("بحث")
                                ?: screen.findElementByText("Search")
                                ?: screen.findEditableField()
                            AgentAction.TypeText(textToType = settingQuery, targetQuery = searchBtn?.text, clearFirst = true)
                        }
                    )
                )
            }
            return ExecutionPlan(
                goal = "فتح الإعدادات والبحث عن \"$settingQuery\"",
                targetAppPackage = settingsApp?.packageName,
                steps = steps
            )
        }

        return null
    }

    private fun extractKeepTitle(query: String): String {
        val pattern = Regex("بعنوان\\s+([^,،\\.]+?)(?:\\s+واكتب|\\s+واكتب فيها|\\s+واكتب فيه|$)", RegexOption.IGNORE_CASE)
        val match = pattern.find(query)
        return match?.groupValues?.get(1)?.trim() ?: "ملاحظة يا مساعد"
    }

    private fun extractKeepContent(query: String): String {
        val pattern = Regex("(?:واكتب فيها|واكتب فيه|واكتب|اكتب فيها|اكتب فيه|اكتب)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val match = pattern.find(query)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractWhatsAppContact(query: String): String {
        val pattern = Regex("(?:محادثة|شات|كلم|لـ|ل|الي|إلى)\\s+([^,،\\s]+)", RegexOption.IGNORE_CASE)
        val match = pattern.find(query)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractWhatsAppMessage(query: String): String {
        val pattern = Regex("(?:واكتب له|واكتب لها|واكتب|وقول له|وقول لها|رسالة|ان)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val match = pattern.find(query)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractSearchQuery(query: String): String {
        val pattern = Regex("(?:وابحث عن|وابحث في|وابحث|ابحث عن|ابحث في|ابحث|دور على)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val match = pattern.find(query)
        return match?.groupValues?.get(1)?.trim() ?: query
    }
}
