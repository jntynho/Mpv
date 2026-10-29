package live.mehiz.mpvkt.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.ui.icons.*

/** Semantic, shared Outline/Broken artwork for each individual preference. */
internal fun optionIconKey(title: Int): IconKey =
  when (title) {
    R.string.luma_oled -> IconKey.BrightnessLow
    R.string.mpv_add_folder -> IconKey.FolderOpen
    R.string.mpv_clear_library_cache -> IconKey.Delete
    R.string.mpv_clear_thumbnail_cache -> IconKey.Delete
    R.string.mpv_control_hidden -> IconKey.Eye
    R.string.mpv_controls_empty -> IconKey.Settings
    R.string.mpv_excluded_folders -> IconKey.FolderOpen
    R.string.mpv_icons_style_title -> IconKey.Palette
    R.string.mpv_include_only -> IconKey.Folder
    R.string.mpv_included_folders -> IconKey.FolderOpen
    R.string.mpv_landscape_controls -> IconKey.Monitor
    R.string.mpv_minimum_duration -> IconKey.Timer
    R.string.mpv_minimum_size -> IconKey.Data
    R.string.mpv_pinch_zoom -> IconKey.AspectRatio
    R.string.mpv_portrait_chapter_title -> IconKey.Bookmarks
    R.string.mpv_portrait_chapters -> IconKey.Bookmarks
    R.string.mpv_rescan_library -> IconKey.Refresh
    R.string.mpv_reset_controls -> IconKey.Refresh
    R.string.mpv_scan_selected_folders -> IconKey.FolderOpen
    R.string.mpv_show_thumbnails -> IconKey.Image
    R.string.mpv_thumbnail_frame -> IconKey.Image
    R.string.mpv_thumbnail_quality -> IconKey.Image
    R.string.mpv_thumbnail_source -> IconKey.Image
    R.string.pref_about_app_version -> IconKey.Info
    R.string.pref_about_oss_libraries -> IconKey.Code
    R.string.pref_about_privacy_policy -> IconKey.Lock
    R.string.pref_advanced_clear_fonts_cache -> IconKey.Delete
    R.string.pref_advanced_clear_mpv_conf_cache -> IconKey.Delete
    R.string.pref_advanced_clear_playback_history -> IconKey.Delete
    R.string.pref_advanced_dump_logs_title -> IconKey.Code
    R.string.pref_advanced_input_conf -> IconKey.Code
    R.string.pref_advanced_mpv_conf -> IconKey.Code
    R.string.pref_advanced_verbose_logging_title -> IconKey.Code
    R.string.pref_appearance_material_you_title -> IconKey.Palette
    R.string.pref_audio_channels -> IconKey.Audio
    R.string.pref_audio_pitch_correction_title -> IconKey.Audiotrack
    R.string.pref_audio_volume_boost_cap -> IconKey.VolumeUp
    R.string.pref_decoder_debanding_title -> IconKey.Palette
    R.string.pref_decoder_gpu_next_title -> IconKey.Forward
    R.string.pref_decoder_try_hw_dec_title -> IconKey.Memory
    R.string.pref_decoder_yuv420p_title -> IconKey.Palette
    R.string.pref_gesture_double_tap_center_title -> IconKey.Touch
    R.string.pref_gesture_double_tap_left_title -> IconKey.Touch
    R.string.pref_gesture_double_tap_right_title -> IconKey.Touch
    R.string.pref_gesture_media_next -> IconKey.Forward
    R.string.pref_gesture_media_play -> IconKey.Play
    R.string.pref_gesture_media_previous -> IconKey.Backward
    R.string.pref_player_automatically_enter_pip -> IconKey.PictureInPictureAlt
    R.string.pref_player_close_after_eof -> IconKey.Stop
    R.string.pref_player_controls_allow_gestures_in_panels -> IconKey.Touch
    R.string.pref_player_controls_display_volume_as_percentage -> IconKey.VolumeUp
    R.string.pref_player_controls_show_loading_circle -> IconKey.Timer
    R.string.pref_player_display_hide_player_control_time -> IconKey.Timer
    R.string.pref_player_display_panel_opacity -> IconKey.Layers
    R.string.pref_player_display_reduce_player_animation -> IconKey.Eye
    R.string.pref_player_display_show_status_bar -> IconKey.Monitor
    R.string.pref_player_double_tap_seek_duration -> IconKey.DoubleArrow
    R.string.pref_player_draw_over_cutout -> IconKey.Monitor
    R.string.pref_player_gestures_brightness -> IconKey.BrightnessHigh
    R.string.pref_player_gestures_hold_for_multiple_speed -> IconKey.Touch
    R.string.pref_player_gestures_seek -> IconKey.DoubleArrow
    R.string.pref_player_gestures_volume -> IconKey.VolumeUp
    R.string.pref_player_orientation -> IconKey.ScreenRotation
    R.string.pref_player_precise_seeking_title -> IconKey.AlignVerticalCenter
    R.string.pref_player_remember_brightness -> IconKey.BrightnessHigh
    R.string.pref_player_save_position_on_quit -> IconKey.History
    R.string.pref_player_show_seekbar_when_seeking -> IconKey.DoubleArrow
    R.string.pref_preferred_languages -> IconKey.Language
    R.string.show_icon_on_double_tap_to_seek -> IconKey.Image
    R.string.show_splash_ovals_on_double_tap_to_seek -> IconKey.Touch
    R.string.show_time_on_double_tap_to_seek -> IconKey.Schedule
    R.string.subtitle_ai -> IconKey.Memory
    R.string.subtitle_ass -> IconKey.Palette
    R.string.subtitle_autoload -> IconKey.FolderOpen
    R.string.subtitle_clear_downloads -> IconKey.Delete
    R.string.subtitle_clear_fonts -> IconKey.Delete
    R.string.subtitle_encodings -> IconKey.Language
    R.string.subtitle_filters -> IconKey.Tune
    R.string.subtitle_font -> IconKey.FormatSize
    R.string.subtitle_fonts_dir -> IconKey.FolderOpen
    R.string.subtitle_formats -> IconKey.InsertDriveFile
    R.string.subtitle_hearing -> IconKey.Headset
    R.string.subtitle_hub -> IconKey.Network
    R.string.subtitle_languages -> IconKey.Language
    R.string.subtitle_mode -> IconKey.Network
    R.string.subtitle_preferred -> IconKey.Language
    R.string.subtitle_reload_fonts -> IconKey.Refresh
    R.string.subtitle_scale -> IconKey.AspectRatio
    R.string.subtitle_secondary -> IconKey.Subtitles
    R.string.subtitle_wyzie -> IconKey.Network
    R.string.swap_the_volume_and_brightness_slider -> IconKey.VolumeUp
    else -> IconKey.Settings
  }

@Composable internal fun optionIcon(title: Int): ImageVector = appIcon(optionIconKey(title))
