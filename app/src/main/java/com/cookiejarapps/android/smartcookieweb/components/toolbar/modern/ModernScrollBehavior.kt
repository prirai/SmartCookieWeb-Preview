package com.cookiejarapps.android.smartcookieweb.components.toolbar.modern

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import kotlin.math.max
import kotlin.math.min

/**
 * Revolutionary scroll behavior that provides buttery smooth toolbar animations
 * with intelligent snapping and momentum-based hiding/showing.
 */
class ModernScrollBehavior(
    context: Context,
    attrs: AttributeSet? = null
) : CoordinatorLayout.Behavior<ModernToolbarSystem>(context, attrs) {

    private var totalScrolled = 0
    private var lastScrollDirection = 0
    private var snapThreshold = 0.3f
    private var isScrollingEnabled = true

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: ModernToolbarSystem,
        layoutDirection: Int
    ): Boolean {
        // Find and connect to the EngineView
        findEngineView(parent)?.let { engine ->
            child.setEngineView(engine)
            android.util.Log.d("ModernScrollBehavior", "Connected to EngineView")
        }
        
        return super.onLayoutChild(parent, child, layoutDirection)
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: ModernToolbarSystem,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        return isScrollingEnabled && axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: ModernToolbarSystem,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
        if (!isScrollingEnabled) return
        
        val toolbarHeight = child.getTotalHeight()
        android.util.Log.d("ModernScrollBehavior", "onNestedPreScroll: dy=$dy, toolbarHeight=$toolbarHeight, currentOffset=${child.getCurrentOffset()}")
        
        if (toolbarHeight <= 0) {
            android.util.Log.w("ModernScrollBehavior", "Toolbar height is 0 - cannot apply scroll behavior")
            return
        }

        // Track scroll direction for momentum
        lastScrollDirection = dy.coerceIn(-1, 1)
        
        totalScrolled += dy
        val newOffset = totalScrolled.coerceIn(0, toolbarHeight)
        
        if (newOffset != child.getCurrentOffset()) {
            child.setToolbarOffset(newOffset)
            android.util.Log.v("ModernScrollBehavior", "Applied offset: $newOffset")
        }
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: ModernToolbarSystem,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        // Intelligent snapping based on scroll momentum and position
        if (dyUnconsumed != 0) {
            val toolbarHeight = child.getTotalHeight()
            val currentOffset = child.getCurrentOffset()
            val threshold = toolbarHeight * snapThreshold
            
            android.util.Log.d("ModernScrollBehavior", "onNestedScroll: dyUnconsumed=$dyUnconsumed, toolbarHeight=$toolbarHeight, currentOffset=$currentOffset, threshold=$threshold")
            
            // Only trigger if we have a valid toolbar height
            if (toolbarHeight > 0) {
                when {
                    dyUnconsumed > 0 && currentOffset > threshold -> {
                        // Scrolling down fast or past threshold - hide completely
                        child.collapse()
                        android.util.Log.d("ModernScrollBehavior", "Smart collapse triggered (offset=$currentOffset, threshold=$threshold)")
                    }
                    dyUnconsumed < 0 -> {
                        // FIXED: Any upward scroll should show the toolbar when it's hidden
                        if (currentOffset > 0) {
                            child.expand()
                            android.util.Log.d("ModernScrollBehavior", "Smart expand triggered - scrolling up (offset=$currentOffset)")
                        }
                    }
                }
            } else {
                android.util.Log.w("ModernScrollBehavior", "Cannot apply smart snapping - toolbar height is 0")
            }
        }
    }

    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: ModernToolbarSystem,
        target: View,
        type: Int
    ) {
        // Final snap decision when scrolling stops
        val toolbarHeight = child.getTotalHeight()
        val currentOffset = child.getCurrentOffset()
        val midpoint = toolbarHeight / 2
        
        if (currentOffset in 1 until toolbarHeight) {
            if (currentOffset < midpoint) {
                child.expand()
                android.util.Log.d("ModernScrollBehavior", "Final snap to expanded")
            } else {
                child.collapse()
                android.util.Log.d("ModernScrollBehavior", "Final snap to collapsed")
            }
        }
    }

    private fun findEngineView(coordinatorLayout: CoordinatorLayout): mozilla.components.concept.engine.EngineView? {
        for (i in 0 until coordinatorLayout.childCount) {
            val child = coordinatorLayout.getChildAt(i)
            
            // Direct EngineView
            if (child is mozilla.components.concept.engine.EngineView) {
                return child
            }
            
            // EngineView in ViewPager2 or Fragment
            if (child is androidx.viewpager2.widget.ViewPager2) continue
            if (child is androidx.fragment.app.FragmentContainerView) {
                return searchForEngineView(child)
            }
        }
        return null
    }

    private fun searchForEngineView(view: View): mozilla.components.concept.engine.EngineView? {
        if (view is mozilla.components.concept.engine.EngineView) return view
        if (view is androidx.viewpager2.widget.ViewPager2) return null
        
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val engineView = searchForEngineView(view.getChildAt(i))
                if (engineView != null) return engineView
            }
        }
        return null
    }

    fun setScrollingEnabled(enabled: Boolean) {
        isScrollingEnabled = enabled
        android.util.Log.d("ModernScrollBehavior", "Scrolling ${if (enabled) "enabled" else "disabled"}")
    }

    fun setSnapThreshold(threshold: Float) {
        snapThreshold = threshold.coerceIn(0.1f, 0.9f)
    }
}