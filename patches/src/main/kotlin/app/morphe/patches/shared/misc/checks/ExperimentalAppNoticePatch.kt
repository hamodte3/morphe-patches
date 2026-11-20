package app.morphe.patches.shared.misc.checks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.returnEarly
import app.morphe.patches.shared.misc.settings.RecommendedAppVersionUtilsFingerprint

internal fun experimentalAppNoticePatch(
    mainActivityFingerprint: Fingerprint,
    recommendedAppVersion: String
) = patch(
    description = "Shows a use dialog message the first time a user launches an experimentally patched app",
) {
    execute {
        RecommendedAppVersionUtilsFingerprint.method.returnEarly(recommendedAppVersion)
    }
}
