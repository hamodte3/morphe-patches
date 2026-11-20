package app.morphe.patches.youtube.misc.mikutube

import app.morphe.patcher.patch.deleteResource
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

private val LOCALE_CLEANUP_BASES = setOf("values", "raw", "xml", "drawable")

private val KEEP_QUALIFIER_REGEX = Regex(
    """^(ar($|-)|en($|-)|b\+ar|b\+en|v\d+|night($|-)|notnight|land($|-)|port($|-)|sw\d+|w\d+|h\d+|ldrtl|ldltr|mcc|mnc|small|normal|large|xlarge|long|notlong|round|notround|widecg|nowidecg|highdr|lowdr|car($|-)|desk|television|watch|vrheadset|ldpi|mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|nodpi|tvdpi|anydpi|nokeys|qwerty|12key|keysexposed|keyshidden|keyssoft|notouch|finger|nonav|dpad|trackball|wheel)"""
)

private val LOW_DPI_DIR_REGEX = Regex(
    """^((?:drawable|mipmap)(?:-(?:night|ldrtl))?)-(ldpi|mdpi|hdpi)$"""
)

private val HIGH_DPIS = arrayOf("xhdpi", "xxhdpi", "xxxhdpi")

@Suppress("unused")
val mikuTubeCleanupPatch = patch(
    name = "MikuTube cleanup",
    description = "Hardens AndroidManifest, strips unused locales (keeps ar/en), cleans low-DPI images safely, and truncates licenses & promo Lottie animations."
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

        // 2. Strip unused locales (keep ar & en + all system/theme qualifiers)
        runCatching {
            val allResFiles = listDecodableFiles("res/*/*").toList()
            val deletedPaths = HashSet<String>()

            allResFiles.forEach { path ->
                val rel = path.removePrefix("res/")
                val dir = rel.substringBefore('/', "")
                val dashIdx = dir.indexOf('-')
                if (dashIdx <= 0) return@forEach

                val base = dir.substring(0, dashIdx)
                if (base !in LOCALE_CLEANUP_BASES) return@forEach

                val qualifier = dir.substring(dashIdx + 1)
                if (!KEEP_QUALIFIER_REGEX.containsMatchIn(qualifier)) {
                    deleteResource(path)
                    deletedPaths.add(path)
                }
            }

            // 3. Safe low-DPI cleanup (delete ldpi/mdpi/hdpi only if xhdpi/xxhdpi/xxxhdpi exists)
            val remainingResSet = allResFiles.filterNot { it in deletedPaths }.toHashSet()
            remainingResSet.forEach { path ->
                val rel = path.removePrefix("res/")
                val dir = rel.substringBefore('/', "")
                val fileName = rel.substringAfter('/', "")
                if (fileName.isEmpty()) return@forEach

                val match = LOW_DPI_DIR_REGEX.matchEntire(dir) ?: return@forEach
                val prefix = match.groupValues[1]

                val hasHighDpiAlternative = HIGH_DPIS.any { highDpi ->
                    "res/$prefix-$highDpi/$fileName" in remainingResSet
                }
                if (hasHighDpiAlternative) {
                    deleteResource(path)
                }
            }
        }

        // 4. Truncate third-party licenses & promo Lottie JSONs in res/raw
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

        // 5. Truncate third-party licenses in assets if exposed
        runCatching {
            listDecodableFiles("assets/third_party_license*").forEach { path ->
                resourceFile(path, false).writeBytes(ByteArray(0))
            }
        }
    }
}
