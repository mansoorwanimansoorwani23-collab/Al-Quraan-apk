package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Quran : Screen("quran", "Quran", Icons.Filled.MenuBook, Icons.Outlined.MenuBook)
    object Prayer : Screen("prayer", "Prayer", Icons.Filled.AccessTime, Icons.Outlined.AccessTime)
    object Discover : Screen("discover", "Discover", Icons.Filled.Explore, Icons.Outlined.Explore)
    object Tracker : Screen("tracker", "Deen Tracker", Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt)
    object AiScholar : Screen("ai_scholar", "AI Scholar", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

enum class DiscoverTab(val title: String, val icon: ImageVector) {
    AI_SCHOLAR("AI Scholar & Voice", Icons.Filled.AutoAwesome),
    QIBLA("Qibla Finder", Icons.Filled.Explore),
    TASBEEH("Digital Tasbeeh", Icons.Filled.TouchApp),
    HADITH("Hadith", Icons.Filled.FormatQuote),
    DUAS("Duas & Azkar", Icons.Filled.Favorite),
    CALENDAR("Hijri Calendar", Icons.Filled.CalendarMonth),
    RAMADAN("Ramadan Mode", Icons.Filled.NightsStay),
    SEARCH("Global Search", Icons.Filled.Search)
}
