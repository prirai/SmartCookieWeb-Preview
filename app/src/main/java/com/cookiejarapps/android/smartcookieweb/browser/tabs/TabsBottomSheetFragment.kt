package com.cookiejarapps.android.smartcookieweb.browser.tabs

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookiejarapps.android.smartcookieweb.BrowserActivity
import com.cookiejarapps.android.smartcookieweb.R
import com.cookiejarapps.android.smartcookieweb.browser.BrowsingMode
import com.cookiejarapps.android.smartcookieweb.browser.BrowsingModeManager
import com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.TabIsland
import com.cookiejarapps.android.smartcookieweb.components.toolbar.modern.TabIslandManager
import com.cookiejarapps.android.smartcookieweb.databinding.FragmentTabsBottomSheetBinding
import com.cookiejarapps.android.smartcookieweb.ext.components
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

/**
 * Modern bottom sheet dialog for managing tabs with tab islands support
 * Shows tab islands in vertical layout similar to toolbar but stacked vertically
 * Supports drag-and-drop for organizing tabs into islands
 */
class TabsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentTabsBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var browsingModeManager: BrowsingModeManager
    private lateinit var configuration: Configuration
    private lateinit var tabsAdapter: TabIslandsVerticalAdapter
    private lateinit var islandManager: TabIslandManager
    private var isInitializing = true
    private var itemTouchHelper: ItemTouchHelper? = null

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

        islandManager = TabIslandManager.getInstance(requireContext())
        setupUI()
        setupTabsAdapter()
        setupDragAndDrop()
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
        binding.newTabButton.apply {
            setOnClickListener { addNewTab() }
            contentDescription = getString(R.string.new_tab)
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

        // Add content descriptions for accessibility
        binding.tabModeToggle.getTabAt(0)?.contentDescription = getString(R.string.tabs_normal)
        binding.tabModeToggle.getTabAt(1)?.contentDescription = getString(R.string.tabs_private)
        binding.dragHandle.contentDescription = getString(R.string.drag_handle_description)
    }

    private fun setupTabsAdapter() {
        tabsAdapter = TabIslandsVerticalAdapter(
            context = requireContext(),
            onTabClick = { tabId -> selectTab(tabId) },
            onTabClose = { tabId -> closeTab(tabId) },
            onIslandHeaderClick = { islandId -> toggleIslandExpanded(islandId) },
            onIslandLongPress = { islandId -> showIslandOptions(islandId) },
            onUngroupedTabLongPress = { tabId -> showUngroupedTabOptions(tabId) }
        )

        binding.tabsRecyclerView.apply {
            adapter = tabsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = true
            setHasFixedSize(false)
            contentDescription = getString(R.string.tabs_list_description)
        }
    }

    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            private var draggedViewHolder: RecyclerView.ViewHolder? = null
            private var dropTargetPosition: Int = -1

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition

                if (fromPosition == -1 || toPosition == -1) return false

                // Get the items being moved
                val fromItem = tabsAdapter.getItemAt(fromPosition)
                val toItem = tabsAdapter.getItemAt(toPosition)

                // Only allow moving tabs (not headers or collapsed islands)
                if (fromItem !is TabIslandsVerticalAdapter.ListItem.TabInIsland &&
                    fromItem !is TabIslandsVerticalAdapter.ListItem.UngroupedTab
                ) {
                    return false
                }

                dropTargetPosition = toPosition
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not implementing swipe
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                when (actionState) {
                    ItemTouchHelper.ACTION_STATE_DRAG -> {
                        draggedViewHolder = viewHolder
                        viewHolder?.itemView?.apply {
                            // Animate elevation and scale
                            animate()
                                .scaleX(1.05f)
                                .scaleY(1.05f)
                                .alpha(0.9f)
                                .setDuration(100)
                                .start()
                            elevation = 8f * resources.displayMetrics.density
                        }
                    }

                    ItemTouchHelper.ACTION_STATE_IDLE -> {
                        draggedViewHolder?.itemView?.apply {
                            // Reset view
                            animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .alpha(1.0f)
                                .setDuration(100)
                                .start()
                            elevation = 0f
                        }

                        // Handle drop
                        if (dropTargetPosition != -1) {
                            handleDrop(draggedViewHolder, dropTargetPosition)
                        }

                        draggedViewHolder = null
                        dropTargetPosition = -1
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }

            override fun onChildDraw(
                canvas: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                    // Highlight drop targets
                    highlightDropTargets(recyclerView, viewHolder, dY)
                }

                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun isLongPressDragEnabled(): Boolean = true
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(binding.tabsRecyclerView)
    }

    private fun highlightDropTargets(
        recyclerView: RecyclerView,
        draggedViewHolder: RecyclerView.ViewHolder,
        dY: Float
    ) {
        val draggedPosition = draggedViewHolder.bindingAdapterPosition
        if (draggedPosition == -1) return

        val draggedItem = tabsAdapter.getItemAt(draggedPosition)
        if (draggedItem !is TabIslandsVerticalAdapter.ListItem.TabInIsland &&
            draggedItem !is TabIslandsVerticalAdapter.ListItem.UngroupedTab
        ) {
            return
        }

        // Find and highlight potential drop targets
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val viewHolder = recyclerView.getChildViewHolder(child)
            val position = viewHolder.bindingAdapterPosition

            if (position == -1) continue

            val item = tabsAdapter.getItemAt(position)

            // Highlight island headers and ungrouped header
            val shouldHighlight = when (item) {
                is TabIslandsVerticalAdapter.ListItem.ExpandedIslandHeader -> true
                is TabIslandsVerticalAdapter.ListItem.CollapsedIsland -> true
                is TabIslandsVerticalAdapter.ListItem.UngroupedHeader -> true
                else -> false
            }

            if (shouldHighlight) {
                val highlightColor = ContextCompat.getColor(
                    requireContext(),
                    R.color.drop_target_highlight
                )
                child.setBackgroundColor(highlightColor)
            } else {
                child.background = null
            }
        }
    }

    private fun handleDrop(viewHolder: RecyclerView.ViewHolder?, targetPosition: Int) {
        if (viewHolder == null || targetPosition == -1) return

        val sourcePosition = viewHolder.bindingAdapterPosition
        if (sourcePosition == -1) return

        val sourceItem = tabsAdapter.getItemAt(sourcePosition)
        val targetItem = tabsAdapter.getItemAt(targetPosition)

        // Extract tab ID from source
        val tabId = when (sourceItem) {
            is TabIslandsVerticalAdapter.ListItem.TabInIsland -> sourceItem.tab.id
            is TabIslandsVerticalAdapter.ListItem.UngroupedTab -> sourceItem.tab.id
            else -> return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            when (targetItem) {
                is TabIslandsVerticalAdapter.ListItem.ExpandedIslandHeader -> {
                    // Add tab to island
                    islandManager.addTabToIsland(tabId, targetItem.island.id)
                    animateItemMove(sourcePosition, targetPosition)
                    updateTabsDisplay()
                }

                is TabIslandsVerticalAdapter.ListItem.CollapsedIsland -> {
                    // Add tab to collapsed island
                    islandManager.addTabToIsland(tabId, targetItem.island.id)
                    animateItemMove(sourcePosition, targetPosition)
                    updateTabsDisplay()
                }

                is TabIslandsVerticalAdapter.ListItem.UngroupedHeader -> {
                    // Remove tab from island
                    val currentIsland = islandManager.getIslandForTab(tabId)
                    if (currentIsland != null) {
                        islandManager.removeTabFromIsland(tabId, currentIsland.id)
                        animateItemMove(sourcePosition, targetPosition)
                        updateTabsDisplay()
                    }
                }

                else -> {
                    // Reordering within same island or ungrouped section
                    // This could be implemented for fine-grained reordering
                }
            }
        }
    }

    private fun animateItemMove(fromPosition: Int, toPosition: Int) {
        // Smooth animation for item movement
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 200
        animator.addUpdateListener {
            binding.tabsRecyclerView.invalidate()
        }
        animator.start()
    }

    private fun updateTabsDisplay() {
        if (!::tabsAdapter.isInitialized) return

        val store = requireContext().components.store.state
        val tabs = if (configuration.browserTabType == BrowserTabType.NORMAL) {
            store.tabs.filter { !it.content.private }
        } else {
            store.tabs.filter { it.content.private }
        }

        // Get all islands from island manager
        val allIslands = islandManager.getAllIslands()

        // Filter islands that have tabs in current mode
        val islandsWithTabs = allIslands.filter { island ->
            island.tabIds.any { tabId -> tabs.any { it.id == tabId } }
        }

        // Get tabs not in any island
        val tabsInIslands = islandsWithTabs.flatMap { it.tabIds }.toSet()
        val ungroupedTabs = tabs.filter { it.id !in tabsInIslands }

        // Animate the update
        binding.tabsRecyclerView.animate()
            .alpha(0.95f)
            .setDuration(50)
            .withEndAction {
                tabsAdapter.updateData(
                    islands = islandsWithTabs,
                    ungroupedTabs = ungroupedTabs,
                    allTabs = tabs,
                    selectedTabId = store.selectedTabId
                )

                // Scroll to selected tab
                store.selectedTabId?.let { selectedId ->
                    val position = tabsAdapter.findPositionOfTab(selectedId)
                    if (position != -1) {
                        binding.tabsRecyclerView.post {
                            (binding.tabsRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                                position,
                                100
                            )
                        }
                    }
                }

                binding.tabsRecyclerView.animate()
                    .alpha(1f)
                    .setDuration(50)
                    .start()
            }
            .start()

        // Show/hide empty state
        if (tabs.isEmpty()) {
            binding.tabsRecyclerView.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.VISIBLE
        } else {
            binding.tabsRecyclerView.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
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

        // Remove from island if it was in one
        viewLifecycleOwner.lifecycleScope.launch {
            val island = islandManager.getIslandForTab(tabId)
            if (island != null) {
                islandManager.removeTabFromIsland(tabId, island.id)
            }
            updateTabsDisplay()
        }
    }

    private fun toggleIslandExpanded(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        // Animate the collapse/expand
        val wasCollapsed = island.isCollapsed
        // Use bottom sheet specific method that allows multiple expanded islands
        islandManager.toggleIslandCollapseBottomSheet(islandId)

        // Use smooth animation
        if (wasCollapsed) {
            // Expanding - animate items appearing
            updateTabsDisplay()
        } else {
            // Collapsing - animate items disappearing
            updateTabsDisplay()
        }
    }

    private fun showIslandOptions(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        val options = arrayOf(
            getString(R.string.tab_island_rename),
            getString(R.string.tab_island_change_color),
            getString(R.string.tab_island_ungroup),
            getString(R.string.tab_island_close_all)
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(if (island.name.isNotBlank()) island.name else getString(R.string.tab_island_name))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameIslandDialog(islandId)
                    1 -> showChangeColorDialog(islandId)
                    2 -> ungroupIsland(islandId)
                    3 -> closeAllTabsInIsland(islandId)
                }
            }
            .show()
    }

    private fun showRenameIslandDialog(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        val input = android.widget.EditText(requireContext()).apply {
            setText(island.name)
            hint = getString(R.string.tab_island_name)
            contentDescription = getString(R.string.tab_island_rename)
            selectAll()
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.tab_island_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString()
                islandManager.renameIsland(islandId, newName)
                updateTabsDisplay()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showChangeColorDialog(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        val colors = TabIsland.DEFAULT_COLORS
        val colorNames = arrayOf(
            "Red", "Green", "Blue", "Orange", "Light Green",
            "Yellow", "Grey", "Pink", "Purple", "Cyan", "Lime", "Deep Orange"
        )

        // Create a custom adapter to show colored circles
        val adapter = object : android.widget.ArrayAdapter<String>(
            requireContext(),
            android.R.layout.select_dialog_item,
            colorNames
        ) {
            override fun getView(
                position: Int,
                convertView: android.view.View?,
                parent: android.view.ViewGroup
            ): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<android.widget.TextView>(android.R.id.text1)

                // Create colored indicator
                val colorCircle = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(colors[position])
                    setSize(48, 48)
                }

                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(colorCircle, null, null, null)
                textView.compoundDrawablePadding = 32

                return view
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.tab_island_choose_color)
            .setAdapter(adapter) { _, which ->
                islandManager.changeIslandColor(islandId, colors[which])
                updateTabsDisplay()

                // Trigger toolbar refresh by forcing a store state notification
                val store = requireContext().components.store.state
                val selectedTabId = store.selectedTabId
                if (selectedTabId != null) {
                    requireContext().components.tabsUseCases.selectTab(selectedTabId)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun ungroupIsland(islandId: String) {
        islandManager.deleteIsland(islandId)
        updateTabsDisplay()
    }

    private fun closeAllTabsInIsland(islandId: String) {
        val island = islandManager.getIsland(islandId) ?: return

        // Close all tabs in the island
        island.tabIds.forEach { tabId ->
            val store = requireContext().components.store.state
            val tab = store.tabs.find { it.id == tabId }
            if (tab != null) {
                requireContext().components.tabsUseCases.removeTab(tab.id)
            }
        }

        // Island will be cleaned up automatically
        updateTabsDisplay()
    }

    private fun showUngroupedTabOptions(tabId: String) {
        val store = requireContext().components.store.state
        val tab = store.tabs.find { it.id == tabId } ?: return

        val options = arrayOf(
            getString(R.string.tab_island_create),
            getString(R.string.cancel)
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(tab.content.title.ifBlank { getString(R.string.tab_island_name) })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCreateIslandDialog(tabId)
                }
            }
            .show()
    }

    private fun showCreateIslandDialog(tabId: String) {
        val store = requireContext().components.store.state
        val ungroupedTabs = if (configuration.browserTabType == BrowserTabType.NORMAL) {
            store.tabs.filter { !it.content.private }
        } else {
            store.tabs.filter { it.content.private }
        }.filter { tab -> !islandManager.isTabInIsland(tab.id) }

        val tabNames = ungroupedTabs.map { tab ->
            tab.content.title.ifBlank { tab.content.url }
        }.toTypedArray()

        val selectedTabs = mutableSetOf(tabId)
        val checkedItems = BooleanArray(ungroupedTabs.size) { index ->
            ungroupedTabs[index].id == tabId
        }

        val input = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.tab_island_name)
        }

        val dialogLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.tab_island_create)
            .setView(dialogLayout)
            .setMultiChoiceItems(tabNames, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    selectedTabs.add(ungroupedTabs[which].id)
                } else {
                    selectedTabs.remove(ungroupedTabs[which].id)
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (selectedTabs.isNotEmpty()) {
                    val name = input.text.toString()
                    islandManager.createIsland(
                        tabIds = selectedTabs.toList(),
                        name = name.ifBlank { null }
                    )
                    updateTabsDisplay()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        _binding = null
    }
}
