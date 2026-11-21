package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.CoreSettings
import com.example.data.MemoryEntity
import com.example.data.MissionEntity
import com.example.ui.components.ActiveTimersStrip
import com.example.ui.components.HudBackgroundCanvas
import com.example.ui.components.chamferedHudPanel
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ArcReactorBlue
import com.example.ui.theme.CyanMuted
import com.example.ui.theme.EmeraldNominal
import com.example.ui.theme.HolographicCyan
import com.example.ui.theme.HudTextMuted
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianInput
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.VoidObsidian
import com.example.viewmodel.DestructiveActionRequest
import com.example.viewmodel.TacticalTimer

private val MEMORY_CATEGORIES = listOf("ALL", "PROFILE", "PREFERENCE", "WORK", "HEALTH", "PERSONAL", "INTEL")
private val MISSION_PRIORITIES = listOf("CRITICAL", "HIGH", "NORMAL", "LOW")

/**
 * Neural Memory Vault Screen: View, search, filter, add, edit, or wipe Room DB memories.
 */
@Composable
fun NeuralMemoryVaultScreen(
    memories: List<MemoryEntity>,
    isOverclock: Boolean,
    onBack: () -> Unit,
    onSaveMemory: (MemoryEntity?, String, String) -> Unit,
    onDeleteMemory: (MemoryEntity) -> Unit,
    onWipeAll: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }

    val filtered = remember(memories, searchQuery, selectedCategory) {
        memories.filter { mem ->
            val catMatch = selectedCategory == "ALL" || mem.category.equals(selectedCategory, ignoreCase = true)
            val qMatch = searchQuery.isBlank() ||
                mem.fact.contains(searchQuery, ignoreCase = true) ||
                mem.category.contains(searchQuery, ignoreCase = true)
            catMatch && qMatch
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HudBackgroundCanvas(isOverclock = isOverclock)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                HudSubScreenTopBar(
                    title = stringResource(R.string.memory_vault_title),
                    subtitle = "${memories.size} ENCRYPTED RECORDS // ROOM DB",
                    onBack = onBack,
                    trailingAction = {
                        if (memories.isNotEmpty()) {
                            IconButton(
                                onClick = { showWipeConfirm = true },
                                modifier = Modifier.testTag("wipe_memories_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Wipe all memories",
                                    tint = AlertCrimson
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingMemory = null
                        showEditorDialog = true
                    },
                    containerColor = HolographicCyan,
                    contentColor = VoidObsidian,
                    shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("add_memory_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add neural memory")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Search neural vault…", style = MaterialTheme.typography.bodyMedium, color = HudTextMuted)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = HolographicCyan)
                    },
                    singleLine = true,
                    colors = hudTextFieldColors(),
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_search_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    MEMORY_CATEGORIES.forEach { cat ->
                        val active = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .background(
                                    if (active) HolographicCyan.copy(alpha = 0.22f) else ObsidianCard,
                                    CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)
                                )
                                .border(
                                    1.dp,
                                    if (active) HolographicCyan else CyanMuted,
                                    CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) HolographicCyan else HudTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (filtered.isEmpty()) {
                    HudEmptyStateCard(
                        icon = Icons.Default.Memory,
                        title = "NO NEURAL RECORDS FOUND",
                        subtitle = "Tell J.A.R.V.I.S. about your preferences, goals, or projects—or tap + to archive a fact manually."
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(filtered, key = { it.id }) { mem ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .chamferedHudPanel(chamfer = 10.dp)
                                    .padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "[${mem.category}]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HolographicCyan
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = mem.fact,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = HudTextPrimary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingMemory = mem
                                        showEditorDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit memory",
                                        tint = HudTextSecondary
                                    )
                                }
                                IconButton(onClick = { onDeleteMemory(mem) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete memory",
                                        tint = AlertCrimson
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        MemoryEditorDialog(
            existing = editingMemory,
            onDismiss = { showEditorDialog = false },
            onSave = { cat, fact ->
                onSaveMemory(editingMemory, cat, fact)
                showEditorDialog = false
            }
        )
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            containerColor = ObsidianElevated,
            title = {
                Text("PURGE NEURAL VAULT", style = MaterialTheme.typography.titleMedium, color = AlertCrimson)
            },
            text = {
                Text(
                    "Erase all ${memories.size} archived long-term memories from local Room storage?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onWipeAll()
                        showWipeConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertCrimson)
                ) {
                    Text("PURGE ALL", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("CANCEL", color = HolographicCyan)
                }
            }
        )
    }
}

@Composable
private fun MemoryEditorDialog(
    existing: MemoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var category by remember { mutableStateOf(existing?.category ?: "PROFILE") }
    var fact by remember { mutableStateOf(existing?.fact ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianElevated,
        title = {
            Text(
                text = if (existing == null) "ARCHIVE NEURAL MEMORY" else "EDIT NEURAL MEMORY",
                style = MaterialTheme.typography.titleMedium,
                color = HolographicCyan
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    MEMORY_CATEGORIES.filterNot { it == "ALL" }.forEach { cat ->
                        val selected = category == cat
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selected) HolographicCyan.copy(alpha = 0.25f) else ObsidianCard,
                                    CutCornerShape(6.dp)
                                )
                                .border(1.dp, if (selected) HolographicCyan else CyanMuted, CutCornerShape(6.dp))
                                .clickable { category = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(cat, style = MaterialTheme.typography.labelSmall, color = if (selected) HolographicCyan else HudTextSecondary)
                        }
                    }
                }
                OutlinedTextField(
                    value = fact,
                    onValueChange = { fact = it },
                    label = { Text("Memory Fact") },
                    minLines = 2,
                    maxLines = 4,
                    colors = hudTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(category, fact) },
                enabled = fact.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = HolographicCyan, contentColor = VoidObsidian)
            ) {
                Text("COMMIT", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = HudTextSecondary)
            }
        }
    )
}

/**
 * Mission Control Screen: Manage active & completed missions, priorities, and tactical timers.
 */
@Composable
fun MissionControlScreen(
    missions: List<MissionEntity>,
    activeTimers: List<TacticalTimer>,
    isOverclock: Boolean,
    onBack: () -> Unit,
    onAddMission: (String, String, String) -> Unit,
    onToggleMission: (MissionEntity) -> Unit,
    onDeleteMission: (MissionEntity) -> Unit,
    onClearCompleted: () -> Unit,
    onStartTimer: (Int, String) -> Unit,
    onCancelTimer: (String) -> Unit
) {
    var showAddMissionDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }

    val pendingCount = missions.count { !it.isCompleted }

    Box(modifier = Modifier.fillMaxSize()) {
        HudBackgroundCanvas(isOverclock = isOverclock)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                HudSubScreenTopBar(
                    title = stringResource(R.string.mission_control_title),
                    subtitle = "$pendingCount ACTIVE DIRECTIVES // ${activeTimers.size} TIMERS",
                    onBack = onBack,
                    trailingAction = {
                        Row {
                            IconButton(
                                onClick = { showTimerDialog = true },
                                modifier = Modifier.testTag("add_tactical_timer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Launch tactical timer",
                                    tint = TacticalAmber
                                )
                            }
                            if (missions.any { it.isCompleted }) {
                                IconButton(onClick = onClearCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Clear completed missions",
                                        tint = HudTextSecondary
                                    )
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddMissionDialog = true },
                    containerColor = HolographicCyan,
                    contentColor = VoidObsidian,
                    shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("add_mission_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add new mission")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                ActiveTimersStrip(
                    timers = activeTimers,
                    onCancelTimer = onCancelTimer
                )

                if (missions.isEmpty()) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        HudEmptyStateCard(
                            icon = Icons.Default.CheckCircle,
                            title = "ALL SECTORS CLEAR",
                            subtitle = "Ask J.A.R.V.I.S. to log a mission by voice or tap + to deploy a new tactical directive."
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 88.dp)
                    ) {
                        items(missions, key = { it.id }) { mission ->
                            val priorityColor = when (mission.priority) {
                                "CRITICAL" -> AlertCrimson
                                "HIGH" -> TacticalAmber
                                "LOW" -> HudTextSecondary
                                else -> HolographicCyan
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .chamferedHudPanel(
                                        chamfer = 10.dp,
                                        borderColor = if (mission.isCompleted) CyanMuted else priorityColor.copy(alpha = 0.65f),
                                        bracketColor = if (mission.isCompleted) HudTextMuted else priorityColor
                                    )
                                    .padding(10.dp)
                            ) {
                                IconButton(onClick = { onToggleMission(mission) }) {
                                    Icon(
                                        imageVector = if (mission.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Toggle mission completion",
                                        tint = if (mission.isCompleted) EmeraldNominal else priorityColor
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "[${mission.priority}]",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = priorityColor
                                        )
                                        if (mission.dueTime.isNotBlank()) {
                                            Text(
                                                text = "DUE // ${mission.dueTime}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = HudTextMuted
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = mission.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (mission.isCompleted) HudTextMuted else HudTextPrimary,
                                        textDecoration = if (mission.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                }
                                IconButton(onClick = { onDeleteMission(mission) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete mission",
                                        tint = AlertCrimson.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMissionDialog) {
        var title by remember { mutableStateOf("") }
        var priority by remember { mutableStateOf("HIGH") }
        var dueTime by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddMissionDialog = false },
            containerColor = ObsidianElevated,
            title = {
                Text("DEPLOY NEW MISSION", style = MaterialTheme.typography.titleMedium, color = HolographicCyan)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Mission Objective") },
                        singleLine = true,
                        colors = hudTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MISSION_PRIORITIES.forEach { p ->
                            val selected = priority == p
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (selected) HolographicCyan.copy(alpha = 0.25f) else ObsidianCard,
                                        CutCornerShape(6.dp)
                                    )
                                    .border(1.dp, if (selected) HolographicCyan else CyanMuted, CutCornerShape(6.dp))
                                    .clickable { priority = p }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(p, style = MaterialTheme.typography.labelSmall, color = if (selected) HolographicCyan else HudTextSecondary)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = dueTime,
                        onValueChange = { dueTime = it },
                        label = { Text("Deadline (optional, e.g. 19:00)") },
                        singleLine = true,
                        colors = hudTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddMission(title, priority, dueTime)
                        showAddMissionDialog = false
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = HolographicCyan, contentColor = VoidObsidian)
                ) {
                    Text("DEPLOY", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMissionDialog = false }) {
                    Text("CANCEL", color = HudTextSecondary)
                }
            }
        )
    }

    if (showTimerDialog) {
        var label by remember { mutableStateOf("Tactical Focus Sprint") }
        var minutesText by remember { mutableStateOf("5") }
        var secondsText by remember { mutableStateOf("0") }

        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            containerColor = ObsidianElevated,
            title = {
                Text("ARM TACTICAL TIMER", style = MaterialTheme.typography.titleMedium, color = TacticalAmber)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Timer Label") },
                        singleLine = true,
                        colors = hudTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = { minutesText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Minutes") },
                            singleLine = true,
                            colors = hudTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = secondsText,
                            onValueChange = { secondsText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Seconds") },
                            singleLine = true,
                            colors = hudTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                val totalSec = (minutesText.toIntOrNull() ?: 0) * 60 + (secondsText.toIntOrNull() ?: 0)
                Button(
                    onClick = {
                        onStartTimer(totalSec.coerceAtLeast(1), label)
                        showTimerDialog = false
                    },
                    enabled = totalSec > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber, contentColor = VoidObsidian)
                ) {
                    Text("ARM", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimerDialog = false }) {
                    Text("CANCEL", color = HudTextSecondary)
                }
            }
        )
    }
}

/**
 * System Core Configuration Dialog backed by DataStore Preferences and Firebase AI Logic status.
 */
@Composable
fun SystemCoreConfigDialog(
    settings: CoreSettings,
    onDismiss: () -> Unit,
    onToggleOverclock: () -> Unit,
    onSelectVoiceLanguage: (String) -> Unit,
    onToggleTts: () -> Unit,
    onChangeSpeechRate: (Float) -> Unit,
    onChangePitch: (Float) -> Unit,
    onChangeCommanderTitle: (String) -> Unit,
    onToggleAutoMemory: (Boolean) -> Unit,
    onClearChat: () -> Unit
) {
    var commanderDraft by remember(settings.commanderTitle) { mutableStateOf(settings.commanderTitle) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .chamferedHudPanel(
                    chamfer = 14.dp,
                    borderColor = HolographicCyan,
                    fillColor = ObsidianElevated
                )
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Text(
                text = stringResource(R.string.core_config_title),
                style = MaterialTheme.typography.headlineSmall,
                color = HolographicCyan
            )

            // Firebase AI Logic + App Check Attestation Card
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EmeraldNominal.copy(alpha = 0.12f), CutCornerShape(8.dp))
                    .border(1.dp, EmeraldNominal.copy(alpha = 0.6f), CutCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = EmeraldNominal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "FIREBASE AI LOGIC // APP CHECK ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldNominal
                    )
                    Text(
                        text = "Zero client API keys required. Attested Google Gemini neural link active (${settings.activeModelId}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = HudTextSecondary
                    )
                }
            }

            // Overclock Mode Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OVERCLOCK REASONING MODE",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (settings.overclockEnabled) TacticalAmber else HudTextPrimary
                    )
                    Text(
                        text = if (settings.overclockEnabled) {
                            "Active: Gemini 4 Argon (Deep Reasoning)"
                        } else {
                            "Active: Gemini 3.8 Flash (Fast Tactical)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = HudTextSecondary
                    )
                }
                Switch(
                    checked = settings.overclockEnabled,
                    onCheckedChange = { onToggleOverclock() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VoidObsidian,
                        checkedTrackColor = TacticalAmber
                    ),
                    modifier = Modifier.testTag("overclock_switch")
                )
            }

            // Voice Capture Language Toggle (en-IN / hi-IN)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "VOICE RECOGNITION LOCALE",
                    style = MaterialTheme.typography.titleSmall,
                    color = HudTextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en-IN" to "ENGLISH / HINGLISH (en-IN)", "hi-IN" to "HINDI (hi-IN)").forEach { (tag, label) ->
                        val active = settings.voiceLanguage == tag
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (active) HolographicCyan.copy(alpha = 0.22f) else ObsidianCard,
                                    CutCornerShape(6.dp)
                                )
                                .border(1.dp, if (active) HolographicCyan else CyanMuted, CutCornerShape(6.dp))
                                .clickable { onSelectVoiceLanguage(tag) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) HolographicCyan else HudTextSecondary
                            )
                        }
                    }
                }
            }

            // Voice Synthesis (TTS) toggle + sliders
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "VOICE SYNTHESIS (TTS)",
                    style = MaterialTheme.typography.titleSmall,
                    color = HudTextPrimary
                )
                Switch(
                    checked = settings.ttsEnabled,
                    onCheckedChange = { onToggleTts() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VoidObsidian,
                        checkedTrackColor = HolographicCyan
                    )
                )
            }

            Column {
                Text(
                    text = "SPEECH RATE: ${String.format("%.2fx", settings.ttsSpeechRate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary
                )
                Slider(
                    value = settings.ttsSpeechRate,
                    onValueChange = onChangeSpeechRate,
                    valueRange = 0.7f..1.45f,
                    colors = SliderDefaults.colors(thumbColor = HolographicCyan, activeTrackColor = HolographicCyan)
                )
                Text(
                    text = "VOICE PITCH: ${String.format("%.2f", settings.ttsPitch)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary
                )
                Slider(
                    value = settings.ttsPitch,
                    onValueChange = onChangePitch,
                    valueRange = 0.7f..1.3f,
                    colors = SliderDefaults.colors(thumbColor = ArcReactorBlue, activeTrackColor = ArcReactorBlue)
                )
            }

            // Auto Memory Extraction Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AUTO NEURAL MEMORY EXTRACTION",
                        style = MaterialTheme.typography.titleSmall,
                        color = HudTextPrimary
                    )
                    Text(
                        text = "Extract durable personal facts via structured JSON.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HudTextSecondary
                    )
                }
                Switch(
                    checked = settings.autoMemoryExtraction,
                    onCheckedChange = onToggleAutoMemory,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VoidObsidian,
                        checkedTrackColor = HolographicCyan
                    )
                )
            }

            // Commander Callsign
            OutlinedTextField(
                value = commanderDraft,
                onValueChange = {
                    commanderDraft = it
                    onChangeCommanderTitle(it)
                },
                label = { Text("Commander Callsign (e.g. Sir, Boss, Ma'am)") },
                singleLine = true,
                colors = hudTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        onClearChat()
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertCrimson),
                    shape = CutCornerShape(8.dp)
                ) {
                    Text("CLEAR COMMS", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = HolographicCyan, contentColor = VoidObsidian),
                    shape = CutCornerShape(8.dp),
                    modifier = Modifier.testTag("close_core_config_button")
                ) {
                    Text("DONE", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Human-in-the-loop Alert Crimson Confirmation Dialog for destructive agentic tool calls.
 */
@Composable
fun DestructiveConfirmationDialog(
    request: DestructiveActionRequest,
    onAuthorize: () -> Unit,
    onAbort: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onAbort,
        containerColor = ObsidianElevated,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AlertCrimson,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.confirm_protocol_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = AlertCrimson
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = HudTextPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PROTOCOL // ${request.protocolCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalAmber
                )
                Text(
                    text = request.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HudTextPrimary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAuthorize,
                colors = ButtonDefaults.buttonColors(containerColor = AlertCrimson, contentColor = HudTextPrimary),
                shape = CutCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_authorize_button")
            ) {
                Text(stringResource(R.string.confirm_authorize), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onAbort,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HolographicCyan),
                shape = CutCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_abort_button")
            ) {
                Text(stringResource(R.string.confirm_abort), style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
private fun HudSubScreenTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    trailingAction: @Composable () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("subscreen_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_navigate_back),
                tint = HolographicCyan
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = HolographicCyan)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = HudTextSecondary)
        }
        trailingAction()
    }
}

@Composable
private fun HudEmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .chamferedHudPanel(chamfer = 12.dp)
            .padding(24.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HolographicCyan.copy(alpha = 0.7f),
            modifier = Modifier.size(36.dp)
        )
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = HolographicCyan)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = HudTextSecondary)
    }
}

@Composable
private fun hudTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HolographicCyan,
    unfocusedBorderColor = CyanMuted,
    focusedLabelColor = HolographicCyan,
    unfocusedLabelColor = HudTextSecondary,
    focusedContainerColor = ObsidianInput,
    unfocusedContainerColor = ObsidianInput,
    cursorColor = HolographicCyan,
    focusedTextColor = HudTextPrimary,
    unfocusedTextColor = HudTextPrimary
)
