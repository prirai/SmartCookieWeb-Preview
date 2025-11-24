package com.cookiejarapps.android.smartcookieweb.components.toolbar.modern

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cookiejarapps.android.smartcookieweb.R
import mozilla.components.browser.state.state.SessionState
import kotlinx.coroutines.*

/**
 * Revolutionary tab group view with pill design, drag-to-reorder, 
 * and beautiful animations. Shows favicon + title in elegant pills.
 */
class EnhancedTabGroupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    private lateinit var tabAdapter: ModernTabPillAdapter
    private var onTabSelected: ((String) -> Unit)? = null
    private var onTabClosed: ((String) -> Unit)? = null
    private var currentTabs = mutableListOf<SessionState>()
    private var selectedTabId: String? = null
    
    // Visual grouping colors
    private val groupColors = listOf(
        0xFFE57373, 0xFF81C784, 0xFF64B5F6, 0xFFFFB74D,
        0xFFAED581, 0xFFFFD54F, 0xFF90A4AE, 0xFFF06292
    ).map { it.toInt() }

    init {
        setupRecyclerView()
        setupItemTouchHelper()
    }

    private fun setupRecyclerView() {
        layoutManager = LinearLayoutManager(context, HORIZONTAL, false)
        tabAdapter = ModernTabPillAdapter(
            onTabClick = { tabId -> 
                onTabSelected?.invoke(tabId)
                animateSelection(tabId)
            },
            onTabClose = { tabId -> 
                onTabClosed?.invoke(tabId)
                animateTabRemoval(tabId)
            }
        )
        adapter = tabAdapter
        
        // CRITICAL: Fix the layout to be a small horizontal bar
        clipToPadding = false
        overScrollMode = OVER_SCROLL_NEVER
        setPadding(4, 2, 4, 2)
        
        // CRITICAL: Proper height for tab pills to be visible
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            120 // Increased height to ensure pills are visible
        )
        
        elevation = 2f
    }

    private fun setupItemTouchHelper() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            ItemTouchHelper.UP
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                
                // Animate the move
                tabAdapter.moveTab(fromPosition, toPosition)
                
                // Update our internal list
                val movedTab = currentTabs.removeAt(fromPosition)
                currentTabs.add(toPosition, movedTab)
                
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (direction == ItemTouchHelper.UP) {
                    val position = viewHolder.adapterPosition
                    if (position != RecyclerView.NO_POSITION && position < currentTabs.size) {
                        val tabToClose = currentTabs[position]
                        android.util.Log.d("TabPillDebug", "🗑️ Swiped up to close tab: ${tabToClose.id}")
                        onTabClosed?.invoke(tabToClose.id)
                    }
                }
            }

            override fun isLongPressDragEnabled(): Boolean = true
            override fun isItemViewSwipeEnabled(): Boolean = true
            
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.animate()?.scaleX(1.1f)?.scaleY(1.1f)?.start()
                }
            }
            
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.animate().scaleX(1f).scaleY(1f).start()
            }
        })
        
        itemTouchHelper.attachToRecyclerView(this)
    }

    fun setup(
        onTabSelected: (String) -> Unit,
        onTabClosed: (String) -> Unit
    ) {
        this.onTabSelected = onTabSelected
        this.onTabClosed = onTabClosed
        tabAdapter.updateCallbacks(onTabSelected, onTabClosed)
    }

    fun updateTabs(tabs: List<SessionState>, selectedId: String?) {
        android.util.Log.d("TabPillDebug", "📋 updateTabs called: ${tabs.size} tabs, selectedId: $selectedId")
        selectedTabId = selectedId
        
        // Debug tab information
        tabs.forEachIndexed { index, tab ->
            android.util.Log.d("TabPillDebug", "Tab $index: id=${tab.id}, title='${tab.content.title}', url='${tab.content.url}'")
        }
        
        // Only show if we have multiple tabs
        val shouldShow = tabs.size > 1
        android.util.Log.d("TabPillDebug", "shouldShow: $shouldShow, currentTabs.size: ${currentTabs.size}")
        
        if (shouldShow && currentTabs != tabs) {
            android.util.Log.d("TabPillDebug", "✅ Updating adapter with ${tabs.size} tabs")
            currentTabs.clear()
            currentTabs.addAll(tabs)
            
            tabAdapter.updateTabs(tabs, selectedId, groupColors)
            animateVisibility(true)
        } else if (!shouldShow) {
            android.util.Log.d("TabPillDebug", "❌ Hiding tab pills - only ${tabs.size} tabs")
            animateVisibility(false)
        } else {
            android.util.Log.d("TabPillDebug", "⚠️ Not updating - tabs haven't changed or shouldShow is false")
        }
    }

    private fun animateVisibility(shouldShow: Boolean) {
        if (shouldShow == (visibility == VISIBLE)) return
        
        if (shouldShow) {
            visibility = VISIBLE
            alpha = 0f
            translationY = -height.toFloat()
            scaleX = 0.95f
            scaleY = 0.95f
            
            animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
        } else {
            animate()
                .alpha(0f)
                .translationY(-height.toFloat())
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(300)
                .withEndAction { visibility = GONE }
                .start()
        }
    }

    private fun animateSelection(tabId: String) {
        // Find the selected item and animate it
        for (i in 0 until childCount) {
            val childView = getChildAt(i)
            val viewHolder = getChildViewHolder(childView)
            if (viewHolder is ModernTabPillAdapter.TabPillViewHolder) {
                if (viewHolder.isTabId(tabId)) {
                    childView.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(100)
                        .withEndAction {
                            childView.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start()
                        }
                        .start()
                    break
                }
            }
        }
    }

    private fun animateTabRemoval(tabId: String) {
        // Find and animate the removed item
        for (i in 0 until childCount) {
            val childView = getChildAt(i)
            val viewHolder = getChildViewHolder(childView)
            if (viewHolder is ModernTabPillAdapter.TabPillViewHolder) {
                if (viewHolder.isTabId(tabId)) {
                    childView.animate()
                        .alpha(0f)
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .translationX(childView.width.toFloat())
                        .setDuration(250)
                        .start()
                    break
                }
            }
        }
    }

    fun getCurrentTabCount(): Int = currentTabs.size
    
    fun getSelectedTabPosition(): Int {
        return currentTabs.indexOfFirst { it.id == selectedTabId }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw subtle background gradient
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            intArrayOf(
                ContextCompat.getColor(context, android.R.color.background_light),
                ContextCompat.getColor(context, android.R.color.background_light)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply {
            shader = gradient
            alpha = 50
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}