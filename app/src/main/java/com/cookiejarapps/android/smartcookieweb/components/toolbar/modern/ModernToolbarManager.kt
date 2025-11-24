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
        // Keep existing top toolbar simple for now - could enhance later
        android.util.Log.d("ModernToolbarManager", "Using existing top toolbar")
    }
    
    private fun initializeModernBottomToolbar() {
        // Create the revolutionary unified toolbar system
        modernToolbarSystem = ModernToolbarSystem(container.context).apply {
            id = generateViewId()
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
    
    enum class NavigationAction {
        BACK, FORWARD, REFRESH, BOOKMARK, SHARE, TAB_COUNT, MENU, 
        SEARCH, NEW_TAB, BOOKMARKS
    }
}