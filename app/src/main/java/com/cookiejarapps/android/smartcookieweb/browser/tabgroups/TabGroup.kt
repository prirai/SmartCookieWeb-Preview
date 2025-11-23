package com.cookiejarapps.android.smartcookieweb.browser.tabgroups

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Tab group data model based on Mozilla's architecture.
 * Represents a collection of related tabs.
 */
@Entity(tableName = "tab_groups")
data class TabGroup(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val color: String = "blue", // Color identifier for visual distinction
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

/**
 * Represents the association between a tab and a group.
 */
@Entity(tableName = "tab_group_members", primaryKeys = ["tabId", "groupId"])
data class TabGroupMember(
    val tabId: String,
    val groupId: String,
    val position: Int = 0
)

/**
 * Domain model representing a tab group with its members.
 */
data class TabGroupWithTabs(
    val group: TabGroup,
    val tabIds: List<String> = emptyList()
) {
    val tabCount: Int get() = tabIds.size
    val isEmpty: Boolean get() = tabIds.isEmpty()
    val isNotEmpty: Boolean get() = tabIds.isNotEmpty()
}

/**
 * Generates randomized 2-character group names as requested.
 * Format: [a-z][0-9] (e.g., "a1", "b2", "z9")
 */
class TabGroupNameGenerator {
    private val usedNames = mutableSetOf<String>()
    
    fun generateName(): String {
        val alphabet = 'a'..'z'
        val numbers = 0..9
        
        // Try to find an unused name
        for (letter in alphabet) {
            for (number in numbers) {
                val name = "$letter$number"
                if (name !in usedNames) {
                    usedNames.add(name)
                    return name
                }
            }
        }
        
        // If all combinations are used, start with random UUIDs (fallback)
        return UUID.randomUUID().toString().substring(0, 2)
    }
    
    fun releaseName(name: String) {
        usedNames.remove(name)
    }
}