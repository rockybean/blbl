package blbl.cat3399.core.theme

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import blbl.cat3399.R
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.prefs.AppPrefs
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [21, 28])
class ThemePresetsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        shadowOf(context.packageManager).setSystemFeature("android.hardware.type.television", false)
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, false)
        shadowOf(context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager)
            .setCurrentModeType(Configuration.UI_MODE_TYPE_NORMAL)
        BiliClient.init(context)
        BiliClient.prefs.themePreset = AppPrefs.THEME_PRESET_MODERN
    }

    @Test
    fun modern_theme_keeps_non_tv_layout_on_regular_devices() {
        assertEquals(R.style.Theme_Blbl_Base_Modern, ThemePresets.resolve(context).baseThemeRes)
    }

    @Test
    fun television_feature_activates_tv_variant_even_when_ui_mode_is_normal() {
        shadowOf(context.packageManager).setSystemFeature("android.hardware.type.television", true)
        assertEquals(R.style.Theme_Blbl_Base_Modern_Tv, ThemePresets.resolve(context).baseThemeRes)
    }

    @Test
    fun leanback_feature_activates_tv_variant_and_keeps_existing_presets() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        assertEquals(R.style.Theme_Blbl_Base_Modern_Tv, ThemePresets.resolve(context).baseThemeRes)
        BiliClient.prefs.themePreset = AppPrefs.THEME_PRESET_DEFAULT
        assertEquals(R.style.Theme_Blbl_Base_Dark, ThemePresets.resolve(context).baseThemeRes)
        BiliClient.prefs.themePreset = AppPrefs.THEME_PRESET_TV_PINK
        assertEquals(R.style.Theme_Blbl_Base_PinkLight, ThemePresets.resolve(context).baseThemeRes)
    }

    @Test
    fun television_ui_mode_activates_tv_variant_without_hardware_feature() {
        shadowOf(context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager)
            .setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
        assertEquals(R.style.Theme_Blbl_Base_Modern_Tv, ThemePresets.resolve(context).baseThemeRes)
    }
}
