package com.example.qrscannerapp

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

// --- Цветовая палитра из прототипа ---
val StardustGlassBg = Color(0xBF1A1A1D)
val StardustItemBg = Color(0x14FFFFFF)
val StardustPrimary = Color(0xFF6A5AE0)
val StardustSecondary = Color(0xFF8A7DFF)
val StardustTextPrimary = Color.White
val StardustTextSecondary = Color(0xFFA0A0A5)
val StardustModalBg = Color(0xFF2a2a2e)


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var showSplashScreen by remember { mutableStateOf(true) }

                if (showSplashScreen) {
                    SplashScreen(onAnimationFinished = {
                        showSplashScreen = false
                    })
                } else {
                    MainApp()
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StardustScreen(
    viewModel: QrScannerViewModel,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settingsManager = remember { SettingsManager(context) }
    val isSoundEnabled by settingsManager.isSoundEnabledFlow.collectAsState(initial = true)
    val isVibrationEnabled by settingsManager.isVibrationEnabledFlow.collectAsState(initial = true)

    val activeTab by viewModel.activeTab.collectAsState()

    val currentList = when (activeTab) {
        ActiveTab.SCOOTERS -> viewModel.scooterCodes
        ActiveTab.BATTERIES -> viewModel.batteryCodes
    }

    var showManualInputDialog by remember { mutableStateOf(false) }
    val exportSheetState = rememberModalBottomSheetState()
    var showExportSheet by remember { mutableStateOf(false) }
    // --- НОВОЕ СОСТОЯНИЕ: для управления диалогом сохранения ---
    var showSaveSessionDialog by remember { mutableStateOf(false) }

    var hasCameraPermission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { granted -> hasCameraPermission = granted }
    LaunchedEffect(key1 = true) { launcher.launch(Manifest.permission.CAMERA) }

    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else { @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }

    val listState = rememberLazyListState()

    LaunchedEffect(key1 = currentList.firstOrNull()) {
        if (currentList.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scanEffect.collect {
            if (isSoundEnabled) {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            }
            if (isVibrationEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else { @Suppress("DEPRECATION") vibrator.vibrate(50) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraView(viewModel = viewModel, hasPermission = hasCameraPermission)
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Меню",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .height(250.dp)
                    .fillMaxWidth()
                    .background(StardustGlassBg)
            ) {
                val tabs = listOf("Самокаты", "АКБ")
                TabRow(
                    selectedTabIndex = activeTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = StardustTextPrimary,
                    indicator = { tabPositions ->
                        if (activeTab.ordinal < tabPositions.size) {
                            Box(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[activeTab.ordinal])
                                    .height(3.dp)
                                    .background(
                                        color = StardustPrimary,
                                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                    )
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = StardustItemBg.copy(alpha = 0.5f))
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = activeTab.ordinal == index,
                            onClick = { viewModel.onTabSelected(ActiveTab.values()[index]) },
                            text = { Text(title) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Найдено: ${currentList.size}", color = StardustTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { viewModel.clearList() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Очистить список", tint = StardustTextSecondary)
                    }
                }
                if (currentList.isEmpty()) {
                    EmptyState(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 24.dp)
                    ) {
                        items(items = currentList, key = { it.id }) { item ->
                            ScanListItem(
                                item = item,
                                modifier = Modifier.animateItemPlacement(tween(durationMillis = 300)),
                                onDelete = { viewModel.removeCode(item) },
                                onCopy = { code ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("QR Code", code))
                                    Toast.makeText(context, "Код скопирован!", Toast.LENGTH_SHORT).show()
                                },
                                onItemShown = { viewModel.markAsOld(item) }
                            )
                        }
                    }
                }
            }
            ActionButtons(
                onAddClick = { showManualInputDialog = true },
                onExportClick = {
                    if (currentList.isNotEmpty()) {
                        showExportSheet = true
                    } else {
                        Toast.makeText(context, "Список пуст, нечего экспортировать", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    if (showManualInputDialog) {
        ManualInputDialog(
            activeTab = activeTab,
            onDismissRequest = { showManualInputDialog = false },
            onAddCode = { code -> viewModel.addManualCode(code); showManualInputDialog = false }
        )
    }

    if (showExportSheet) {
        ExportSheet(
            listToExport = currentList,
            sheetState = exportSheetState,
            onDismiss = { showExportSheet = false },
            onCopyAll = { list ->
                val allCodes = list.joinToString("\n") { it.code }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("All Codes", allCodes))
                Toast.makeText(context, "Все коды скопированы!", Toast.LENGTH_SHORT).show()
                scope.launch { exportSheetState.hide() }.invokeOnCompletion { if (!exportSheetState.isVisible) showExportSheet = false }
            },
            onShare = { list ->
                val allCodes = list.joinToString("\n") { it.code }
                val sendIntent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, allCodes); type = "text/plain" }
                val shareIntent = Intent.createChooser(sendIntent, null)
                context.startActivity(shareIntent)
                scope.launch { exportSheetState.hide() }.invokeOnCompletion { if (!exportSheetState.isVisible) showExportSheet = false }
            },
            // --- ИЗМЕНЕНИЕ: Теперь кнопка открывает диалог, а не сохраняет напрямую ---
            onSaveSession = {
                scope.launch { exportSheetState.hide() }.invokeOnCompletion {
                    if (!exportSheetState.isVisible) {
                        showExportSheet = false
                        showSaveSessionDialog = true // Открываем диалог после того, как лист скрылся
                    }
                }
            }
        )
    }

    // --- НОВЫЙ БЛОК: Отображение диалога сохранения сессии ---
    if (showSaveSessionDialog) {
        SaveSessionDialog(
            onDismissRequest = { showSaveSessionDialog = false },
            onSave = { sessionName ->
                viewModel.saveCurrentSession(sessionName)
                showSaveSessionDialog = false
            }
        )
    }
}


// --- НОВЫЙ КОМПОНЕНТ: Диалоговое окно для ввода имени сессии ---
@Composable
fun SaveSessionDialog(
    onDismissRequest: () -> Unit,
    onSave: (name: String?) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = StardustModalBg)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Сохранить сессию",
                    color = StardustTextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Введите название для сессии (необязательно)",
                    color = StardustTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название сессии...") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = StardustItemBg, unfocusedContainerColor = StardustItemBg,
                        focusedTextColor = StardustTextPrimary, unfocusedTextColor = StardustTextPrimary,
                        cursorColor = StardustPrimary,
                        focusedIndicatorColor = StardustPrimary, unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = StardustTextSecondary, unfocusedLabelColor = StardustTextSecondary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onDismissRequest, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = StardustItemBg)) {
                        Text("Отмена", color = StardustTextSecondary, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { onSave(text) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = StardustPrimary)) {
                        Text("Сохранить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Ожидание сканирования...", color = StardustTextSecondary, fontSize = 16.sp)
    }
}

// ... Остальной код файла MainActivity.kt без изменений ...
@Composable
fun ScanListItem(
    item: ScanItem,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onCopy: (String) -> Unit,
    onItemShown: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (item.isNew) StardustPrimary.copy(alpha = 0.3f) else Color.Transparent,
        animationSpec = tween(durationMillis = 500)
    )

    LaunchedEffect(item.isNew) {
        if (item.isNew) {
            delay(1500L)
            onItemShown()
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.thumbnail?.let { byteArray ->
                val bitmap = remember(byteArray) {
                    BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Scan thumbnail",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = item.code,
                color = StardustTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { onCopy(item.code) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Копировать", tint = StardustTextSecondary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "Удалить", tint = StardustTextSecondary)
                }
            }
        }
        HorizontalDivider(color = StardustItemBg, thickness = 1.dp)
    }
}

@Composable
fun ActionButtons(onAddClick: () -> Unit, onExportClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StardustGlassBg)
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onAddClick,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StardustPrimary)
        ) {
            Text("+", fontSize = 32.sp, color = StardustTextPrimary)
        }
        Button(
            onClick = onExportClick,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StardustPrimary)
        ) {
            Text("Экспорт / Поделиться", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StardustTextPrimary)
        }
    }
}

