package blbl.cat3399.ui

import android.app.Activity
import android.app.Application
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.core.ui.dispatchToDpadItemKeyHandler
import blbl.cat3399.feature.video.VideoCardActionDelegate
import blbl.cat3399.feature.video.VideoCardAdapter
import blbl.cat3399.feature.video.VideoCardConfiguredLongPressAction
import blbl.cat3399.feature.video.VideoCardQuickAction
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [21, 28])
@LooperMode(LooperMode.Mode.PAUSED)
class BrowseUiTest {
    private lateinit var controller: ActivityController<Activity>

    private fun recycler(theme: Int, adapter: RecyclerView.Adapter<*>): RecyclerView {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(theme)
        val recycler = RecyclerView(activity)
        recycler.layoutManager = LinearLayoutManager(activity)
        recycler.adapter = adapter
        activity.setContentView(recycler)
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
        )
        recycler.layout(0, 0, 1200, 800)
        return recycler
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.pause().stop().destroy()
    }

    private val navItems = listOf(
        SidebarNavAdapter.NavItem(SidebarNavAdapter.ID_HOME, "推荐", R.drawable.ic_nav_home),
        SidebarNavAdapter.NavItem(SidebarNavAdapter.ID_MY, "我的", R.drawable.ic_nav_my),
    )

    @Test
    fun browsing_layouts_inflate_in_every_theme_and_only_modern_expands_settings_target() {
        val themes = listOf(
            R.style.Theme_Blbl_Base_Dark, R.style.Theme_Blbl_Base_PinkLight,
            R.style.Theme_Blbl_Base_TvPinkIllustration, R.style.Theme_Blbl_Base_Modern,
        )
        themes.forEach { theme ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), theme)
            val inflater = LayoutInflater.from(context)
            val main = inflater.inflate(R.layout.activity_main, null)
            val settings = main.findViewById<View>(R.id.btn_sidebar_settings)
            val expectedSize = if (theme == R.style.Theme_Blbl_Base_Modern) {
                (48 * context.resources.displayMetrics.density).toInt()
            } else {
                context.resources.getDimensionPixelSize(R.dimen.sidebar_main_settings_size)
            }
            assertEquals(expectedSize, settings.layoutParams.width)
            assertEquals(expectedSize, settings.layoutParams.height)
            assertNotNull(inflater.inflate(R.layout.item_video_card, null))
            assertNotNull(inflater.inflate(R.layout.item_live_card, null))
        }
    }

    @Test
    fun default_theme_keeps_selected_label_and_original_heights() {
        val adapter = SidebarNavAdapter { true }
        adapter.submit(navItems, SidebarNavAdapter.ID_HOME)
        val recycler = recycler(R.style.Theme_Blbl_Base_Dark, adapter)
        val selected = recycler.findViewHolderForAdapterPosition(0)!!.itemView
        val other = recycler.findViewHolderForAdapterPosition(1)!!.itemView
        assertEquals(View.VISIBLE, selected.findViewById<TextView>(R.id.tv_label).visibility)
        assertEquals(View.GONE, other.findViewById<TextView>(R.id.tv_label).visibility)
        assertEquals(recycler.resources.getDimensionPixelSize(R.dimen.sidebar_nav_item_height_selected), selected.layoutParams.height)
        assertEquals(recycler.resources.getDimensionPixelSize(R.dimen.sidebar_nav_item_height_default), other.layoutParams.height)
    }

    @Test
    fun modern_navigation_keeps_labels_and_height_during_selection_without_rebinding_icon() {
        var clicked = -1
        val adapter = SidebarNavAdapter { clicked = it.id; true }
        adapter.submit(navItems, SidebarNavAdapter.ID_HOME)
        val recycler = recycler(R.style.Theme_Blbl_Base_Modern, adapter)
        val holder = recycler.findViewHolderForAdapterPosition(1) as SidebarNavAdapter.Vh
        val root = holder.itemView
        val height = root.layoutParams.height
        val icon = root.findViewById<ImageView>(R.id.iv_icon).drawable
        val changes = mutableListOf<Pair<Int, Any?>>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                changes.add(positionStart to payload)
            }
        })
        adapter.select(SidebarNavAdapter.ID_MY, trigger = false)
        val payload = changes.single { it.first == 1 }.second!!
        adapter.onBindViewHolder(holder, 1, mutableListOf(payload))
        assertTrue(root.isSelected)
        assertEquals(View.VISIBLE, root.findViewById<TextView>(R.id.tv_label).visibility)
        assertEquals(height, root.layoutParams.height)
        assertSame(icon, root.findViewById<ImageView>(R.id.iv_icon).drawable)
        assertEquals("我的", root.contentDescription)
        root.performClick()
        assertEquals(SidebarNavAdapter.ID_MY, clicked)
        assertEquals(SidebarNavAdapter.ID_MY.toLong(), adapter.getItemId(1))
    }

    @Test
    fun repeated_navigation_submission_only_updates_changed_selection() {
        val adapter = SidebarNavAdapter { true }
        adapter.submit(navItems, SidebarNavAdapter.ID_HOME)
        val changes = mutableListOf<Int>()
        var fullUpdates = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() { fullUpdates++ }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                assertNotNull(payload)
                changes.add(positionStart)
            }
        })
        adapter.submit(navItems, SidebarNavAdapter.ID_HOME)
        assertTrue(changes.isEmpty())
        adapter.submit(navItems, SidebarNavAdapter.ID_MY)
        assertEquals(listOf(0, 1), changes)
        assertEquals(0, fullUpdates)
    }

    @Test
    fun action_selection_advances_without_rebinding_title_and_collapse_restores_metadata() {
        val card = VideoCard(
            bvid = "BVtest", cid = null, title = "测试视频", coverUrl = "", durationSec = 60,
            ownerName = "测试UP", ownerFace = null, view = 100, danmaku = 10,
            pubDate = null, pubDateText = null,
        )
        val actions = listOf(
            VideoCardQuickAction.watchLater("稍后再看"), VideoCardQuickAction.openDetail("详情"),
            VideoCardQuickAction.openUp("UP主页"), VideoCardQuickAction.dismiss("不感兴趣"),
        )
        val delegate = object : VideoCardActionDelegate {
            override fun resolveLongPressAction(card: VideoCard, position: Int) = VideoCardConfiguredLongPressAction.MANUAL
            override fun manualActions(card: VideoCard, position: Int) = actions
            override fun onActionSelected(card: VideoCard, position: Int, action: VideoCardQuickAction) = Unit
        }
        val adapter = VideoCardAdapter(onClick = { _, _ -> }, actionDelegate = delegate)
        adapter.submit(listOf(card))
        val recycler = recycler(R.style.Theme_Blbl_Base_Modern, adapter)
        val holder = recycler.findViewHolderForAdapterPosition(0) as VideoCardAdapter.Vh
        val root = holder.itemView
        var lastPayload: Any? = null
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) { lastPayload = payload }
        })
        assertTrue(root.performLongClick())
        adapter.onBindViewHolder(holder, 0)
        assertTrue(root.findViewById<View>(R.id.btn_action_watch_later).isSelected)
        var titleChanges = 0
        root.findViewById<TextView>(R.id.tv_title).addTextChangedListener { titleChanges++ }
        repeat(2) {
            assertTrue(root.dispatchToDpadItemKeyHandler(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
            assertNotNull(lastPayload)
            adapter.onBindViewHolder(holder, 0, mutableListOf(lastPayload!!))
        }
        assertEquals(0, titleChanges)
        assertTrue(root.findViewById<View>(R.id.btn_action_up).isSelected)
        assertFalse(root.findViewById<View>(R.id.btn_action_watch_later).isSelected)
        assertTrue(root.dispatchToDpadItemKeyHandler(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        adapter.onBindViewHolder(holder, 0)
        assertEquals(View.GONE, root.findViewById<View>(R.id.ll_actions).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.tv_duration).visibility)
    }
}
