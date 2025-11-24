package com.cookiejarapps.android.smartcookieweb.components.toolbar.modern

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import com.cookiejarapps.android.smartcookieweb.R

/**
 * Revolutionary contextual toolbar with beautiful animations, 
 * adaptive buttons, and intelligent state management.
 */
class ModernContextualToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    // Action buttons
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var bookmarkButton: ImageButton
    private lateinit var shareButton: ImageButton
    private lateinit var menuButton: ImageButton

    // Navigation callbacks
    private var onBackClick: (() -> Unit)? = null
    private var onForwardClick: (() -> Unit)? = null
    private var onRefreshClick: (() -> Unit)? = null
    private var onBookmarkClick: (() -> Unit)? = null
    private var onShareClick: (() -> Unit)? = null
    private var onTabCountClick: (() -> Unit)? = null
    private var onMenuClick: (() -> Unit)? = null

    // State tracking
    private var canGoBack = false
    private var canGoForward = false
    private var isLoading = false
    private var isBookmarked = false

    // Animation
    private val pulseAnimator = ValueAnimator.ofFloat(1f, 1.1f, 1f)

    init {
        setupToolbar()
        setupAnimations()
    }

    private fun setupToolbar() {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(16, 8, 16, 8)
        
        // CRITICAL: Fix height to prevent screen takeover
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            60 // Fixed height in pixels
        )
        
        // Create action buttons with beautiful styling
        createActionButtons()
        setupButtonListeners()
        updateButtonStates()
        
        // Add subtle background
        setBackgroundResource(R.drawable.modern_toolbar_background)
        elevation = 2f
    }

    private fun createActionButtons() {
        val buttonSize = 48
        val buttonMargin = 4
        
        // Back button
        backButton = createStyledButton(R.drawable.ic_arrow_back, "Go back").apply {
            addView(this, createButtonLayoutParams(buttonSize, buttonMargin))
        }
        
        // Forward button
        forwardButton = createStyledButton(R.drawable.ic_arrow_forward, "Go forward").apply {
            addView(this, createButtonLayoutParams(buttonSize, buttonMargin))
        }
        
        // Refresh/Stop button
        refreshButton = createStyledButton(R.drawable.ic_refresh, "Refresh").apply {
            addView(this, createButtonLayoutParams(buttonSize, buttonMargin))
        }
        
        // Spacer to push remaining buttons to the right
        val spacer = View(context).apply {
            addView(this, LayoutParams(0, 1, 1f))
        }
        
        // Bookmark button
        bookmarkButton = createStyledButton(R.drawable.ic_bookmark_border, "Bookmark").apply {
            addView(this, createButtonLayoutParams(buttonSize, buttonMargin))
        }
        
        // Share button
        shareButton = createStyledButton(R.drawable.ic_share, "Share").apply {
            addView(this, createButtonLayoutParams(buttonSize, buttonMargin))
        }
        
        // Tab count button
        val tabCountButton = createStyledButton(android.R.drawable.ic_menu_view, "Tabs").apply {
            addView(this, createButtonLayoutParams(buttonSize, buttonMargin))
            setOnClickListener {
                onTabCountClick?.invoke()
                animateButtonPress(this)
            }
        }
        
        // Menu button  
        menuButton = createStyledButton(R.drawable.ic_more_vert, "More options").apply {
            addView(this, createButtonLayoutParams(buttonSize, buttonMargin))
        }
    }

    private fun createStyledButton(iconRes: Int, contentDescription: String): ImageButton {
        return ImageButton(context).apply {
            setImageResource(iconRes)
            this.contentDescription = contentDescription
            background = ContextCompat.getDrawable(context, R.drawable.modern_button_background)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            imageTintList = ContextCompat.getColorStateList(context, R.color.toolbar_icon_tint)
            
            // Add ripple effect
            foreground = ContextCompat.getDrawable(context, R.drawable.modern_button_ripple)
            isClickable = true
            isFocusable = true
        }
    }

    private fun createButtonLayoutParams(size: Int, margin: Int): LayoutParams {
        return LayoutParams(size, size).apply {
            setMargins(margin, 0, margin, 0)
        }
    }

    private fun setupButtonListeners() {
        backButton.setOnClickListener { 
            onBackClick?.invoke()
            animateButtonPress(backButton)
        }
        
        forwardButton.setOnClickListener { 
            onForwardClick?.invoke() 
            animateButtonPress(forwardButton)
        }
        
        refreshButton.setOnClickListener { 
            onRefreshClick?.invoke()
            animateRefreshButton()
        }
        
        bookmarkButton.setOnClickListener { 
            onBookmarkClick?.invoke()
            animateBookmarkToggle()
        }
        
        shareButton.setOnClickListener { 
            onShareClick?.invoke()
            animateButtonPress(shareButton)
        }
        
        menuButton.setOnClickListener { 
            onMenuClick?.invoke()
            animateButtonPress(menuButton)
        }
    }

    private fun setupAnimations() {
        pulseAnimator.apply {
            duration = 150
            repeatCount = 0
        }
    }

    // Public API for setting callbacks
    fun setNavigationCallbacks(
        onBack: (() -> Unit)? = null,
        onForward: (() -> Unit)? = null,
        onRefresh: (() -> Unit)? = null,
        onBookmark: (() -> Unit)? = null,
        onShare: (() -> Unit)? = null,
        onTabCount: (() -> Unit)? = null,
        onMenu: (() -> Unit)? = null
    ) {
        onBackClick = onBack
        onForwardClick = onForward
        onRefreshClick = onRefresh
        onBookmarkClick = onBookmark
        onShareClick = onShare
        onTabCountClick = onTabCount
        onMenuClick = onMenu
    }

    // Public API for updating button states
    fun updateNavigationState(canBack: Boolean, canForward: Boolean) {
        canGoBack = canBack
        canGoForward = canForward
        updateButtonStates()
    }

    fun updateLoadingState(loading: Boolean) {
        isLoading = loading
        updateRefreshButton()
    }

    fun updateBookmarkState(bookmarked: Boolean) {
        isBookmarked = bookmarked
        updateBookmarkButton()
    }

    private fun updateButtonStates() {
        // Update back button
        backButton.apply {
            isEnabled = canGoBack
            alpha = if (canGoBack) 1f else 0.5f
        }
        
        // Update forward button
        forwardButton.apply {
            isEnabled = canGoForward
            alpha = if (canGoForward) 1f else 0.5f
        }
    }

    private fun updateRefreshButton() {
        if (isLoading) {
            refreshButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            refreshButton.contentDescription = "Stop loading"
            // Add rotation animation
            refreshButton.animate()
                .rotation(refreshButton.rotation + 180f)
                .setDuration(300)
                .start()
        } else {
            refreshButton.setImageResource(R.drawable.ic_refresh)
            refreshButton.contentDescription = "Refresh"
        }
    }

    private fun updateBookmarkButton() {
        val iconRes = if (isBookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border
        bookmarkButton.setImageResource(iconRes)
        
        if (isBookmarked) {
            bookmarkButton.imageTintList = ContextCompat.getColorStateList(context, R.color.accent_color)
        } else {
            bookmarkButton.imageTintList = ContextCompat.getColorStateList(context, R.color.toolbar_icon_tint)
        }
    }

    // Beautiful animations
    private fun animateButtonPress(button: ImageButton) {
        button.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
        
        // Haptic feedback
        button.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun animateRefreshButton() {
        refreshButton.animate()
            .rotation(refreshButton.rotation + 360f)
            .setDuration(500)
            .start()
        
        animateButtonPress(refreshButton)
    }

    private fun animateBookmarkToggle() {
        // Heart-like animation for bookmark
        bookmarkButton.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(200)
            .withEndAction {
                bookmarkButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
        
        // Update state after animation
        postDelayed({ updateBookmarkButton() }, 100)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw subtle separator lines between button groups
        val paint = Paint().apply {
            color = ContextCompat.getColor(context, android.R.color.darker_gray)
            alpha = 50
            strokeWidth = 1f
        }
        
        // Draw separators
        val separatorX1 = refreshButton.right + 8f
        
        canvas.drawLine(separatorX1, 16f, separatorX1, height - 16f, paint)
    }
}