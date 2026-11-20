/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2518
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-patches/commit/724e6d61b2ecd868c1a9a37d465a688e83a74799
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.shared.layout.branding

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.PatchAvailability
import app.morphe.patcher.patch.PatchDefinition
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.deleteResource
import app.morphe.patcher.patch.folderOption
import app.morphe.patcher.patch.listDecodableFiles
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.patcher.patch.resourceXml
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.returnEarly
import app.morphe.patcher.xml.XmlElement
import app.morphe.patches.all.misc.clone.setOrGetFallbackPackageName
import app.morphe.patches.shared.misc.fix.bitmap.fixRecycledBitmapPatch
import app.morphe.patches.shared.misc.settings.preference.BasePreference
import app.morphe.patches.shared.misc.settings.preference.BasePreferenceScreen
import app.morphe.patches.shared.misc.settings.preference.ListPreference
import app.morphe.patches.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import app.morphe.patches.util.resource.StringResourceSanitizer
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import app.morphe.util.inputStreamFromBundledResource
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import java.io.File
import java.util.logging.Logger

private val mipmapDirectories = mapOf(
    "mipmap-mdpi" to "108x108 px",
    "mipmap-hdpi" to "162x162 px",
    "mipmap-xhdpi" to "216x216 px",
    "mipmap-xxhdpi" to "324x324 px",
    "mipmap-xxxhdpi" to "432x432 px"
)

private val iconStyleNames = arrayOf(
    "black",
    "dark",
    "light",
    "play",
    "play_black",
)

private const val ORIGINAL_USER_ICON_STYLE_NAME = "original"
private const val CUSTOM_USER_ICON_STYLE_NAME = "custom"
private const val DEFAULT_ICON_STYLE_NAME = "black"

private val iconStyleValues = buildMap {
    put("Automatic", null)
    put("Original", ORIGINAL_USER_ICON_STYLE_NAME)

    iconStyleNames.forEach { style ->
        put(style.replace('_', ' ').replaceFirstChar(Char::uppercase), style)
    }

    put("Custom", CUSTOM_USER_ICON_STYLE_NAME)
}

private const val LAUNCHER_RESOURCE_NAME_PREFIX = "morphe_launcher_"
private const val LAUNCHER_ADAPTIVE_BACKGROUND_PREFIX = "morphe_adaptive_background_"
private const val LAUNCHER_ADAPTIVE_FOREGROUND_PREFIX = "morphe_adaptive_foreground_"
private const val LAUNCHER_ADAPTIVE_MONOCHROME_PREFIX = "morphe_adaptive_monochrome"
private const val NOTIFICATION_ICON_NAME = "morphe_notification_icon"

private val USER_CUSTOM_ADAPTIVE_FILE_NAMES = arrayOf(
    "$LAUNCHER_ADAPTIVE_BACKGROUND_PREFIX$CUSTOM_USER_ICON_STYLE_NAME.png",
    "$LAUNCHER_ADAPTIVE_FOREGROUND_PREFIX$CUSTOM_USER_ICON_STYLE_NAME.png"
)

private const val USER_CUSTOM_MONOCHROME_FILE_NAME = "${LAUNCHER_ADAPTIVE_MONOCHROME_PREFIX}_$CUSTOM_USER_ICON_STYLE_NAME.xml"
private const val USER_CUSTOM_NOTIFICATION_ICON_XML_FILE_NAME = "${NOTIFICATION_ICON_NAME}_$CUSTOM_USER_ICON_STYLE_NAME.xml"
private const val USER_CUSTOM_NOTIFICATION_ICON_PNG_FILE_NAME = "${NOTIFICATION_ICON_NAME}_$CUSTOM_USER_ICON_STYLE_NAME.png"

private val notificationIconPngDirectories = mapOf(
    "drawable-mdpi" to "24x24 px",
    "drawable-hdpi" to "36x36 px",
    "drawable-xhdpi" to "48x48 px",
    "drawable-xxhdpi" to "72x72 px",
    "drawable-xxxhdpi" to "96x96 px",
)

