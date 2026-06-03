package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioSongSample
import com.example.model.ChannelTrack
import com.example.model.TrackGroup
import com.example.ui.theme.*
import com.example.viewmodel.AudioMixViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: AudioMixViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_screen_layout"),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    DAWMainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DAWMainScreen(
    viewModel: AudioMixViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State bindings
    val tracks by viewModel.tracks.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playheadSeconds by viewModel.playheadSeconds.collectAsState()
    val isLooping by viewModel.isLooping.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingStems.collectAsState()
    val analysisProgress by viewModel.analysisProgress.collectAsState()

    // Master meters
    val mvLeft by viewModel.masterVULeft.collectAsState()
    val mvRight by viewModel.masterVURight.collectAsState()
    val isClipping by viewModel.isClipping.collectAsState()
    val spectrumBands by viewModel.spectrumBands.collectAsState()
    val liveWaveform by viewModel.liveWaveform.collectAsState()

    // UI Panel displays
    var showImportSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAiAssistantDrawer by remember { mutableStateOf(false) }
    
    // Active track custom setup states
    var selectedTrackMenuId by remember { mutableStateOf<Int?>(null) }
    var selectedTrackStyleId by remember { mutableStateOf<Int?>(null) }

    // State for renaming a track
    var showRenameDialog by remember { mutableStateOf<ChannelTrack?>(null) }
    var renameInputString by remember { mutableStateOf("") }

    // Rendering simulation values
    val isRendering by viewModel.isRendering.collectAsState()
    val renderProgress by viewModel.renderProgress.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepBackground)
    ) {
        // Futuristic grid background styling
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = size.width / 40.dp.toPx()
            val rows = size.height / 40.dp.toPx()
            for (i in 0..cols.toInt()) {
                drawLine(
                    color = Color(0x0500E5FF),
                    start = androidx.compose.ui.geometry.Offset(i * 40.dp.toPx(), 0f),
                    end = androidx.compose.ui.geometry.Offset(i * 40.dp.toPx(), size.height),
                    strokeWidth = 1f
                )
            }
            for (i in 0..rows.toInt()) {
                drawLine(
                    color = Color(0x0500E5FF),
                    start = androidx.compose.ui.geometry.Offset(0f, i * 40.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(size.width, i * 40.dp.toPx()),
                    strokeWidth = 1f
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // --- TOP DECK: HEADER, MASTER LEVEL METERS & ANALYZERS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0A1018), Color.Transparent)
                        )
                    )
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Branded Title
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BRO AUDIO MIX",
                        color = NeonBlue,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("header_app_title")
                    )
                    Text(
                        text = currentSong?.let { "${it.title} - ${it.artist}" } ?: "Siap Impor Audio",
                        color = DarkTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // AI Spectrum & Wave viewer deck
                Row(
                    modifier = Modifier
                        .width(110.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF04080F))
                        .border(1.dp, Color(0x3300E5FF)),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    spectrumBands.forEach { bandValue ->
                        val barHeight = animateFloatAsState(
                            targetValue = if (isPlaying) bandValue * 30.dp.value else 2.dp.value,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "band"
                        )
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(barHeight.value.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(NeonPink, NeonBlue)
                                    ),
                                    shape = RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Master Dual Stereo METERS & Peak warning
                MasterPeakMeter(
                    mvLeft = mvLeft,
                    mvRight = mvRight,
                    isClipping = isClipping
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Action deck triggers (AI assistant & export actions with glass circular borders)
                IconButton(
                    onClick = { showAiAssistantDrawer = true },
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0x0DFFFFFF), CircleShape)
                        .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                        .testTag("ai_assistant_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Bro AI Assistant",
                        tint = NeonBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { showExportDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0x0DFFFFFF), CircleShape)
                        .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                        .testTag("export_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = "Export Track",
                        tint = NeonPink,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0x0DFFFFFF), thickness = 1.dp)

            // --- TRACKS TIMELINE CONSOLE VIEW ---
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxWidth()
            ) {
                if (isPlaying) {
                    // Scrolling visualizer bar indicator across screen
                    LinearProgressIndicator(
                        progress = { (playheadSeconds / 180.0f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = NeonPink,
                        trackColor = Color.Transparent,
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header timeline markers
                    item {
                        TimelineRuler(playheadSeconds = playheadSeconds)
                    }

                    items(tracks, key = { it.id }) { track ->
                        TrackTimelineRow(
                            track = track,
                            isPlaying = isPlaying,
                            playheadSeconds = playheadSeconds,
                            onVolumeChanged = { viewModel.updateTrackVolume(track.id, it) },
                            onMuteClicked = { viewModel.toggleTrackMute(track.id) },
                            onSoloClicked = { viewModel.toggleTrackSolo(track.id) },
                            onMenuClicked = { selectedTrackMenuId = track.id },
                            onStyleClicked = { selectedTrackStyleId = track.id },
                            onAnalyzeClicked = { viewModel.runAudioTrackAnalysis(track.id) }
                        )
                    }
                }
            }

            // --- BOTTOM SECTION: SONG CATALOG / SOUND SOURCE LIBRARY ---
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .border(
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0x1BFFFFFF), Color.Transparent)
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .background(Color(0xFF0A1018))
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIBRARY INSTRUMEN & SUMBER AUDIO",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )

                        TextButton(
                            onClick = { showImportSheet = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = NeonBlue)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Impor Lagu Sampel", fontSize = 11.sp)
                        }
                    }

                    // Multi-category instrumental sound library panel (Tracks 6-10 destination drops)
                    SoundLibraryPanel(
                        onInstrumentSelected = { instrumentName ->
                            val emptyClonedTrack = tracks.firstOrNull { it.group == TrackGroup.CLONED && it.volume <= 0.05f }
                            if (emptyClonedTrack != null) {
                                viewModel.injectLibraryInstrument(emptyClonedTrack.id, instrumentName)
                            } else {
                                Toast.makeText(context, "Maksimum track tambahan (F6-F10) telah terisi! Hapus atau bersihkan track tambahan.", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }

            // --- DECK FOOTER: TRANSPORT & TIME SEEK CONTROLS ---
            TransportControlDeck(
                isPlaying = isPlaying,
                isLooping = isLooping,
                playheadSeconds = playheadSeconds,
                onPlayPause = { viewModel.togglePlayPause() },
                onStop = { viewModel.resetTransport() },
                onRewind = { viewModel.seekTo(playheadSeconds - 5.1f) },
                onForward = { viewModel.seekTo(playheadSeconds + 5.1f) },
                onLoopToggle = { viewModel.toggleLoop() },
                onSeek = { viewModel.seekTo(it) }
            )

            // Permanent watermark footer
            Text(
                text = "Bro Audio Banjarnegara",
                color = Color(0x408E9AA8),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                textAlign = TextAlign.Center
            )
        }

        // --- PROGRESS OVERLAY FOR AI AUDIO ANALYSIS STEM SPLITTING ---
        if (isAnalyzing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF2060A0F)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = NeonBlue,
                        strokeWidth = 5.dp,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "AI STEM SEPARATION ACTIVE",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Menganalisis audio & memisahkan instrumen secara mandiri...",
                        color = DarkTextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${analysisProgress.toInt()}% Selesai",
                        color = NeonBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Linear loading slider
                    LinearProgressIndicator(
                        progress = { analysisProgress / 100.0f },
                        modifier = Modifier
                            .width(200.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = NeonBlue,
                        trackColor = Color(0x3300E5FF)
                    )
                }
            }
        }

        // --- DRAWER SHEET: SONG SAMPLE CATALOG LOADER ---
        if (showImportSheet) {
            ImportSongsSheet(
                samples = viewModel.songLibrary,
                onClose = { showImportSheet = false },
                onSelectSong = { song ->
                    showImportSheet = false
                    viewModel.loadSongSample(song)
                }
            )
        }

        // --- DRAWER SHEET: STUDIO QUALITY RENDER EXPORT PANEL ---
        if (showExportDialog) {
            ExportDAWDialog(
                isRendering = isRendering,
                renderProgress = renderProgress,
                onDismiss = { showExportDialog = false },
                onStartRender = { format, quality ->
                    viewModel.startRenderAudio(format, quality) {
                        showExportDialog = false
                        Toast.makeText(context, "Sukses mengekspor audio $format berkualitas $quality kKbps!", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        // --- DRAWER SIDE PANEL: USER INTERACTIVE GEMINI CHAT HELPER ---
        if (showAiAssistantDrawer) {
            AiAssistantSideDrawer(
                viewModel = viewModel,
                onClose = { showAiAssistantDrawer = false }
            )
        }

        // --- CONTEXT DIALOG: TRACK CUSTOM OPTIONS (⋮ Menu Actions) ---
        selectedTrackMenuId?.let { trackId ->
            val track = tracks.firstOrNull { it.id == trackId }
            if (track != null) {
                TrackActionMenuDialog(
                    track = track,
                    onDismiss = { selectedTrackMenuId = null },
                    onRenameClicked = {
                        selectedTrackMenuId = null
                        showRenameDialog = track
                        renameInputString = track.name
                    },
                    onPanSelected = { panVal ->
                        selectedTrackMenuId = null
                        viewModel.updateTrackPanning(track.id, panVal)
                    },
                    onDuplicateClicked = {
                        selectedTrackMenuId = null
                        viewModel.duplicateTrack(track.id)
                    },
                    onDeleteClicked = {
                        selectedTrackMenuId = null
                        viewModel.deleteTrack(track.id)
                    },
                    onColorPicked = { colorHex ->
                        selectedTrackMenuId = null
                        viewModel.setChannelColor(track.id, colorHex)
                    },
                    onAnalyzeClicked = {
                        selectedTrackMenuId = null
                        viewModel.runAudioTrackAnalysis(track.id)
                    }
                )
            }
        }

        // --- CONTEXT DIALOG: AI INSTRUMENT CLONING STYLE SELECTION ---
        selectedTrackStyleId?.let { trackId ->
            val track = tracks.firstOrNull { it.id == trackId }
            if (track != null) {
                StyleConverterSelectorDialog(
                    track = track,
                    onDismiss = { selectedTrackStyleId = null },
                    onStyleSelected = { style ->
                        selectedTrackStyleId = null
                        viewModel.runStyleConversion(track.id, style)
                    }
                )
            }
        }

        // --- POPUP DIALOG: RENAME CHANNEL TRACK ---
        showRenameDialog?.let { trackToRename ->
            AlertDialog(
                onDismissRequest = { showRenameDialog = null },
                shape = RoundedCornerShape(12.dp),
                containerColor = SurfaceDark,
                title = {
                    Text("Ganti Nama Track", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    @Suppress("DEPRECATION")
                    OutlinedTextField(
                        value = renameInputString,
                        onValueChange = { renameInputString = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = Color(0x33FFFFFF)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rename_text_field")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameInputString.isNotBlank()) {
                                viewModel.renameChannelTrack(trackToRename.id, renameInputString)
                            }
                            showRenameDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = Color.Black)
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = null }) {
                        Text("Batal", color = Color.White)
                    }
                }
            )
        }
    }
}

// --- MASTER PEAK METER COMPONENT ---
@Composable
fun MasterPeakMeter(
    mvLeft: Float,
    mvRight: Float,
    isClipping: Boolean
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("L", color = DarkTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            
            // Neon red clipping alert lamp
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isClipping) CrimsonPeak else Color(0xFF330000))
                    .border(
                        width = 1.dp,
                        color = if (isClipping) Color.Red else Color.Transparent,
                        shape = CircleShape
                    )
            )
            
            Text("R", color = DarkTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(2.dp))

        // Left / Right level meters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // L level meter block
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF0C1421))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(mvLeft.coerceIn(0f, 1f))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(AcidGreen, AmberVU, CrimsonPeak)
                            )
                        )
                )
            }
            
            // R level meter block
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF0C1421))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(mvRight.coerceIn(0f, 1f))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(AcidGreen, AmberVU, CrimsonPeak)
                            )
                        )
                )
            }
        }
    }
}

