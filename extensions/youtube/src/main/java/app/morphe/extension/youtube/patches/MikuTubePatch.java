package app.morphe.extension.youtube.patches;

import android.content.Context;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.youtube.settings.Settings;

@SuppressWarnings("unused")
public final class MikuTubePatch {

    private static final String SEED_MARKER = ".mikutube_seeded_v1";
    private static final long CACHE_MAX_BYTES = 150L * 1024L * 1024L; // 150 MB
    private static final long CACHE_MAX_AGE_MS = 5L * 24L * 60L * 60L * 1000L; // 5 days
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static final String DEFAULT_SETTINGS =
            "\"app_refresh_rate\": \"120\",\n"
            + "\"app_refresh_rate_type\": \"portrait\",\n"
            + "\"custom_branding_notification_icon\": \"original\",\n"
            + "\"debug_toast_on_error\": false,\n"
            + "\"disable_auto_feed_refresh\": true,\n"
            + "\"disable_connect_your_devices_popup\": true,\n"
            + "\"disable_sign_in_to_tv_popup\": true,\n"
            + "\"header_logo\": \"custom\",\n"
            + "\"hide_aislist_blocklist_home\": true,\n"
            + "\"hide_aislist_blocklist_search\": true,\n"
            + "\"hide_aislist_warnlist_home\": true,\n"
            + "\"hide_aislist_warnlist_search\": true,\n"
            + "\"hide_album_cards\": true,\n"
            + "\"hide_artist_cards\": true,\n"
            + "\"hide_cast_button\": true,\n"
            + "\"hide_clip_button\": true,\n"
            + "\"hide_connect_button\": true,\n"
            + "\"hide_courses_menu\": true,\n"
            + "\"hide_create_button\": true,\n"
            + "\"hide_explore_button\": true,\n"
            + "\"hide_fashion_menu\": true,\n"
            + "\"hide_gaming_menu\": true,\n"
            + "\"hide_get_premium_button\": true,\n"
            + "\"hide_help_feedback_menu\": true,\n"
            + "\"hide_hype_button\": true,\n"
            + "\"hide_hype_menu\": true,\n"
            + "\"hide_invite_to_message_card\": true,\n"
            + "\"hide_learning_menu\": true,\n"
            + "\"hide_live_menu\": true,\n"
            + "\"hide_memberships_menu\": true,\n"
            + "\"hide_mix_playlists\": true,\n"
            + "\"hide_more_button\": true,\n"
            + "\"hide_movies_menu\": true,\n"
            + "\"hide_music_menu\": true,\n"
            + "\"hide_navigation_button_labels\": true,\n"
            + "\"hide_news_menu\": true,\n"
            + "\"hide_notifications_button\": true,\n"
            + "\"hide_playables_menu\": true,\n"
            + "\"hide_player_flyout_help\": true,\n"
            + "\"hide_player_flyout_listen_with_youtube_music\": true,\n"
            + "\"hide_player_flyout_watch_in_vr\": true,\n"
            + "\"hide_podcasts_menu\": true,\n"
            + "\"hide_privacy_tos_footer\": true,\n"
            + "\"hide_promote_button\": true,\n"
            + "\"hide_quick_actions_mix_button\": true,\n"
            + "\"hide_quick_actions_more_button\": true,\n"
            + "\"hide_remix_button\": true,\n"
            + "\"hide_report_button\": true,\n"
            + "\"hide_shop_button\": true,\n"
            + "\"hide_shopping_menu\": true,\n"
            + "\"hide_shorts_remix_button\": true,\n"
            + "\"hide_sports_menu\": true,\n"
            + "\"hide_stop_ads_button\": true,\n"
            + "\"hide_subscribed_channels_bar\": true,\n"
            + "\"hide_subscriptions_button\": true,\n"
            + "\"hide_thanks_button\": true,\n"
            + "\"hide_ticket_shelf\": true,\n"
            + "\"hide_toolbar_chat_button\": true,\n"
            + "\"hide_toolbar_create_button\": true,\n"
            + "\"hide_toolbar_microphone_button\": true,\n"
            + "\"hide_youtube_create_menu\": true,\n"
            + "\"hide_youtube_kids_menu\": true,\n"
            + "\"hide_youtube_music_menu\": true,\n"
            + "\"hide_youtube_premium_menu\": true,\n"
            + "\"hide_youtube_studio_menu\": true,\n"
            + "\"hide_youtube_works_menu\": true,\n"
            + "\"miniplayer_width_dip\": 175,\n"
            + "\"queue_type\": \"morphe\",\n"
            + "\"remember_shorts_quality_last_selected\": true,\n"
            + "\"remember_video_quality_last_selected\": true,\n"
            + "\"replace_links_with_shortener\": true,\n"
            + "\"sb_compact_skip_button\": true,\n"
            + "\"settings_menu_filter_strings\": \"Family Center\\nAbout\\nSend feedback\\nYouTube Terms of Service\\nHelp\\nHelp and policy\\nWatch on TV\\nLive chat\\nTry experimental new features\\nConnected apps\\nPrivacy\\nYour data in YouTube\\nManage all history\\nBilling & payments\\nPurchases and memberships\\nLanguages\\nNotifications\\nBackground & downloads\\nAccessibility\\nData saving\\nCaptions\\nSwitch or manage account\\nVideo quality preferences\",\n"
            + "\"settings_name\": \"Mod Settings\",\n"
            + "\"shorts_autoplay_background\": false,\n"
            + "\"shorts_icon_style\": \"fluent\",\n"
            + "\"shorts_quality_default_wifi\": 1440,\n"
            + "\"splash_screen_style\": \"disabled\",\n"
            + "\"spoof_video_streams_av1\": true,\n"
            + "\"spoof_video_streams_client_type\": \"visionos_1_02\",\n"
            + "\"spoof_video_streams_stats_for_nerds\": false,\n"
            + "\"swipe_brightness_value\": 1,\n"
            + "\"swipe_left_zone\": \"brightness\",\n"
            + "\"swipe_overlay_progress_brightness_color\": \"#BFFFFFFF\",\n"
            + "\"swipe_overlay_progress_speed_color\": \"#BFFF9100\",\n"
            + "\"swipe_overlay_progress_volume_color\": \"#BFFFFFFF\",\n"
            + "\"swipe_right_zone\": \"volume\",\n"
            + "\"theme_color_dark\": \"dark_blue\",\n"
            + "\"theme_color_light\": \"light_blue\",\n"
            + "\"video_action_bar_layout\": \"classic\",\n"
            + "\"video_quality_default_mobile\": 720,\n"
            + "\"video_quality_default_wifi\": 1440,\n";