private fun customIconFiles(path: String): List<File>? {
    val folder = File(path.trim())
    if (!folder.isDirectory) {
        logger.warning("Custom icon path is not a folder: ${folder.absolutePath}")
        return null
    }

    val adaptiveIcons = mipmapDirectories.keys.flatMap { density ->
        val layers = USER_CUSTOM_ADAPTIVE_FILE_NAMES.map { folder.resolve("$density/$it") }.filter(File::isFile)
        if (layers.isNotEmpty() && layers.size != USER_CUSTOM_ADAPTIVE_FILE_NAMES.size) {
            logger.warning("Custom icon folder $density must contain all of " +
                    "${USER_CUSTOM_ADAPTIVE_FILE_NAMES.joinToString()} but only has ${layers.joinToString { it.name }}")
            return null
        }
        layers
    }
    val optionalIcons = listOf(
        "drawable/$USER_CUSTOM_MONOCHROME_FILE_NAME",
        "drawable/$USER_CUSTOM_NOTIFICATION_ICON_XML_FILE_NAME"
    ) + notificationIconPngDirectories.keys.map { "$it/$USER_CUSTOM_NOTIFICATION_ICON_PNG_FILE_NAME" }

    return (adaptiveIcons + optionalIcons.map(folder::resolve).filter(File::isFile)).ifEmpty {
        logger.warning("Custom icon folder has none of the expected icon files: ${folder.absolutePath}")
        null
    }
}

internal const val EXTENSION_CLASS = "Lapp/morphe/extension/shared/patches/CustomBrandingPatch;"

private val logger = Logger.getLogger(Patch::class.java.name)

