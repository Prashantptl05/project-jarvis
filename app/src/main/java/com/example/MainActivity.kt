package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.ActiveTimersStrip
import com.example.ui.components.ArcReactorCore
import com.example.ui.components.BottomCommandDeck
import com.example.ui.components.CommsMessageCard
import com.example.ui.components.HudBackgroundCanvas
import com.example.ui.components.ProtocolExecutionBanner
import com.example.ui.components.TopTelemetryBar
import com.example.ui.screens.DestructiveConfirmationDialog
import com.example.ui.screens.MissionControlScreen
import com.example.ui.screens.NeuralMemoryVaultScreen
import com.example.ui.screens.SystemCoreConfigDialog
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.ObsidianElevated
import com.example.viewmodel.CoreVisualState
import com.example.viewmodel.JarvisViewModel

const val ROUTE_HUD = "tactical_hud"
const val ROUTE_MEMORY_VAULT = "neural_memory_vault"
const val ROUTE_MISSION_CONTROL = "mission_control"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure Firebase App Check with launch intent before UI/ViewModels
        configureAppCheck(this, intent)

        // STEP 2: Enable dark transparent edge-to-edge HUD display
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        setContent {
            JarvisTheme {
                JarvisAppRoot()
            }
        }
    }
}

@Composable
private fun JarvisAppRoot(
    viewModel: JarvisViewModel = viewModel()
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val missions by viewModel.missions.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val visualState by viewModel.coreVisualState.collectAsStateWithLifecycle()
    val voiceAmplitude by viewModel.voiceAmplitude.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val executingProtocol by viewModel.executingProtocol.collectAsStateWithLifecycle()
    val activeTimers by viewModel.activeTimers.collectAsStateWithLifecycle()
    val attachedImage by viewModel.attachedImage.collectAsStateWithLifecycle()
    val hudNotice by viewModel.hudNotice.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()

    var showCoreConfig by rememberSaveable { mutableStateOf(false) }

    val micDeniedMsg = stringResource(R.string.mic_permission_denied)

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleVoiceCapture()
        } else {
            viewModel.postNotice(micDeniedMsg)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.attachBitmap(bitmap)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachImageFromUri(uri)
        }
    }

    val triggerVoiceCapture: () -> Unit = {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudioPermission) {
            viewModel.toggleVoiceCapture()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_HUD
    ) {
        composable(ROUTE_HUD) {
            var inputText by rememberSaveable { mutableStateOf("") }
            val listState = rememberLazyListState()

            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.lastIndex)
                }
            }

            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            ) {
                HudBackgroundCanvas(isOverclock = settings.overclockEnabled)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp)
                        .statusBarsPadding()
                ) {
                    TopTelemetryBar(
                        telemetry = telemetry,
                        settings = settings,
                        pendingMissionCount = missions.count { !it.isCompleted },
                        memoryCount = memories.size,
                        onToggleOverclock = viewModel::toggleOverclock,
                        onToggleTts = viewModel::toggleTtsMuted,
                        onOpenMemories = { navController.navigate(ROUTE_MEMORY_VAULT) },
                        onOpenMissions = { navController.navigate(ROUTE_MISSION_CONTROL) },
                        onOpenSettings = { showCoreConfig = true }
                    )

                    ArcReactorCore(
                        visualState = visualState,
                        voiceAmplitude = voiceAmplitude,
                        isOverclock = settings.overclockEnabled,
                        partialTranscript = partialTranscript,
                        onTapCore = {
                            if (visualState == CoreVisualState.SPEAKING) {
                                viewModel.stopSpeaking()
                            } else {
                                triggerVoiceCapture()
                            }
                        }
                    )

                    ProtocolExecutionBanner(protocolName = executingProtocol)

                    ActiveTimersStrip(
                        timers = activeTimers,
                        onCancelTimer = viewModel::cancelTacticalTimer
                    )

                    AnimatedVisibility(visible = !hudNotice.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .background(ObsidianElevated, CutCornerShape(8.dp))
                                .border(1.dp, AlertCrimson.copy(alpha = 0.7f), CutCornerShape(8.dp))
                                .clickable { viewModel.dismissNotice() }
                                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            Text(
                                text = hudNotice.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = HudTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = viewModel::dismissNotice) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss alert notice",
                                    tint = AlertCrimson
                                )
                            }
                        }
                    }

                    // Tactical Comms Stream
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            CommsMessageCard(message = msg)
                        }
                    }

                    BottomCommandDeck(
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        attachedImage = attachedImage,
                        isListening = visualState == CoreVisualState.LISTENING,
                        isThinking = isThinking,
                        onRemoveAttachedImage = viewModel::clearAttachedImage,
                        onQuickCommand = { prompt ->
                            viewModel.submitUserCommand(prompt)
                        },
                        onCameraClick = {
                            cameraLauncher.launch(null)
                        },
                        onGalleryClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onVoiceClick = triggerVoiceCapture,
                        onSendClick = {
                            val toSend = inputText
                            inputText = ""
                            viewModel.submitUserCommand(toSend)
                        }
                    )
                }
            }
        }

        composable(ROUTE_MEMORY_VAULT) {
            NeuralMemoryVaultScreen(
                memories = memories,
                isOverclock = settings.overclockEnabled,
                onBack = { navController.popBackStack() },
                onSaveMemory = viewModel::addOrUpdateMemory,
                onDeleteMemory = viewModel::deleteMemory,
                onWipeAll = viewModel::wipeAllMemories
            )
        }

        composable(ROUTE_MISSION_CONTROL) {
            MissionControlScreen(
                missions = missions,
                activeTimers = activeTimers,
                isOverclock = settings.overclockEnabled,
                onBack = { navController.popBackStack() },
                onAddMission = viewModel::addMissionManual,
                onToggleMission = viewModel::toggleMissionCompleted,
                onDeleteMission = viewModel::deleteMissionManual,
                onClearCompleted = viewModel::clearCompletedMissions,
                onStartTimer = { secs, label -> viewModel.startTacticalTimer(secs, label) },
                onCancelTimer = viewModel::cancelTacticalTimer
            )
        }
    }

    if (showCoreConfig) {
        SystemCoreConfigDialog(
            settings = settings,
            onDismiss = { showCoreConfig = false },
            onToggleOverclock = viewModel::toggleOverclock,
            onSelectVoiceLanguage = viewModel::updateVoiceLanguage,
            onToggleTts = viewModel::toggleTtsMuted,
            onChangeSpeechRate = viewModel::updateTtsRate,
            onChangePitch = viewModel::updateTtsPitch,
            onChangeCommanderTitle = viewModel::updateCommanderTitle,
            onToggleAutoMemory = viewModel::updateAutoMemoryExtraction,
            onClearChat = viewModel::clearChatHistory
        )
    }

    pendingConfirmation?.let { request ->
        DestructiveConfirmationDialog(
            request = request,
            onAuthorize = { viewModel.respondToConfirmation(true) },
            onAbort = { viewModel.respondToConfirmation(false) }
        )
    }
}
