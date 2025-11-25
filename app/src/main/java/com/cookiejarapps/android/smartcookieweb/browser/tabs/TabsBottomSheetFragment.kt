package com.cookiejarapps.android.smartcookieweb.browser.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookiejarapps.android.smartcookieweb.BrowserActivity
import com.cookiejarapps.android.smartcookieweb.R
import com.cookiejarapps.android.smartcookieweb.browser.BrowsingMode
import com.cookiejarapps.android.smartcookieweb.browser.BrowsingModeManager
import com.cookiejarapps.android.smartcookieweb.browser.tabgroups.TabGroupManager
import com.cookiejarapps.android.smartcookieweb.browser.tabgroups.TabGroupWithTabs
import com.cookiejarapps.android.smartcookieweb.databinding.FragmentTabsBottomSheetBinding
import com.cookiejarapps.android.smartcookieweb.ext.components
import com.cookiejarapps.android.smartcookieweb.preferences.UserPreferences
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.loader.ThumbnailLoader

/**
 * Modern bottom sheet dialog for managing tabs with tab groups support
 * Features: collapsing/expanding groups, drag-and-drop tabs between groups
 */
class TabsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentTabsBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var browsingModeManager: BrowsingModeManager
    private lateinit var configuration: Configuration
    private lateinit var tabsAdapter: TabsWithGroupsAdapter
    private lateinit var tabGroupManager: TabGroupManager
    private var isInitializing = true

    companion object {
        const val TAG = "TabsBottomSheetFragment"
        fun newInstance() = TabsBottomSheetFragment()
    }

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

        tabGroupManager = requireContext().components.tabGroupManager
        setupUI()
        setupTabsAdapter()
        updateTabsDisplay()
    }

    override fun onStart() {
        super.onStart()

        // Configure bottom sheet behavior
        val bottomSheetDialog = dialog as com.google.android.material.bottomsheet.BottomSheetDialog
        val behavior = bottomSheetDialog.behavior

        val screenHeight = resources.displayMetrics.heightPixels
        val desiredHeight = (screenHeight * 0.85).toInt()

        behavior.isFitToContents = false
        behavior.peekHeight = desiredHeight
        behavior.expandedOffset = screenHeight - desiredHeight
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        behavior.isHideable = true
        behavior.isDraggable = false

        bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
            val layoutParams = bottomSheet.layoutParams
            layoutParams.height = desiredHeight
            bottomSheet.layoutParams = layoutParams
        }

        bottomSheetDialog.setCancelable(true)
        bottomSheetDialog.setCanceledOnTouchOutside(true)
    }

    private fun setupUI() {
        browsingModeManager = (activity as BrowserActivity).browsingModeManager
        configuration = Configuration(
            if (browsingModeManager.mode == BrowsingMode.Normal)
                BrowserTabType.NORMAL
            else
                BrowserTabType.PRIVATE
        )

        // Setup new tab button
        binding.newTabButton.setOnClickListener {
            addNewTab()
        }

        // Setup tab mode toggle
        binding.tabModeToggle.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (isInitializing) return

                when (tab.position) {
                    0 -> animateTabModeTransition(BrowserTabType.NORMAL)
                    1 -> animateTabModeTransition(BrowserTabType.PRIVATE)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupTabsAdapter() {
        val thumbnailLoader = ThumbnailLoader(requireContext().components.thumbnailStorage)

        tabsAdapter = TabsWithGroupsAdapter(
            context = requireContext(),
            thumbnailLoader = thumbnailLoader,
            onTabClick = { tabId -> selectTab(tabId) },
            onTabClose = { tabId -> closeTab(tabId) },
            onGroupExpand = { groupId -> toggleGroupExpanded(groupId) },
            onTabMovedToGroup = { tabId, targetGroupId -> moveTabToGroup(tabId, targetGroupId) },
            onTabRemovedFromGroup = { tabId -> removeTabFromGroup(tabId) }
        )

        binding.tabsRecyclerView.adapter = tabsAdapter
        binding.tabsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.tabsRecyclerView.isNestedScrollingEnabled = true
        binding.tabsRecyclerView.setHasFixedSize(false)

        // Setup drag and drop
        val itemTouchHelper = ItemTouchHelper(TabDragCallback(tabsAdapter))
        itemTouchHelper.attachToRecyclerView(binding.tabsRecyclerView)

        // Observe tab groups changes
        viewLifecycleOwner.lifecycleScope.launch {
            tabGroupManager.allGroups.collect {
                updateTabsDisplay()
            }
        }
    }

    private fun updateTabsDisplay() {
        if (!::tabsAdapter.isInitialized) return

        val store = requireContext().components.store.state
        val tabs = if (configuration.browserTabType == BrowserTabType.NORMAL) {
            store.tabs.filter { !it.content.private }
        } else {
            store.tabs.filter { it.content.private }
        }

        // Get all groups
        viewLifecycleOwner.lifecycleScope.launch {
            val groups = tabGroupManager.getAllGroups()
            val groupsWithTabs = mutableListOf<TabGroupWithTabs>()

            for (group in groups) {
                val tabIds = tabGroupManager.getTabIdsInGroup(group.id)
                // Only include groups that have tabs in current mode
                val groupTabIds = tabIds.filter { tabId ->
                    tabs.any { it.id == tabId }
                }
                if (groupTabIds.isNotEmpty()) {
                    groupsWithTabs.add(TabGroupWithTabs(group, groupTabIds))
                }
            }

            // Get ungrouped tabs
            val groupedTabIds = groupsWithTabs.flatMap { it.tabIds }.toSet()
            val ungroupedTabs = tabs.filter { it.id !in groupedTabIds }

            tabsAdapter.updateData(
                groups = groupsWithTabs,
                ungroupedTabs = ungroupedTabs,
                selectedTabId = store.selectedTabId
            )

            // Show/hide empty state
            if (tabs.isEmpty()) {
                binding.tabsRecyclerView.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.VISIBLE
            } else {
                binding.tabsRecyclerView.visibility = View.VISIBLE
                binding.emptyStateLayout.visibility = View.GONE
            }
        }

        // Set correct tab mode selection
        if (isInitializing) {
            binding.tabModeToggle.selectTab(
                binding.tabModeToggle.getTabAt(browsingModeManager.mode.ordinal)
            )
            isInitializing = false
        }
    }

    private fun animateTabModeTransition(targetMode: BrowserTabType) {
        binding.tabsRecyclerView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                switchToTabMode(targetMode)
                binding.tabsRecyclerView.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun switchToTabMode(targetMode: BrowserTabType) {
        val store = requireContext().components.store.state
        val targetTabs = if (targetMode == BrowserTabType.NORMAL) {
            store.tabs.filter { !it.content.private }
        } else {
            store.tabs.filter { it.content.private }
        }

        browsingModeManager.mode = if (targetMode == BrowserTabType.NORMAL) {
            BrowsingMode.Normal
        } else {
            BrowsingMode.Private
        }
        configuration = Configuration(targetMode)

        if (targetTabs.isEmpty()) {
            addNewTab()
            return
        } else {
            val currentSelectedTabId = store.selectedTabId
            val currentSelectedTab = store.tabs.find { it.id == currentSelectedTabId }
            val targetTab = if (currentSelectedTab != null && targetTabs.contains(currentSelectedTab)) {
                currentSelectedTab
            } else {
                targetTabs.maxByOrNull { it.lastAccess } ?: targetTabs.last()
            }

            requireContext().components.tabsUseCases.selectTab(targetTab.id)
            updateTabsDisplay()
        }
    }

    private fun addNewTab() {
        val isPrivate = configuration.browserTabType == BrowserTabType.PRIVATE
        val homepage = "about:homepage"

        requireContext().components.tabsUseCases.addTab.invoke(
            homepage,
            selectTab = true,
            private = isPrivate
        )

        dismiss()
    }

    private fun selectTab(tabId: String) {
        val store = requireContext().components.store.state
        val tab = store.tabs.find { it.id == tabId } ?: return

        requireContext().components.tabsUseCases.selectTab(tab.id)

        if (tab.content.private && browsingModeManager.mode == BrowsingMode.Normal) {
            browsingModeManager.mode = BrowsingMode.Private
        } else if (!tab.content.private && browsingModeManager.mode == BrowsingMode.Private) {
            browsingModeManager.mode = BrowsingMode.Normal
        }

        if (tab.content.url == "about:homepage") {
            requireContext().components.sessionUseCases.reload(tab.id)
        } else {
            try {
                val navController = requireActivity().findNavController(R.id.container)
                if (navController.currentDestination?.id != R.id.browserFragment) {
                    if (!navController.popBackStack(R.id.browserFragment, false)) {
                        navController.navigate(R.id.browserFragment)
                    }
                }
            } catch (e: Exception) {
                requireContext().components.sessionUseCases.reload(tab.id)
            }
        }

        dismiss()
    }

    private fun closeTab(tabId: String) {
        val store = requireContext().components.store.state
        val tab = store.tabs.find { it.id == tabId } ?: return

        requireContext().components.tabsUseCases.removeTab(tab.id)

        // Remove from group if it was in one
        viewLifecycleOwner.lifecycleScope.launch {
            tabGroupManager.removeTabFromGroups(tabId)
            updateTabsDisplay()
        }
    }

    private fun toggleGroupExpanded(groupId: String) {
        tabsAdapter.toggleGroupExpanded(groupId)
    }

    private fun moveTabToGroup(tabId: String, targetGroupId: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            // First remove from current group
            tabGroupManager.removeTabFromGroups(tabId)

            // Then add to new group if specified
            if (targetGroupId != null) {
                tabGroupManager.addTabToGroup(tabId, targetGroupId)
            }

            updateTabsDisplay()
        }
    }

    private fun removeTabFromGroup(tabId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            tabGroupManager.removeTabFromGroups(tabId)
            updateTabsDisplay()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