internal fun baseCustomBrandingPatch(
    originalLauncherIconName: () -> String,
    originalNotificationIconName: String,
    originalAppName: String,
    originalAppPackageName: String,
    isYouTubeMusic: Boolean,
    numberOfPresetAppNames: Int,
    mainActivityOnCreateFingerprint: Fingerprint,
    mainActivityName: String,
    activityAliasNameWithIntents: String,
    preferenceScreen: BasePreferenceScreen.Screen,
    appLauncherIconNames: () -> Map<String, String> = { emptyMap() },
    block: PatchDefinition.() -> Unit,
    executeBlock: () -> Unit = {}
): Patch = patch(
    name = "Custom branding",
    description = "Adds options to change the app icon and app name. " +
            "For mounted (root) installations the branding is applied while patching, " +
            "because it cannot be changed from the app settings."
) {

    availability { installer, _ ->
        when (installer) {
            InstallerType.MOUNT -> PatchAvailability.DISABLED
            else -> PatchAvailability.ENABLED
        }
    }

    val customName by stringOption(
        key = "customName",
        title = "App name",
        description = "Custom app name."
    )

    val customIcon by folderOption(
        key = "customIcon",
        title = "Custom icon",
        description = """
            Folder with images to use as a custom icon.

            The folder must contain one or more of the following folders, depending on the DPI of the device:
            ${mipmapDirectories.keys.joinToString("\n") { "- $it" }}

            Each of the folders must contain all of the following files:
            ${USER_CUSTOM_ADAPTIVE_FILE_NAMES.joinToString("\n")}

            The image dimensions must be as follows:
            ${mipmapDirectories.map { (dpi, dim) -> "- $dpi: $dim" }.joinToString("\n")}

            Optionally, the path contains a 'drawable' folder with a monochrome icon file:
            $USER_CUSTOM_MONOCHROME_FILE_NAME

            Optionally, the path contains a notification icon in one of the following formats:
            - XML vector drawable placed in 'drawable':
              $USER_CUSTOM_NOTIFICATION_ICON_XML_FILE_NAME
            - PNG raster images placed in the matching 'drawable-<dpi>' folders:
              ${notificationIconPngDirectories.map { (dpi, dim) -> "- $dpi/$USER_CUSTOM_NOTIFICATION_ICON_PNG_FILE_NAME ($dim)" }.joinToString("\n")}
        """
    ) {
        it == null || customIconFiles(it) != null
    }

    val appIconStyle by stringOption(
        key = "appIcon",
        values = iconStyleValues,
        title = "App icon",
        description = """
            The app icon to use, including the notification icon.

            'Automatic' uses the custom icon if one is provided, and otherwise 'Black'.
            Select 'Original' to keep the icon of the unpatched app.

            The icon can be changed later in the app settings, but only for the launcher.
            Android Auto, the app list of the system settings and the apk file always show
            the icon chosen here, and a mounted (root) installation can only change it by
            patching again.
        """
    ) {
        it == null || iconStyleValues.containsValue(it)
    }

    fun resolvedCustomName(): String? = customName ?: if (isYouTubeMusic) null else "MikuTube"
    val hasBundledCustomIcon = !isYouTubeMusic

    block()

    dependsOn(
        fixRecycledBitmapPatch,
        patch {
            execute {
                setExtensionIsPatchIncluded(EXTENSION_CLASS)

                mainActivityOnCreateFingerprint.method.addInstruction(
                    0,
                    "invoke-static { }, $EXTENSION_CLASS->setBranding()V"
                )

                NumberOfPresetAppNamesExtensionFingerprint.method.returnEarly(numberOfPresetAppNames)
                UserProvidedCustomNameExtensionFingerprint.method.returnEarly(resolvedCustomName() != null)
                UserProvidedCustomIconExtensionFingerprint.method.returnEarly(customIcon != null || hasBundledCustomIcon)
                OriginalLauncherIconNameExtensionFingerprint.method.returnEarly(originalLauncherIconName())
                OriginalNotificationIconNameExtensionFingerprint.method.returnEarly(originalNotificationIconName)

                NotificationBuilderFingerprint.let {
                    it.method.apply {
                        mapOf(
                            2 to "getColor",
                            0 to "getSmallIcon"
                        ).forEach { (offset, methodName) ->
                            val index = it.instructionMatches[offset].index
                            val register = getInstruction<FiveRegisterInstruction>(index).registerD

                            addInstructions(
                                index,
                                """
                                    invoke-static { v$register }, $EXTENSION_CLASS->$methodName(I)I
                                    move-result v$register
                                """
                            )
                        }
                    }
                }

                NotificationIconFingerprint.let {
                    it.method.apply {
                        val index = it.instructionMatches.last().index
                        val register = getInstruction<TwoRegisterInstruction>(index).registerA

                        addInstructions(
                            index,
                            """
                                invoke-static { v$register }, $EXTENSION_CLASS->getSmallIcon(I)I
                                move-result v$register
                            """
                        )
                    }
                }
            }

            finalize {
                val iconStyle = resolveIconStyle(appIconStyle, customIcon, hasBundledCustomIcon)

                DefaultIconStyleNameExtensionFingerprint.method.returnEarly(iconStyle)

                val isMounted = isMountedInstall(originalAppPackageName)

                MountedIconAppliedExtensionFingerprint.method.returnEarly(
                    isMounted && iconStyle != ORIGINAL_USER_ICON_STYLE_NAME
                )

                MountedNotificationIconAppliedExtensionFingerprint.method.returnEarly(
                    isMounted && hasMountedNotificationIcon(iconStyle, customIcon, hasBundledCustomIcon)
                )
            }
        },
        patch {
            finalize {
                val finalCustomName = resolvedCustomName()
                val useCustomName = finalCustomName != null

                if (isMountedInstall(originalAppPackageName)) {
                    applyMountedBranding(
                        originalLauncherIconName(),
                        originalNotificationIconName,
                        originalAppName,
                        appIconStyle,
                        customIcon,
                        finalCustomName,
                        hasBundledCustomIcon
                    )

                    return@finalize
                }

                resourceXml("AndroidManifest.xml").use { manifest ->
                    val application = manifest.elements("application").first()
                    val originalAlias = manifest.elementByAttribute(
                        "android:name",
                        activityAliasNameWithIntents
                    )
                    val intentFilters = originalAlias.children

                    fun createAlias(
                        aliasName: String,
                        iconMipmapName: String,
                        appNameIndex: Int,
                        useCustomName: Boolean,
                        enabled: Boolean,
                    ) {
                        val label = if (useCustomName) {
                            finalCustomName ?: "Custom"
                        } else if (appNameIndex == 1) {
                            originalAppName
                        } else {
                            "@string/morphe_custom_branding_name_entry_$appNameIndex"
                        }
                        application.appendChild("activity-alias") {
                            setAttribute("android:name", aliasName)
                            setAttribute("android:enabled", enabled.toString())
                            setAttribute("android:exported", "true")
                            setAttribute("android:icon", "@mipmap/$iconMipmapName")
                            setAttribute("android:label", label)
                            setAttribute("android:targetActivity", mainActivityName)

                            if (isYouTubeMusic) {
                                appendChild("intent-filter") {
                                    appendChild("action") { setAttribute("android:name", "android.intent.action.MAIN") }
                                    appendChild("category") { setAttribute("android:name", "android.intent.category.LAUNCHER") }
                                }
                            } else {
                                intentFilters.forEach { appendCopy(it) }
                            }
                        }
                    }

                    val enabledNameIndex = if (useCustomName) numberOfPresetAppNames else 1
                    val enabledIconStyle = resolveIconStyle(appIconStyle, customIcon, hasBundledCustomIcon)

                    if (enabledIconStyle != ORIGINAL_USER_ICON_STYLE_NAME) {
                        application.setAttribute("android:icon", "@mipmap/$LAUNCHER_RESOURCE_NAME_PREFIX$enabledIconStyle")
                    }

                    application.removeAttribute("android:roundIcon")

                    for (appNameIndex in 1..numberOfPresetAppNames) {
                        fun aliasName(name: String): String = ".morphe_" + name + '_' + appNameIndex

                        val useCustomNameLabel = (useCustomName && appNameIndex == numberOfPresetAppNames)

                        createAlias(
                            aliasName = aliasName(ORIGINAL_USER_ICON_STYLE_NAME),
                            iconMipmapName = originalLauncherIconName(),
                            appNameIndex = appNameIndex,
                            useCustomName = useCustomNameLabel,
                            enabled = appNameIndex == enabledNameIndex &&
                                    enabledIconStyle == ORIGINAL_USER_ICON_STYLE_NAME,
                        )

                        iconStyleNames.forEach { style ->
                            createAlias(
                                aliasName = aliasName(style),
                                iconMipmapName = LAUNCHER_RESOURCE_NAME_PREFIX + style,
                                appNameIndex = appNameIndex,
                                useCustomName = useCustomNameLabel,
                                enabled = (appNameIndex == enabledNameIndex && style == enabledIconStyle),
                            )
                        }

                        appLauncherIconNames().forEach { (style, iconMipmapName) ->
                            createAlias(
                                aliasName = aliasName(style),
                                iconMipmapName = iconMipmapName,
                                appNameIndex = appNameIndex,
                                useCustomName = useCustomNameLabel,
                                enabled = false,
                            )
                        }

                        createAlias(
                            aliasName = aliasName(CUSTOM_USER_ICON_STYLE_NAME),
                            iconMipmapName = LAUNCHER_RESOURCE_NAME_PREFIX + CUSTOM_USER_ICON_STYLE_NAME,
                            appNameIndex = appNameIndex,
                            useCustomName = useCustomNameLabel,
                            enabled = appNameIndex == enabledNameIndex &&
                                    enabledIconStyle == CUSTOM_USER_ICON_STYLE_NAME,
                        )
                    }

                    if (isYouTubeMusic) {
                        originalAlias.elementByAttribute(
                            "android:name",
                            "android.intent.action.MAIN"
                        ).remove()
                    } else {
                        originalAlias.elements("intent-filter").toList().forEach { it.remove() }
                    }

                    application.setAttribute(
                        "android:label",
                        if (useCustomName) {
                            finalCustomName!!
                        } else {
                            "@string/morphe_custom_branding_name_entry_2"
                        }
                    )
                }
            }
        }
    )

    execute {
        val finalCustomName = resolvedCustomName()
        val useCustomName = finalCustomName != null
        val customIconPath = customIcon
        val useCustomIcon = customIconPath != null || hasBundledCustomIcon

        if (appIconStyle == CUSTOM_USER_ICON_STYLE_NAME && !useCustomIcon) {
            throw PatchException("The 'Custom' app icon requires the 'Custom icon' option.")
        }

        val preferences = mutableSetOf<BasePreference>()

        preferences += if (useCustomName) {
            ListPreference(
                key = "morphe_custom_branding_name",
                entriesKey = "morphe_custom_branding_name_custom_entries",
                entryValuesKey = "morphe_custom_branding_name_custom_entry_values"
            )
        } else {
            ListPreference("morphe_custom_branding_name")
        }

        val arrayKeySuffix = if (appLauncherIconNames().isEmpty()) "" else "_21_41"

        if (useCustomIcon) {
            preferences += ListPreference(
                key = "morphe_custom_branding_icon",
                tag = "app.morphe.extension.shared.settings.preference.IconListPreference",
                entriesKey = "morphe_custom_branding_icon${arrayKeySuffix}_custom_entries",
                entryValuesKey = "morphe_custom_branding_icon${arrayKeySuffix}_custom_entry_values"
            )
            preferences += ListPreference(
                key = "morphe_custom_branding_notification_icon",
                tag = "app.morphe.extension.shared.settings.preference.NotificationIconListPreference",
                entriesKey = "morphe_custom_branding_notification_icon_custom_entries",
                entryValuesKey = "morphe_custom_branding_notification_icon_custom_entry_values"
            )
        } else {
            preferences += ListPreference(
                key = "morphe_custom_branding_icon",
                tag = "app.morphe.extension.shared.settings.preference.IconListPreference",
                entriesKey = "morphe_custom_branding_icon${arrayKeySuffix}_entries",
                entryValuesKey = "morphe_custom_branding_icon${arrayKeySuffix}_entry_values"
            )
            preferences += ListPreference(
                key = "morphe_custom_branding_notification_icon",
                tag = "app.morphe.extension.shared.settings.preference.NotificationIconListPreference"
            )
        }

        preferenceScreen.addPreferences(noTitleUnsortedPreferenceCategory(preferences))

        iconStyleNames.forEach { style ->
            copyResources(
                "custom-branding",
                ResourceGroup(
                    "drawable",
                    "$LAUNCHER_ADAPTIVE_BACKGROUND_PREFIX$style.xml",
                    "$LAUNCHER_ADAPTIVE_FOREGROUND_PREFIX$style.xml",
                    "${LAUNCHER_ADAPTIVE_MONOCHROME_PREFIX}_$style.xml",
                    "${NOTIFICATION_ICON_NAME}_$style.xml",
                ),
                ResourceGroup(
                    "mipmap-anydpi",
                    "$LAUNCHER_RESOURCE_NAME_PREFIX$style.xml"
                )
            )
        }

        copyResources(
            "custom-branding",
            ResourceGroup(
                "drawable",
                USER_CUSTOM_MONOCHROME_FILE_NAME,
                USER_CUSTOM_NOTIFICATION_ICON_XML_FILE_NAME,
            ),
            ResourceGroup(
                "mipmap-anydpi",
                "$LAUNCHER_RESOURCE_NAME_PREFIX$CUSTOM_USER_ICON_STYLE_NAME.xml"
            )
        )

        mipmapDirectories.keys.forEach { dpi ->
            copyResources(
                "custom-branding",
                ResourceGroup(
                    dpi,
                    "$LAUNCHER_ADAPTIVE_BACKGROUND_PREFIX$CUSTOM_USER_ICON_STYLE_NAME.png",
                    "$LAUNCHER_ADAPTIVE_FOREGROUND_PREFIX$CUSTOM_USER_ICON_STYLE_NAME.png",
                )
            )
        }

        if (customIconPath != null) {
            customIconFiles(customIconPath)!!.forEach { file ->
                val targetFile = resourceFile("res/${file.parentFile.name}/${file.name}", false)
                targetFile.parentFile.mkdirs()
                file.copyTo(targetFile, overwrite = true)
            }
        } else if (hasBundledCustomIcon) {
            notificationIconPngDirectories.keys.forEach { dpi ->
                bundledBrandingResource("$dpi/$USER_CUSTOM_NOTIFICATION_ICON_PNG_FILE_NAME")?.let { bytes ->
                    val targetFile = resourceFile("res/$dpi/$USER_CUSTOM_NOTIFICATION_ICON_PNG_FILE_NAME", false)
                    targetFile.parentFile.mkdirs()
                    targetFile.writeBytes(bytes)
                }
            }
        }

        executeBlock()
    }
}

