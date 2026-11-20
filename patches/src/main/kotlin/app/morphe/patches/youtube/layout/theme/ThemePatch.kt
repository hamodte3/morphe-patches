/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2524
 * https://github.com/MorpheApp/morphe-patches/pull/3684
 * https://github.com/MorpheApp/morphe-patches/pull/4109
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-patches/commit/724e6d61b2ecd868c1a9a37d465a688e83a74799
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.layout.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.listDecodableFiles
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml
import app.morphe.patcher.patch.resourceXmlOrNull
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.xml.ResourceXml
import app.morphe.patches.shared.layout.theme.STYLE_DEFAULT_COLOR_NAMES_DARK
import app.morphe.patches.shared.layout.theme.STYLE_DEFAULT_COLOR_NAMES_LIGHT
import app.morphe.patches.shared.layout.theme.THEME_COLOR_EXTENSION_CLASS
import app.morphe.patches.shared.layout.theme.THEME_DEFAULT_COLOR_NAMES_DARK
import app.morphe.patches.shared.layout.theme.THEME_DEFAULT_COLOR_NAMES_LIGHT
import app.morphe.patches.shared.layout.theme.baseThemePatch
import app.morphe.patches.shared.layout.theme.baseThemeResourcePatch
import app.morphe.patches.shared.layout.theme.patchedThemeColorDark
import app.morphe.patches.shared.layout.theme.patchedThemeColorLight
import app.morphe.patches.shared.layout.theme.usePatchedThemeColor
import app.morphe.patches.shared.misc.settings.preference.BasePreference
import app.morphe.patches.shared.misc.settings.preference.InputType
import app.morphe.patches.shared.misc.settings.preference.ListPreference
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.shared.misc.settings.preference.TextPreference
import app.morphe.patches.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import app.morphe.patches.youtube.layout.seekbar.seekbarColorPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.playservice.is_20_31_or_greater
import app.morphe.patches.youtube.misc.playservice.is_21_06_or_greater
import app.morphe.patches.youtube.misc.playservice.is_21_08_or_greater
import app.morphe.patches.youtube.misc.playservice.is_21_30_or_greater
import app.morphe.patches.youtube.misc.playservice.is_21_35_or_greater
import app.morphe.patches.youtube.misc.playservice.is_21_45_or_greater
import app.morphe.patches.youtube.misc.playservice.versionCheckPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.shared.LayoutConstructorFingerprint
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.insertLiteralOverride
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS = "Lapp/morphe/extension/youtube/patches/theme/ThemePatch;"

private const val STATIC_WHITE_COLOR_NAME = "yt_white1"

private val STATIC_WHITE_ATTRIBUTE_NAMES = setOf(
    "ytStaticWhite",
    "ytOverlayIconActiveOther"
)

private const val ANDROID_WHITE_COLOR_ID = 0x106000B

private val youTubeColorNamesDark = {
    THEME_DEFAULT_COLOR_NAMES_DARK + if (is_21_06_or_greater)
        setOf(
            "yt_ref_color_constants_default_baseline_black_black3",
            "yt_ref_color_constants_cooler_baseline_black_black3",
            "yt_sys_color_baseline_dark_menu_background",
            "yt_sys_color_baseline_dark_static_black",
            "yt_sys_color_baseline_dark_raised_background",
            "yt_sys_color_baseline_dark_base_background",
            "yt_sys_color_baseline_dark_status_bar",
            "yt_sys_color_baseline_mobile_dark_default_status_bar",
            "yt_sys_color_baseline_light_inverted_background",
            "yt_sys_color_baseline_light_static_black",
            "yt_ref_color_constants_default_grey5",
            "yt_sys_color_baseline_dark_default_menu_background",
            "yt_ref_color_constants_colors_mainstage_grey5",
            "yt_ref_color_constants_colors_mainstage_grey20",
            "yt_ref_color_constants_colors_mainstage_grey25"
        ) else emptySet()
}

private val youTubeColorNamesLight = {
    THEME_DEFAULT_COLOR_NAMES_LIGHT + setOf(
        "yt_status_bar_background_light"
    ) + if (is_21_06_or_greater) {
        setOf(
            "yt_sys_color_baseline_light_base_background",
            "yt_sys_color_baseline_light_raised_background",
            "yt_sys_color_baseline_light_status_bar",
            "yt_sys_color_baseline_mobile_light_default_status_bar",
            "yt_ref_color_constants_default_baseline_white_white3",
            "yt_ref_color_constants_default_grey95",
            "yt_ref_color_constants_cooler_baseline_white_white3",
            "yt_sys_color_baseline_light_menu_background",
            "yt_sys_color_baseline_light_default_menu_background",
        )
    } else {
        emptySet()
    }
}

