package com.cookiejarapps.android.smartcookieweb.browser.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.cookiejarapps.android.smartcookieweb.BrowserActivity
import com.cookiejarapps.android.smartcookieweb.R
import com.cookiejarapps.android.smartcookieweb.browser.BrowsingMode
import com.cookiejarapps.android.smartcookieweb.browser.BrowsingModeManager
import com.cookiejarapps.android.smartcookieweb.databinding.FragmentTabsBottomSheetBinding
import com.cookiejarapps.android.smartcookieweb.ext.components
import com.cookiejarapps.android.smartcookieweb.preferences.UserPreferences
import com.cookiejarapps.android.smartcookieweb.settings.HomepageChoice
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.loader.ThumbnailLoader

/**
 * Bottom sheet dialog for managing tabs - clean, mobile-friendly interface
 */
class TabsBottomSheetFragment : BottomSheetDialogFragment() {
    
    private var _binding: FragmentTabsBottomSheetBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var browsingModeManager: BrowsingModeManager
    private lateinit var configuration: Configuration
    private lateinit var tabsAdapter: TabListAdapter
    private var isInitializing = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTabsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupTabsAdapter()
        // Only update display after adapter is set up
        updateTabsDisplay()
    }
    
    private fun setupUI() {
        browsingModeManager = (activity as BrowserActivity).browsingModeManager
        configuration = Configuration(
            if (browsingModeManager.mode == BrowsingMode.Normal) 
                BrowserTabType.NORMAL 
            else 
                BrowserTabType.PRIVATE
        )
        
        // Header elements removed - cleaner UI
        
        // Setup new tab button
        binding.newTabButton.setOnClickListener {
            addNewTab()
        }
        
        // Setup tab mode toggle (Normal/Private)
        binding.tabModeToggle.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                // Only respond to user interactions, not programmatic changes during initialization
                if (isInitializing) return
                
                when (tab.position) {
                    0 -> {
                        // Switch to normal browsing mode
                        switchToTabMode(BrowserTabType.NORMAL)
                    }
                    1 -> {
                        // Switch to private browsing mode
                        switchToTabMode(BrowserTabType.PRIVATE)
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        
        // Select current mode after everything is set up
        // This will be done in updateTabsDisplay() after adapter is ready
    }
    
    private fun setupTabsAdapter() {
        val thumbnailLoader = ThumbnailLoader(requireContext().components.thumbnailStorage)
        
        // Create adapter with TabsTray.Delegate
        tabsAdapter = TabListAdapter(
            thumbnailLoader = thumbnailLoader,
            delegate = object : mozilla.components.browser.tabstray.TabsTray.Delegate {
                override fun onTabSelected(tab: TabSessionState, source: String?) {
                    selectTab(tab)
                }
                
                override fun onTabClosed(tab: TabSessionState, source: String?) {
                    closeTab(tab)
                }
            }
        )
        
        binding.tabsRecyclerView.adapter = tabsAdapter
        
        // Setup layout manager
        val layoutManager = if (UserPreferences(requireContext()).showTabsInGrid) {
            GridLayoutManager(requireContext(), 2)
        } else {
            LinearLayoutManager(requireContext())
        }
        
        binding.tabsRecyclerView.layoutManager = layoutManager
    }
    
    private fun updateTabsDisplay() {
        // Safety check to prevent crash
        if (!::tabsAdapter.isInitialized) {
            return
        }
        
        val store = requireContext().components.store.state
        val tabs = if (configuration.browserTabType == BrowserTabType.NORMAL) {
            store.tabs.filter { !it.content.private }
        } else {
            store.tabs.filter { it.content.private }
        }
        
        // Update adapter with proper method
        tabsAdapter.updateTabs(tabs, null, store.selectedTabId)
        
        // Show/hide empty state
        if (tabs.isEmpty()) {
            binding.tabsRecyclerView.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.VISIBLE
        } else {
            binding.tabsRecyclerView.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
        }
        
        // Set the correct tab selection (only once after adapter is ready)
        if (isInitializing) {
            binding.tabModeToggle.selectTab(
                binding.tabModeToggle.getTabAt(browsingModeManager.mode.ordinal)
            )
            // Mark initialization as complete after first setup
            isInitializing = false
        }
    }
    
    private fun switchToTabMode(targetMode: BrowserTabType) {
        val store = requireContext().components.store.state
        val targetTabs = if (targetMode == BrowserTabType.NORMAL) {
            store.tabs.filter { !it.content.private }
        } else {
            store.tabs.filter { it.content.private }
        }
        
        // Update browsing mode and configuration
        browsingModeManager.mode = if (targetMode == BrowserTabType.NORMAL) {
            BrowsingMode.Normal
        } else {
            BrowsingMode.Private
        }
        configuration = Configuration(targetMode)
        
        if (targetTabs.isEmpty()) {
            // No tabs in target mode - create a new one
            addNewTab()
            return // addNewTab() will dismiss the bottom sheet
        } else {
            // Find the most recently selected tab in the target mode, or fall back to last tab
            val currentSelectedTabId = store.selectedTabId
            val currentSelectedTab = store.tabs.find { it.id == currentSelectedTabId }
            val targetTab = if (currentSelectedTab != null && targetTabs.contains(currentSelectedTab)) {
                // If currently selected tab is in target mode, keep it selected
                currentSelectedTab
            } else {
                // Otherwise, find the most recent tab in target mode or use the last one
                targetTabs.maxByOrNull { it.lastAccess } ?: targetTabs.last()
            }
            
            requireContext().components.tabsUseCases.selectTab(targetTab.id)
            
            // Update the display
            updateTabsDisplay()
        }
    }
    
    private fun addNewTab() {
        val isPrivate = configuration.browserTabType == BrowserTabType.PRIVATE
        
        val homepage = when (UserPreferences(requireContext()).homepageType) {
            HomepageChoice.VIEW.ordinal -> "about:homepage"
            HomepageChoice.BLANK_PAGE.ordinal -> "about:blank"
            HomepageChoice.CUSTOM_PAGE.ordinal -> UserPreferences(requireContext()).customHomepageUrl
            else -> "about:homepage"
        }
        
        requireContext().components.tabsUseCases.addTab.invoke(
            homepage,
            selectTab = true,
            private = isPrivate
        )
        
        dismiss()
    }
    
    private fun selectTab(tab: TabSessionState) {
        // Select the tab first
        requireContext().components.tabsUseCases.selectTab(tab.id)
        
        // Update browsing mode if needed
        if (tab.content.private && browsingModeManager.mode == BrowsingMode.Normal) {
            browsingModeManager.mode = BrowsingMode.Private
        } else if (!tab.content.private && browsingModeManager.mode == BrowsingMode.Private) {
            browsingModeManager.mode = BrowsingMode.Normal
        }
        
        // Navigate properly based on tab content (same logic as original TabsTrayFragment)
        if (tab.content.url == "about:homepage") {
            // Homepage will not correctly set private / normal mode, so reload
            requireContext().components.sessionUseCases.reload(tab.id)
        } else {
            // For loaded content, navigate to browser fragment
            try {
                val navController = requireActivity().findNavController(com.cookiejarapps.android.smartcookieweb.R.id.container)
                if (navController.currentDestination?.id == com.cookiejarapps.android.smartcookieweb.R.id.browserFragment) {
                    // Already on browser fragment, just dismiss
                } else if (!navController.popBackStack(com.cookiejarapps.android.smartcookieweb.R.id.browserFragment, false)) {
                    // Navigate to browser fragment
                    navController.navigate(com.cookiejarapps.android.smartcookieweb.R.id.browserFragment)
                }
            } catch (e: Exception) {
                // Fallback: just reload the tab
                requireContext().components.sessionUseCases.reload(tab.id)
            }
        }
        
        dismiss()
    }
    
    private fun closeTab(tab: TabSessionState) {
        val store = requireContext().components.store.state
        val tabs = if (configuration.browserTabType == BrowserTabType.NORMAL) {
            store.tabs.filter { !it.content.private }
        } else {
            store.tabs.filter { it.content.private }
        }
        
        // Handle tab selection after closing
        if (tabs.size > 1 && store.selectedTabId == tab.id) {
            val tabIndex = tabs.indexOfFirst { it.id == tab.id }
            val nextTab = if (tabIndex == 0) tabs[1] else tabs[tabIndex - 1]
            requireContext().components.tabsUseCases.selectTab(nextTab.id)
        }
        
        // Remove the tab
        requireContext().components.tabsUseCases.removeTab(tab.id)
        
        // Immediately update the display to show the tab was closed
        updateTabsDisplay()
        
        // Handle app exit if no tabs left
        if (tabs.size == 1) {
            if (configuration.browserTabType == BrowserTabType.NORMAL) {
                requireActivity().finishAndRemoveTask()
            } else {
                // Switch to normal mode
                browsingModeManager.mode = BrowsingMode.Normal
                val normalTabs = store.tabs.filter { !it.content.private }
                if (normalTabs.isNotEmpty()) {
                    requireContext().components.tabsUseCases.selectTab(normalTabs.last().id)
                }
            }
            dismiss()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        const val TAG = "TabsBottomSheetFragment"
        
        fun newInstance(): TabsBottomSheetFragment {
            return TabsBottomSheetFragment()
        }
    }
}