private fun applyMountedBranding(
    originalLauncherIconName: String,
    originalNotificationIconName: String,
    originalAppName: String,
    appIconStyle: String?,
    customIcon: String?,
    customName: String?,
    hasBundledCustomIcon: Boolean = false
) {
    if (customName != null) {
        val resourceNames = appNameResourceNames(originalAppName)

        resourceNames.forEachIndexed { index, resourceName ->
            if (setAppNameResource(resourceName, customName)) return@forEachIndexed

            if (index == 0) {
                throw PatchException("Could not find the app name resource: $resourceName")
            }

            logger.warning("Could not find the app name resource: $resourceName")
        }

        logger.info("Mounted install app name: $customName (${resourceNames.joinToString()})")
    }

    val iconStyle = resolveIconStyle(appIconStyle, customIcon, hasBundledCustomIcon)

    if (iconStyle == ORIGINAL_USER_ICON_STYLE_NAME) return

    setLauncherIconResource(originalLauncherIconName, iconStyle)
    logger.info("Mounted install app icon: $iconStyle")

    if (setNotificationIconResource(originalNotificationIconName, iconStyle, customIcon, hasBundledCustomIcon)) {
        logger.info("Mounted install notification icon: $iconStyle")
    }
}

private fun bundledBrandingResource(resourceFile: String): ByteArray? =
    inputStreamFromBundledResource("custom-branding", resourceFile)?.use { it.readBytes() }

