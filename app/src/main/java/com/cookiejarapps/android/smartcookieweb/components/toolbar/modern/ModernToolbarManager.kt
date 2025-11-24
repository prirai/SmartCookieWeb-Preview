package com.cookiejarapps.android.smartcookieweb.components.toolbar.modern

import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.cookiejarapps.android.smartcookieweb.components.toolbar.ToolbarPosition
import com.cookiejarapps.android.smartcookieweb.preferences.UserPreferences
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.browser.state.state.SessionState
import kotlinx.coroutines.launch

/**
 * Revolutionary toolbar manager that orchestrates all modern toolbar components
 * for a seamless, beautiful user experience.
 */
class ModernToolbarManager(
    private val container: CoordinatorLayout,
    private val toolbarPosition: ToolbarPosition,
    private val fragment: Fragment,
    private val lifecycleOwner: LifecycleOwner
) {
    
    var modernToolbarSystem: ModernToolbarSystem? = null
        private set
    private var enhancedTabGroupView: EnhancedTabGroupView? = null
    private var modernContextualToolbar: com.cookiejarapps.android.smartcookieweb.toolbar.ContextualBottomToolbar? = null
    private var browserToolbar: BrowserToolbar? = null
    
    // Controllers and callbacks
    private var onTabSelected: ((String) -> Unit)? = null
    private var onTabClosed: ((String) -> Unit)? = null
    private var onNavigationAction: ((NavigationAction) -> Unit)? = null
    
    fun initialize(
        browserToolbarInstance: BrowserToolbar,
        onTabSelected: (String) -> Unit,
        onTabClosed: (String) -> Unit,
        onNavigationAction: (NavigationAction) -> Unit
    ) {
        this.browserToolbar = browserToolbarInstance
        this.onTabSelected = onTabSelected
        this.onTabClosed = onTabClosed
        this.onNavigationAction = onNavigationAction
        
        when (toolbarPosition) {
            ToolbarPosition.TOP -> initializeTopToolbar()
            ToolbarPosition.BOTTOM -> initializeModernBottomToolbar()
        }
        
        android.util.Log.d("ModernToolbarManager", "Initialized for position: $toolbarPosition")
    }
    
    private fun initializeTopToolbar() {
        android.util.Log.d("ModernToolbarManager", "Initializing modern hybrid TOP/BOTTOM toolbar system")
        
        // Create TWO modern toolbar systems for hybrid layout:
        // 1. Top system: Address bar only
        // 2. Bottom system: Tab bar + contextual toolbar
        
        // TOP SYSTEM: Address bar only
        val topToolbarSystem = ModernToolbarSystem(container.context).apply {
            id = generateViewId()
            setToolbarPosition(ModernToolbarSystem.ToolbarPosition.TOP)
        }
        
        val topParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP
            behavior = ModernScrollBehavior(container.context)
        }
        
        container.addView(topToolbarSystem, topParams)
        
        // Add only browser toolbar to top
        browserToolbar?.let { toolbar ->
            (toolbar.parent as? ViewGroup)?.removeView(toolbar)
            topToolbarSystem.addComponent(toolbar, ModernToolbarSystem.ComponentType.ADDRESS_BAR)
            android.util.Log.d("ModernToolbarManager", "Added address bar to TOP system")
        }
        
        // Connect engine view to top system for proper scroll behavior
        findEngineView(container)?.let { engineView ->
            topToolbarSystem.setEngineView(engineView)
            android.util.Log.d("ModernToolbarManager", "Connected engine view to TOP system")
        }
        
        // BOTTOM SYSTEM: Tab group + contextual toolbar
        modernToolbarSystem = ModernToolbarSystem(container.context).apply {
            id = generateViewId()
            setToolbarPosition(ModernToolbarSystem.ToolbarPosition.BOTTOM)
        }
        
        val bottomParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM
            behavior = ModernScrollBehavior(container.context)
        }
        
        container.addView(modernToolbarSystem, bottomParams)
        
        // Add tab group and contextual toolbar to bottom
        createEnhancedTabGroupView()
        createModernContextualToolbar()
        
        // Configure scroll behavior for both systems
        updateScrollBehavior()
        
        android.util.Log.d("ModernToolbarManager", "Initialized hybrid toolbar system - address bar at TOP, tabs+controls at BOTTOM")
    }
    
    private fun initializeModernBottomToolbar() {
        // Create the revolutionary unified toolbar system
        modernToolbarSystem = ModernToolbarSystem(container.context).apply {
            id = generateViewId()
            setToolbarPosition(ModernToolbarSystem.ToolbarPosition.BOTTOM)
        }
        
        // Setup proper CoordinatorLayout params with our custom behavior
        val params = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM
            behavior = ModernScrollBehavior(container.context)
        }
        
        // Add to container
        container.addView(modernToolbarSystem, params)
        
        // Create and add components
        createEnhancedTabGroupView()
        addBrowserToolbar()
        createModernContextualToolbar()
        
        // Configure scroll behavior
        updateScrollBehavior()
        
        android.util.Log.d("ModernToolbarManager", "Initialized modern bottom toolbar system")
    }
    
    private fun createEnhancedTabGroupView() {
        enhancedTabGroupView = EnhancedTabGroupView(container.context).apply {
            setup(
                onTabSelected = { tabId ->
                    android.util.Log.d("ModernToolbarManager", "Tab selected: $tabId")
                    this@ModernToolbarManager.onTabSelected?.invoke(tabId)
                },
                onTabClosed = { tabId ->
                    android.util.Log.d("ModernToolbarManager", "Tab closed: $tabId")
                    this@ModernToolbarManager.onTabClosed?.invoke(tabId)
                }
            )
        }
        
        modernToolbarSystem?.addComponent(
            enhancedTabGroupView!!,
            ModernToolbarSystem.ComponentType.TAB_GROUP
        )
    }
    
    private fun addBrowserToolbar() {
        browserToolbar?.let { toolbar ->
            // Remove from existing parent first
            (toolbar.parent as? ViewGroup)?.removeView(toolbar)
            android.util.Log.d("ModernToolbarManager", "Removed BrowserToolbar from existing parent")
            
            modernToolbarSystem?.addComponent(
                toolbar,
                ModernToolbarSystem.ComponentType.ADDRESS_BAR
            )
        }
    }
    
    private fun createModernContextualToolbar() {
        // COMPLETE migration: Original theming + working functionality
        modernContextualToolbar = com.cookiejarapps.android.smartcookieweb.toolbar.ContextualBottomToolbar(container.context).apply {
            // Restore the listener to make buttons actually work
            listener = object : com.cookiejarapps.android.smartcookieweb.toolbar.ContextualBottomToolbar.ContextualToolbarListener {
                override fun onBackClicked() { 
                    android.util.Log.d("ModernToolbar", "Back clicked - original functionality")
                    onNavigationAction?.invoke(NavigationAction.BACK) 
                }
                override fun onForwardClicked() { 
                    android.util.Log.d("ModernToolbar", "Forward clicked - original functionality")
                    onNavigationAction?.invoke(NavigationAction.FORWARD) 
                }
                override fun onShareClicked() { 
                    android.util.Log.d("ModernToolbar", "Share clicked - original functionality")
                    onNavigationAction?.invoke(NavigationAction.SHARE) 
                }
                override fun onSearchClicked() { 
                    android.util.Log.d("ModernToolbar", "Search clicked - original functionality")
                    onNavigationAction?.invoke(NavigationAction.SEARCH) 
                }
                override fun onNewTabClicked() { 
                    android.util.Log.d("ModernToolbar", "New tab clicked - original functionality")
                    onNavigationAction?.invoke(NavigationAction.NEW_TAB) 
                }
                override fun onTabCountClicked() { 
                    android.util.Log.d("ModernToolbar", "Tab count clicked - original functionality")
                    onNavigationAction?.invoke(NavigationAction.TAB_COUNT) 
                }
                override fun onMenuClicked() { 
                    android.util.Log.d("ModernToolbar", "Menu clicked - original functionality")
                    onNavigationAction?.invoke(NavigationAction.MENU) 
                }
                override fun onBookmarksClicked() { 
                    android.util.Log.d("ModernToolbar", "Bookmarks clicked - original functionality")
                    onNavigationAction?.invoke(NavigationAction.BOOKMARKS) 
                }
            }
            android.util.Log.d("ModernToolbarManager", "Complete migration: Original theming + working functionality")
        }
        
        modernToolbarSystem?.addComponent(
            modernContextualToolbar!!,
            ModernToolbarSystem.ComponentType.CONTEXTUAL
        )
    }
    
    fun updateTabs(tabs: List<SessionState>, selectedTabId: String?) {
        enhancedTabGroupView?.updateTabs(tabs, selectedTabId)
    }
    
    fun updateNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
        // The original ContextualBottomToolbar handles its own navigation state
        android.util.Log.d("ModernToolbarManager", "Navigation state: back=$canGoBack, forward=$canGoForward")
    }
    
    fun updateLoadingState(isLoading: Boolean) {
        // The original ContextualBottomToolbar handles loading state automatically
        android.util.Log.d("ModernToolbarManager", "Loading state: $isLoading")
    }
    
    fun updateModernContext(
        tab: mozilla.components.browser.state.state.TabSessionState?,
        canGoBack: Boolean,
        canGoForward: Boolean,
        tabCount: Int,
        isHomepage: Boolean = false
    ) {
        // Use the original ContextualBottomToolbar's updateForContext method
        modernContextualToolbar?.updateForContext(tab, canGoBack, canGoForward, tabCount, isHomepage)
        android.util.Log.d("ModernToolbarManager", "Updated original toolbar context - tabs: $tabCount, homepage: $isHomepage")
    }
    
    private fun updateScrollBehavior() {
        val prefs = UserPreferences(container.context)
        val behavior = (modernToolbarSystem?.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as? ModernScrollBehavior
        
        if (prefs.hideBarWhileScrolling) {
            modernToolbarSystem?.enableScrolling()
            behavior?.setScrollingEnabled(true)
            android.util.Log.d("ModernToolbarManager", "Enabled scroll behavior")
        } else {
            modernToolbarSystem?.disableScrolling()
            behavior?.setScrollingEnabled(false)
            android.util.Log.d("ModernToolbarManager", "Disabled scroll behavior")
        }
    }
    
    fun expand() {
        modernToolbarSystem?.expand()
    }
    
    fun collapse() {
        modernToolbarSystem?.collapse()
    }
    
    fun enableScrolling() {
        modernToolbarSystem?.enableScrolling()
    }
    
    fun disableScrolling() {
        modernToolbarSystem?.disableScrolling()
    }
    
    private fun generateViewId(): Int {
        return android.view.View.generateViewId()
    }
    
    private fun findEngineView(coordinatorLayout: CoordinatorLayout): mozilla.components.concept.engine.EngineView? {
        for (i in 0 until coordinatorLayout.childCount) {
            val child = coordinatorLayout.getChildAt(i)
            
            // Direct EngineView
            if (child is mozilla.components.concept.engine.EngineView) {
                return child
            }
            
            // EngineView in nested layouts
            if (child is ViewGroup) {
                val engineView = searchForEngineView(child)
                if (engineView != null) return engineView
            }
        }
        return null
    }

    private fun searchForEngineView(view: android.view.View): mozilla.components.concept.engine.EngineView? {
        if (view is mozilla.components.concept.engine.EngineView) return view
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val engineView = searchForEngineView(view.getChildAt(i))
                if (engineView != null) return engineView
            }
        }
        return null
    }
    
    enum class NavigationAction {
        BACK, FORWARD, REFRESH, BOOKMARK, SHARE, TAB_COUNT, MENU, 
        SEARCH, NEW_TAB, BOOKMARKS
    }
}