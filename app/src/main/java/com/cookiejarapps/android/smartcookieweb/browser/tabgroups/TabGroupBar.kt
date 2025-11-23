package com.cookiejarapps.android.smartcookieweb.browser.tabgroups

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookiejarapps.android.smartcookieweb.R
import com.cookiejarapps.android.smartcookieweb.databinding.TabGroupBarBinding
import com.cookiejarapps.android.smartcookieweb.ext.components
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.lib.state.ext.flowScoped

/**
 * Horizontal tab group bar that appears above the address bar.
 * Shows current tab groups and allows switching between them.
 */
class TabGroupBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var binding: TabGroupBarBinding
    private var adapter: TabGroupAdapter? = null
    private var tabGroupManager: TabGroupManager? = null
    private var listener: TabGroupBarListener? = null

    init {
        orientation = HORIZONTAL
        binding = TabGroupBarBinding.inflate(LayoutInflater.from(context), this, true)
        setupRecyclerView()
    }

    fun setup(
        tabGroupManager: TabGroupManager,
        lifecycleOwner: LifecycleOwner,
        listener: TabGroupBarListener
    ) {
        this.tabGroupManager = tabGroupManager
        this.listener = listener
        
        adapter = TabGroupAdapter { tabId ->
            listener.onTabSelected(tabId)
        }
        
        binding.tabGroupsRecyclerView.adapter = adapter
        
        // Observe groups and current group changes
        // Observe current group changes
        lifecycleOwner.lifecycleScope.launch {
            tabGroupManager.currentGroup.collect { currentGroup ->
                updateCurrentGroup(currentGroup)
            }
        }
        
        // Observe only selected tab changes to reduce excessive updates
        context.components.store.flowScoped(lifecycleOwner) { flow ->
            flow.map { state -> state.selectedTabId }
                .distinctUntilChanged()
                .collect { selectedTabId: String? ->
                    // Only update if the selected tab actually changed
                    val currentGroup = tabGroupManager?.currentGroup?.value
                    if (currentGroup != null && currentGroup.tabCount > 1 && isVisible) {
                        adapter?.updateCurrentGroup(currentGroup, selectedTabId)
                    }
                }
        }
    }

    private fun setupRecyclerView() {
        binding.tabGroupsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(false)
        }
    }

    private fun updateCurrentGroup(currentGroup: TabGroupWithTabs?) {
        // Only show if current group has multiple tabs
        val shouldShow = currentGroup != null && currentGroup.tabCount > 1
        
        if (shouldShow) {
            // Get selected tab ID from the browser store
            val selectedTabId = context.components.store.state.selectedTabId
            adapter?.updateCurrentGroup(currentGroup, selectedTabId)
            isVisible = true
        } else {
            isVisible = false
        }
    }
    
    /**
     * Force refresh the current group display with updated selection state.
     */
    fun refreshSelection() {
        val currentGroup = tabGroupManager?.currentGroup?.value
        if (currentGroup != null && currentGroup.tabCount > 1) {
            val selectedTabId = context.components.store.state.selectedTabId
            adapter?.updateCurrentGroup(currentGroup, selectedTabId)
        }
    }

    interface TabGroupBarListener {
        fun onTabSelected(tabId: String)
    }
}