private fun isMountedInstall(originalAppPackageName: String) =
    setOrGetFallbackPackageName(originalAppPackageName) == originalAppPackageName

private fun hasMountedNotificationIcon(
    iconStyle: String,
    customIcon: String?,
    hasBundledCustomIcon: Boolean = false
): Boolean {
    if (iconStyle == ORIGINAL_USER_ICON_STYLE_NAME) return false
    if (iconStyle != CUSTOM_USER_ICON_STYLE_NAME) return true

    if (customIcon == null) {
        return hasBundledCustomIcon && (
            bundledBrandingResource("drawable/$USER_CUSTOM_NOTIFICATION_ICON_XML_FILE_NAME") != null ||
            notificationIconPngDirectories.keys.any { dpi ->
                bundledBrandingResource("$dpi/$USER_CUSTOM_NOTIFICATION_ICON_PNG_FILE_NAME") != null
            }
        )
    }

    val iconPathFile = File(customIcon.trim())

    return iconPathFile.resolve("drawable/$USER_CUSTOM_NOTIFICATION_ICON_XML_FILE_NAME").exists() ||
            notificationIconPngDirectories.keys.any { dpi ->
                iconPathFile.resolve("$dpi/$USER_CUSTOM_NOTIFICATION_ICON_PNG_FILE_NAME").exists()
            }
}

