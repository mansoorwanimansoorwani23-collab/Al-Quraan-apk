package com.example.ui.screens.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ai.AiScholarResponse
import com.example.ai.AudioPlayerHelper
import com.example.ai.GeminiScholarService
import com.example.ai.GroundingSource
import com.example.ai.VoiceRecognitionHelper
import com.example.ui.components.CategoryChip
import com.example.ui.theme.*
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String,
    val sender: String, // "USER" or "AI"
    val text: String,
    val audioBase64: String? = null,
    val sources: List<GroundingSource> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScholarScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioPlayer = remember { AudioPlayerHelper(context) }

    var selectedMode by remember { mutableStateOf(0) } // 0: Google Search Grounding & Q&A, 1: Gemini Live Voice
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isLiveSpeaking by remember { mutableStateOf(false) }
    var isListeningUser by remember { mutableStateOf(false) }
    var speechStatusMessage by remember { mutableStateOf<String?>(null) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                id = "init_1",
                sender = "AI",
                text = "Assalamu Alaikum! I am DeenMate AI Scholar, crafted by Rauf. I can discuss Islamic jurisprudence, provide verified Quran & Hadith citations, answer current events with real-time Google Search grounding, or converse with you using Gemini Live Voice."
            )
        )
    }

    val listState = rememberLazyListState()

    val suggestedQuestions = listOf(
        "Latest global moon sighting & Hijri news",
        "Explain the virtues of Tahajjud prayer",
        "Fact-check: Is 'Seek knowledge even in China' Sahih?",
        "What are the sunnahs of Friday (Jumu'ah)?",
        "Fasting rules during international flights",
        "Significance of Ayat al-Kursi (2:255)"
    )

    fun sendMessage(queryText: String, isVoiceMode: Boolean = false) {
        if (queryText.isBlank() || isLoading) return
        val userMsg = ChatMessage(
            id = "u_${System.currentTimeMillis()}",
            sender = "USER",
            text = queryText
        )
        messages.add(userMsg)
        inputText = ""
        isLoading = true
        speechStatusMessage = null

        coroutineScope.launch {
            if (messages.size > 0) {
                listState.animateScrollToItem(messages.size - 1)
            }
            val response: AiScholarResponse = if (isVoiceMode) {
                GeminiScholarService.generateLiveVoiceResponse(queryText)
            } else {
                GeminiScholarService.askGroundedScholar(queryText)
            }

            val aiMsg = ChatMessage(
                id = "ai_${System.currentTimeMillis()}",
                sender = "AI",
                text = response.text,
                audioBase64 = response.audioBase64,
                sources = response.sources,
                searchQueries = response.searchQueries
            )
            messages.add(aiMsg)
            isLoading = false
            if (messages.size > 0) {
                listState.animateScrollToItem(messages.size - 1)
            }

            if (response.audioBase64 != null) {
                isLiveSpeaking = true
                audioPlayer.playBase64Audio(response.audioBase64) {
                    isLiveSpeaking = false
                }
            }
        }
    }

    val voiceRecognitionHelper = remember {
        VoiceRecognitionHelper(
            context = context,
            onResult = { transcribed ->
                isListeningUser = false
                sendMessage(transcribed, isVoiceMode = true)
            },
            onError = { err ->
                isListeningUser = false
                speechStatusMessage = err
            },
            onListeningStateChanged = { listening ->
                isListeningUser = listening
                if (listening) {
                    speechStatusMessage = "Listening... Speak your Islamic question"
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceRecognitionHelper.startListening()
        } else {
            Toast.makeText(context, "Microphone permission required for voice interaction", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stopAudio()
            voiceRecognitionHelper.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Mode Switcher Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DeenMate AI Scholar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Developed by Rauf • Gemini Live & Google Search",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Developer Tag Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "by Rauf",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryChip(
                        title = "Google Search Grounded Q&A",
                        isSelected = selectedMode == 0,
                        onClick = { selectedMode = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    CategoryChip(
                        title = "Gemini Live Voice",
                        isSelected = selectedMode == 1,
                        onClick = { selectedMode = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (selectedMode == 1) {
            // Live Voice Interface
            GeminiLiveVoiceView(
                isLiveSpeaking = isLiveSpeaking,
                isListeningUser = isListeningUser,
                statusMessage = speechStatusMessage,
                lastResponseText = messages.lastOrNull { it.sender == "AI" }?.text ?: "",
                onStartListening = {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPerm) {
                        voiceRecognitionHelper.startListening()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopListeningAndSend = { transcribedText ->
                    voiceRecognitionHelper.stopListening()
                    sendMessage(transcribedText, isVoiceMode = true)
                },
                onQuickVoiceQuery = { voiceQuery ->
                    sendMessage(voiceQuery, isVoiceMode = true)
                },
                onStopAudio = {
                    audioPlayer.stopAudio()
                    isLiveSpeaking = false
                }
            )
        } else {
            // Text & Google Search Grounded Chat Interface
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    // Suggested Prompts Carousel
                    Text(
                        text = "Suggested Questions & Real-Time Topics",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestedQuestions) { q ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { sendMessage(q) }
                            ) {
                                Text(
                                    text = q,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    ChatBubbleItem(
                        message = msg,
                        onOpenUrl = { url ->
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(browserIntent)
                            } catch (_: Exception) {}
                        },
                        onPlayAudio = { base64 ->
                            if (base64 != null) {
                                isLiveSpeaking = true
                                audioPlayer.playBase64Audio(base64) {
                                    isLiveSpeaking = false
                                }
                            }
                        }
                    )
                }

                if (isLoading) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Consulting Islamic knowledge sources...",
                                fontSize = 13.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask about prayer, hadith, news, rulings...", fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_scholar_input"),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { sendMessage(inputText) },
                        enabled = inputText.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("ai_scholar_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessage,
    onOpenUrl: (String) -> Unit,
    onPlayAudio: (String?) -> Unit
) {
    val isUser = message.sender == "USER"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            tonalElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Mosque,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DeenMate AI Scholar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (message.audioBase64 != null) {
                            IconButton(
                                onClick = { onPlayAudio(message.audioBase64) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VolumeUp,
                                    contentDescription = "Play Voice",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )

                // Grounding Citations
                if (!isUser && message.sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = GoldAccentDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Grounded Sources (${message.sources.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccentDark
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    message.sources.take(3).forEach { source ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { onOpenUrl(source.url) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = source.title,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeminiLiveVoiceView(
    isLiveSpeaking: Boolean,
    isListeningUser: Boolean,
    statusMessage: String?,
    lastResponseText: String,
    onStartListening: () -> Unit,
    onStopListeningAndSend: (String) -> Unit,
    onQuickVoiceQuery: (String) -> Unit,
    onStopAudio: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isLiveSpeaking || isListeningUser) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status Info
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isLiveSpeaking) MaterialTheme.colorScheme.primaryContainer else if (isListeningUser) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = when {
                        isLiveSpeaking -> "🎙️ Gemini Live Voice Speaking..."
                        isListeningUser -> "🔴 Listening to Your Voice..."
                        statusMessage != null -> statusMessage
                        else -> "✨ Gemini Live Voice Ready"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLiveSpeaking) MaterialTheme.colorScheme.onPrimaryContainer else if (isListeningUser) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Conversational Islamic Voice Scholar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Natural conversational speech powered by Gemini Native Audio • Tap to speak",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Animated Waveform Circle
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isLiveSpeaking) EmeraldPrimary else if (isListeningUser) Color(0xFFE53935) else IslamicContainerDark,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
                .clickable {
                    if (isLiveSpeaking) {
                        onStopAudio()
                    } else if (isListeningUser) {
                        onStopListeningAndSend("Please recite and explain the spiritual virtues of Ayat al-Kursi.")
                    } else {
                        onStartListening()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isLiveSpeaking -> Icons.Filled.VolumeUp
                        isListeningUser -> Icons.Filled.Mic
                        else -> Icons.Filled.MicNone
                    },
                    contentDescription = "Voice Assistant",
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        // Live Transcript / Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Live Transcript & Answers",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = lastResponseText.ifBlank { "Tap the microphone or choose a quick voice prompt below to start speaking with Gemini Live." },
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quick Voice Prompts
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Quick Voice Inquiries",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val voicePrompts = listOf(
                "Recite & explain Dua for entering home",
                "What are the 5 pillars of Islam?",
                "Give me a daily morning reflection"
            )

            voicePrompts.forEach { vp ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onQuickVoiceQuery(vp) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = vp, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
