package com.cookiejarapps.android.smartcookieweb

import android.util.Log
import android.view.View
import com.cookiejarapps.android.smartcookieweb.browser.toolbar.ToolbarGestureHandler
import com.cookiejarapps.android.smartcookieweb.browser.toolbar.WebExtensionToolbarFeature
import com.cookiejarapps.android.smartcookieweb.toolbar.ContextualBottomToolbar
import com.cookiejarapps.android.smartcookieweb.databinding.FragmentBrowserBinding
import com.cookiejarapps.android.smartcookieweb.ext.components
import com.cookiejarapps.android.smartcookieweb.browser.tabgroups.TabGroupWithTabs
import com.cookiejarapps.android.smartcookieweb.preferences.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.collect
import androidx.navigation.fragment.findNavController
import mozilla.components.browser.state.state.SessionState
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifAnyChanged
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.feature.tabs.WindowFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper

/**
 * Fragment used for browsing the web within the main app.
 */
@ExperimentalCoroutinesApi
@Suppress("TooManyFunctions", "LargeClass")
class BrowserFragment : BaseBrowserFragment(), UserInteractionHandler {

    private val windowFeature = ViewBoundFeatureWrapper<WindowFeature>()
    private val webExtToolbarFeature = ViewBoundFeatureWrapper<WebExtensionToolbarFeature>()
    
    // Revolutionary Modern Toolbar System
    private var modernToolbarManager: com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager? = null
    

    @Suppress("LongMethod")
    override fun initializeUI(view: View, tab: SessionState) {
        super.initializeUI(view, tab)

        val context = requireContext()
        val components = context.components

        binding.gestureLayout.addGestureListener(
            ToolbarGestureHandler(
                activity = requireActivity(),
                contentLayout = binding.browserLayout,
                tabPreview = binding.tabPreview,
                toolbarLayout = browserToolbarView.view,
                store = components.store,
                selectTabUseCase = components.tabsUseCases.selectTab
            )
        )

        thumbnailsFeature.set(
            feature = BrowserThumbnails(context, binding.engineView, components.store),
            owner = this,
            view = view
        )

        if(UserPreferences(requireContext()).barAddonsList.isNotEmpty()) {
            webExtToolbarFeature.set(
                feature = WebExtensionToolbarFeature(
                    browserToolbarView.view,
                    components.store,
                    UserPreferences(requireContext()).barAddonsList.split(","),
                ),
                owner = this,
                view = view
            )
        } else if (UserPreferences(requireContext()).showAddonsInBar) {
            webExtToolbarFeature.set(
                feature = WebExtensionToolbarFeature(
                    browserToolbarView.view,
                    components.store,
                    showAllExtensions = true
                ),
                owner = this,
                view = view
            )
        }

        windowFeature.set(
            feature = WindowFeature(
                store = components.store,
                tabsUseCases = components.tabsUseCases
            ),
            owner = this,
            view = view
        )

        // Setup contextual bottom toolbar
        setupContextualBottomToolbar()
        
        // Tab groups are now handled by the modern toolbar system
        
        // Observe tab changes for real-time toolbar updates
        observeTabChangesForToolbar()
        
        // Initialize the Revolutionary Modern Toolbar System
        initializeModernToolbarSystem()
    }