private val youTubeStyleNamesDark = {
    STYLE_DEFAULT_COLOR_NAMES_DARK + if (is_21_35_or_greater) {
        mapOf(
            "yt.sys.color.baseline.dark" to setOf(
                "yt_sys_color_baseline_menu_background",
                "yt_sys_color_baseline_status_bar"
            ),
            "yt.sys.color.baseline.dark.cooler" to setOf(
                "yt_sys_color_baseline_base_background",
                "yt_sys_color_baseline_menu_background",
                "yt_sys_color_baseline_raised_background",
                "yt_sys_color_baseline_status_bar"
            )
        )
    } else {
        emptyMap()
    }
}

private val youTubeStyleNamesLight = {
    STYLE_DEFAULT_COLOR_NAMES_LIGHT + if (is_21_35_or_greater) {
        mapOf(
            "yt.sys.color.baseline" to setOf(
                "yt_sys_color_baseline_static_white_background",
                "yt_sys_color_baseline_base_background",
                "yt_sys_color_baseline_menu_background",
                "yt_sys_color_baseline_raised_background",
                "yt_sys_color_baseline_status_bar"
            )
        )
    } else {
        emptyMap()
    }
}

private val FOREGROUND_COLOR_CALLS = listOf(
    methodCall(
        definingClass = "Landroid/text/style/ForegroundColorSpan;",
        name = "<init>",
        parameters = listOf("I"),
        returnType = "V"
    ) to 1,
    methodCall(
        definingClass = "Landroid/graphics/PorterDuffColorFilter;",
        name = "<init>",
        parameters = listOf("I", $$"Landroid/graphics/PorterDuff$Mode;"),
        returnType = "V"
    ) to 1,
    methodCall(
        definingClass = "Landroid/graphics/BlendModeColorFilter;",
        name = "<init>",
        parameters = listOf("I", "Landroid/graphics/BlendMode;"),
        returnType = "V"
    ) to 1,
    methodCall(
        definingClass = "Landroid/content/res/ColorStateList;",
        name = "valueOf",
        parameters = listOf("I"),
        returnType = "Landroid/content/res/ColorStateList;"
    ) to 0,
    methodCall(
        name = "setColorFilter",
        parameters = listOf("I", $$"Landroid/graphics/PorterDuff$Mode;"),
        returnType = "V"
    ) to 1,
    methodCall(
        name = "setColorFilter",
        parameters = listOf("I"),
        returnType = "V"
    ) to 1,
    methodCall(
        name = "setTint",
        parameters = listOf("I"),
        returnType = "V"
    ) to 1
)

private const val LAUNCHER_THEME_NAME = "Theme.YouTube.Home"
private const val LAUNCHER_THEME_PARENT = "@style/Base.V27.Theme.YouTube.Home"
private const val LIGHT_STATUS_BAR_ITEM_NAME = "android:windowLightStatusBar"

private val VALUES_DIRECTORY_REGEX = Regex("""values(-v\d+)?""")

private fun ResourceXml.findLauncherThemeParent(): String? = resourceOrNull(LAUNCHER_THEME_NAME)?.attribute("parent")

private fun ResourceXml.setLauncherThemeLightStatusBar() {
    val theme = resourceOrNull(LAUNCHER_THEME_NAME) ?: root.appendChild("style") {
        setAttribute("name", LAUNCHER_THEME_NAME)
        setAttribute("parent", LAUNCHER_THEME_PARENT)
    }

    val item = theme.children("item").firstOrNull { it.attribute("name") == LIGHT_STATUS_BAR_ITEM_NAME }
        ?: theme.appendChild("item") { setAttribute("name", LIGHT_STATUS_BAR_ITEM_NAME) }
    item.text = "true"
}

