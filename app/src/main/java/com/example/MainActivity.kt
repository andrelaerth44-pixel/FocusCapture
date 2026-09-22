package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.detection.CropRect
import com.example.storage.HistoryItem
import com.example.storage.HistoryManager
import com.example.ui.components.ConfidenceBadge
import com.example.ui.components.CropOverlay
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.BorderFocus
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CrimsonRecord
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.FocusCaptureTheme
import com.example.ui.theme.IndigoGlow
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.FocusCaptureViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: FocusCaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FocusCaptureTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val context = LocalContext.current

                // Launcher para captura de tela via MediaProjection
                val projectionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                        val metrics = resources.displayMetrics
                        viewModel.startRecording(
                            context = context,
                            resultCode = result.resultCode,
                            data = result.data!!,
                            width = metrics.widthPixels,
                            height = metrics.heightPixels,
                            density = metrics.densityDpi
                        )
                    } else {
                        Toast.makeText(context, "Permissão de gravação de tela cancelada", Toast.LENGTH_SHORT).show()
                    }
                }

                // Launcher para permissão de notificações no Android 13+
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* prossegue com a captura */ }

                // Launcher para seletor de vídeo moderno do Android Photo Picker
                val videoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        viewModel.onVideoSourceReady(uri)
                    }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    containerColor = ObsidianBg
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (val screen = currentScreen) {
                            is AppScreen.Home -> {
                                HomeScreen(
                                    onStartRecordingClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                        projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                    },
                                    onImportVideoClick = {
                                        videoPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.VideoOnly
                                            )
                                        )
                                    },
                                    onHistoryClick = {
                                        viewModel.navigateTo(AppScreen.History)
                                    }
                                )
                            }
                            is AppScreen.Recording -> {
                                RecordingScreen(
                                    seconds = screen.seconds,
                                    onStopClick = {
                                        viewModel.stopRecording(context)
                                    }
                                )
                            }
                            is AppScreen.Analyzing -> {
                                AnalyzingScreen(message = screen.statusMessage)
                            }
                            is AppScreen.ReviewAndCrop -> {
                                ReviewAndCropScreen(
                                    state = screen,
                                    onCropChange = { viewModel.updateCropRect(it) },
                                    onPresetSelect = { viewModel.applyPreset(it, screen) },
                                    onConfirmClick = { viewModel.executeCrop(screen) },
                                    onBackClick = { viewModel.navigateHome() }
                                )
                            }
                            is AppScreen.Processing -> {
                                ProcessingScreen(progress = screen.progress)
                            }
                            is AppScreen.Result -> {
                                ResultScreen(
                                    state = screen,
                                    onSaveGallery = {
                                        Toast.makeText(context, "Vídeo salvo na pasta Filmes/FocusCapture!", Toast.LENGTH_SHORT).show()
                                    },
                                    onShare = {
                                        viewModel.shareVideo(context, screen.croppedFile)
                                    },
                                    onReCrop = {
                                        viewModel.onVideoSourceReady(screen.originalUri)
                                    },
                                    onHome = {
                                        viewModel.navigateHome()
                                    }
                                )
                            }
                            is AppScreen.History -> {
                                HistoryScreen(
                                    onBack = { viewModel.navigateHome() },
                                    onItemClick = { item ->
                                        viewModel.onVideoSourceReady(Uri.fromFile(java.io.File(item.filePath)))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// TELA 1: HOME
// ---------------------------------------------------------
@Composable
fun HomeScreen(
    onStartRecordingClick: () -> Unit,
    onImportVideoClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cabeçalho com Logo Futurista
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(ElectricCyan, IndigoGlow)
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = "Logo FocusCapture",
                        tint = ObsidianBg,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "FOCUSCAPTURE",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "Detecção e Recorte Automático",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = DarkSurfaceVariant,
                modifier = Modifier
                    .size(42.dp)
                    .clickable { onHistoryClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Histórico",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Card explicativo do foco inteligente
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DETECTOR ESPACIAL E TEMPORAL",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElectricCyan,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Grave a tela reproduzindo TikTok, YouTube ou Reels. O FocusCapture detecta a área exata do vídeo e remove barras de status, bordas pretas e menus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            }
        }

        // Botão Primário: Gravar Tela
        Button(
            onClick = onStartRecordingClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("record_screen_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = CrimsonRecord,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(18.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Gravar Tela",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Botão Secundário: Importar Vídeo da Galeria
        Button(
            onClick = onImportVideoClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("import_video_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkSurfaceHighlight,
                contentColor = TextPrimary
            ),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Importar Vídeo Gravado",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Grid de Modos
        Text(
            text = "3 MODOS DE CORTE",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModeCard(
                modifier = Modifier.weight(1f),
                title = "Automático",
                subtitle = "Foco direto no player",
                icon = Icons.Default.Adjust,
                accent = ElectricCyan
            )
            ModeCard(
                modifier = Modifier.weight(1f),
                title = "Ajuste Fino",
                subtitle = "Guias táteis inteligentes",
                icon = Icons.Default.CropFree,
                accent = IndigoGlow
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Histórico Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onHistoryClick() }
                .testTag("history_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Histórico",
                        tint = ElectricCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Histórico de Vídeos",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Acesse seus vídeos processados",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

// ---------------------------------------------------------
// TELA 2: GRAVANDO TELA
// ---------------------------------------------------------
@Composable
fun RecordingScreen(
    seconds: Long,
    onStopClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_recording")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_recording_alpha"
    )

    val timeString = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Indicador Vermelho Pulsante
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(CrimsonRecord.copy(alpha = 0.2f * pulseAlpha))
                .border(2.dp, CrimsonRecord.copy(alpha = pulseAlpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(CrimsonRecord)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "GRAVANDO TELA",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = CrimsonRecord
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = timeString,
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            ),
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Text(
                text = "Abra o aplicativo com o vídeo (TikTok, YouTube, Reels) e deixe reproduzir. Quando terminar, volte aqui ou toque na notificação para finalizar.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp),
                lineHeight = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onStopClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag("stop_recording_button"),
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRecord),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Parar e Processar Vídeo",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

// ---------------------------------------------------------
// TELA 3: ANALISANDO VÍDEO
// ---------------------------------------------------------
@Composable
fun AnalyzingScreen(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = ElectricCyan,
            strokeWidth = 5.dp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Análise Visual em Andamento",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AnalysisStepRow(label = "Amostrando quadros espaçados no tempo", isComplete = true)
                AnalysisStepRow(label = "Calculando diferenças de luminância", isComplete = true)
                AnalysisStepRow(label = "Isolando barras de status e navegação", isComplete = false)
            }
        }
    }
}

@Composable
fun AnalysisStepRow(label: String, isComplete: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (isComplete) Icons.Default.Check else Icons.Default.Adjust,
            contentDescription = null,
            tint = if (isComplete) EmeraldSuccess else ElectricCyan,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isComplete) TextPrimary else TextSecondary
        )
    }
}

// ---------------------------------------------------------
// TELA 4: REVISÃO E AJUSTE DE CORTE (INTERATIVA)
// ---------------------------------------------------------
@Composable
fun ReviewAndCropScreen(
    state: AppScreen.ReviewAndCrop,
    onCropChange: (CropRect) -> Unit,
    onPresetSelect: (Float?) -> Unit,
    onConfirmClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val pixelRect = state.currentCropRect.toPixelRect(state.metadata.displayWidth, state.metadata.displayHeight)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Barra Superior
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
            }

            ConfidenceBadge(confidence = state.detectionResult.confidence)

            IconButton(onClick = { onPresetSelect(null) }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Redefinir", tint = TextSecondary)
            }
        }

        // Diagnóstico Explicativo da Detecção
        if (state.detectionResult.description.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Text(
                    text = state.detectionResult.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Área Central do Frame com CropOverlay Interativo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (state.previewBitmap != null) {
                val bmpW = state.previewBitmap.width.toFloat()
                val bmpH = state.previewBitmap.height.toFloat()
                val bmpAspect = if (bmpH > 0) bmpW / bmpH else 1.0f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(bmpAspect, matchHeightConstraintsFirst = true)
                ) {
                    Image(
                        bitmap = state.previewBitmap.asImageBitmap(),
                        contentDescription = "Pré-visualização do vídeo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    CropOverlay(
                        cropRect = state.currentCropRect,
                        onCropRectChange = onCropChange,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Text("Carregando frame...", color = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Presets de Proporção
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PresetChip(label = "9:16", onClick = { onPresetSelect(9f / 16f) })
            PresetChip(label = "16:9", onClick = { onPresetSelect(16f / 9f) })
            PresetChip(label = "1:1", onClick = { onPresetSelect(1f) })
            PresetChip(label = "4:3", onClick = { onPresetSelect(4f / 3f) })
            PresetChip(label = "Livre", onClick = { onPresetSelect(null) })
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Informação de Resolução Resultante
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Resolução Final:",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Text(
                text = "${pixelRect.width} × ${pixelRect.height} px",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Botão de Confirmação do Recorte
        Button(
            onClick = onConfirmClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("confirm_crop_button"),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Confirmar e Recortar Vídeo",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun PresetChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DarkSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

// ---------------------------------------------------------
// TELA 5: PROCESSANDO CORTE
// ---------------------------------------------------------
@Composable
fun ProcessingScreen(progress: Float) {
    val percentage = (progress * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(110.dp),
                color = ElectricCyan,
                trackColor = DarkSurfaceVariant,
                strokeWidth = 8.dp
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "Recortando Vídeo Nativo",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Processando com aceleração MediaCodec e preservando áudio sincronizado.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = ElectricCyan,
            trackColor = DarkSurfaceVariant
        )
    }
}

// ---------------------------------------------------------
// TELA 6: RESULTADO FINAL
// ---------------------------------------------------------
@Composable
fun ResultScreen(
    state: AppScreen.Result,
    onSaveGallery: () -> Unit,
    onShare: () -> Unit,
    onReCrop: () -> Unit,
    onHome: () -> Unit
) {
    val pixelRect = state.cropRect.toPixelRect(state.metadata.displayWidth, state.metadata.displayHeight)
    val croppedUri = Uri.fromFile(state.croppedFile)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Barra Superior
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onHome) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Início", tint = TextPrimary)
            }
            Text(
                text = "VÍDEO PRONTO",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = EmeraldSuccess
            )
            IconButton(onClick = onShare) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "Compartilhar", tint = ElectricCyan)
            }
        }

        // Reprodutor do Vídeo Recortado
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            VideoPlayerView(
                videoUri = croppedUri,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cartão de Metadados
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Dimensão", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = "${pixelRect.width} × ${pixelRect.height}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Status", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = "Salvo na Galeria",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldSuccess
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Formato", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = "MP4 H.264",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botões de Ação
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("share_video_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Compartilhar", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }

            Button(
                onClick = onReCrop,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("recrop_video_button"),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceHighlight, contentColor = TextPrimary),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Icon(imageVector = Icons.Default.Crop, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Ajustar", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Text("Voltar ao Início", color = TextSecondary)
        }
    }
}

// ---------------------------------------------------------
// TELA 7: HISTÓRICO DE RECORTES
// ---------------------------------------------------------
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onItemClick: (HistoryItem) -> Unit
) {
    val items by HistoryManager.items.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Histórico de Vídeos",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nenhum vídeo recortado ainda.\nGrave uma tela ou importe um vídeo da galeria!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item) },
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${item.width} × ${item.height} px • ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            IconButton(
                                onClick = { HistoryManager.removeItem(context, item.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Excluir",
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
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
fun ModeCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "FocusCapture $name!", modifier = modifier)
}
