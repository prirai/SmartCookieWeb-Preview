package com.cookiejarapps.android.smartcookieweb

import android.util.Log
import android.view.View
import com.cookiejarapps.android.smartcookieweb.browser.toolbar.ToolbarGestureHandler
import com.cookiejarapps.android.smartcookieweb.browser.toolbar.WebExtensionToolbarFeature
import com.cookiejarapps.android.smartcookieweb.toolbar.ContextualBottomToolbar
import com.cookiejarapps.android.smartcookieweb.databinding.FragmentBrowserBinding
import com.cookiejarapps.android.smartcookieweb.ext.components
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
        
        
        // Observe tab changes for real-time toolbar updates
        observeTabChangesForToolbar()
    }

    private fun setupContextualBottomToolbar() {
        val toolbar = binding.contextualBottomToolbar
        
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
}