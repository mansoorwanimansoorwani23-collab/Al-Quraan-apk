package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.IslamicDataSource
import com.example.domain.calculator.CalculationMethod
import com.example.domain.calculator.Madhhab
import com.example.ui.MainViewModel
import com.example.ui.components.SectionHeader
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MainViewModel
) {
    val settings by viewModel.userSettings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showMethodDialog by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }
    var showSourcesDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Location & Calculation Group
        item {
            SectionHeader(title = "Prayer Times & Calculations")
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_calculation_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingRow(
                        title = "Location / City",
                        subtitle = "${settings.cityName}, ${settings.countryName}",
                        icon = Icons.Filled.LocationOn,
                        onClick = { showCityDialog = true }
                    )

                    Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    SettingRow(
                        title = "Calculation Method",
                        subtitle = settings.calculationMethod.displayName,
                        icon = Icons.Filled.Calculate,
                        onClick = { showMethodDialog = true }
                    )

                    Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    SettingToggleRow(
                        title = "Hanafi Asr Time",
                        subtitle = if (settings.madhhab == Madhhab.HANAFI) "Hanafi (Shadow length 2x)" else "Standard Shafi'i/Maliki/Hanbali (Shadow length 1x)",
                        icon = Icons.Filled.AccessTime,
                        isChecked = settings.madhhab == Madhhab.HANAFI,
                        onCheckedChange = { isHanafi ->
                            coroutineScope.launch {
                                viewModel.preferencesRepository.updateMadhhab(if (isHanafi) Madhhab.HANAFI else Madhhab.STANDARD)
                            }
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // 12-Hour vs 24-Hour Time Format Setting
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("settings_time_format_section")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("Time Format", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        if (settings.is24HourFormat) "24-Hour clock (e.g. 13:30, 20:15)" else "12-Hour clock with AM/PM (e.g. 1:30 PM)",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 12-Hour Option Button
                            FilterChip(
                                selected = !settings.is24HourFormat,
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.preferencesRepository.update24HourFormat(false)
                                    }
                                },
                                label = {
                                    Text(
                                        "12-Hour (1:30 PM)",
                                        fontSize = 12.sp,
                                        fontWeight = if (!settings.is24HourFormat) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (!settings.is24HourFormat) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.weight(1f).testTag("time_format_12h_chip")
                            )

                            // 24-Hour Option Button
                            FilterChip(
                                selected = settings.is24HourFormat,
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.preferencesRepository.update24HourFormat(true)
                                    }
                                },
                                label = {
                                    Text(
                                        "24-Hour (13:30)",
                                        fontSize = 12.sp,
                                        fontWeight = if (settings.is24HourFormat) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (settings.is24HourFormat) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.weight(1f).testTag("time_format_24h_chip")
                            )
                        }
                    }
                }
            }
        }

        // Notification Preferences
        item {
            SectionHeader(title = "Adhan & Reminders")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingToggleRow(
                        title = "Prayer Notifications",
                        subtitle = "Native local alerts for 5 daily prayers",
                        icon = Icons.Filled.NotificationsActive,
                        isChecked = settings.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                viewModel.preferencesRepository.updatePrayerNotification("all", enabled)
                            }
                        }
                    )

                    Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    SettingToggleRow(
                        title = "Morning & Evening Azkar",
                        subtitle = "Daily morning & evening dhikr reminders",
                        icon = Icons.Filled.Favorite,
                        isChecked = settings.morningAzkarNotification,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                viewModel.preferencesRepository.updateDailyReminders("morning", enabled)
                            }
                        }
                    )
                }
            }
        }

        // Quran & Reading Settings
        item {
            SectionHeader(title = "Quran & Display")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Arabic Font Size: ${settings.arabicFontSize.toInt()}sp", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Slider(
                        value = settings.arabicFontSize,
                        onValueChange = { coroutineScope.launch { viewModel.preferencesRepository.updateArabicFontSize(it) } },
                        valueRange = 18f..40f,
                        steps = 10
                    )

                    Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    SettingToggleRow(
                        title = "Show English Translation",
                        subtitle = "Display Sahih International translation",
                        icon = Icons.Filled.Translate,
                        isChecked = settings.showTranslation,
                        onCheckedChange = { coroutineScope.launch { viewModel.preferencesRepository.updateShowTranslation(it) } }
                    )

                    Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    SettingToggleRow(
                        title = "Show Transliteration",
                        subtitle = "Helpful pronunciation guide",
                        icon = Icons.Filled.Spellcheck,
                        isChecked = settings.showTransliteration,
                        onCheckedChange = { coroutineScope.launch { viewModel.preferencesRepository.updateShowTransliteration(it) } }
                    )
                }
            }
        }

        // About & Authentic Sources Reassurance
        item {
            SectionHeader(title = "Authenticity & Offline Privacy")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingRow(
                        title = "Islamic Content Sources & References",
                        subtitle = "Verified Uthmani Quran, Sahih Bukhari, Sahih Muslim, Hisn al-Muslim",
                        icon = Icons.Filled.Verified,
                        onClick = { showSourcesDialog = true }
                    )

                    Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    SettingRow(
                        title = "100% Offline & Private",
                        subtitle = "All calculations, prayers, and tracker data stay locally on your device",
                        icon = Icons.Filled.Security,
                        onClick = {}
                    )

                    Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    SettingRow(
                        title = "About DeenMate & Developer",
                        subtitle = "Lead Developer: Rauf • Version 1.0",
                        icon = Icons.Filled.Info,
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }
    }

    // About Developer Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Mosque, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About DeenMate", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("DeenMate — Complete Muslim Lifestyle Mobile App", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Lead Developer: Rauf", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Purpose: Built to empower every Muslim with authentic, offline-first tools for daily worship, Quran study, accurate prayer times, Qibla finding, and deen tracking.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Architecture: 100% on-device astronomical prayer calculations, local Uthmani Quran reader, native Android exact alarms, verified authentic Hadith & Dua engines, and in-app Islamic knowledge search & web reader.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("May Allah accept this humble effort from Developer Rauf and make it beneficial for the Ummah.", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Sources Dialog
    if (showSourcesDialog) {
        AlertDialog(
            onDismissRequest = { showSourcesDialog = false },
            title = { Text("Authentic Islamic Sources", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("• Quran Text: Standard King Fahd Complex Uthmani Script.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Translation: Sahih International verified translation.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Hadith: Authenticated Sahih collections (Sahih al-Bukhari, Sahih Muslim, Jami` at-Tirmidhi, 40 Hadith Nawawi) with strict reference numbering.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Duas & Azkar: Hisn al-Muslim (Fortress of the Muslim) by Saeed bin Ali bin Wahf Al-Qahtani.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Prayer Calculations: High-precision astronomical equations using standard solar declination and equation of time.", fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourcesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // City Dialog
    if (showCityDialog) {
        AlertDialog(
            onDismissRequest = { showCityDialog = false },
            title = { Text("Select Location", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(IslamicDataSource.POPULAR_CITIES) { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectCity(city)
                                    showCityDialog = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${city.name}, ${city.country}", fontSize = 14.sp)
                            if (settings.cityName == city.name) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCityDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Calculation Method Dialog
    if (showMethodDialog) {
        AlertDialog(
            onDismissRequest = { showMethodDialog = false },
            title = { Text("Calculation Method", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(CalculationMethod.values()) { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch { viewModel.preferencesRepository.updateCalculationMethod(method) }
                                    showMethodDialog = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(method.displayName, fontSize = 13.sp, fontWeight = if (settings.calculationMethod == method) FontWeight.Bold else FontWeight.Normal)
                            if (settings.calculationMethod == method) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMethodDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}
