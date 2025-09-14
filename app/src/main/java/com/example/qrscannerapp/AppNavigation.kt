package com.example.qrscannerapp

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Scanner : Screen("scanner", "Сканер", Icons.Outlined.DocumentScanner)
    object Settings : Screen("settings", "Настройки", Icons.Outlined.Settings)
    object History : Screen("history", "История", Icons.Outlined.History)
    object About : Screen("about", "О приложении", Icons.Outlined.HelpOutline)
    object SessionDetail : Screen("session_detail/{sessionId}", "Детали сессии", Icons.Outlined.ReceiptLong)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val context = LocalContext.current
    val appVersion = try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        "v${packageInfo.versionName}"
    } catch (e: Exception) {
        "v?"
    }

    // --- НОВЫЙ БЛОК: Логика для индикатора обновления в меню ---
    val updateManager = remember { UpdateManager(context) }
    val updateState by updateManager.updateState.collectAsState()
    // Проверяем обновления один раз при запуске приложения
    LaunchedEffect(Unit) {
        updateManager.checkForUpdates()
    }


    val menuItems = listOf(
        Screen.Scanner,
        Screen.Settings,
        Screen.History,
        Screen.About
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = StardustModalBg
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(12.dp))
                    menuItems.forEach { screen ->
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.title,
                                    tint = if (currentRoute == screen.route) StardustTextPrimary else StardustTextSecondary
                                )
                            },
                            label = {
                                Text(
                                    screen.title,
                                    color = if (currentRoute == screen.route) StardustTextPrimary else StardustTextSecondary
                                )
                            },
                            // --- НОВЫЙ БЛОК: Отображение индикатора обновления ---
                            badge = {
                                if (screen is Screen.Settings && updateState is UpdateState.UpdateAvailable) {
                                    Badge(containerColor = Color.Red)
                                }
                            },
                            selected = currentRoute == screen.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = StardustPrimary,
                                unselectedContainerColor = Color.Transparent
                            )
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Lucius Studio", color = StardustTextSecondary, fontSize = 14.sp)
                        Text(appVersion, color = StardustTextSecondary.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                val currentScreen = menuItems.find { it.route == currentRoute }
                val topBarTitle = when {
                    currentRoute?.startsWith("session_detail/") == true -> Screen.SessionDetail.title
                    else -> currentScreen?.title ?: ""
                }

                if (currentRoute != Screen.Scanner.route) {
                    TopAppBar(
                        title = { Text(topBarTitle) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Меню")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = StardustModalBg,
                            titleContentColor = StardustTextPrimary,
                            navigationIconContentColor = StardustTextPrimary
                        )
                    )
                }
            }
        ) { innerPadding ->
            // --- ИЗМЕНЕНИЕ: Передаем updateManager и appVersion дальше ---
            AppNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
                onMenuClick = {
                    scope.launch { drawerState.open() }
                },
                updateManager = updateManager,
                appVersion = appVersion
            )
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit,
    // --- ИЗМЕНЕНИЕ: Принимаем новые параметры ---
    updateManager: UpdateManager,
    appVersion: String
) {
    val viewModel: QrScannerViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application)
    )

    NavHost(
        navController = navController,
        startDestination = Screen.Scanner.route,
        modifier = modifier
    ) {
        composable(Screen.Scanner.route) {
            StardustScreen(viewModel = viewModel, onMenuClick = onMenuClick)
        }
        composable(Screen.Settings.route) {
            // --- ИЗМЕНЕНИЕ: Передаем параметры в SettingsScreen ---
            SettingsScreen(updateManager = updateManager, appVersion = appVersion)
        }
        composable(Screen.History.route) {
            HistoryScreen(viewModel = viewModel, navController = navController)
        }
        composable(Screen.About.route) {
            AboutScreen()
        }
        composable(Screen.SessionDetail.route) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")
            if (sessionId != null) {
                SessionDetailScreen(sessionId = sessionId, viewModel = viewModel, navController = navController)
            } else {
                PlaceholderScreen(text = "Ошибка: ID сессии не найден")
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: QrScannerViewModel, navController: NavHostController) {
    val sessions = viewModel.scanSessions

    if (sessions.isEmpty()) {
        PlaceholderScreen(text = "История сканирований пуста.\n\nСохраните сессию на главном экране, чтобы она появилась здесь.")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(StardustModalBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = sessions, key = { it.id }) { session ->
                SessionListItem(
                    session = session,
                    onClick = {
                        navController.navigate("session_detail/${session.id}")
                    }
                )
            }
        }
    }
}

