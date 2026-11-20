package app.morphe.patches.youtube.misc.mikutube

import app.morphe.patcher.patch.listDecodableFiles
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.patcher.patch.resourceXml
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE

private val DISABLE_COMPONENT_REGEX = Regex(
    """AppMeasurement|FirebaseAnalytics|Crashlytics|AnalyticsReceiver|AnalyticsService|AnalyticsJobService|MobileAdsInitProvider|AdActivity|AdService|MeasurementService|FirebasePerfProvider|FirebaseInitProvider|CrashlyticsInitProvider|LifecycleProcessInitProvider|PlayCoreDialogWrapperActivity|AssetPackExtractionService|ExtractionForegroundService|SplitInstallService|MissingSplits|FeedbackActivity|GoogleHelp|BugReport|CrashReport|SendReport"""
)

private val STARTUP_METADATA_REGEX = Regex(
    """Firebase|Crashlytics|EmojiCompatInitializer|Analytics"""
)

private val REMOVE_PERMISSION_REGEX = Regex(
    """com\.google\.android\.gms\.permission\.AD_ID|android\.permission\.ACCESS_ADSERVICES_|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|ACCESS_BACKGROUND_LOCATION|ACTIVITY_RECOGNITION|NEARBY_WIFI_DEVICES"""
)

private val REMOVE_QUERY_PACKAGE_REGEX = Regex(
    """facebook|katana|snapchat|tiktok|musically|cardboard|ar\.core|daydream|walletnfcrel""",
    RegexOption.IGNORE_CASE
)

private val PROMO_LOTTIE_REGEX = Regex(
    """promo|premium|upsell|onboarding|welcome|offer|trial|membership""",
    RegexOption.IGNORE_CASE
)

private val COMPONENT_TAGS = arrayOf(
    "service",
    "receiver",
    "provider",
    "activity",
    "activity-alias"
)

@Suppress("unused")
val mikuTubeCleanupPatch = patch(
    name = "MikuTube cleanup",
    description = "Cleans AndroidManifest components, permissions, and queries, and truncates license files and promo Lottie animations."
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        // 1. AndroidManifest.xml hardening & cleanup
        resourceXml("AndroidManifest.xml").use { manifest ->
            manifest.elements("application").firstOrNull()?.let { application ->
                application.setAttribute("android:enableOnBackInvokedCallback", "true")
                application.setAttribute("android:allowBackup", "false")
                application.setAttribute("android:debuggable", "false")

                application.elements("meta-data").toList().forEach { meta ->
                    val name = meta.attribute("android:name").orEmpty()
                    val value = meta.attribute("android:value").orEmpty()
                    if (value == "androidx.startup" && STARTUP_METADATA_REGEX.containsMatchIn(name)) {
                        meta.remove()
                    }
                }
            }

            COMPONENT_TAGS.forEach { tagName ->
                manifest.elements(tagName).forEach { element ->
                    val name = element.attribute("android:name").orEmpty()
                    if (DISABLE_COMPONENT_REGEX.containsMatchIn(name)) {
                        element.setAttribute("android:enabled", "false")
                    }
                }
            }

            manifest.elements("uses-permission").toList().forEach { permission ->
                val name = permission.attribute("android:name").orEmpty()
                if (REMOVE_PERMISSION_REGEX.containsMatchIn(name)) {
                    permission.remove()
                }
            }

            manifest.elements("queries").forEach { queries ->
                queries.elements("package").toList().forEach { pkg ->
                    val name = pkg.attribute("android:name").orEmpty()
                    if (REMOVE_QUERY_PACKAGE_REGEX.containsMatchIn(name)) {
                        pkg.remove()
                    }
                }
            }
        }

        // 2. Truncate third-party licenses & promo Lottie JSONs in res/raw
        runCatching {
            listDecodableFiles("res/raw/*").forEach { path ->
                val fileName = path.substringAfterLast('/').lowercase()
                when {
                    fileName == "third_party_licenses" || fileName == "third_party_license_metadata" -> {
                        resourceFile(path, false).writeBytes(ByteArray(0))
                    }

                    fileName.endsWith(".json") && PROMO_LOTTIE_REGEX.containsMatchIn(fileName) -> {
                        runCatching {
                            val file = resourceFile(path)
                            if (file.isFile) {
                                val content = file.readText()
                                if (content.contains("\"layers\"") && content.contains("\"fr\"")) {
                                    file.writeText("{}")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Truncate third-party licenses in assets if exposed
        runCatching {
            listDecodableFiles("assets/third_party_license*").forEach { path ->
                resourceFile(path, false).writeBytes(ByteArray(0))
            }
        }
    }
}