private fun resolveIconStyle(
    appIconStyle: String?,
    customIcon: String?,
    hasBundledCustomIcon: Boolean = false
): String =
    appIconStyle
        ?: if (customIcon != null || hasBundledCustomIcon) CUSTOM_USER_ICON_STYLE_NAME else DEFAULT_ICON_STYLE_NAME

private fun appNameResourceNames(originalAppName: String): List<String> {
    val declaredName = stringResourceNameOrNull(originalAppName)
        ?: throw PatchException("Expected a string resource but found: $originalAppName")

    val resourceNames = mutableListOf(declaredName)

    resourceXml("AndroidManifest.xml").use { manifest ->
        val application = manifest.elements("application").first()
        stringResourceNameOrNull(application.attribute("android:label").orEmpty())?.let {
            resourceNames += it
        }

        arrayOf("activity", "activity-alias").forEach { tagName ->
            manifest.elements(tagName).forEach { element ->
                if (!element.isLauncherEntry()) return@forEach

                stringResourceNameOrNull(element.attribute("android:label").orEmpty())?.let {
                    resourceNames += it
                }
            }
        }
    }

    return resourceNames.distinct()
}

private fun stringResourceNameOrNull(reference: String): String? =
    if (reference.startsWith("@string/")) reference.substring("@string/".length) else null

private fun XmlElement.isLauncherEntry(): Boolean =
    elements("category").any { it.attribute("android:name") == "android.intent.category.LAUNCHER" }

private fun setAppNameResource(resourceName: String, appName: String): Boolean {
    var defaultAppNameSet = false

    listDecodableFiles("res/values*/strings.xml").forEach { path ->
        resourceXml(path).use { strings ->
            if (path != "res/values/strings.xml") {
                strings.resourceOrNull(resourceName)?.remove()
                return@use
            }

            val sanitizedAppName = StringResourceSanitizer.sanitizeAndroidResourceString(
                resourceName,
                appName
            )

            defaultAppNameSet = true
            val string = strings.resourceOrNull(resourceName)
                ?: strings.root.appendChild("string") { setAttribute("name", resourceName) }
            string.text = sanitizedAppName
        }
    }

    return defaultAppNameSet
}