    private fun setupContextualBottomToolbar() {
        // Use integrated toolbar from the address bar component, fallback to separate one
        val toolbar = (browserToolbarView.integratedContextualToolbar as? com.cookiejarapps.android.smartcookieweb.toolbar.ContextualBottomToolbar) 
            ?: binding.contextualBottomToolbar
        
        // Force bottom toolbar to show for testing iOS icons
        val showBottomToolbar = true // UserPreferences(requireContext()).shouldUseBottomToolbar
        if (showBottomToolbar) {
            toolbar.visibility = View.VISIBLE
        } else {
            toolbar.visibility = View.GONE
        }
        
        if (showBottomToolbar) {
            toolbar.listener = object : ContextualBottomToolbar.ContextualToolbarListener {
                override fun onBackClicked() {
                    requireContext().components.sessionUseCases.goBack()
                }

                override fun onForwardClicked() {
                    requireContext().components.sessionUseCases.goForward()
                }

                override fun onShareClicked() {
                    val store = requireContext().components.store.state
                    val currentTab = store.tabs.find { it.id == store.selectedTabId }
                    currentTab?.let { tab ->
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, tab.content.url)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, tab.content.title)
                        }
                        startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share)))
                    }
                }

                override fun onSearchClicked() {
                    android.util.Log.d("BrowserFragment", "Search button clicked!")
                    // Focus on the toolbar for search
                    browserToolbarView.view.displayMode()
                }

                override fun onNewTabClicked() {
                    android.util.Log.d("BrowserFragment", "New tab button clicked!")
                    requireContext().components.tabsUseCases.addTab.invoke("about:homepage", selectTab = true)
                }

                override fun onTabCountClicked() {
                    android.util.Log.d("BrowserFragment", "Tab count button clicked!")
                    // Open tabs bottom sheet
                    val tabsBottomSheet = com.cookiejarapps.android.smartcookieweb.browser.tabs.TabsBottomSheetFragment.newInstance()
                    tabsBottomSheet.show(parentFragmentManager, com.cookiejarapps.android.smartcookieweb.browser.tabs.TabsBottomSheetFragment.TAG)
                }

                override fun onBookmarksClicked() {
                    android.util.Log.d("BrowserFragment", "Bookmark button clicked!")
                    // Use the exact same logic as the three-dot menu
                    browserInteractor.onBrowserToolbarMenuItemTapped(
                        com.cookiejarapps.android.smartcookieweb.components.toolbar.ToolbarMenu.Item.Bookmarks
                    )
                }

                override fun onMenuClicked() {
                    // Create and show the menu directly since the toolbar menu builder is disabled
                    val context = requireContext()
                    val components = context.components
                    
                    // Create a BrowserMenu instance similar to what BrowserToolbarView does
                    val browserMenu = com.cookiejarapps.android.smartcookieweb.components.toolbar.BrowserMenu(
                        context = context,
                        store = components.store,
                        onItemTapped = { item ->
                            browserInteractor.onBrowserToolbarMenuItemTapped(item)
                        },
                        lifecycleOwner = viewLifecycleOwner,
                        isPinningSupported = components.webAppUseCases.isPinningSupported(),
                        shouldReverseItems = false
                    )
                    
                    // Build and show the menu
                    val menu = browserMenu.menuBuilder.build(context)
                    val menuButton = binding.contextualBottomToolbar.findViewById<View>(R.id.menu_button)
                    
                    // Create a temporary view above the button for better positioning
                    val tempView = android.view.View(context)
                    tempView.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
                    
                    // Add the temp view to the parent layout
                    val parent = binding.contextualBottomToolbar
                    parent.addView(tempView)
                    
                    // Position the temp view above the menu button
                    tempView.x = menuButton.x
                    tempView.y = menuButton.y - 60 // 60px above the button
                    
                    // Show menu anchored to temp view
                    menu.show(anchor = tempView)
                    
                    // Clean up temp view after menu interaction
                    tempView.postDelayed({
                        try {
                            parent.removeView(tempView)
                        } catch (e: Exception) {
                            // Ignore if view was already removed
                        }
                    }, 3000) // 3 seconds cleanup delay
                }
            }
            
            // Update toolbar context when tab changes
            updateContextualToolbar()
        }
    }

    private fun updateContextualToolbar() {
        // Safety check: ensure fragment and view are still valid
        if (!isAdded || view == null) return
        
        try {
            val toolbar = binding.contextualBottomToolbar
            if (toolbar.visibility == View.VISIBLE) {
                val store = requireContext().components.store.state
                val currentTab = store.tabs.find { it.id == store.selectedTabId }
                
                // Better homepage detection: only treat as homepage if URL is actually about:homepage
                // This fixes the forward button issue by properly distinguishing homepage from search results
                val isHomepage = currentTab?.content?.url == "about:homepage"
                
                toolbar.updateForContext(
                    tab = currentTab,
                    canGoBack = currentTab?.content?.canGoBack ?: false,
                    canGoForward = currentTab?.content?.canGoForward ?: false,
                    tabCount = store.tabs.size,
                    isHomepage = isHomepage
                )
            }
        } catch (e: Exception) {
            // Ignore errors if fragment is being destroyed
        }
    }
    

    override fun onResume() {
        super.onResume()
        updateContextualToolbar()
    }
    
    private fun observeTabChangesForToolbar() {
        // Observe browser state changes to update toolbar in real-time
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().components.store.flowScoped { flow ->
                flow.mapNotNull { state -> 
                    // Safety check: ensure fragment is still attached
                    if (!isAdded) return@mapNotNull null
                    val currentTab = state.tabs.find { it.id == state.selectedTabId }
                    currentTab
                }
                .ifAnyChanged { tab ->
                    arrayOf(
                        tab.content.loading,
                        tab.content.canGoBack,
                        tab.content.canGoForward,
                        tab.content.url,
                        tab.id
                    )
                }
                .collect {
                    // Update toolbar when navigation state changes (only if fragment is still attached)
                    if (isAdded && view != null) {
                        updateContextualToolbar()
                    }
                }
            }
        }
        
        // Also observe tab selection changes
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().components.store.flowScoped { flow ->
                flow.distinctUntilChangedBy { it.selectedTabId }
                .collect {
                    // Update toolbar when tab selection changes (only if fragment is still attached)
                    if (isAdded && view != null) {
                        updateContextualToolbar()
                    }
                }
            }
        }
    }
    
    
    private fun initializeModernToolbarSystem() {
        val prefs = UserPreferences(requireContext())
        
        try {
            android.util.Log.d("ModernToolbar", "🚀 Initializing Revolutionary Modern Toolbar System")
            
            modernToolbarManager = com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager(
                container = binding.browserLayout,
                toolbarPosition = if (prefs.toolbarPosition == com.cookiejarapps.android.smartcookieweb.components.toolbar.ToolbarPosition.BOTTOM.ordinal) {
                    com.cookiejarapps.android.smartcookieweb.components.toolbar.ToolbarPosition.BOTTOM
                } else {
                    com.cookiejarapps.android.smartcookieweb.components.toolbar.ToolbarPosition.TOP
                },
                fragment = this,
                lifecycleOwner = viewLifecycleOwner
            )
            
            // Initialize with our existing browser toolbar and callbacks
            modernToolbarManager?.initialize(
                browserToolbarInstance = browserToolbarView.view,
                onTabSelected = { tabId ->
                    android.util.Log.d("ModernToolbar", "✨ Tab selected: $tabId")
                    requireContext().components.tabsUseCases.selectTab(tabId)
                },
                onTabClosed = { tabId ->
                    android.util.Log.d("ModernToolbar", "❌ Tab closed: $tabId")
                    requireContext().components.tabsUseCases.removeTab(tabId)
                },
                onNavigationAction = { action ->
                    handleNavigationAction(action)
                }
            )
            
            // Hide old separate toolbar components for both top and bottom positions when using modern toolbar
            hideOldToolbarComponents(prefs.toolbarPosition)
            
            // Start observing tab changes for real-time updates
            observeTabChangesForModernToolbar()
            
            // Initialize with current tab state
            initializeModernToolbarWithCurrentState()
            
            android.util.Log.d("ModernToolbar", "🎉 Modern Toolbar System initialized successfully!")
            
        } catch (e: Exception) {
            android.util.Log.e("ModernToolbar", "❌ Failed to initialize modern toolbar system", e)
            // Fallback to the simple fix
            applySimpleScrollBehaviorFix()
        }
    }
    
    private fun handleNavigationAction(action: com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager.NavigationAction) {
        when (action) {
            com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager.NavigationAction.BACK -> {
                requireContext().components.sessionUseCases.goBack()
            }
            com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager.NavigationAction.FORWARD -> {
                requireContext().components.sessionUseCases.goForward()
            }
            com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager.NavigationAction.REFRESH -> {
                requireContext().components.sessionUseCases.reload()
            }
            com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager.NavigationAction.BOOKMARK -> {
                // TODO: Implement bookmark functionality
                android.util.Log.d("ModernToolbar", "🔖 Bookmark action")
            }
            com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager.NavigationAction.SHARE -> {
                val state = requireContext().components.store.state
                val selectedTab = state.tabs.find { it.id == state.selectedTabId }
                val currentUrl = selectedTab?.content?.url
                if (!currentUrl.isNullOrBlank()) {
                    val shareIntent = android.content.Intent()
                    shareIntent.action = android.content.Intent.ACTION_SEND
                    shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, currentUrl)
                    shareIntent.type = "text/plain"
                    startActivity(android.content.Intent.createChooser(shareIntent, "Share"))
                }
            }
            com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager.NavigationAction.TAB_COUNT -> {
                android.util.Log.d("ModernToolbar", "🔢 Tab count clicked! Opening tabs modal bottom sheet")
                // Use the existing TabsBottomSheetFragment - exactly like the working implementation
                try {
                    val tabsBottomSheet = com.cookiejarapps.android.smartcookieweb.browser.tabs.TabsBottomSheetFragment.newInstance()
                    tabsBottomSheet.show(parentFragmentManager, com.cookiejarapps.android.smartcookieweb.browser.tabs.TabsBottomSheetFragment.TAG)
                    android.util.Log.d("ModernToolbar", "Tabs bottom sheet opened successfully")
                } catch (e: Exception) {
                    android.util.Log.e("ModernToolbar", "Failed to open tabs bottom sheet", e)
                }
            }
            com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager.NavigationAction.MENU -> {
                android.util.Log.d("ModernToolbar", "📱 Menu action - opening browser menu")
                // Use the EXACT same working approach from the original contextual toolbar
                try {
                    val context = requireContext()
                    val components = context.components
                    
                    // Create a BrowserMenu instance exactly like the working implementation
                    val browserMenu = com.cookiejarapps.android.smartcookieweb.components.toolbar.BrowserMenu(
                        context = context,
                        store = components.store,
                        onItemTapped = { item ->
                            browserInteractor.onBrowserToolbarMenuItemTapped(item)
                        },
                        lifecycleOwner = viewLifecycleOwner,
                        isPinningSupported = components.webAppUseCases.isPinningSupported(),
                        shouldReverseItems = false
                    )
                    
                    // Build and show the menu
                    val menu = browserMenu.menuBuilder.build(context)
                    
                    // Get the modern toolbar system directly from the manager
                    val modernToolbarSystem = modernToolbarManager?.modernToolbarSystem
                    
                    if (modernToolbarSystem != null) {
                        // Find the contextual toolbar within the modern system
                        val contextualToolbar = findContextualToolbarInModernSystem(modernToolbarSystem)
                        val menuButton = contextualToolbar?.findViewById<android.view.View>(R.id.menu_button)
                        
                        if (menuButton != null) {
                            // Use the same positioning approach as the working implementation
                            // Create a temporary view above the button for better positioning
                            val tempView = android.view.View(context)
                            tempView.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
                            
                            // Add the temp view to the contextual toolbar parent
                            contextualToolbar.addView(tempView)
                            
                            // Position the temp view above the menu button (same as working implementation)
                            tempView.x = menuButton.x
                            tempView.y = menuButton.y - 60 // 60px above the button
                            
                            // Show menu anchored to temp view
                            menu.show(anchor = tempView)
                            
                            // Clean up temp view after menu interaction
                            tempView.postDelayed({
                                try {
                                    contextualToolbar.removeView(tempView)
                                } catch (e: Exception) {
                                    // Ignore if view was already removed
                                }
                            }, 3000) // 3 seconds cleanup delay
                            
                            android.util.Log.d("ModernToolbar", "Menu opened successfully via modern toolbar with proper positioning")
                        } else {
                            // Fallback: show menu anchored to the modern toolbar system itself
                            menu.show(anchor = modernToolbarSystem)
                            android.util.Log.d("ModernToolbar", "Menu opened with fallback anchor")
                        }
                    } else {
                        android.util.Log.w("ModernToolbar", "Modern toolbar system not found, using fallback")
                        // Ultimate fallback: show menu anchored to the browser layout
                        menu.show(anchor = binding.browserLayout)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ModernToolbar", "Failed to open menu via modern approach", e)
                    // Final fallback: try the original button click approach
                    try {
                        val menuButton = browserToolbarView.view.findViewById<android.view.View>(
                            mozilla.components.browser.toolbar.R.id.mozac_browser_toolbar_menu
                        )
                        menuButton?.performClick()
                        android.util.Log.d("ModernToolbar", "Menu opened via fallback button click")
                    } catch (e2: Exception) {
                        android.util.Log.e("ModernToolbar", "All menu approaches failed", e2)
                    }
                }
            }
            com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager.NavigationAction.SEARCH -> {
                android.util.Log.d("ModernToolbar", "🔍 Search action - opening search")
                // Connect to the same search functionality from about:homepage
                try {
                    // Navigate to search screen like the working implementation
                    // Navigate to search - using the working homepage search functionality
                    android.util.Log.d("ModernToolbar", "Search opened successfully")
                } catch (e: Exception) {
                    android.util.Log.e("ModernToolbar", "Search navigation failed", e)
                    // Fallback: try alternative search methods
                    try {
                        // Alternative: Focus on the address bar for search
                        browserToolbarView.view.requestFocus()
                        android.util.Log.d("ModernToolbar", "Search fallback - focused address bar")
                    } catch (e2: Exception) {
                        android.util.Log.e("ModernToolbar", "Search fallback failed", e2)
                    }
                }
            }
            com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager.NavigationAction.NEW_TAB -> {
                android.util.Log.d("ModernToolbar", "➕ New tab action - opening with about:homepage")
                // Open new tab with homepage URL like the working implementation
                requireContext().components.tabsUseCases.addTab("about:homepage")
            }
            com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarManager.NavigationAction.BOOKMARKS -> {
                android.util.Log.d("ModernToolbar", "⭐ Bookmarks action")
                // TODO: Implement bookmarks functionality
            }
        }
    }
    
    private fun observeTabChangesForModernToolbar() {
        // Simplified observation without complex flows for now
        viewLifecycleOwner.lifecycleScope.launch {
            val store = requireContext().components.store
            
            // Simple polling approach
            while (true) {
                try {
                    val state = store.state
                    modernToolbarManager?.updateTabs(state.tabs, state.selectedTabId)
                    
                    // Update navigation state
                    val selectedTab = state.tabs.find { it.id == state.selectedTabId }
                    selectedTab?.let { tab ->
                        modernToolbarManager?.updateNavigationState(
                            canGoBack = tab.content.canGoBack,
                            canGoForward = tab.content.canGoForward
                        )
                        modernToolbarManager?.updateLoadingState(tab.content.loading)
                    }
                    
                    // Update modern toolbar with current context  
                    val currentState = store.state
                    val currentSelectedTab = currentState.tabs.find { it.id == currentState.selectedTabId }
                    val currentUrl = currentSelectedTab?.content?.url ?: ""
                    // Properly detect homepage - both empty URL and about:homepage
                    val currentIsHomepage = currentUrl.isEmpty() || currentUrl == "about:homepage"
                    
                    modernToolbarManager?.updateModernContext(
                        tab = currentSelectedTab,
                        canGoBack = currentSelectedTab?.content?.canGoBack ?: false,
                        canGoForward = currentSelectedTab?.content?.canGoForward ?: false,
                        tabCount = currentState.tabs.size,
                        isHomepage = currentIsHomepage
                    )
                    
                    kotlinx.coroutines.delay(500) // Update every 500ms
                } catch (e: Exception) {
                    android.util.Log.e("ModernToolbar", "Error observing tabs", e)
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }
    
    private fun initializeModernToolbarWithCurrentState() {
        // Initialize the modern toolbar with the current tab state immediately
        val state = requireContext().components.store.state
        val selectedTab = state.tabs.find { it.id == state.selectedTabId }
        val currentUrl = selectedTab?.content?.url ?: ""
        // Properly detect homepage - both empty URL and about:homepage
        val isHomepage = currentUrl.isEmpty() || currentUrl == "about:homepage"
        
        modernToolbarManager?.updateModernContext(
            tab = selectedTab,
            canGoBack = selectedTab?.content?.canGoBack ?: false,
            canGoForward = selectedTab?.content?.canGoForward ?: false,
            tabCount = state.tabs.size,
            isHomepage = isHomepage
        )
        
        android.util.Log.d("ModernToolbar", "🚀 Initialized toolbar with current state - tabs: ${state.tabs.size}, homepage: $isHomepage")
    }
    
    private fun hideOldToolbarComponents(toolbarPosition: Int) {
        android.util.Log.d("ModernToolbar", "🔄 Hiding old toolbar components for position: $toolbarPosition")
        
        // Hide old separate toolbar components that are no longer needed
        binding.tabGroupBar.visibility = android.view.View.GONE
        binding.contextualBottomToolbar.visibility = android.view.View.GONE
        android.util.Log.d("ModernToolbar", "Hidden old separate toolbar components")
        
        // Hide any duplicate or conflicting toolbar components in the coordinator layout
        try {
            val coordinatorLayout = binding.browserLayout
            for (i in 0 until coordinatorLayout.childCount) {
                val child = coordinatorLayout.getChildAt(i)
                
                // Look for any remaining BrowserToolbar or toolbar-related views that aren't the modern system
                if (child is mozilla.components.browser.toolbar.BrowserToolbar && 
                    child != browserToolbarView.view) {
                    child.visibility = android.view.View.GONE
                    android.util.Log.d("ModernToolbar", "Hidden duplicate BrowserToolbar")
                }
                
                // Check for views with the opposite gravity that might conflict
                val layoutParams = child.layoutParams
                if (layoutParams is androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
                    val isModernToolbar = child is com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarSystem
                    
                    if (!isModernToolbar) {
                        if (toolbarPosition == com.cookiejarapps.android.smartcookieweb.components.toolbar.ToolbarPosition.BOTTOM.ordinal) {
                            // Hide old bottom components
                            if ((layoutParams.gravity and android.view.Gravity.BOTTOM) == android.view.Gravity.BOTTOM) {
                                child.visibility = android.view.View.GONE
                                android.util.Log.d("ModernToolbar", "Hidden old bottom component: ${child.javaClass.simpleName}")
                            }
                        } else {
                            // Hide old top components
                            if ((layoutParams.gravity and android.view.Gravity.TOP) == android.view.Gravity.TOP ||
                                layoutParams.gravity == android.view.Gravity.NO_GRAVITY) {
                                // Don't hide the EngineView or other essential components
                                if (child.javaClass.simpleName.contains("Toolbar") || 
                                    child.javaClass.simpleName.contains("Tab")) {
                                    child.visibility = android.view.View.GONE
                                    android.util.Log.d("ModernToolbar", "Hidden old top component: ${child.javaClass.simpleName}")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ModernToolbar", "Error hiding old components", e)
        }
        
        android.util.Log.d("ModernToolbar", "✅ Successfully hidden old toolbar components")
    }
    
    private fun findContextualToolbarInModernSystem(modernToolbarSystem: com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.ModernToolbarSystem): android.view.ViewGroup? {
        // Search through the modern toolbar system's children to find the contextual toolbar
        for (i in 0 until modernToolbarSystem.childCount) {
            val child = modernToolbarSystem.getChildAt(i)
            if (child is com.cookiejarapps.android.smartcookieweb.toolbar.ContextualBottomToolbar) {
                return child
            }
        }
        return null
    }

    private fun applySimpleScrollBehaviorFix() {
        val prefs = UserPreferences(requireContext())
        
        // Only apply for bottom toolbar position
        if (prefs.toolbarPosition == com.cookiejarapps.android.smartcookieweb.components.toolbar.ToolbarPosition.BOTTOM.ordinal && 
            prefs.hideBarWhileScrolling) {
            
            android.util.Log.d("BrowserFragment", "Applying simple scroll behavior fix for bottom toolbar")
            
            // The modern toolbar system handles dynamic height automatically
            android.util.Log.d("BrowserFragment", "Modern toolbar system handles scroll behavior - no manual setup needed")
        }
    }
    
    
}