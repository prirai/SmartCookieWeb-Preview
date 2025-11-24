package com.cookiejarapps.android.smartcookieweb.components.toolbar.modern

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cookiejarapps.android.smartcookieweb.R
import mozilla.components.browser.state.state.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Revolutionary adapter for beautiful tab pills with favicons, titles, 
 * dynamic colors, and smooth animations.
 */
class ModernTabPillAdapter(
    private var onTabClick: (String) -> Unit,
    private var onTabClose: (String) -> Unit
) : RecyclerView.Adapter<ModernTabPillAdapter.TabPillViewHolder>() {

    private var tabs = mutableListOf<SessionState>()
    private var selectedTabId: String? = null
    private var groupColors = listOf<Int>()
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabPillViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.modern_tab_pill_item, parent, false)
        return TabPillViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabPillViewHolder, position: Int) {
        val tab = tabs[position]
        val isSelected = tab.id == selectedTabId
        val colorIndex = position % groupColors.size
        val pillColor = if (groupColors.isNotEmpty()) groupColors[colorIndex] else 0xFF6200EE.toInt()
        
        holder.bind(tab, isSelected, pillColor)
    }

    override fun getItemCount(): Int = tabs.size

    fun updateTabs(newTabs: List<SessionState>, selectedId: String?, colors: List<Int>) {
        selectedTabId = selectedId
        groupColors = colors
        
        // Smooth list updates with animations
        val oldSize = tabs.size
        tabs.clear()
        tabs.addAll(newTabs)
        
        when {
            oldSize == 0 && newTabs.isNotEmpty() -> {
                notifyItemRangeInserted(0, newTabs.size)
            }
            oldSize > newTabs.size -> {
                notifyItemRangeChanged(0, newTabs.size)
                notifyItemRangeRemoved(newTabs.size, oldSize - newTabs.size)
            }
            oldSize < newTabs.size -> {
                notifyItemRangeChanged(0, oldSize)
                notifyItemRangeInserted(oldSize, newTabs.size - oldSize)
            }
            else -> {
                notifyItemRangeChanged(0, newTabs.size)
            }
        }
        
        android.util.Log.d("ModernTabPill", "Updated ${newTabs.size} tabs, selected: $selectedId")
    }

    fun moveTab(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                tabs[i] = tabs[i + 1].also { tabs[i + 1] = tabs[i] }
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                tabs[i] = tabs[i - 1].also { tabs[i - 1] = tabs[i] }
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        android.util.Log.d("ModernTabPill", "Moved tab from $fromPosition to $toPosition")
    }

    fun updateCallbacks(onTabClick: (String) -> Unit, onTabClose: (String) -> Unit) {
        this.onTabClick = onTabClick
        this.onTabClose = onTabClose
    }

    inner class TabPillViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.tabPillCard)
        private val faviconView: ImageView = itemView.findViewById(R.id.tabFavicon)
        private val titleView: TextView = itemView.findViewById(R.id.tabTitle)
        private val closeButton: ImageView = itemView.findViewById(R.id.tabCloseButton)
        private val selectionIndicator: View = itemView.findViewById(R.id.selectionIndicator)
        
        private var currentTabId: String? = null

        fun bind(tab: SessionState, isSelected: Boolean, pillColor: Int) {
            currentTabId = tab.id
            
            // Set title with smart truncation
            val title = tab.content.title.takeIf { !it.isNullOrBlank() } ?: 
                       tab.content.url.takeIf { !it.isNullOrBlank() } ?: "New Tab"
            titleView.text = title
            
            // Load favicon asynchronously with beautiful fallback
            loadFavicon(tab)
            
            // Apply gorgeous styling based on selection and color
            applyPillStyling(isSelected, pillColor)
            
            // Setup interactions with haptic feedback
            cardView.setOnClickListener { 
                onTabClick(tab.id)
                animateClick()
                vibrateHaptic()
            }
            
            closeButton.setOnClickListener { 
                onTabClose(tab.id)
                animateClose()
                vibrateHaptic()
            }
        }

        private fun loadFavicon(tab: SessionState) {
            scope.launch {
                try {
                    val favicon = withContext(Dispatchers.IO) {
                        // Try to get favicon from tab or generate beautiful default
                        tab.content.icon ?: generateBeautifulFavicon(
                            tab.content.url ?: "", 
                            itemView.context
                        )
                    }
                    faviconView.setImageBitmap(favicon)
                } catch (e: Exception) {
                    // Fallback to beautiful material icon
                    faviconView.setImageResource(R.drawable.ic_language)
                }
            }
        }

        private fun applyPillStyling(isSelected: Boolean, pillColor: Int) {
            if (isSelected) {
                // Selected: Vibrant pill with accent
                val gradient = GradientDrawable().apply {
                    cornerRadius = 24f
                    setColor(pillColor)
                    alpha = 230
                }
                cardView.background = gradient
                cardView.elevation = 12f
                cardView.scaleX = 1.02f
                cardView.scaleY = 1.02f
                
                titleView.setTextColor(Color.WHITE)
                selectionIndicator.visibility = View.VISIBLE
                selectionIndicator.setBackgroundColor(Color.WHITE)
                
                // Glow effect
                cardView.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                cardView.clipToOutline = false
                
            } else {
                // Unselected: Elegant subtle pill
                val gradient = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(ContextCompat.getColor(itemView.context, android.R.color.background_light))
                    setStroke(2, pillColor and 0x40FFFFFF)
                }
                cardView.background = gradient
                cardView.elevation = 4f
                cardView.scaleX = 1f
                cardView.scaleY = 1f
                
                titleView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.primary_text_dark))
                selectionIndicator.visibility = View.GONE
                
                cardView.clipToOutline = true
            }
        }

        private fun animateClick() {
            cardView.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    cardView.animate()
                        .scaleX(1.02f)
                        .scaleY(1.02f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }

        private fun animateClose() {
            // Beautiful removal animation
            cardView.animate()
                .alpha(0f)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .rotationY(90f)
                .setDuration(300)
                .start()
        }

        private fun vibrateHaptic() {
            itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }

        fun isTabId(tabId: String): Boolean = currentTabId == tabId
    }

    /**
     * Generates a beautiful Material Design favicon with gradient and letter
     */
    private fun generateBeautifulFavicon(url: String, context: Context): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Beautiful gradient background
        val domain = try {
            java.net.URL(url).host?.removePrefix("www.") ?: url
        } catch (e: Exception) {
            url
        }
        
        val colors = intArrayOf(
            0xFF6200EE.toInt(),
            0xFF03DAC6.toInt()
        )
        
        val gradient = RadialGradient(
            size / 2f, size / 2f, size / 2f,
            colors, null, Shader.TileMode.CLAMP
        )
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }
        
        // Draw circle with gradient
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        // Draw letter with beautiful typography
        val letter = domain.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        
        paint.apply {
            shader = null
            color = Color.WHITE
            textSize = size * 0.45f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("google-sans", android.graphics.Typeface.BOLD)
        }
        
        val textBounds = Rect()
        paint.getTextBounds(letter, 0, letter.length, textBounds)
        
        canvas.drawText(
            letter,
            size / 2f,
            size / 2f - textBounds.exactCenterY(),
            paint
        )
        
        return bitmap
    }
}