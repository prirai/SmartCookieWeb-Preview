package com.cookiejarapps.android.smartcookieweb.browser.tabs

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.cookiejarapps.android.smartcookieweb.R
import com.cookiejarapps.android.smartcookieweb.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.tabstray.TabViewHolder
import mozilla.components.browser.tabstray.TabsTrayStyling
import mozilla.components.browser.tabstray.thumbnail.TabThumbnailView
import mozilla.components.support.base.observer.Observable
import mozilla.components.support.ktx.android.content.getColorFromAttr

class TabListViewHolder(
        itemView: View
) : TabViewHolder(itemView) {
    @VisibleForTesting
    internal val iconView: ImageView? = itemView.findViewById(R.id.mozac_browser_tabstray_icon)
    @VisibleForTesting
    internal val titleView: TextView = itemView.findViewById(R.id.mozac_browser_tabstray_title)
    @VisibleForTesting
    internal val closeView: AppCompatImageButton = itemView.findViewById(R.id.mozac_browser_tabstray_close)

    override var tab: TabSessionState? = null

    @VisibleForTesting
    internal var styling: TabsTrayStyling? = null

    override fun bind(
        tab: TabSessionState,
        isSelected: Boolean,
        styling: TabsTrayStyling,
        delegate: mozilla.components.browser.tabstray.TabsTray.Delegate
    ) {
        this.tab = tab
        this.styling = styling

        val title = if (tab.content.title.isNotEmpty()) {
            tab.content.title
        } else {
            tab.content.url
        }

        titleView.text = title

        itemView.setOnClickListener {
            delegate.onTabSelected(tab)
        }

        closeView.setOnClickListener {
            delegate.onTabClosed(tab)
        }

        updateSelectedTabIndicator(isSelected)

        // Set favicon - check cache if tab icon is null
        if (tab.content.icon != null) {
            iconView?.setImageBitmap(tab.content.icon)
        } else {
            // Check favicon cache for this URL
            iconView?.setImageBitmap(null) // Clear first
            CoroutineScope(Dispatchers.Main).launch {
                val cachedIcon = itemView.context.components.faviconCache.loadFavicon(tab.content.url)
                if (cachedIcon != null) {
                    iconView?.setImageBitmap(cachedIcon)
                }
            }
        }
    }

    override fun updateSelectedTabIndicator(showAsSelected: Boolean) {
        if (showAsSelected) {
            showItemAsSelected()
        } else {
            showItemAsNotSelected()
        }
    }

    @VisibleForTesting
    internal fun showItemAsSelected() {
        titleView.setTextColor(itemView.context.getColorFromAttr(android.R.attr.textColorPrimary))
        itemView.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.selected_tab))
        closeView.imageTintList = ColorStateList.valueOf(itemView.context.getColorFromAttr(android.R.attr.textColorPrimary))
    }

    @VisibleForTesting
    internal fun showItemAsNotSelected() {
        titleView.setTextColor(itemView.context.getColorFromAttr(android.R.attr.textColorPrimary))
        itemView.setBackgroundColor(itemView.context.getColorFromAttr(R.attr.colorSurface))
        closeView.imageTintList = ColorStateList.valueOf(itemView.context.getColorFromAttr(android.R.attr.textColorPrimary))
    }
}