val themePatch = baseThemePatch(
    extensionClassDescriptor = EXTENSION_CLASS,
    includeLightColor = true,
    useModernLithoColorHook = {
        is_21_30_or_greater
    },
    block = {
        val themeResourcePatch = patch {

            execute {
                fun addColorResource(
                    resourcePath: String,
                    colorName: String,
                    colorValue: String,
                ) {
                    resourceXml(resourcePath, copy = false).use { colors ->
                        colors.root.appendChild("color") {
                            setAttribute("name", colorName)
                            setAttribute("category", "color")
                            text = colorValue
                        }
                    }
                }

                resourceXml("res/values/styles.xml").use { styles ->
                    styles.root.children("style").forEach { style ->
                        style.children("item").forEach { item ->
                            if (item.text == "@color/$STATIC_WHITE_COLOR_NAME"
                                && item.attribute("name") in STATIC_WHITE_ATTRIBUTE_NAMES
                            ) {
                                item.text = "@android:color/white"
                            }
                        }
                    }
                    styles.setLauncherThemeLightStatusBar()
                }

                val splashBackgroundColorKey = "morphe_splash_background_color"
                addColorResource(
                    "res/values/colors.xml",
                    splashBackgroundColorKey,
                    patchedThemeColorLight
                )
                addColorResource(
                    "res/values-night/colors.xml",
                    splashBackgroundColorKey,
                    patchedThemeColorDark
                )

                val launchScreenName = if (is_21_45_or_greater) {
                    "quantum_launchscreen_youtube_2024_q4"
                } else {
                    "quantum_launchscreen_youtube"
                }
                arrayOf(
                    "res/drawable/$launchScreenName.xml",
                    "res/drawable-sw600dp/$launchScreenName.xml",
                ).forEach editSplashScreen@{ resourceFileName ->
                    resourceXml(resourceFileName).use { drawable ->
                        drawable.elements("layer-list").first().children.forEach { node ->
                            if (node.hasAttribute("android:drawable")) {
                                node.setAttribute("android:drawable", "@color/$splashBackgroundColorKey")
                                return@editSplashScreen
                            }
                        }

                        throw PatchException("Failed to modify launch screen")
                    }
                }

                val colorSplashBackgroundColor = "@color/$splashBackgroundColorKey"
                val nightItems = listOf(
                    "android:navigationBarColor" to colorSplashBackgroundColor,
                    "android:windowBackground" to colorSplashBackgroundColor,
                    "android:colorBackground" to colorSplashBackgroundColor,
                    "colorPrimaryDark" to colorSplashBackgroundColor,
                    "android:windowLightStatusBar" to "false",
                )

                listDecodableFiles("res/values*/styles.xml").forEach { stylesPath ->
                    val directory = stylesPath.removePrefix("res/").substringBefore('/')
                    if (!directory.matches(VALUES_DIRECTORY_REGEX)) return@forEach

                    val parent = resourceXml(stylesPath).use { it.findLauncherThemeParent() } ?: return@forEach

                    val nightStylesPath = "res/${directory.replace("values", "values-night")}/styles.xml"
                    resourceXml(nightStylesPath, copy = false).use { nightStyles ->
                        nightStyles.root.appendChild("style") {
                            setAttribute("name", LAUNCHER_THEME_NAME)
                            setAttribute("parent", parent)
                            nightItems.forEach { (name, value) ->
                                appendChild("item") {
                                    setAttribute("name", name)
                                    text = value
                                }
                            }
                        }
                    }
                }

                arrayOf(
                    "res/values-v27/styles.xml",
                    "res/values-v31/styles.xml"
                ).forEach { stylesPath ->
                    resourceXmlOrNull(stylesPath)?.use { it.setLauncherThemeLightStatusBar() }
                }
            }
        }

        dependsOn(
            sharedExtensionPatch,
            settingsPatch,
            seekbarColorPatch,
            versionCheckPatch,
            baseThemeResourcePatch(
                includeLightColor = true,
                colorNamesDark = youTubeColorNamesDark,
                colorNamesLight = youTubeColorNamesLight,
                styleColorNamesDark = youTubeStyleNamesDark,
                styleColorNamesLight = youTubeStyleNamesLight,
                splashScreenThemeParent = "@style/Theme.YouTube.Home"
            ),
            themeResourcePatch
        )

        compatibleWith(COMPATIBILITY_YOUTUBE)
    },

    executeBlock = {
        val colorPreferences = if (usePatchedThemeColor) {
            emptyArray<BasePreference>()
        } else {
            arrayOf<BasePreference>(
                noTitleUnsortedPreferenceCategory(
                    ListPreference(
                        "morphe_theme_color_dark",
                        tag = "app.morphe.extension.shared.theme.ThemeColorListPreference"
                    ),
                    TextPreference(
                        "morphe_theme_color_dark_custom",
                        tag = "app.morphe.extension.shared.settings.preference.ColorPickerPreference",
                        inputType = InputType.TEXT_CAP_CHARACTERS
                    ),
                    ListPreference(
                        "morphe_theme_color_light",
                        tag = "app.morphe.extension.shared.theme.ThemeColorListPreference"
                    ),
                    TextPreference(
                        "morphe_theme_color_light_custom",
                        tag = "app.morphe.extension.shared.settings.preference.ColorPickerPreference",
                        inputType = InputType.TEXT_CAP_CHARACTERS
                    ),
                    SwitchPreference("morphe_theme_color_change_foreground", summary = true)
                )
            )
        }

        PreferenceScreen.GENERAL.addPreferences(
            *colorPreferences,
            SwitchPreference("morphe_gradient_loading_screen", summary = true)
        )

        val preferences = mutableSetOf(
            SwitchPreference("morphe_seekbar_custom_color"),
            TextPreference(
                "morphe_seekbar_custom_color_primary",
                tag = "app.morphe.extension.shared.settings.preference.ColorPickerPreference",
                inputType = InputType.TEXT_CAP_CHARACTERS
            ),
            TextPreference(
                "morphe_seekbar_custom_color_accent",
                tag = "app.morphe.extension.shared.settings.preference.ColorPickerPreference",
                inputType = InputType.TEXT_CAP_CHARACTERS
            )
        )

        PreferenceScreen.SEEKBAR.addPreferences(
            noTitleUnsortedPreferenceCategory(preferences)
        )

        if (!usePatchedThemeColor) {
            MainActivityOnCreateFingerprint.method.addInstruction(
                0,
                "invoke-static/range { p0 .. p0 }, $THEME_COLOR_EXTENSION_CLASS" +
                        "->setSplashScreenTheme(Landroid/app/Activity;)V"
            )
        }

        listOf(PivotBarNewContentDotFingerprint, TopBarNewContentCountFingerprint).forEach {
            it.method.apply {
                arrayOf(
                    it.instructionMatches.last().index,
                    it.instructionMatches[2].index
                ).forEach { checkCastIndex ->
                    val stubRegister = getInstruction<OneRegisterInstruction>(checkCastIndex).registerA

                    addInstruction(
                        checkCastIndex + 1,
                        "invoke-static { v$stubRegister }, $THEME_COLOR_EXTENSION_CLASS" +
                                "->onNewContentIndicator(Landroid/view/ViewStub;)V"
                    )
                }
            }
        }

        StatusBarDarkIconsFingerprint.let {
            it.method.apply {
                val index = it.instructionMatches[2].index
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                replaceInstruction(index, "const-wide v$register, 0x3fe0000000000000L")
            }
        }

        FOREGROUND_COLOR_CALLS.forEach { (filter, colorPosition) ->
            filter.matchAllMethodIndicesForEach(requireMatches = false) { index ->
                val colorRegister = getInstruction(index).registersUsed[colorPosition]

                addInstructions(
                    index,
                    """
                        invoke-static/range { v$colorRegister .. v$colorRegister }, $THEME_COLOR_EXTENSION_CLASS->getForegroundColor(I)I
                        move-result v$colorRegister
                    """
                )
            }
        }

        if (is_20_31_or_greater) {
            LayoutConstructorFingerprint.method.apply {
                findInstructionIndicesReversedOrThrow(
                    resourceLiteral(ResourceType.COLOR, STATIC_WHITE_COLOR_NAME)
                ).forEach { index ->
                    val register = getInstruction<OneRegisterInstruction>(index).registerA
                    replaceInstruction(index, "const v$register, $ANDROID_WHITE_COLOR_ID")
                }
            }
        }

        methodCall(
            definingClass = "Landroid/content/res/ColorStateList;",
            name = "<init>",
            parameters = listOf("[[I", "[I"),
            returnType = "V"
        ).matchAllMethodIndicesForEach(requireMatches = false) { index ->
            val colorsRegister = getInstruction(index).registersUsed[2]

            addInstruction(
                index,
                "invoke-static/range { v$colorsRegister .. v$colorsRegister }, " +
                        "$THEME_COLOR_EXTENSION_CLASS->replaceForegroundColors([I)V"
            )
        }

        LithoImageColorFilterFingerprint.method.addInstructions(
            0,
            """
                invoke-static { p1 }, $THEME_COLOR_EXTENSION_CLASS->getForegroundColorFilter(Landroid/graphics/ColorFilter;)Landroid/graphics/ColorFilter;
                move-result-object p1
            """
        )

        LottieLayerColorFingerprint.matchAllMethodIndicesForEach { index ->
            val colorRegister = getInstruction(index).registersUsed[1]

            addInstructions(
                index,
                """
                    invoke-static/range { v$colorRegister .. v$colorRegister }, $THEME_COLOR_EXTENSION_CLASS->getForegroundLottieColor(I)I
                    move-result v$colorRegister
                """
            )
        }

        UseGradientLoadingScreenFingerprint.matchAll().forEach {
            it.method.insertLiteralOverride(
                it.instructionMatches.first().index,
                "$EXTENSION_CLASS->gradientLoadingScreenEnabled(Z)Z"
            )
        }

        if (is_21_08_or_greater && !is_21_35_or_greater) {
            CarbonColorThemeFeatureFlagFingerprint.matchAll().forEach {
                it.method.insertLiteralOverride(
                    it.instructionMatches.first().index,
                    false
                )
            }
        }

        SpinnerThemeHookFingerprint.matchAll().forEach { match ->
            val checkCastIndex = match.instructionMatches.last().index
            val spinnerRegister = match.method.getInstruction<OneRegisterInstruction>(checkCastIndex).registerA

            match.method.addInstruction(
                checkCastIndex + 1,
                "invoke-static { v$spinnerRegister }, " +
                        "$EXTENSION_CLASS->overrideSpinnerPopupBackground(Landroid/view/View;)V"
            )
        }
    }
)