private fun setNotificationIconResource(
    originalNotificationIconName: String,
    iconStyle: String,
    customIcon: String?,
    hasBundledCustomIcon: Boolean = false
): Boolean {
    if (!hasMountedNotificationIcon(iconStyle, customIcon, hasBundledCustomIcon)) return false

    val vectorIcon: ByteArray?
    val rasterIcons: Map<String, ByteArray>

    if (iconStyle == CUSTOM_USER_ICON_STYLE_NAME) {
        if (customIcon != null) {
            val iconPathFile = File(customIcon.trim())

            vectorIcon = iconPathFile
                .resolve("drawable/$USER_CUSTOM_NOTIFICATION_ICON_XML_FILE_NAME")
                .takeIf { it.exists() }
                ?.readBytes()

            rasterIcons = notificationIconPngDirectories.keys.mapNotNull { dpi ->
                val sourceFile = iconPathFile.resolve("$dpi/$USER_CUSTOM_NOTIFICATION_ICON_PNG_FILE_NAME")
                if (sourceFile.exists()) dpi to sourceFile.readBytes() else null
            }.toMap()
        } else {
            vectorIcon = bundledBrandingResource("drawable/$USER_CUSTOM_NOTIFICATION_ICON_XML_FILE_NAME")

            rasterIcons = notificationIconPngDirectories.keys.mapNotNull { dpi ->
                val bytes = bundledBrandingResource("$dpi/$USER_CUSTOM_NOTIFICATION_ICON_PNG_FILE_NAME")
                if (bytes != null) dpi to bytes else null
            }.toMap()
        }
    } else {
        vectorIcon = bundledBrandingResource("drawable/${NOTIFICATION_ICON_NAME}_$iconStyle.xml")
            ?: throw PatchException("Could not find the bundled notification icon style: $iconStyle")

        rasterIcons = emptyMap()
    }

    var removedIcons = 0

    val iconsToDelete = listDecodableFiles("res/drawable*/$originalNotificationIconName.*")
    iconsToDelete.forEach { path ->
        deleteResource(path)
        removedIcons++
    }

    if (removedIcons == 0) {
        logger.warning(
            "Could not find the notification icon to replace: @drawable/$originalNotificationIconName"
        )
        return false
    }

    if (vectorIcon != null) {
        val targetFile = resourceFile("res/drawable/$originalNotificationIconName.xml")
        targetFile.parentFile.mkdirs()
        targetFile.writeBytes(vectorIcon)
    }

    rasterIcons.forEach { (dpi, bytes) ->
        val targetFile = resourceFile("res/$dpi/$originalNotificationIconName.png")
        targetFile.parentFile.mkdirs()
        targetFile.writeBytes(bytes)
    }

    return true
}

private fun setLauncherIconResource(
    originalLauncherIconName: String,
    iconStyle: String
) {
    val iconReferences = mutableSetOf("mipmap" to originalLauncherIconName)

    resourceXml("AndroidManifest.xml").use { manifest ->
        val application = manifest.elements("application").first()

        arrayOf("android:icon", "android:roundIcon").forEach { attributeName ->
            val reference = application.attribute(attributeName).orEmpty()
            val typeIndex = reference.indexOf('/')

            if (!reference.startsWith("@") || typeIndex < 0 || reference.contains(':')) {
                return@forEach
            }

            iconReferences += reference.substring(1, typeIndex) to reference.substring(typeIndex + 1)
        }
    }

    val bundledIcon =
        bundledBrandingResource("mipmap-anydpi/$LAUNCHER_RESOURCE_NAME_PREFIX$iconStyle.xml")
            ?: throw PatchException("Could not find the bundled icon style: $iconStyle")

    var replacedIcons = 0

    iconReferences.forEach { (resourceType, resourceName) ->
        listDecodableFiles("res/$resourceType*/$resourceName.xml").forEach { path ->
            resourceFile(path, false).writeBytes(bundledIcon)
            replacedIcons++
        }
    }

    if (replacedIcons == 0) {
        throw PatchException(
            "Could not find an adaptive launcher icon to replace: " +
                    iconReferences.joinToString { (type, name) -> "@$type/$name" }
        )
    }
}