@Composable
fun SessionListItem(session: ScanSession, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StardustGlassBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val sessionType = when (session.type) {
                    SessionType.SCOOTERS -> "Самокаты"
                    SessionType.BATTERIES -> "АКБ"
                }

                if (session.name.isNullOrBlank()) {
                    Text(
                        text = formatTimestamp(session.timestamp),
                        color = StardustTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$sessionType: Найдено ${session.items.size} шт.",
                        color = StardustTextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = session.name,
                        color = StardustTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatTimestamp(session.timestamp)} • $sessionType: ${session.items.size} шт.",
                        color = StardustTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Перейти к деталям",
                tint = StardustTextSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(sessionId: String, viewModel: QrScannerViewModel, navController: NavHostController) {
    val session by remember { derivedStateOf { viewModel.scanSessions.find { it.id == sessionId } } }

    LaunchedEffect(session) {
        if (session == null) {
            navController.popBackStack()
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    val exportSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    session?.let { currentSession ->
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = StardustModalBg,
            bottomBar = {
                BottomAppBar(
                    containerColor = StardustGlassBg,
                    contentColor = StardustTextSecondary,
                ) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Удалить сессию")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { showExportSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = StardustPrimary)
                    ) {
                        Text("Экспорт / Поделиться")
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp)
            ) {
                items(items = currentSession.items, key = { it.id }) { item ->
                    HistoryScanListItem(
                        item = item,
                        onDelete = {
                            viewModel.deleteItemFromSession(sessionId, item)
                        },
                        onCopy = { code ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("QR Code", code))
                            Toast.makeText(context, "Код скопирован!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Удалить сессию?") },
                text = { Text("Это действие нельзя будет отменить. Вся информация об этой сессии будет удалена навсегда.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteSession(sessionId)
                            showDeleteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("Удалить") }
                },
                dismissButton = {
                    Button(onClick = { showDeleteDialog = false }) {
                        Text("Отмена")
                    }
                },
                containerColor = StardustModalBg,
                titleContentColor = StardustTextPrimary,
                textContentColor = StardustTextSecondary
            )
        }

        if (showExportSheet) {
            SessionExportSheet(
                listToExport = currentSession.items,
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
                }
            )
        }
    }
}

@Composable
fun HistoryScanListItem(
    item: ScanItem,
    onDelete: () -> Unit,
    onCopy: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.thumbnail?.let { byteArray ->
                val bitmap = remember(byteArray) { BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size) }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Scan thumbnail",
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionExportSheet(
    listToExport: List<ScanItem>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCopyAll: (List<ScanItem>) -> Unit,
    onShare: (List<ScanItem>) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StardustModalBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Экспорт сессии", color = StardustTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
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

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy 'в' HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun SettingsScreen(updateManager: UpdateManager, appVersion: String) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    val isSoundEnabled by settingsManager.isSoundEnabledFlow.collectAsState(initial = true)
    val isVibrationEnabled by settingsManager.isVibrationEnabledFlow.collectAsState(initial = true)
    val updateState by updateManager.updateState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StardustModalBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(title = "Эффекты при сканировании") {
            SettingsToggleRow(
                title = "Звуковой сигнал",
                isChecked = isSoundEnabled,
                onCheckedChange = { newCheckedState ->
                    scope.launch {
                        settingsManager.setSoundEnabled(newCheckedState)
                    }
                }
            )
            HorizontalDivider(color = StardustItemBg)
            SettingsToggleRow(
                title = "Вибрация",
                isChecked = isVibrationEnabled,
                onCheckedChange = { newCheckedState ->
                    scope.launch {
                        settingsManager.setVibrationEnabled(newCheckedState)
                    }
                }
            )
        }
        UpdateSection(
            updateState = updateState,
            currentVersion = appVersion,
            onCheckForUpdates = { scope.launch { updateManager.checkForUpdates() } },
            onDownload = { info -> updateManager.downloadAndInstallUpdate(info, scope) },
            onReset = { updateManager.resetState() }
        )
    }
}

@Composable
fun UpdateSection(
    updateState: UpdateState,
    currentVersion: String,
    onCheckForUpdates: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onReset: () -> Unit
) {
    SectionCard(title = "Обновление приложения") {
        when (val state = updateState) {
            UpdateState.Idle -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Текущая версия: $currentVersion", color = StardustTextSecondary, modifier = Modifier.weight(1f))
                    Button(onClick = onCheckForUpdates) { Text("Проверить") }
                }
            }
            UpdateState.Checking -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Проверка обновлений...", color = StardustTextSecondary)
                }
            }
            UpdateState.UpdateNotAvailable -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("У вас установлена последняя версия.", color = StardustTextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onCheckForUpdates) { Text("Проверить снова") }
                }
            }
            is UpdateState.UpdateAvailable -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Доступно обновление!", color = StardustPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Новая версия: ${state.info.latestVersionName}", color = StardustTextPrimary)
                    Text("Описание:", color = StardustTextSecondary)
                    Text(state.info.releaseNotes, color = StardustTextPrimary, modifier = Modifier
                        .fillMaxWidth()
                        .background(StardustItemBg, RoundedCornerShape(8.dp))
                        .padding(8.dp))
                    Button(onClick = { onDownload(state.info) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Скачать и установить")
                    }
                }
            }
            is UpdateState.Downloading -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Загрузка обновления... (${state.progress}%)", color = StardustTextPrimary)
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = StardustPrimary,
                        trackColor = StardustItemBg
                    )
                }
            }
            is UpdateState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(state.message, color = Color.Red, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onCheckForUpdates) { Text("Попробовать снова") }
                }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, color = StardustTextPrimary, fontSize = 16.sp)
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StardustPrimary,
                checkedTrackColor = StardustSecondary,
                uncheckedThumbColor = StardustTextSecondary,
                uncheckedTrackColor = StardustItemBg
            )
        )
    }
}

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StardustModalBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "Автор") {
                Text(
                    text = "Владислав С.",
                    color = StardustTextPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ClickableInfoRow(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    text = "@Cyberdyne_Industries"
                ) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Cyberdyne_Industries"))
                    context.startActivity(intent)
                }
                ClickableInfoRow(
                    icon = Icons.Default.AlternateEmail,
                    text = "pankratovvlda69@gmail.com"
                ) {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:pankratovvlda69@gmail.com"))
                    context.startActivity(intent)
                }
            }
        }

        item {
            CollapsibleSection(title = "Руководство") {
                Text(
                    text = "1. На главном экране наведите камеру на QR-код для автоматического сканирования.\n\n" +
                            "2. Отсканированные номера добавляются в соответствующий список ('Самокаты' или 'АКБ').\n\n" +
                            "3. Вы можете копировать или удалять номера по отдельности, а также экспортировать или поделиться всем списком.\n\n" +
                            "4. Если QR-код поврежден или его невозможно отсканировать, используйте кнопку '+' для ручного ввода номера.",
                    color = StardustTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        item {
            SectionCard(title = "Информация о версии") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StardustItemBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.release_date),
                        color = StardustTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(color = StardustTextSecondary.copy(alpha = 0.3f))
                    Text(
                        text = stringResource(id = R.string.release_notes),
                        color = StardustTextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = StardustTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = StardustGlassBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun ClickableInfoRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = StardustSecondary)
        Text(text = text, color = StardustPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CollapsibleSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }

    SectionCard(title = title) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Показать/скрыть руководство",
                color = StardustTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                tint = StardustTextPrimary
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                content()
            }
        }
    }
}


@Composable
fun PlaceholderScreen(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StardustModalBg),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = StardustTextPrimary, fontSize = 24.sp, textAlign = TextAlign.Center)
    }
}