    private MikuTubePatch() {
    }

    public static void initialize(Context context) {
        if (context == null || !INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        seedDefaultSettingsOnce(context);
        scheduleSmartCacheCleaner(context.getApplicationContext());
    }

    private static void seedDefaultSettingsOnce(Context context) {
        try {
            File filesDir = context.getFilesDir();
            if (filesDir == null) return;

            File marker = new File(filesDir, SEED_MARKER);
            if (marker.exists()) return;

            Settings.HIDE_VIDEO_ADS.get();

            for (Method method : Setting.class.getDeclaredMethods()) {
                String name = method.getName();
                if (!"importFromJSON".equalsIgnoreCase(name)
                        && !"importFromProperties".equalsIgnoreCase(name)) {
                    continue;
                }

                method.setAccessible(true);
                Class<?>[] params = method.getParameterTypes();
                Object result;

                if (params.length == 2
                        && Context.class.isAssignableFrom(params[0])
                        && params[1] == String.class) {
                    result = method.invoke(null, context, DEFAULT_SETTINGS);
                } else if (params.length == 1 && params[0] == String.class) {
                    result = method.invoke(null, DEFAULT_SETTINGS);
                } else {
                    continue;
                }

                if (!(result instanceof Boolean) || Boolean.TRUE.equals(result)) {
                    //noinspection ResultOfMethodCallIgnored
                    marker.createNewFile();
                    Logger.printInfo(() -> "MikuTube default settings seeded successfully");
                    return;
                }
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to seed MikuTube default settings", ex);
        }
    }

    private static void scheduleSmartCacheCleaner(Context appContext) {
        if (appContext == null) return;
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(8000L);
                File cacheDir = appContext.getCacheDir();
                if (cacheDir != null && dirSize(cacheDir) > CACHE_MAX_BYTES) {
                    long cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS;
                    pruneOld(cacheDir, cutoff);
                }
            } catch (Throwable ex) {
                Logger.printException(() -> "SmartCacheCleaner failure", ex);
            }
        }, "mikutube-smart-cache");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private static long dirSize(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();

        String name = file.getName();
        if ("code_cache".equals(name) || "databases".equals(name)) return 0L;

        File[] children = file.listFiles();
        if (children == null) return 0L;

        long total = 0L;
        for (File child : children) {
            total += dirSize(child);
        }
        return total;
    }

    private static void pruneOld(File dir, long cutoffMs) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

        String name = dir.getName();
        if ("code_cache".equals(name) || "databases".equals(name)) return;

        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (child == null) continue;
            if (child.isDirectory()) {
                pruneOld(child, cutoffMs);
            } else if (child.lastModified() < cutoffMs) {
                //noinspection ResultOfMethodCallIgnored
                child.delete();
            }
        }
    }
}
