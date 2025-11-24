package com.cookiejarapps.android.smartcookieweb.browser.tabgroups

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.cookiejarapps.android.smartcookieweb.R
import com.cookiejarapps.android.smartcookieweb.databinding.TabGroupItemBinding
import com.cookiejarapps.android.smartcookieweb.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Adapter for displaying tab groups as circular favicons.
 */
class TabGroupAdapter(
    private val onTabClick: (String) -> Unit  // Changed to tab click instead of group click
) : RecyclerView.Adapter<TabGroupAdapter.TabGroupViewHolder>() {

    private var currentGroupWithTabs: TabGroupWithTabs? = null
    private var selectedTabId: String? = null

    fun updateCurrentGroup(groupWithTabs: TabGroupWithTabs?, selectedTabId: String?) {
        // Only update if something actually changed to reduce flickering
        if (this.currentGroupWithTabs != groupWithTabs || this.selectedTabId != selectedTabId) {
            this.currentGroupWithTabs = groupWithTabs
            this.selectedTabId = selectedTabId
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabGroupViewHolder {
        val binding = TabGroupItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TabGroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabGroupViewHolder, position: Int) {
        // We only show one item - the current group's tabs
        currentGroupWithTabs?.let { groupWithTabs ->
            holder.bind(groupWithTabs, selectedTabId)
        }
    }

    override fun getItemCount() = if (currentGroupWithTabs != null) 1 else 0

    inner class TabGroupViewHolder(
        private val binding: TabGroupItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(groupWithTabs: TabGroupWithTabs, selectedTabId: String?) {
            val faviconContainer = binding.faviconContainer
            faviconContainer.removeAllViews()
            
            // Get tab details from browser store
            CoroutineScope(Dispatchers.Main).launch {
                val store = binding.root.context.components.store
                val faviconCache = binding.root.context.components.faviconCache
                
                // Create circular favicon for each tab in the group (maintain original order)
                groupWithTabs.tabIds.forEachIndexed { index, tabId ->
                    val tab = store.state.tabs.find { it.id == tabId }
                    if (tab != null) {
                        createFaviconCircle(faviconContainer, tab, tabId == selectedTabId, faviconCache)
                    }
                }
            }
        }
        
        private fun createFaviconCircle(
            container: LinearLayout,
            tab: mozilla.components.browser.state.state.TabSessionState,
            isSelected: Boolean,
            faviconCache: com.cookiejarapps.android.smartcookieweb.utils.FaviconCache
        ) {
            val context = container.context
            val faviconView = LayoutInflater.from(context)
                .inflate(R.layout.tab_favicon_circle, container, false)
            
            val faviconContainer = faviconView.findViewById<FrameLayout>(R.id.faviconContainer)
            val imageView = faviconView.findViewById<ImageView>(R.id.faviconImage)
            val selectionIndicator = faviconView.findViewById<View>(R.id.selectionIndicator)
            val closeButton = faviconView.findViewById<ImageView>(R.id.closeButton)
            
            // Set selection state
            selectionIndicator.isVisible = isSelected
            
            // Show close button on hover/long press (for now always visible for testing)
            closeButton.isVisible = true
            
            // Set favicon with smart circular clipping
            if (tab.content.icon != null) {
                val icon = tab.content.icon!!
                val minDimension = kotlin.math.min(icon.width, icon.height)
                
                // Create circular bitmap with proper sizing
                val circularIcon = android.graphics.Bitmap.createBitmap(minDimension, minDimension, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(circularIcon)
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                }
                
                // Draw circular background
                val radius = minDimension / 2f
                canvas.drawCircle(radius, radius, radius, paint)
                
                // Clip to circle and draw original icon
                paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
                
                // Scale and center the original icon
                val srcRect = android.graphics.Rect(
                    (icon.width - minDimension) / 2,
                    (icon.height - minDimension) / 2,
                    (icon.width + minDimension) / 2,
                    (icon.height + minDimension) / 2
                )
                val dstRect = android.graphics.Rect(0, 0, minDimension, minDimension)
                canvas.drawBitmap(icon, srcRect, dstRect, paint)
                
                imageView.setImageBitmap(circularIcon)
            } else {
                // Try to load from cache
                CoroutineScope(Dispatchers.Main).launch {
                    val cachedIcon = faviconCache.loadFavicon(tab.content.url)
                    if (cachedIcon != null) {
                        imageView.setImageBitmap(cachedIcon)
                    } else {
                        // Fallback to default icon
                        imageView.setImageResource(R.drawable.ic_baseline_link)
                    }
                }
            }
            
            // Set click listener for tab switching
            faviconView.setOnClickListener {
                android.util.Log.d("TabGroupAdapter", "Favicon clicked for tab: ${tab.id}")
                onTabClick(tab.id)
            }
            
            faviconContainer.setOnClickListener {
                android.util.Log.d("TabGroupAdapter", "Favicon container clicked for tab: ${tab.id}")
                onTabClick(tab.id)
            }
            
            // Set close button click listener
            closeButton.setOnClickListener {
                android.util.Log.d("TabGroupAdapter", "Close button clicked for tab: ${tab.id}")
                // Close the tab
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val tabsUseCases = context.components.tabsUseCases
                        tabsUseCases.removeTab(tab.id)
                    } catch (e: Exception) {
                        android.util.Log.e("TabGroupAdapter", "Error closing tab: ${e.message}")
                    }
                }
            }
            
            // Make sure the views are clickable and focusable
            faviconView.isClickable = true
            faviconView.isFocusable = true
            faviconContainer.isClickable = true
            faviconContainer.isFocusable = true
            closeButton.isClickable = true
            closeButton.isFocusable = true
            
            container.addView(faviconView)
        }

    }
}