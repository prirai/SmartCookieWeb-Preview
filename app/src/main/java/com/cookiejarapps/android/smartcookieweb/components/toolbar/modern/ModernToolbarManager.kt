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
    
    private var modernToolbarSystem: ModernToolbarSystem? = null
    private var enhancedTabGroupView: EnhancedTabGroupView? = null
    private var modernContextualToolbar: ModernContextualToolbar? = null
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
        modernContextualToolbar = ModernContextualToolbar(container.context).apply {
            setNavigationCallbacks(
                onBack = { onNavigationAction?.invoke(NavigationAction.BACK) },
                onForward = { onNavigationAction?.invoke(NavigationAction.FORWARD) },
                onRefresh = { onNavigationAction?.invoke(NavigationAction.REFRESH) },
                onBookmark = { onNavigationAction?.invoke(NavigationAction.BOOKMARK) },
                onShare = { onNavigationAction?.invoke(NavigationAction.SHARE) },
                onTabCount = { onNavigationAction?.invoke(NavigationAction.TAB_COUNT) },
                onMenu = { onNavigationAction?.invoke(NavigationAction.MENU) }
            )
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
        modernContextualToolbar?.updateNavigationState(canGoBack, canGoForward)
    }
    
    fun updateLoadingState(isLoading: Boolean) {
        modernContextualToolbar?.updateLoadingState(isLoading)
    }
    
    fun updateBookmarkState(isBookmarked: Boolean) {
        modernContextualToolbar?.updateBookmarkState(isBookmarked)
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
        BACK, FORWARD, REFRESH, BOOKMARK, SHARE, TAB_COUNT, MENU
    }
}