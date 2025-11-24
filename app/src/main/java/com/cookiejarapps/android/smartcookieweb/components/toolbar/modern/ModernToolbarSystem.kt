package com.cookiejarapps.android.smartcookieweb.components.toolbar.modern

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import mozilla.components.concept.toolbar.ScrollableToolbar
import mozilla.components.concept.engine.EngineView

/**
 * Revolutionary unified toolbar system that combines all toolbar components
 * into one seamless, beautifully animated container with perfect scroll behavior.
 */
class ModernToolbarSystem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), ScrollableToolbar {

    private var currentOffset = 0
    private var scrollingEnabled = true
    private val hideAnimator = ValueAnimator()
    private var engineView: EngineView? = null

    // Components
    private var tabGroupComponent: View? = null
    private var addressBarComponent: View? = null
    private var contextualComponent: View? = null

    init {
        orientation = VERTICAL
        clipToPadding = false
        clipChildren = false
    }

    fun addComponent(component: View, type: ComponentType) {
        android.util.Log.d("ModernToolbar", "Adding component: ${component.javaClass.simpleName}, type: $type")
        
        val layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )
        
        when (type) {
            ComponentType.TAB_GROUP -> {
                tabGroupComponent = component
                addView(component, 0, layoutParams) // Top position
                android.util.Log.d("ModernToolbar", "Added tab group component at index 0")
            }
            ComponentType.ADDRESS_BAR -> {
                addressBarComponent = component
                val index = if (tabGroupComponent != null) 1 else 0
                addView(component, index, layoutParams)
                android.util.Log.d("ModernToolbar", "Added address bar component at index $index")
            }
            ComponentType.CONTEXTUAL -> {
                contextualComponent = component
                addView(component, layoutParams) // Bottom position
                android.util.Log.d("ModernToolbar", "Added contextual component at bottom")
            }
        }
        
        android.util.Log.d("ModernToolbar", "Now have ${childCount} children total")
        
        // Update engine view about our new height
        updateDynamicToolbarHeight()
    }

    fun removeComponent(type: ComponentType) {
        val component = when (type) {
            ComponentType.TAB_GROUP -> tabGroupComponent
            ComponentType.ADDRESS_BAR -> addressBarComponent
            ComponentType.CONTEXTUAL -> contextualComponent
        }
        
        component?.let {
            removeView(it)
            when (type) {
                ComponentType.TAB_GROUP -> tabGroupComponent = null
                ComponentType.ADDRESS_BAR -> addressBarComponent = null
                ComponentType.CONTEXTUAL -> contextualComponent = null
            }
        }
        
        updateDynamicToolbarHeight()
    }

    fun setEngineView(engine: EngineView) {
        engineView = engine
        updateDynamicToolbarHeight()
    }

    private fun updateDynamicToolbarHeight() {
        val totalHeight = getTotalHeight()
        if (totalHeight > 0) {
            engineView?.setDynamicToolbarMaxHeight(totalHeight)
            android.util.Log.d("ModernToolbar", "Updated dynamic height: $totalHeight")
        }
    }

    fun getTotalHeight(): Int {
        var totalHeight = 0
        android.util.Log.d("ModernToolbar", "Calculating total height - childCount: $childCount")
        
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            android.util.Log.d("ModernToolbar", "Child $i: ${child.javaClass.simpleName}, visible=${child.isVisible}, height=${child.height}")
            if (child.isVisible) {
                totalHeight += child.height
            }
        }
        
        android.util.Log.d("ModernToolbar", "Total calculated height: $totalHeight")
        return totalHeight
    }

    override fun enableScrolling() {
        scrollingEnabled = true
        android.util.Log.d("ModernToolbar", "Scrolling enabled")
    }

    override fun disableScrolling() {
        scrollingEnabled = false
        expand()
        android.util.Log.d("ModernToolbar", "Scrolling disabled")
    }

    override fun expand() {
        if (!scrollingEnabled) return
        animateToOffset(0)
    }

    override fun collapse() {
        if (!scrollingEnabled) return
        val totalHeight = getTotalHeight()
        if (totalHeight > 0) {
            animateToOffset(totalHeight)
        }
    }

    private fun animateToOffset(targetOffset: Int) {
        hideAnimator.cancel()
        hideAnimator.removeAllUpdateListeners()
        
        hideAnimator.apply {
            setIntValues(currentOffset, targetOffset)
            duration = 300
            addUpdateListener { animation ->
                val offset = animation.animatedValue as Int
                setToolbarOffset(offset)
            }
            start()
        }
    }

    fun setToolbarOffset(offset: Int) {
        val totalHeight = getTotalHeight()
        currentOffset = offset.coerceIn(0, totalHeight)
        
        // FIXED: Translate DOWN to hide (positive Y), UP to show (0 Y)
        translationY = currentOffset.toFloat()  // Positive moves DOWN (hiding)
        alpha = if (totalHeight > 0) {
            1f - (currentOffset.toFloat() / totalHeight * 0.3f) // Subtle fade
        } else 1f
        
        // Apply clipping to engine view
        engineView?.setVerticalClipping(currentOffset)
        
        // Enhanced logging with Y position
        android.util.Log.d("ModernToolbar", "📍 Y Position: $translationY, Offset: $currentOffset/$totalHeight, Alpha: $alpha")
    }

    fun getCurrentOffset(): Int = currentOffset

    enum class ComponentType {
        TAB_GROUP,
        ADDRESS_BAR, 
        CONTEXTUAL
    }
}