// --- TIMELINE ROW MARKERS ---
@Composable
fun TimelineRuler(
    playheadSeconds: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(Color(0xFF0C121C))
            .border(1.dp, Color(0x1F8E9AA8), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "TIMELINE:",
            color = NeonBlue,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(70.dp)
        )
        
        // Loop time ticks
        Box(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (sec in listOf(0, 30, 60, 90, 120, 150, 180)) {
                    Text(
                        text = String.format("%02d:%02d", sec / 60, sec % 60),
                        color = if (playheadSeconds in (sec - 5f)..(sec + 5f)) NeonPink else DarkTextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun RowMetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x0FFFFFFF))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = title,
                    color = Color.Gray,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- DETAILED CHANNEL TRACK MIX ROW ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackTimelineRow(
    track: ChannelTrack,
    isPlaying: Boolean,
    playheadSeconds: Float,
    onVolumeChanged: (Float) -> Unit,
    onMuteClicked: () -> Unit,
    onSoloClicked: () -> Unit,
    onMenuClicked: () -> Unit,
    onStyleClicked: () -> Unit,
    onAnalyzeClicked: () -> Unit
) {
    val trackActiveColor = Color(android.graphics.Color.parseColor(track.colorHex))
    val isUnusedMockTrack = track.group == TrackGroup.CLONED && track.waveformPoints.isEmpty()
    var isExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isUnusedMockTrack) Color(0x0AFFFFFF) else Color(0x14FFFFFF)
            )
            .border(
                width = 1.dp,
                color = if (track.isSoloed) NeonPink else if (isUnusedMockTrack) Color(0x0DFFFFFF) else Color(0x1BFFFFFF),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        // Vertical neon indicator stripe matching the active channel target color (full-height matching)
        Box(
            modifier = Modifier
                .matchParentSize()
                .align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(trackActiveColor)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Track Info / Tag Block
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .padding(end = 4.dp)
                        .clickable(enabled = !isUnusedMockTrack) { isExpanded = !isExpanded }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(trackActiveColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = track.name,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isUnusedMockTrack) "Status: Kosong" else "Style: ${track.currentStyle}",
                            color = DarkTextSecondary,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isUnusedMockTrack) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (track.aiTempo != null) Color(0x2B00E5FF) else Color(0x0DFFFFFF))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "AI",
                                    color = if (track.aiTempo != null) NeonBlue else Color.LightGray,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Waveform Scroll Visual Canvas or Offline placeholder
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x2B000000))
                        .clickable(enabled = !isUnusedMockTrack) { isExpanded = !isExpanded }
                ) {
                    if (isUnusedMockTrack) {
                        Text(
                            text = "--- Seret / Klik instrumen untuk aktifkan track ---",
                            color = Color(0x338E9AA8),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Render real multi-bar waveform details
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            track.waveformPoints.forEachIndexed { idx, value ->
                                // Animate level based on playback
                                val multiplier = if (isPlaying && (idx == (playheadSeconds / 4.5f).toInt() % 40)) 1.3f else 1.0f
                                val h = (value * multiplier * 26.dp.value).coerceIn(2f, 32f)
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(h.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(
                                            if (idx < (playheadSeconds / 4.5f).toInt()) {
                                                trackActiveColor
                                            } else {
                                                trackActiveColor.copy(alpha = 0.25f)
                                            }
                                        )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Stereo panning mini scale displays
                Column(
                    modifier = Modifier.width(42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when {
                            track.pan < -0.1f -> "L ${(track.pan * -100).toInt()}"
                            track.pan > 0.1f -> "R ${(track.pan * 100).toInt()}"
                            else -> "Center"
                        },
                        color = if (kotlin.math.abs(track.pan) > 0.1f) NeonBlue else DarkTextSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Volume Fader Slider
                Slider(
                    value = track.volume,
                    onValueChange = onVolumeChanged,
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = trackActiveColor,
                        activeTrackColor = trackActiveColor.copy(alpha = 0.7f),
                        inactiveTrackColor = Color(0x1AFFFFFF)
                    ),
                    modifier = Modifier
                        .width(76.dp)
                        .height(28.dp)
                        .testTag("fader_slider_${track.id}")
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Lit channel mixer controls (Solo and Mute circle triggers)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Solo control button tag
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (track.isSoloed) NeonPink else Color(0x11FFFFFF))
                            .clickable(onClick = onSoloClicked)
                            .border(1.dp, if (track.isSoloed) Color.Transparent else Color(0x1AFFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "S",
                            color = if (track.isSoloed) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Mute control button tag
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (track.isMuted) CrimsonPeak else Color(0x11FFFFFF))
                            .clickable(onClick = onMuteClicked)
                            .border(1.dp, if (track.isMuted) Color.Transparent else Color(0x1AFFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M",
                            color = if (track.isMuted) Color.White else CrimsonPeak,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                // AI style convert button
                IconButton(
                    onClick = onStyleClicked,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "AI Style Convert",
                        tint = if (track.isCloned) NeonPink else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // AI analysis expand toggle button (Glass circle)
                if (!isUnusedMockTrack) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isExpanded) Color(0x1A00E5FF) else Color(0x0CFFFFFF))
                            .border(
                                width = 1.dp,
                                color = if (isExpanded) NeonBlue.copy(alpha = 0.5f) else Color(0x1AFFFFFF),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Open AI Track Analyzer",
                            tint = if (track.aiTempo != null) NeonBlue else Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(28.dp))
                }

                // Triple dots triggers settings
                IconButton(
                    onClick = onMenuClicked,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu Setting",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // --- AI ANALYSIS EXPANDED PANEL SECTION ---
            if (!isUnusedMockTrack && isExpanded) {
                HorizontalDivider(color = Color(0x0DFFFFFF), thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x0A000000))
                        .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = NeonBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Track Analyzer Insights",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Close or Action Trigger button
                        if (track.isAnalyzingAi) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x0DFFFFFF))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = NeonBlue,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "Menganalisis...",
                                        color = NeonBlue,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        } else {
                            TextButton(
                                onClick = onAnalyzeClicked,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = NeonBlue,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = if (track.aiTempo == null) "Mulai Analisis" else "Analisis Ulang",
                                        color = NeonBlue,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (track.isAnalyzingAi) {
                        // Analysis Loading State
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x0AFFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Mengevaluasi karakteristik audio, mengidentifikasi tempo (BPM) & tangga nada transien...",
                                color = DarkTextSecondary,
                                fontSize = 9.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (track.aiTempo == null) {
                        // Empty/Initial State
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x05FFFFFF))
                                .border(1.dp, Color(0x0AFFFFFF), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Belum ada data analisis musik untuk track ini.",
                                    color = DarkTextSecondary,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Klik 'Mulai Analisis' untuk menjalankan scan digital cerdas dengan Gemini AI.",
                                    color = Color.Gray,
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Metrics row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RowMetricCard(
                                title = "TEMPO (BPM)",
                                value = "${track.aiTempo} BPM",
                                icon = Icons.Default.Speed,
                                color = NeonBlue,
                                modifier = Modifier.weight(1f)
                            )
                            RowMetricCard(
                                title = "MUSIC KEY",
                                value = track.aiKey ?: "Unidentified",
                                icon = Icons.Default.MusicNote,
                                color = NeonPink,
                                modifier = Modifier.weight(1f)
                            )
                            RowMetricCard(
                                title = "DETEKSI MOOD",
                                value = track.aiMood ?: "Unidentified",
                                icon = Icons.Default.EmojiEmotions,
                                color = AcidGreen,
                                modifier = Modifier.weight(1.1f)
                            )
                        }

                        track.aiDescription?.let { desc ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1B00E5FF))
                                    .border(1.dp, NeonBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "\"$desc\"",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 9.sp,
                                    lineHeight = 13.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- DIGITAL INSTRUMENTS ACCESS LIBRARY ---
@Composable
fun SoundLibraryPanel(
    onInstrumentSelected: (String) -> Unit
) {
    val categories = listOf("Piano", "Guitar", "Bass", "Strings", "Sintetis", "Hibrid", "Kendang/Tradisi")
    var selectedCategory by remember { mutableStateOf("Piano") }

    val instruments = when (selectedCategory) {
        "Piano" -> listOf("Grand Piano", "Electric Piano", "Hammond Organ", "Clavinet Preset")
        "Guitar" -> listOf("Acoustic Guitar", "Electric Guitar Clean", "Distortion Cyber Guitar", "Harmonics Acoustic")
        "Bass" -> listOf("Acoustic Double-Bass", "Electric Precision Bass", "Cyber Sub Bass Synth", "Acid Bass Wave")
        "Strings" -> listOf("Violin Ensemble", "Solo Viola Pluck", "Cello Slow Drone", "Orchestra String Run")
        "Sintetis" -> listOf("SuperSaw Pad", "Cosmic Bell Pluck", "Sine Wave Synth", "Chiptune Square Lead")
        "Hibrid" -> listOf("Vox Choir Pad", "Drums Electro Kit", "Rock Heavy Kit", "EDM Punchy Kick")
        else -> listOf("Kendang Double Slap", "Angklung Pluck", "Gamelan Saron Pitch", "Indo Kenong Gong")
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Horizontal category filters chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(categories) { cat ->
                val active = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Color(0x1B00E5FF) else Color(0x0DFFFFFF))
                        .clickable { selectedCategory = cat }
                        .border(
                            width = 1.dp,
                            color = if (active) Color(0x4D00E5FF) else Color(0x1AFFFFFF),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cat,
                        color = if (active) NeonBlue else DarkTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Horizontal sound presets
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(instruments) { instr ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                    modifier = Modifier
                        .width(136.dp)
                        .clickable { onInstrumentSelected(instr) }
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = instr,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "+ Tambah ke DAW Track",
                            color = NeonBlue,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// --- FOOTER TRANSPORT DISPLAY CONTROL DECK ---
@Composable
fun TransportControlDeck(
    isPlaying: Boolean,
    isLooping: Boolean,
    playheadSeconds: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onLoopToggle: () -> Unit,
    onSeek: (Float) -> Unit
) {
    val totalSeconds = 180f
    val currentFormatted = String.format("%02d:%02d", (playheadSeconds / 60).toInt(), (playheadSeconds % 60).toInt())
    val totalFormatted = "03:00"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A1018))
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp)
    ) {
        HorizontalDivider(color = Color(0x11FFFFFF), thickness = 1.dp)
        Spacer(modifier = Modifier.height(4.dp))
        // Seek fader slider and timestamp displays
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentFormatted,
                color = NeonBlue,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp)
            )

            Slider(
                value = playheadSeconds,
                onValueChange = onSeek,
                valueRange = 0.0f..totalSeconds,
                colors = SliderDefaults.colors(
                    thumbColor = NeonPink,
                    activeTrackColor = NeonPink,
                    inactiveTrackColor = Color(0x1AFFFFFF)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .testTag("timeline_seekbar")
            )

            Text(
                text = totalFormatted,
                color = DarkTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Standard Transport buttons row matching Ableton/Cubase ergonomics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Loop toggle Button (Glass circle)
            IconButton(
                onClick = onLoopToggle,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isLooping) Color(0x1B00E5FF) else Color(0x0DFFFFFF))
                    .border(1.dp, if (isLooping) NeonBlue.copy(alpha = 0.5f) else Color(0x1AFFFFFF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.AllInclusive,
                    contentDescription = "Looping Toggle",
                    tint = if (isLooping) NeonBlue else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Rewind 5s (Glass circle)
            IconButton(
                onClick = onRewind,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x0DFFFFFF))
                    .border(1.dp, Color(0x1AFFFFFF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Fast Rewind",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Play / Pause big core button (Prominent pure white circle with black icon)
            FloatingActionButton(
                onClick = onPlayPause,
                shape = CircleShape,
                containerColor = Color.White,
                contentColor = Color.Black,
                modifier = Modifier
                    .size(54.dp)
                    .testTag("play_pause_fab")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play Pause Toggle",
                    modifier = Modifier.size(28.dp),
                    tint = Color.Black
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Stop transport button (Glass circle)
            IconButton(
                onClick = onStop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x0DFFFFFF))
                    .border(1.dp, Color(0x1AFFFFFF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop timeline",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Fast forward 5s (Glass circle)
            IconButton(
                onClick = onForward,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x0DFFFFFF))
                    .border(1.dp, Color(0x1AFFFFFF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Fast Forward",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// --- SHEET: SAMPLE CATALOG SEPARATION PROCESSOR ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSongsSheet(
    samples: List<AudioSongSample>,
    onClose: () -> Unit,
    onSelectSong: (AudioSongSample) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "LAGU SAMPEL RE-MIXING & STEM SEPARATION",
                color = NeonBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(samples) { song ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF162232)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSong(song) }
                            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("${song.artist} • ${song.genre}", color = DarkTextSecondary, fontSize = 11.sp)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x1B00E5FF))
                                    .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Pisahkan Stems AI", color = NeonBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// --- SIDE PANEL: BRO AI CHAT ASSISTANT (Gemini) ---
@Composable
fun AiAssistantSideDrawer(
    viewModel: AudioMixViewModel,
    onClose: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsState()
    val inputText by viewModel.chatInputText.collectAsState()
    val isLoading by viewModel.isChatLoading.collectAsState()
    val listState = rememberLazyListState()

    // Scroll to latest message on append
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6060A0F))
            .clickable { onClose() } // Tap outside dismiss
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.82f)
                .clickable(enabled = false) {}, // Intercept click
            color = SurfaceDark,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Drawer Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = NeonBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ASISTEN BRO AI",
                            color = NeonBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close Drawer", tint = Color.White)
                    }
                }

                Divider(color = Color(0x33A0AEC0), modifier = Modifier.padding(vertical = 12.dp))

                // Chat logger list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                        ) {
                            Text(
                                text = msg.sender,
                                color = if (msg.isUser) NeonPink else NeonBlue,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 8.dp,
                                            topEnd = 8.dp,
                                            bottomStart = if (msg.isUser) 8.dp else 0.dp,
                                            bottomEnd = if (msg.isUser) 0.dp else 8.dp
                                        )
                                    )
                                    .background(
                                        if (msg.isUser) Color(0xFF1E2738) else Color(0x3300E5FF)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (msg.isUser) Color(0x33FFFFFF) else Color(0x3300E5FF),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = msg.message,
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (isLoading) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = NeonBlue, strokeWidth = 1.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bro AI sedang merumuskan saran preset...", color = DarkTextSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Quick prompts helpers
                Text(
                    text = "Q-RECOMENDATION PROMPTS:",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Tips memisahkan vokal secara jernih?",
                        "Cara merubah gitar pop menjadi rock distorsi?",
                        "Tips mengencangkan bass line agar tidak sember?",
                        "Bagaimana cara mengatasi clipping merah?"
                    ).forEach { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A2635))
                                .clickable { viewModel.quickAssistantPrompt(chip) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(chip, color = Color.LightGray, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chat Input and send deck
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    @Suppress("DEPRECATION")
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { viewModel.setChatInput(it) },
                        placeholder = { Text("Tanyakan audio synthesis...", fontSize = 11.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_chat_text_field"),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = Color(0x228E9AA8)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { viewModel.sendChatMessage() },
                        modifier = Modifier
                            .size(38.dp)
                            .background(NeonBlue, RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send prompt",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- DIALOG: TRACK PROPERTIES EDIT MENUS (⋮ Button Settings) ---
@Composable
fun TrackActionMenuDialog(
    track: ChannelTrack,
    onDismiss: () -> Unit,
    onRenameClicked: () -> Unit,
    onPanSelected: (Float) -> Unit,
    onDuplicateClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onColorPicked: (String) -> Unit,
    onAnalyzeClicked: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = "PENGATURAN: ${track.name}",
                color = NeonBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Rename Track option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRenameClicked() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(18.dp))
                    Text("Ganti Nama Track", color = Color.White, fontSize = 13.sp)
                }

                // 1b. AI analysis request trigger
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAnalyzeClicked() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(18.dp))
                    Text("Jalankan Analisis AI Track", color = Color.White, fontSize = 13.sp)
                }

                // 2. Quick panning setup row
                Text("Pan Posisi Audio:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(
                        "Full Left" to -1.0f,
                        "Center" to 0.0f,
                        "Full Right" to 1.0f
                    ).forEach { (label, value) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF243449))
                                .clickable { onPanSelected(value) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 3. Track paint custom color selection row
                Text("Ubah Warna Track:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("#00E5FF", "#FF007F", "#FFD700", "#39FF14", "#EA80FC", "#A0AEC0").forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (track.colorHex == hex) 2.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { onColorPicked(hex) }
                        )
                    }
                }

                Divider(color = Color(0x33A0AEC0), modifier = Modifier.padding(vertical = 4.dp))

                // 4. Duplicate Track Option (only if track of cloned can accept duplication)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDuplicateClicked() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = NeonPink, modifier = Modifier.size(18.dp))
                    Text("Duplikasi Track", color = Color.White, fontSize = 13.sp)
                }

                // 5. Reset/Delete Track Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDeleteClicked() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CrimsonPeak, modifier = Modifier.size(18.dp))
                    Text("Hapus / Setel Ulang", color = Color.White, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Selesai", color = NeonBlue)
            }
        }
    )
}

// --- DIALOG: AI STYLE PRESENTS CONVERTER DISPATCHER ---
@Composable
fun StyleConverterSelectorDialog(
    track: ChannelTrack,
    onDismiss: () -> Unit,
    onStyleSelected: (String) -> Unit
) {
    val styles = listOf(
        "Pop" to "Style Pop Seimbang",
        "Rock" to "Drum Hentak, Gitar Distorsi",
        "Metal" to "Gitar Distorsi Berat Cepat",
        "EDM" to "Elektronika Ketukan Modern",
        "House" to "Bass Punchy Menentram",
        "Dangdut" to "Gendang Slap Dangdut Tradisional",
        "Jazz" to "Groove Ayun Santai",
        "Blues" to "Sentuhan Akord Sendu",
        "Reggae" to "Ketukan Sinkopasi Santai",
        "Funk" to "Ritem Bass Menari",
        "Orchestral" to "Megah Bioskop Gesekan Gesek",
        "Cinematic" to "Atmosfer Fiksi Sains"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = "AI CLONING & STYLE CONVERTER",
                color = NeonPink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column {
                Text(
                    text = "Konversi Style instrumen asli lagu tanpa merubah tempo lagu asli menggunakan model AI cloning server. Pilih style preset:",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(styles) { (styleName, desc) ->
                        val currentActive = track.currentStyle == styleName
                        Card(
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentActive) Color(0xFF231C2E) else Color(0xFF16212F)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStyleSelected(styleName) }
                                .border(
                                    width = 1.dp,
                                    color = if (currentActive) NeonPink else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = styleName,
                                    color = if (currentActive) NeonPink else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(desc, color = DarkTextSecondary, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Color.White)
            }
        }
    )
}

// --- DIALOG: AUDIO DOWNLOAD CHANNELS RENDER EXPORTER ---
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ExportDAWDialog(
    isRendering: Boolean,
    renderProgress: Float,
    onDismiss: () -> Unit,
    onStartRender: (format: String, quality: String) -> Unit
) {
    val codecs = listOf("MP3", "WAV", "FLAC", "AAC", "OGG", "M4A")
    val qualities = listOf("128 kbps", "192 kbps", "256 kbps", "320 kbps", "Lossless")
    
    var selectedCodec by remember { mutableStateOf("WAV") }
    var selectedQuality by remember { mutableStateOf("320 kbps") }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!isRendering) onDismiss() },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = "EKSPOR MULTI-TRACK AUDIO",
                color = NeonBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isRendering) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = NeonPink,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("SEDANG ENCODING RENDER AUDIO...", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Kualitas: $selectedCodec ($selectedQuality)", color = DarkTextSecondary, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("${renderProgress.toInt()}%", color = NeonPink, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    Text("Tentukan format kompresi dan kualitas file hasil render studio audio:", color = Color.LightGray, fontSize = 11.sp)
                    
                    Text("Codec Format Audio:", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    // Codecs Select Row Grid
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        codecs.forEach { codec ->
                            val active = codec == selectedCodec
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (active) NeonBlue else Color(0xFF1E2633))
                                    .clickable { selectedCodec = codec }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(codec, color = if (active) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Kualitas Hasil Simpan:", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    // Qualities Select Row Grid
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        qualities.forEach { qual ->
                            val active = qual == selectedQuality
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (active) NeonPink else Color(0xFF1E2633))
                                    .clickable { selectedQuality = qual }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(qual, color = if (active) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Divider(color = Color(0x33A0AEC0), modifier = Modifier.padding(vertical = 4.dp))

                    // Simulated Social share triggers
                    Text("Atau langsung bagikan ke sosial media:", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("WhatsApp", "Telegram", "Instagram", "TikTok", "Gmail Drive").forEach { appName ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF141C27))
                                    .clickable {
                                        Toast.makeText(context, "Membuka media sosial $appName untuk render instan...", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(appName, color = Color.LightGray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isRendering) {
                Button(
                    onClick = { onStartRender(selectedCodec, selectedQuality) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = Color.Black)
                ) {
                    Text("Mulai Ekspor DAW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isRendering) {
                TextButton(onClick = onDismiss) {
                    Text("Batal", color = Color.White)
                }
            }
        }
    )
}