@Composable
fun ManualInputDialog(
    activeTab: ActiveTab,
    onDismissRequest: () -> Unit,
    onAddCode: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    val title: String
    val label: String
    val keyboardOptions: KeyboardOptions
    val onValueChange: (String) -> Unit

    when (activeTab) {
        ActiveTab.SCOOTERS -> {
            title = "Добавить самокат"
            label = "Введите номер самоката..."
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            onValueChange = { newValue -> text = newValue.filter { it.isDigit() } }
        }
        ActiveTab.BATTERIES -> {
            title = "Добавить АКБ"
            label = "Введите серийный номер АКБ..."
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            onValueChange = { newValue -> text = newValue }
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = StardustModalBg)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, color = StardustTextPrimary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = keyboardOptions,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = StardustItemBg, unfocusedContainerColor = StardustItemBg,
                        focusedTextColor = StardustTextPrimary, unfocusedTextColor = StardustTextPrimary,
                        cursorColor = StardustPrimary,
                        focusedIndicatorColor = StardustPrimary, unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = StardustTextSecondary, unfocusedLabelColor = StardustTextSecondary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onDismissRequest, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = StardustItemBg)) {
                        Text("Отмена", color = StardustTextSecondary, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { onAddCode(text) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = StardustPrimary), enabled = text.isNotBlank()) {
                        Text("Добавить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    listToExport: List<ScanItem>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCopyAll: (List<ScanItem>) -> Unit,
    onShare: (List<ScanItem>) -> Unit,
    onSaveSession: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StardustModalBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Экспорт данных", color = StardustTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onSaveSession, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = StardustSecondary)) {
                Text("Сохранить сессию в историю", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = { onCopyAll(listToExport) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = StardustPrimary)) {
                Text("Копировать в буфер обмена", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { onShare(listToExport) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = StardustPrimary)) {
                Text("Поделиться (как текст)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StardustItemBg)
            ) {
                Text("Отмена", color = StardustTextSecondary, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
fun CameraView(viewModel: QrScannerViewModel, hasPermission: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        .also {
                            it.setAnalyzer(Executors.newSingleThreadExecutor(), QrCodeAnalyzer(
                                onCodeScanned = { code, thumbnail -> viewModel.onCodeScanned(code, thumbnail) },
                                onStatusUpdate = { message -> viewModel.updateStatus(message, isError = true) }
                            ))
                        }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalyzer)
                    } catch (e: Exception) { Log.e("CameraView", "Camera bind error", e) }
                    previewView.setOnTouchListener { _, event ->
                        val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        camera?.cameraControl?.startFocusAndMetering(action)
                        true
                    }
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .align(Alignment.Center)
                    .border(3.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
            )
            IconButton(
                onClick = { isTorchOn = !isTorchOn; camera?.cameraControl?.enableTorch(isTorchOn) },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(imageVector = Icons.Filled.FlashOn, contentDescription = "Flashlight", tint = if (isTorchOn) Color.Yellow else Color.White, modifier = Modifier.size(32.dp))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Требуется разрешение на использование камеры", color = StardustTextPrimary)
            }
        }
    }
}

private fun Bitmap.toByteArray(): ByteArray {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, 80, stream)
    return stream.toByteArray()
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        val planeProxy = imageProxy.planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.e("imageProxyToBitmap", "Conversion failed", e)
        null
    }
}

class QrCodeAnalyzer(
    private val onCodeScanned: (String, ByteArray?) -> Unit,
    private val onStatusUpdate: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private var isProcessing = false
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing) { imageProxy.close(); return }
        isProcessing = true
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val options = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
            val scanner = BarcodeScanning.getClient(options)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        barcodes.firstNotNullOfOrNull { it.rawValue }?.let { code ->
                            val thumbnailBitmap = imageProxyToBitmap(imageProxy)
                            val thumbnailByteArray = thumbnailBitmap?.toByteArray()
                            onCodeScanned(code, thumbnailByteArray)
                        }
                    }
                }
                .addOnFailureListener { e -> Log.e("QrCodeAnalyzer", "Scan error", e); onStatusUpdate("Ошибка сканера. Попробуйте снова.") }
                .addOnCompleteListener { isProcessing = false; imageProxy.close() }
        } else { isProcessing = false; imageProxy.close() }
    }
}