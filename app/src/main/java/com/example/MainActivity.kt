package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.HistoryEntry
import com.example.data.KnownDevice
import com.example.scanning.DetectedDevice
import com.example.ui.ARFinderScreen
import com.example.ui.RadarViewModel
import com.example.ui.RadarViewModelFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

// --- Obsidian High-Tech Theme Colors ---
val ObsidianBg = Color(0xFF080C14)
val DarkSurface = Color(0xFF111827)
val CardBorder = Color(0xFF1F2937)
val NeonGreen = Color(0xFF10B981) // High glowing emerald green
val NeonCyan = Color(0xFF06B6D4) // Electric cyan
val WarmRed = Color(0xFFEF4444) // Bright warning red
val OrangeAccent = Color(0xFFF59E0B) // Warn yellow/orange for proximity
val TextPrimary = Color(0xFFF9FAFB)
val TextSecondary = Color(0xFF9CA3AF)

@Composable
fun WifiDiagnosticsWidget(viewModel: RadarViewModel) {
    val wifiDiagnostics by viewModel.wifiDiagnostics.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.getWifiDiagnostics() }
    
    if (wifiDiagnostics.isNotEmpty() && (wifiDiagnostics["networkCount"] as? Int ?: 0) > 0) {
        Card(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Diagnostics Wi-Fi", fontWeight = FontWeight.Bold, color = NeonCyan)
                Text("Nombre de réseaux : ${wifiDiagnostics["networkCount"]}")
                Text("RSSI Moyen : ${"%.1f".format(wifiDiagnostics["avgRssi"])} dBm")
            }
        }
    }
}

@Composable
fun StalkerAlertWidget(viewModel: RadarViewModel) {
    val potentialStalkers by viewModel.potentialStalkers.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.analyzePotentialStalkers() }

    if (potentialStalkers.isNotEmpty()) {
        Card(modifier = Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚠️ Alerte Stalker", color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                Text("${potentialStalkers.size} appareils suspects détectés.", color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val viewModel: RadarViewModel = viewModel(
                factory = RadarViewModelFactory(context)
            )

            // Dynamic tracking of permissions state
            var hasPermissions by remember { mutableStateOf(checkAllPermissions(context)) }

            // Define permissions launcher
            val permissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                val allGranted = results.values.all { it }
                hasPermissions = allGranted
                if (allGranted) {
                    Toast.makeText(context, "Permissions accordées !", Toast.LENGTH_SHORT).show()
                    viewModel.bindPresenceService()
                } else {
                    Toast.makeText(context, "Certaines permissions sont requises pour scanner.", Toast.LENGTH_LONG).show()
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = ObsidianBg,
                    surface = DarkSurface,
                    primary = NeonGreen,
                    secondary = NeonCyan,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBg
                ) {
                    if (!hasPermissions) {
                        OnboardingPermissionScreen(
                            onRequestPermissions = {
                                val perms = getRequiredPermissionsList()
                                permissionsLauncher.launch(perms)
                            }
                        )
                    } else {
                        // Ensure we are bound to the service once permissions are active
                        LaunchedEffect(Unit) {
                            viewModel.bindPresenceService()
                        }
                        MainAppContent(viewModel = viewModel)
                    }
                }
            }
        }
    }

    private fun checkAllPermissions(context: Context): Boolean {
        return getRequiredPermissionsList().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissionsList(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }
}

// --- Onboarding / Permission Screen ---
@Composable
fun OnboardingPermissionScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Aesthetic Radar Icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(NeonGreen.copy(alpha = 0.1f), CircleShape)
                .border(2.dp, NeonGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Radar,
                contentDescription = "Radar Icon",
                tint = NeonGreen,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Presence Radar",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Détectez en continu les appareils BLE et Wi-Fi à proximité de manière 100% locale, privée et optimisée pour votre batterie.",
            fontSize = 16.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Permissions needed description card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Autorisations requises :",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = NeonCyan,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                PermissionItem(
                    icon = Icons.Default.Bluetooth,
                    title = "Bluetooth Low Energy",
                    desc = "Permet de scanner les paquets publicitaires BLE des montres, traceurs et écouteurs."
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionItem(
                    icon = Icons.Default.Wifi,
                    title = "Scan Réseau Wi-Fi",
                    desc = "Reconnaît les routeurs et appareils émetteurs Wi-Fi connus à proximité."
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionItem(
                    icon = Icons.Default.LocationOn,
                    title = "Localisation précise",
                    desc = "Exigé par le système Android pour effectuer des scans radio BLE et Wi-Fi."
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("grant_permissions_button"),
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Accorder les autorisations",
                color = ObsidianBg,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NeonGreen,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
            Text(text = desc, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

// --- Main App Scaffolding ---
@Composable
fun MainAppContent(viewModel: RadarViewModel) {
    val activeTabState = viewModel.activeTab.value

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            RadarTopAppBar(viewModel = viewModel)
        },
        bottomBar = {
            RadarBottomNavigation(
                activeTab = activeTabState,
                onTabSelected = { viewModel.activeTab.value = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ObsidianBg)
        ) {
            when (activeTabState) {
                "radar" -> RadarScreen(viewModel = viewModel)
                "hunt" -> {
                    val isArMode by viewModel.isArMode.collectAsStateWithLifecycle()
                    if (isArMode) {
                        ARFinderScreen(viewModel = viewModel)
                    } else {
                        HuntScreen(viewModel = viewModel)
                    }
                }
                "known" -> KnownDevicesScreen(viewModel = viewModel)
                "history" -> HistoryScreen(viewModel = viewModel)
                "settings" -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

// --- Custom Top App Bar ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarTopAppBar(viewModel: RadarViewModel) {
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Presence Radar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                // Glow indicator for scanning state
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (isScanning) NeonGreen else TextSecondary,
                            shape = CircleShape
                        )
                        .drawBehind {
                            if (isScanning) {
                                drawCircle(
                                    color = NeonGreen.copy(alpha = 0.4f),
                                    radius = size.minDimension * 1.5f
                                )
                            }
                        }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScanning) "Scan Actif" else "Scan Inactif",
                    fontSize = 12.sp,
                    color = if (isScanning) NeonGreen else TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = isScanning,
                    onCheckedChange = { viewModel.toggleScanning(it) },
                    modifier = Modifier.testTag("scan_toggle_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBg,
                        checkedTrackColor = NeonGreen,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = CardBorder
                    )
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ObsidianBg,
            titleContentColor = TextPrimary
        ),
        modifier = Modifier.border(0.dp, CardBorder, RoundedCornerShape(0.dp))
    )
}

// --- Custom Bottom Navigation Bar ---
@Composable
fun RadarBottomNavigation(activeTab: String, onTabSelected: (String) -> Unit) {
    NavigationBar(
        containerColor = DarkSurface,
        tonalElevation = 8.dp,
        modifier = Modifier.border(1.dp, CardBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        NavigationBarItem(
            selected = activeTab == "radar",
            onClick = { onTabSelected("radar") },
            label = { Text("Radar") },
            icon = {
                Icon(
                    imageVector = if (activeTab == "radar") Icons.Default.Radar else Icons.Outlined.Radar,
                    contentDescription = "Radar"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = NeonGreen,
                indicatorColor = NeonGreen,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )

        NavigationBarItem(
            selected = activeTab == "hunt",
            onClick = { onTabSelected("hunt") },
            label = { Text("Chasse") },
            icon = {
                Icon(
                    imageVector = if (activeTab == "hunt") Icons.Default.MyLocation else Icons.Outlined.MyLocation,
                    contentDescription = "Chasse"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = NeonGreen,
                indicatorColor = NeonGreen,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )

        NavigationBarItem(
            selected = activeTab == "known",
            onClick = { onTabSelected("known") },
            label = { Text("Appareils") },
            icon = {
                Icon(
                    imageVector = if (activeTab == "known") Icons.Default.Devices else Icons.Outlined.Devices,
                    contentDescription = "Appareils"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = NeonGreen,
                indicatorColor = NeonGreen,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )

        NavigationBarItem(
            selected = activeTab == "history",
            onClick = { onTabSelected("history") },
            label = { Text("Historique") },
            icon = {
                Icon(
                    imageVector = if (activeTab == "history") Icons.Default.History else Icons.Outlined.History,
                    contentDescription = "Historique"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = NeonGreen,
                indicatorColor = NeonGreen,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )

        NavigationBarItem(
            selected = activeTab == "settings",
            onClick = { onTabSelected("settings") },
            label = { Text("Paramètres") },
            icon = {
                Icon(
                    imageVector = if (activeTab == "settings") Icons.Default.Settings else Icons.Outlined.Settings,
                    contentDescription = "Paramètres"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = ObsidianBg,
                selectedTextColor = NeonGreen,
                indicatorColor = NeonGreen,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
    }
}

// ==================== SCREEN 1: RADAR ====================
@Composable
fun RadarScreen(viewModel: RadarViewModel) {
    val detectedDevices by viewModel.detectedDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    var showAddAliasDialog by remember { mutableStateOf<DetectedDevice?>(null) }
    var isFullScreenRadarOpen by remember { mutableStateOf(false) }

    if (isFullScreenRadarOpen) {
        FullScreenRadarDialog(
            isScanning = isScanning,
            devices = detectedDevices,
            viewModel = viewModel,
            onDismiss = { isFullScreenRadarOpen = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Custom interactive canvas Radar widget
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Brush.verticalGradient(listOf(ObsidianBg, DarkSurface))),
            contentAlignment = Alignment.Center
        ) {
            RadarVisualizerWidget(
                isScanning = isScanning,
                devices = detectedDevices,
                modifier = Modifier.size(200.dp),
                onDeviceClick = { clickedDevice ->
                    viewModel.startHunting(clickedDevice)
                }
            )

            // Fullscreen toggle button
            IconButton(
                onClick = { isFullScreenRadarOpen = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(DarkSurface.copy(alpha = 0.85f), CircleShape)
                    .border(1.dp, NeonGreen.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Radar Plein Écran",
                    tint = NeonGreen
                )
            }
        }

        // Baromètre & Altimètre
        BarometerWidget(viewModel = viewModel)
        
        // Wi-Fi Diagnostics
        WifiDiagnosticsWidget(viewModel = viewModel)

        HorizontalDivider(color = CardBorder, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Appareils Détectés (${detectedDevices.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary
            )
            if (isScanning && detectedDevices.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = NeonGreen
                )
            }
        }

        if (detectedDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Radar else Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isScanning) "Recherche en cours d'appareils..." else "Scan désactivé",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isScanning) {
                            "Assurez-vous que le Bluetooth et le Wi-Fi sont activés."
                        } else {
                            "Activez l'interrupteur en haut à droite pour démarrer la surveillance."
                        },
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(detectedDevices, key = { it.identifier }) { device ->
                    DetectedDeviceCard(
                        device = device,
                        viewModel = viewModel,
                        onAddKnown = { showAddAliasDialog = device },
                        onStartHunt = { viewModel.startHunting(device) }
                    )
                }
            }
        }
    }

    // Dialog for adding detected device to known devices
    showAddAliasDialog?.let { device ->
        var aliasInput by remember { mutableStateOf(device.name ?: "") }
        val currentFloorEstimate by viewModel.estimatedFloor.collectAsStateWithLifecycle()
        var floorInput by remember { mutableStateOf(currentFloorEstimate) }

        Dialog(onDismissRequest = { showAddAliasDialog = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Enregistrer l'appareil",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Donnez un alias reconnaissable pour suivre ses entrées et sorties.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text("Alias (ex: Clés de Marc)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alias_input_field")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tactile floor configuration selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Étage attribué :", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { floorInput-- },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Remove, contentDescription = "Moins", tint = NeonGreen)
                            }
                            Text(
                                text = if (floorInput == 0) "RDC" else "Étage $floorInput",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { floorInput++ },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Plus", tint = NeonGreen)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Identifiant: ${device.identifier}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddAliasDialog = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Text("Annuler")
                        }

                        Button(
                            onClick = {
                                if (aliasInput.isNotBlank()) {
                                    viewModel.addKnownDevice(
                                        identifier = device.identifier,
                                        alias = aliasInput,
                                        type = device.type,
                                        floor = floorInput
                                    )
                                    showAddAliasDialog = null
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_device_confirm_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                        ) {
                            Text("Enregistrer", color = ObsidianBg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// --- Radial Sweeping Radar Widget with Meter Scale & Sweeping Cone ---
@Composable
fun RadarVisualizerWidget(
    isScanning: Boolean,
    devices: List<DetectedDevice>,
    modifier: Modifier = Modifier.size(200.dp),
    showLabels: Boolean = false,
    selectedDevice: DetectedDevice? = null,
    onDeviceClick: ((DetectedDevice) -> Unit)? = null
) {
    var sweepAngle by remember { mutableFloatStateOf(0f) }
    var pulseScale by remember { mutableFloatStateOf(0.15f) }

    // Robust timer loop immune to system animation duration scale / battery saver restrictions on Android 10
    LaunchedEffect(isScanning) {
        if (isScanning) {
            val startTime = android.os.SystemClock.uptimeMillis()
            while (true) {
                val elapsed = android.os.SystemClock.uptimeMillis() - startTime
                sweepAngle = ((elapsed % 3500) / 3500f) * 360f
                val pulseProgress = (elapsed % 2200) / 2200f
                pulseScale = 0.15f + 0.85f * (1f - (1f - pulseProgress) * (1f - pulseProgress))
                delay(16)
            }
        } else {
            sweepAngle = 0f
            pulseScale = 0.15f
        }
    }

    // Store locations of rendered device blips for click detection
    val blipLocations = remember { mutableStateListOf<Pair<DetectedDevice, Offset>>() }

    // Reusable Paint objects to avoid GC frame drops on real Android 10 hardware
    val meterTextPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#9CA3AF")
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    val beaconTextPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#F9FAFB")
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
    }

    Canvas(
        modifier = modifier.pointerInput(devices) {
            detectTapGestures { tapOffset ->
                onDeviceClick?.let { onClick ->
                    val clicked = blipLocations.minByOrNull { (_, pos) ->
                        val dx = pos.x - tapOffset.x
                        val dy = pos.y - tapOffset.y
                        dx * dx + dy * dy
                    }
                    if (clicked != null) {
                        val dx = clicked.second.x - tapOffset.x
                        val dy = clicked.second.y - tapOffset.y
                        if (dx * dx + dy * dy < 40.dp.toPx() * 40.dp.toPx()) {
                            onClick(clicked.first)
                        }
                    }
                }
            }
        }
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.width / 2
        val densityVal = density

        blipLocations.clear()

        // 1. Draw pulsing beacon glow in the background
        drawCircle(
            color = NeonGreen.copy(alpha = (0.22f * (1f - pulseScale)).coerceIn(0f, 0.22f)),
            radius = maxRadius * pulseScale,
            center = center
        )

        // 2. Draw crosshair axes
        drawLine(
            color = CardBorder.copy(alpha = 0.8f),
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = CardBorder.copy(alpha = 0.8f),
            start = Offset(center.x, 0f),
            end = Offset(center.x, size.height),
            strokeWidth = 1.dp.toPx()
        )

        // 3. Distance Scale Rings in Meters (1m, 3m, 5m, 10m, 15m)
        meterTextPaint.textSize = densityVal * 9f

        val maxMeters = 15f
        val ringDistances = listOf(1f, 3f, 5f, 10f, 15f)

        ringDistances.forEach { meters ->
            val ringRadius = maxRadius * (meters / maxMeters).coerceAtMost(1f)
            drawCircle(
                color = CardBorder,
                radius = ringRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Draw meter label along vertical axis
            drawContext.canvas.nativeCanvas.drawText(
                "${meters.toInt()}m",
                center.x,
                (center.y - ringRadius + densityVal * 10f).coerceAtLeast(densityVal * 12f),
                meterTextPaint
            )
        }

        // 4. Draw sweeping radar cone & line
        val sweepRad = Math.toRadians(sweepAngle.toDouble())
        val endX = center.x + maxRadius * cos(sweepRad).toFloat()
        val endY = center.y + maxRadius * sin(sweepRad).toFloat()

        // Sweeping trailing arc / cone (Direct GPU hardware accelerated drawArc)
        drawArc(
            brush = Brush.radialGradient(
                colors = listOf(NeonGreen.copy(alpha = 0.35f), NeonGreen.copy(alpha = 0.05f), Color.Transparent),
                center = center,
                radius = maxRadius
            ),
            startAngle = sweepAngle - 45f,
            sweepAngle = 45f,
            useCenter = true,
            topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
            size = androidx.compose.ui.geometry.Size(maxRadius * 2f, maxRadius * 2f)
        )

        // Primary glowing sweep line
        drawLine(
            color = NeonGreen,
            start = center,
            end = Offset(endX, endY),
            strokeWidth = 2.5.dp.toPx()
        )

        // Center origin dot
        drawCircle(color = NeonGreen, radius = 3.5.dp.toPx(), center = center)

        // 5. Map devices to the canvas as glowing beacons using estimated distance
        beaconTextPaint.textSize = densityVal * 10f

        devices.forEach { device ->
            val angleDeg = (device.identifier.hashCode() % 360).toDouble()
            val angleRad = Math.toRadians(angleDeg)

            val distance = device.estimatedDistanceMeters.toFloat().coerceIn(0.5f, maxMeters)
            val normalizedRatio = (distance / maxMeters).coerceIn(0.08f, 0.95f)
            val finalRadius = maxRadius * normalizedRatio

            val x = center.x + finalRadius * cos(angleRad).toFloat()
            val y = center.y + finalRadius * sin(angleRad).toFloat()
            val blipOffset = Offset(x, y)

            blipLocations.add(device to blipOffset)

            val isSelected = selectedDevice?.identifier == device.identifier
            val beaconColor = if (isSelected) WarmRed else if (device.isKnown) NeonGreen else NeonCyan

            // Glowing outer ring
            drawCircle(
                color = beaconColor.copy(alpha = if (isSelected) 0.5f else 0.3f),
                radius = if (isSelected) 12.dp.toPx() else 8.dp.toPx(),
                center = blipOffset
            )
            // Solid center beacon
            drawCircle(
                color = beaconColor,
                radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                center = blipOffset
            )

            // Draw text labels if showLabels is true or selected
            if (showLabels || isSelected) {
                val labelText = "${device.alias ?: device.name ?: "Appareil"} (${String.format("%.1fm", device.estimatedDistanceMeters)})"
                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    x,
                    (y - densityVal * 8f),
                    beaconTextPaint
                )
            }
        }
    }
}

// --- FULLSCREEN RADAR DIALOG COMPOSABLE ---
@Composable
fun FullScreenRadarDialog(
    isScanning: Boolean,
    devices: List<DetectedDevice>,
    viewModel: RadarViewModel,
    onDismiss: () -> Unit
) {
    var selectedDevice by remember { mutableStateOf<DetectedDevice?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg),
            color = ObsidianBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Radar Plein Écran 🛰️",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Échelle en mètres (1m à 15m) • ${devices.size} appareils",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(DarkSurface, CircleShape)
                            .border(1.dp, CardBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Large Fullscreen Radar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Brush.radialGradient(listOf(DarkSurface, ObsidianBg)), RoundedCornerShape(20.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RadarVisualizerWidget(
                        isScanning = isScanning,
                        devices = devices,
                        modifier = Modifier.fillMaxSize(),
                        showLabels = true,
                        selectedDevice = selectedDevice,
                        onDeviceClick = { clicked -> selectedDevice = clicked }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Target Details Bottom Sheet/Card if a blip is selected
                if (selectedDevice != null) {
                    val device = selectedDevice!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, NeonGreen)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (device.type == "WIFI" || device.type == "MDNS") Icons.Default.Wifi else Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = NeonGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = device.alias ?: device.name ?: "Appareil Détecté",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 15.sp
                                    )
                                }
                                Text(
                                    text = String.format("%.1f mètres", device.estimatedDistanceMeters),
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ID : ${device.identifier} • RSSI : ${device.rssi} dBm",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.startHunting(device)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                                ) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Chasser", color = ObsidianBg, fontWeight = FontWeight.Bold)
                                }

                                if (!device.isKnown) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.addKnownDevice(
                                                identifier = device.identifier,
                                                alias = device.name ?: "Appareil ${device.type}",
                                                type = device.type
                                            )
                                        },
                                        border = BorderStroke(1.dp, NeonCyan)
                                    ) {
                                        Text("Sauvegarder", color = NeonCyan)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "💡 Touchez n'importe quel point lumineux du radar pour voir les détails de l'appareil",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

// --- Composable Card for Detected Device ---
@Composable
fun DetectedDeviceCard(
    device: DetectedDevice,
    viewModel: RadarViewModel,
    onAddKnown: () -> Unit,
    onStartHunt: () -> Unit
) {
    val knownDevices by viewModel.knownDevices.collectAsStateWithLifecycle()
    val currentFloor by viewModel.estimatedFloor.collectAsStateWithLifecycle()
    val savedKnown = knownDevices.find { it.identifier == device.identifier }
    val savedFloor = savedKnown?.floor
    val isSos = device.type == "SOS"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(
            1.dp,
            when {
                isSos -> WarmRed.copy(alpha = 0.8f)
                device.isKnown -> NeonGreen.copy(alpha = 0.4f)
                else -> CardBorder
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Device Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = when {
                            isSos -> WarmRed.copy(alpha = 0.2f)
                            device.isKnown -> NeonGreen.copy(alpha = 0.1f)
                            else -> CardBorder
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isSos -> Icons.Default.Warning
                        device.type == "WIFI" || device.type == "MDNS" -> Icons.Default.Wifi
                        device.isKnown -> Icons.Default.Devices
                        else -> Icons.Default.Bluetooth
                    },
                    contentDescription = null,
                    tint = if (isSos) WarmRed else if (device.isKnown) NeonGreen else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Metadata
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.alias ?: device.name ?: "Appareil Inconnu",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSos) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(WarmRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DETRESSE",
                                fontSize = 9.sp,
                                color = WarmRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (device.isKnown) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(NeonGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Suivi",
                                fontSize = 9.sp,
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (savedFloor != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val isSameFloor = savedFloor == currentFloor
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSameFloor) NeonGreen.copy(alpha = 0.2f) else CardBorder,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (savedFloor == 0) "RDC" else "Étage $savedFloor",
                                fontSize = 9.sp,
                                color = if (isSameFloor) NeonGreen else NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${device.type} • ID: ${device.identifier.take(14)}...",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Real-time distance and RSSI
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        tint = if (device.rssi > -70) NeonGreen else TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${device.rssi} dBm",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(CardBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = device.distanceCategory,
                            fontSize = 10.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (savedFloor != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val floorDiff = savedFloor - currentFloor
                        Text(
                            text = when {
                                floorDiff == 0 -> "🎯 Même étage"
                                floorDiff > 0 -> "↕️ +$floorDiff étage${if (floorDiff > 1) "s" else ""}"
                                else -> "↕️ $floorDiff étage${if (floorDiff < -1) "s" else ""}"
                            },
                            fontSize = 10.sp,
                            color = if (floorDiff == 0) NeonGreen else TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hunt button
                IconButton(
                    onClick = onStartHunt,
                    modifier = Modifier
                        .background(NeonGreen.copy(alpha = 0.15f), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Chasser l'appareil",
                        tint = NeonGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (!device.isKnown) {
                    IconButton(
                        onClick = onAddKnown,
                        modifier = Modifier
                            .background(CardBorder, CircleShape)
                            .size(36.dp)
                            .testTag("add_known_device_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Ajouter aux connus",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}


// ==================== SCREEN 2: GESTION DES APPAREILS CONNUS ====================
@Composable
fun KnownDevicesScreen(viewModel: RadarViewModel) {
    val knownDevices by viewModel.knownDevices.collectAsStateWithLifecycle()
    var showManualAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Appareils Enregistrés",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
                Text(
                    text = "${knownDevices.size} suivis en continu",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Button(
                onClick = { showManualAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("add_device_manually_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = ObsidianBg)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ajouter", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (knownDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DevicesOther,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Aucun appareil enregistré",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ajoutez des montres, tags Bluetooth ou réseaux Wi-Fi pour enregistrer leurs passages.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(knownDevices, key = { it.identifier }) { device ->
                    val detectedList by viewModel.detectedDevices.collectAsStateWithLifecycle()
                    val activeMatch = detectedList.find { it.identifier == device.identifier }
                    val mockDevice = activeMatch ?: DetectedDevice(
                        identifier = device.identifier,
                        name = device.alias,
                        rssi = -100,
                        type = device.type,
                        isKnown = true,
                        alias = device.alias
                    )

                    KnownDeviceRow(
                        device = device,
                        onDelete = { viewModel.deleteKnownDevice(device) },
                        onStartHunt = { viewModel.startHunting(mockDevice) },
                        onToggleArrival = { enabled ->
                            viewModel.updateKnownDeviceNotifications(device, notifyArrival = enabled, notifyDeparture = device.notifyOnDeparture)
                        },
                        onToggleDeparture = { enabled ->
                            viewModel.updateKnownDeviceNotifications(device, notifyArrival = device.notifyOnArrival, notifyDeparture = enabled)
                        }
                    )
                }
            }
        }
    }

    if (showManualAddDialog) {
        var macInput by remember { mutableStateOf("") }
        var aliasInput by remember { mutableStateOf("") }
        var typeInput by remember { mutableStateOf("BLE") } // "BLE" or "WIFI"
        val currentFloorEstimate by viewModel.estimatedFloor.collectAsStateWithLifecycle()
        var floorInput by remember { mutableStateOf(currentFloorEstimate) }

        Dialog(onDismissRequest = { showManualAddDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ajouter un Appareil",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text("Nom / Alias (ex: iPhone Marc)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_alias_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = macInput,
                        onValueChange = { macInput = it },
                        label = { Text("Adresse MAC / SSID (Nom)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_id_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tactile floor configuration selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Étage attribué :", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { floorInput-- },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Remove, contentDescription = "Moins", tint = NeonGreen)
                            }
                            Text(
                                text = if (floorInput == 0) "RDC" else "Étage $floorInput",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { floorInput++ },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Plus", tint = NeonGreen)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Type selection segmented control
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (typeInput == "BLE") NeonGreen else DarkSurface)
                                .clickable { typeInput = "BLE" }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "BLE (Bluetooth)",
                                color = if (typeInput == "BLE") ObsidianBg else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (typeInput == "WIFI") NeonGreen else DarkSurface)
                                .clickable { typeInput = "WIFI" }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Wi-Fi (SSID)",
                                color = if (typeInput == "WIFI") ObsidianBg else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showManualAddDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Text("Fermer")
                        }

                        Button(
                            onClick = {
                                if (macInput.isNotBlank() && aliasInput.isNotBlank()) {
                                    viewModel.addKnownDevice(
                                        identifier = macInput,
                                        alias = aliasInput,
                                        type = typeInput,
                                        floor = floorInput
                                    )
                                    showManualAddDialog = false
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("manual_save_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                        ) {
                            Text("Enregistrer", color = ObsidianBg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KnownDeviceRow(
    device: KnownDevice,
    onDelete: () -> Unit,
    onStartHunt: () -> Unit,
    onToggleArrival: (Boolean) -> Unit,
    onToggleDeparture: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (device.type == "WIFI") Icons.Default.Wifi else Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.alias,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (device.floor == 0) "RDC" else "Étage ${device.floor}",
                                fontSize = 9.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Identifiant: ${device.identifier}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onStartHunt) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Chasser l'appareil",
                            tint = NeonGreen
                        )
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Supprimer",
                            tint = WarmRed.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Notification preferences row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Alerte Entrée", fontSize = 11.sp, color = TextSecondary)
                    Switch(
                        checked = device.notifyOnArrival,
                        onCheckedChange = onToggleArrival,
                        modifier = Modifier.scale(0.6f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = WarmRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Alerte Sortie", fontSize = 11.sp, color = TextSecondary)
                    Switch(
                        checked = device.notifyOnDeparture,
                        onCheckedChange = onToggleDeparture,
                        modifier = Modifier.scale(0.6f)
                    )
                }
            }
        }
    }
}


// --- BAROMETER & ELEVATION DETECTOR COMPOSABLE ---
@Composable
fun BarometerWidget(viewModel: RadarViewModel) {
    val isBarometerAvailable by viewModel.isBarometerAvailable.collectAsStateWithLifecycle()
    val currentPressure by viewModel.currentPressure.collectAsStateWithLifecycle()
    val referencePressure by viewModel.referencePressure.collectAsStateWithLifecycle()
    val estimatedRelativeAltitude by viewModel.estimatedRelativeAltitude.collectAsStateWithLifecycle()
    val estimatedFloor by viewModel.estimatedFloor.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Compress,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Baromètre & Altimètre (Étage)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }
                
                // Calibration Button
                TextButton(
                    onClick = { viewModel.calibrateGroundLevel() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Calibrer RDC", color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main values grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pressure column
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pression", fontSize = 10.sp, color = TextSecondary)
                    Text(
                        text = if (currentPressure != null) String.format("%.2f hPa", currentPressure) else "--- hPa",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }

                // Altitude column
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Rel. Altitude", fontSize = 10.sp, color = TextSecondary)
                    Text(
                        text = String.format("%+.1f m", estimatedRelativeAltitude),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (estimatedRelativeAltitude >= 0) NeonGreen else WarmRed
                    )
                }

                // Estimated Floor column
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Étage Estimé", fontSize = 10.sp, color = TextSecondary)
                    Text(
                        text = when {
                            estimatedFloor == 0 -> "RDC (0)"
                            estimatedFloor > 0 -> "Étage +$estimatedFloor"
                            else -> "Sous-sol $estimatedFloor"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = NeonCyan
                    )
                }
            }

            // Reference details info & Status
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("Réf. RDC : %.2f hPa", referencePressure),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                
                // Sensor status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isBarometerAvailable) "Capteur Réel Actif" else "Capteur Non Présent",
                        fontSize = 11.sp,
                        color = if (isBarometerAvailable) NeonGreen else TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}


// --- IMMERSIVE HUNT SCREEN COMPOSABLE ---
@Composable
fun HuntScreen(viewModel: RadarViewModel) {
    val huntingDevice by viewModel.huntingDevice.collectAsStateWithLifecycle()
    val huntRssiTrend by viewModel.huntRssiTrend.collectAsStateWithLifecycle()
    val huntDistanceText by viewModel.huntDistanceText.collectAsStateWithLifecycle()
    val huntDistanceProgress by viewModel.huntDistanceProgress.collectAsStateWithLifecycle()
    val huntSignalLost by viewModel.huntSignalLost.collectAsStateWithLifecycle()
    val isHeatmapMode by viewModel.isHeatmapMode.collectAsStateWithLifecycle()
    val currentFloor by viewModel.estimatedFloor.collectAsStateWithLifecycle()
    val detectedDevices by viewModel.detectedDevices.collectAsStateWithLifecycle()

    // Smooth pulse speed adjusting automatically to distance!
    val pulseDuration = remember(huntDistanceProgress) {
        val baseDuration = 1800
        val reduction = (huntDistanceProgress * 1400).toInt()
        (baseDuration - reduction).coerceIn(400, 1800)
    }

    var pulseScale by remember { mutableFloatStateOf(0.6f) }
    var pulseAlpha by remember { mutableFloatStateOf(0.8f) }

    LaunchedEffect(huntingDevice, pulseDuration) {
        if (huntingDevice != null) {
            val startTime = android.os.SystemClock.uptimeMillis()
            while (true) {
                val elapsed = android.os.SystemClock.uptimeMillis() - startTime
                val progress = (elapsed % pulseDuration) / pulseDuration.toFloat()
                pulseScale = 0.6f + 0.7f * progress
                pulseAlpha = (0.8f * (1f - progress)).coerceIn(0f, 0.8f)
                delay(16)
            }
        } else {
            pulseScale = 0.6f
            pulseAlpha = 0f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (huntingDevice == null) {
            // Empty state - select a device to hunt
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.FilterCenterFocus,
                    contentDescription = null,
                    tint = NeonGreen.copy(alpha = 0.5f),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Mode Chasse (Find Phone) 🎯",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sélectionnez un appareil ci-dessous pour le traquer en temps réel grâce à son signal radio.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))
                
                // Show list of active devices to quickly start hunting
                Text(
                    text = "Appareils Disponibles à Proximité :",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(start = 12.dp, bottom = 8.dp)
                )

                if (detectedDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .background(DarkSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aucun appareil détecté pour le moment", color = TextSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(detectedDevices) { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.startHunting(device) },
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (device.type == "WIFI" || device.type == "MDNS") Icons.Default.Wifi else Icons.Default.Bluetooth,
                                            tint = if (device.isKnown) NeonGreen else TextSecondary,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(device.alias ?: device.name ?: "Appareil Inconnu", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                            Text("RSSI: ${device.rssi} dBm", color = TextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                    Icon(imageVector = Icons.Default.ArrowForwardIos, tint = NeonGreen, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val device = huntingDevice!!
            
            // We have a target being hunted!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Target Header card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(NeonGreen.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (device.type == "WIFI" || device.type == "MDNS") Icons.Default.Wifi else Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.alias ?: device.name ?: "Cible de Chasse", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                            Text("ID: ${device.identifier}", fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(
                            modifier = Modifier
                                .background(if (huntSignalLost) WarmRed.copy(alpha = 0.15f) else NeonGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (huntSignalLost) "PERDU" else "ACTIF",
                                color = if (huntSignalLost) WarmRed else NeonGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.toggleHeatmapMode() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (isHeatmapMode) NeonCyan.copy(alpha = 0.2f) else DarkSurface, CircleShape)
                                .border(1.dp, if (isHeatmapMode) NeonCyan else CardBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Carte Thermique",
                                tint = if (isHeatmapMode) NeonCyan else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isHeatmapMode) {
                    HeatmapWidget(viewModel = viewModel)
                } else {
                    // Immersive Pulsing Target Widget
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(Brush.radialGradient(listOf(DarkSurface, ObsidianBg)), CircleShape)
                        .border(1.dp, CardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val maxR = size.width / 2
                        val maxMeters = 15f
                        val densityVal = density
                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#6B7280")
                            textSize = densityVal * 8f
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }

                        listOf(2f, 5f, 10f, 15f).forEach { meters ->
                            val r = maxR * (meters / maxMeters)
                            drawCircle(
                                color = CardBorder.copy(alpha = 0.6f),
                                radius = r,
                                center = center,
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "${meters.toInt()}m",
                                center.x,
                                center.y - r + densityVal * 9f,
                                textPaint
                            )
                        }

                        if (!huntSignalLost) {
                            val targetDistance = device.estimatedDistanceMeters.toFloat().coerceIn(0.5f, maxMeters)
                            val targetR = maxR * (targetDistance / maxMeters).coerceIn(0.1f, 0.95f)
                            drawCircle(
                                color = NeonGreen.copy(alpha = 0.4f),
                                radius = 10.dp.toPx(),
                                center = Offset(center.x, center.y - targetR)
                            )
                            drawCircle(
                                color = NeonGreen,
                                radius = 5.dp.toPx(),
                                center = Offset(center.x, center.y - targetR)
                            )

                            // Drawing animated pulse ring
                            drawCircle(
                                color = NeonGreen.copy(alpha = pulseAlpha),
                                radius = (size.width / 2) * pulseScale,
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }

                    // Foreground circular status meter
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (huntSignalLost) "Recherche..." else huntDistanceText,
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            color = if (huntSignalLost) TextSecondary else when (huntDistanceText) {
                                "Brûlant ! 🔥" -> WarmRed
                                "Chaud ☀️" -> OrangeAccent
                                "Tiède 🌤" -> NeonCyan
                                else -> TextSecondary
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (huntSignalLost) "Pas de signal" else "${device.rssi} dBm",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Distance estimée : ${if (huntSignalLost) "---" else String.format("%.1fm", device.estimatedDistanceMeters)}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Retours Sonores & Haptiques (Effet Geiger)
                val isGeigerAudio by viewModel.isGeigerAudioEnabled.collectAsStateWithLifecycle()
                val isGeigerHaptic by viewModel.isGeigerHapticEnabled.collectAsStateWithLifecycle()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Rétroaction Geiger active",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "GEIGER MODE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonGreen
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Audio feedback switch
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setGeigerAudioEnabled(!isGeigerAudio) }
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isGeigerAudio) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        contentDescription = null,
                                        tint = if (isGeigerAudio) NeonGreen else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Bips Sonores", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                        Text("Fréquence RF", fontSize = 9.sp, color = TextSecondary)
                                    }
                                }
                                Switch(
                                    checked = isGeigerAudio,
                                    onCheckedChange = { viewModel.setGeigerAudioEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianBg,
                                        checkedTrackColor = NeonGreen
                                    ),
                                    modifier = Modifier.scale(0.75f).testTag("geiger_audio_toggle")
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Haptic feedback switch
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setGeigerHapticEnabled(!isGeigerHaptic) }
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Vibration,
                                        contentDescription = null,
                                        tint = if (isGeigerHaptic) NeonGreen else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Vibrations", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                        Text("Pulsions Geiger", fontSize = 9.sp, color = TextSecondary)
                                    }
                                }
                                Switch(
                                    checked = isGeigerHaptic,
                                    onCheckedChange = { viewModel.setGeigerHapticEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianBg,
                                        checkedTrackColor = NeonGreen
                                    ),
                                    modifier = Modifier.scale(0.75f).testTag("geiger_haptic_toggle")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Trend display card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Tendance Signal", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (huntSignalLost) "Inconnue" else huntRssiTrend,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (huntSignalLost) TextSecondary else when {
                                    huntRssiTrend.contains("approche") -> NeonGreen
                                    huntRssiTrend.contains("éloigne") -> WarmRed
                                    else -> TextPrimary
                                }
                            )
                        }
                    }

                    // Floor tracking card
                    val knownDevices by viewModel.knownDevices.collectAsStateWithLifecycle()
                    val savedKnown = knownDevices.find { it.identifier == device.identifier }
                    val targetFloor = savedKnown?.floor ?: 0

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("AR Finder", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            IconButton(onClick = { viewModel.toggleArMode(true) }) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "AR Finder", tint = NeonCyan)
                            }
                        }
                    }
                }

                // Interactive Guidance Tip Bar
                Spacer(modifier = Modifier.height(16.dp))
                val knownDevices by viewModel.knownDevices.collectAsStateWithLifecycle()
                val savedKnown = knownDevices.find { it.identifier == device.identifier }
                val targetFloor = savedKnown?.floor ?: 0
                val floorDiff = targetFloor - currentFloor

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when {
                                huntSignalLost -> "Déplacez-vous doucement pour retrouver l'émission de la cible."
                                savedKnown == null -> "Pour plus de précision, enregistrez cet appareil pour comparer les étages."
                                floorDiff == 0 -> "🎯 Vous êtes au même étage ! Suivez la force du signal (dBm)."
                                floorDiff > 0 -> "↕️ L'appareil se trouve au-dessus de vous (+${floorDiff} étage${if (floorDiff > 1) "s" else ""}). Montez !"
                                else -> "↕️ L'appareil se trouve au-dessous de vous (${floorDiff} étage${if (floorDiff < -1) "s" else ""}). Descendez !"
                            },
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                }

                val isCompassEnabled by viewModel.isCompassEnabled.collectAsStateWithLifecycle()
                if (isCompassEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CompassWidget(viewModel = viewModel)
                }
                } // End of else (!isHeatmapMode)
            }

            // Bottom Stop Hunt Button
            Button(
                onClick = { viewModel.stopHunting() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WarmRed),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = TextPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Arrêter la Chasse", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}


// ==================== SCREEN 3: HISTORIQUE DES EVENEMENTS ====================
@Composable
fun HistoryScreen(viewModel: RadarViewModel) {
    val historyLogs by viewModel.history.collectAsStateWithLifecycle()
    val searchQuery by viewModel.historySearchQuery.collectAsStateWithLifecycle()

    val filteredHistory = remember(historyLogs, searchQuery) {
        if (searchQuery.isBlank()) {
            historyLogs
        } else {
            historyLogs.filter {
                it.alias.contains(searchQuery, ignoreCase = true) ||
                        it.identifier.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Journal des Événements",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Présences passées & détectées",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            if (historyLogs.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearHistory() },
                    colors = ButtonDefaults.textButtonColors(contentColor = WarmRed),
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Icon(imageVector = Icons.Default.ClearAll, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Effacer", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search text field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setHistorySearchQuery(it) },
            placeholder = { Text("Rechercher un appareil...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = CardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth().testTag("history_search_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun événement enregistré.",
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredHistory, key = { it.id }) { log ->
                    HistoryLogCard(log = log)
                }
            }
        }
    }
}

@Composable
fun HistoryLogCard(log: HistoryEntry) {
    val isArrival = log.eventType == "ARRIVED"
    val formattedTime = remember(log.timestamp) {
        val sdf = SimpleDateFormat("HH:mm:ss • dd MMM", Locale.FRANCE)
        sdf.format(Date(log.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (isArrival) NeonGreen.copy(alpha = 0.15f) else WarmRed.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isArrival) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = if (isArrival) NeonGreen else WarmRed,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.alias,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                Text(
                    text = "ID: ${log.identifier.take(14)}...",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                if (log.latitude != null && log.longitude != null) {
                    Text(
                        text = String.format("📍 GPS: %.4f, %.4f", log.latitude, log.longitude),
                        fontSize = 10.sp,
                        color = NeonCyan
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isArrival) "ARRIVÉ" else "PARTI",
                    color = if (isArrival) NeonGreen else WarmRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = formattedTime,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
}


// ==================== SCREEN 4: PARAMETRES & GUIDES CONTEXTUELS ====================
@Composable
fun SettingsScreen(viewModel: RadarViewModel) {
    StalkerAlertWidget(viewModel = viewModel)
    val rssiVal by viewModel.rssiThreshold.collectAsStateWithLifecycle()
    val powerSaverEnabled by viewModel.isPowerSaver.collectAsStateWithLifecycle()
    val departureSecs by viewModel.departureDelaySec.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    val isWifiScanningEnabled by viewModel.isWifiScanningEnabled.collectAsStateWithLifecycle()
    val isGpsTrackingEnabled by viewModel.isGpsTrackingEnabled.collectAsStateWithLifecycle()
    val isHistoryLoggingEnabled by viewModel.isHistoryLoggingEnabled.collectAsStateWithLifecycle()
    val isSystemNotificationsEnabled by viewModel.isSystemNotificationsEnabled.collectAsStateWithLifecycle()
    val isCompassEnabled by viewModel.isCompassEnabled.collectAsStateWithLifecycle()

    // Emergency SOS Beacon state collections
    val isBeaconActive by viewModel.isBeaconActive.collectAsStateWithLifecycle()
    val isMonitoringInactivity by viewModel.isMonitoringInactivity.collectAsStateWithLifecycle()
    val countdownRemaining by viewModel.countdownSecondsRemaining.collectAsStateWithLifecycle()
    val inactivityProgress by viewModel.lastInactivityProgress.collectAsStateWithLifecycle()

    var selectedCountdownSecs by remember { mutableStateOf(30) } // default 30s
    var selectedInactivitySecs by remember { mutableStateOf(30) } // default 30s

    var soundEnabled by remember { mutableStateOf(viewModel.isBeaconSoundEnabled) }
    var flashlightEnabled by remember { mutableStateOf(viewModel.isBeaconFlashlightEnabled) }
    var bleEnabled by remember { mutableStateOf(viewModel.isBeaconBleEnabled) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = "Paramètres du Radar",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = TextPrimary
            )
            Text(
                text = "Optimisez la précision de détection et l'autonomie",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }

        // Section Foreground Service Control
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, if (isScanning) NeonGreen else CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = if (isScanning) NeonGreen else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Suivi en Arrière-plan (Service)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isScanning) "Actif • Notification permanente" else "Inactif",
                                    fontSize = 12.sp,
                                    color = if (isScanning) NeonGreen else TextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = isScanning,
                            onCheckedChange = { viewModel.toggleScanning(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBg,
                                checkedTrackColor = NeonGreen
                            ),
                            modifier = Modifier.testTag("service_toggle_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Permet de scruter le Bluetooth LE & Wi-Fi en permanence en arrière-plan, d'envoyer des notifications d'arrivée et des alertes anti-oubli critiques.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Section Notifications Test
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Test des Notifications & Alertes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Testez la réception des notifications push et des alertes sonores/vibrantes sur votre smartphone.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.sendTestNotification(isDeparture = false) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, NeonGreen)
                        ) {
                            Text("Test Arrivée 🔔", color = NeonGreen, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.sendTestNotification(isDeparture = true) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, WarmRed)
                        ) {
                            Text("Alerte Anti-Oubli 🚨", color = WarmRed, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section Balise d'Urgence SOS (Sauvetage)
        item {
            var pulseAlpha by remember { mutableFloatStateOf(0.4f) }
            LaunchedEffect(isBeaconActive) {
                if (isBeaconActive) {
                    val startTime = android.os.SystemClock.uptimeMillis()
                    while (true) {
                        val elapsed = android.os.SystemClock.uptimeMillis() - startTime
                        val progress = (elapsed % 800) / 800f
                        pulseAlpha = 0.4f + 0.6f * (0.5f + 0.5f * kotlin.math.sin(progress * 2 * Math.PI.toFloat()))
                        delay(16)
                    }
                } else {
                    pulseAlpha = 1.0f
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(
                    1.dp,
                    if (isBeaconActive) WarmRed.copy(alpha = pulseAlpha) else if (isMonitoringInactivity || countdownRemaining > 0) OrangeAccent else CardBorder
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isBeaconActive) WarmRed else if (isMonitoringInactivity || countdownRemaining > 0) OrangeAccent else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Balise de Détresse SOS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isBeaconActive) "ÉMISSION ACTIVE 🚨" else if (countdownRemaining > 0) "ARMEMENT EN COURS... ⏳" else if (isMonitoringInactivity) "SURVEILLANCE ACTIVÉE 📡" else "Inactif",
                                    fontSize = 11.sp,
                                    color = if (isBeaconActive) WarmRed else if (countdownRemaining > 0 || isMonitoringInactivity) OrangeAccent else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Badges/Status indicator
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isBeaconActive) WarmRed.copy(alpha = 0.2f)
                                    else if (countdownRemaining > 0 || isMonitoringInactivity) OrangeAccent.copy(alpha = 0.2f)
                                    else CardBorder
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Sauvetage",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBeaconActive) WarmRed else if (countdownRemaining > 0 || isMonitoringInactivity) OrangeAccent else TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Permet à l'appareil d'émettre des signaux radio continus, d'activer un signal sonore puissant et de faire clignoter le flash en SOS s'il est perdu ou dans des décombres.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Banners / Dynamic states
                    if (isBeaconActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(WarmRed.copy(alpha = 0.15f * pulseAlpha))
                                .border(1.dp, WarmRed.copy(alpha = pulseAlpha), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "🚨 BALISE ACTIVE ET ÉMETTRICE 🚨",
                                    color = WarmRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Vos flashs, sirène et signaux Bluetooth (BLE) SOS s'exécutent en boucle.",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.stopBeacon() },
                                    colors = ButtonDefaults.buttonColors(containerColor = WarmRed),
                                    modifier = Modifier.fillMaxWidth().testTag("stop_sos_beacon_button")
                                ) {
                                    Text("ARRÊTER LA BALISE", color = TextPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (countdownRemaining > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(OrangeAccent.copy(alpha = 0.15f))
                                .border(1.dp, OrangeAccent, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "⚠️ ACTIVATION DE LA BALISE DANS",
                                    color = OrangeAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "$countdownRemaining SECONDES",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { viewModel.cancelBeaconCountdown() },
                                    border = BorderStroke(1.dp, OrangeAccent),
                                    modifier = Modifier.fillMaxWidth().testTag("cancel_sos_countdown_button")
                                ) {
                                    Text("ANNULER LE MINUTEUR", color = OrangeAccent)
                                }
                            }
                        }
                    } else if (isMonitoringInactivity) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.15f))
                                .border(1.dp, NeonCyan, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📡 SURVEILLANCE D'INACTIVITÉ ACTIVE",
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "${(inactivityProgress * 100).toInt()}%",
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = inactivityProgress,
                                    color = NeonCyan,
                                    trackColor = CardBorder,
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "La balise s'activera s'il n'y a aucun mouvement pendant la période définie (bougez le téléphone pour réinitialiser le compteur).",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { viewModel.stopInactivityMonitoring() },
                                    border = BorderStroke(1.dp, NeonCyan),
                                    modifier = Modifier.fillMaxWidth().testTag("stop_inactivity_monitoring_button")
                                ) {
                                    Text("ANNULER LA SURVEILLANCE", color = NeonCyan)
                                }
                            }
                        }
                    } else {
                        // Configuration Panel
                        Column {
                            // Toggles Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Sound Switch
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = if (soundEnabled) NeonGreen else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Sirène 🔊", fontSize = 10.sp, color = TextPrimary)
                                    Switch(
                                        checked = soundEnabled,
                                        onCheckedChange = {
                                            soundEnabled = it
                                            viewModel.isBeaconSoundEnabled = it
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = NeonGreen),
                                        modifier = Modifier.scale(0.8f).testTag("beacon_sound_toggle")
                                    )
                                }
                                // Flashlight Switch
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = if (flashlightEnabled) NeonGreen else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Flash 🔦", fontSize = 10.sp, color = TextPrimary)
                                    Switch(
                                        checked = flashlightEnabled,
                                        onCheckedChange = {
                                            flashlightEnabled = it
                                            viewModel.isBeaconFlashlightEnabled = it
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = NeonGreen),
                                        modifier = Modifier.scale(0.8f).testTag("beacon_flashlight_toggle")
                                    )
                                }
                                // BLE Switch
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = if (bleEnabled) NeonGreen else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Radio BLE 📡", fontSize = 10.sp, color = TextPrimary)
                                    Switch(
                                        checked = bleEnabled,
                                        onCheckedChange = {
                                            bleEnabled = it
                                            viewModel.isBeaconBleEnabled = it
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = NeonGreen),
                                        modifier = Modifier.scale(0.8f).testTag("beacon_ble_toggle")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action buttons & custom Sliders
                            // 1. Immediate trigger
                            Button(
                                onClick = { viewModel.activateBeaconImmediately() },
                                colors = ButtonDefaults.buttonColors(containerColor = WarmRed),
                                modifier = Modifier.fillMaxWidth().testTag("activate_sos_beacon_button")
                            ) {
                                Text("🔴 ACTIVER LA BALISE IMMÉDIATEMENT", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = CardBorder)
                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. Countdown trigger options
                            Text(
                                text = "Déclenchement Retardé (Compte à Rebours)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedCountdownSecs >= 60) "${selectedCountdownSecs / 60} min" else "$selectedCountdownSecs s",
                                    color = NeonGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = { viewModel.startCountdownBeacon(selectedCountdownSecs) },
                                    border = BorderStroke(1.dp, NeonGreen),
                                    modifier = Modifier.height(32.dp).testTag("start_countdown_button"),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Lancer", color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Slider(
                                value = selectedCountdownSecs.toFloat(),
                                onValueChange = { selectedCountdownSecs = it.toInt() },
                                valueRange = 10f..300f,
                                steps = 29, // steps of 10s
                                colors = SliderDefaults.colors(activeTrackColor = NeonGreen, thumbColor = NeonGreen)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 3. Inactivity trigger options
                            Text(
                                text = "Activer s'il n'y a plus aucun mouvement (Béton/Décombres)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedInactivitySecs >= 60) "${selectedInactivitySecs / 60} min" else "$selectedInactivitySecs s",
                                    color = NeonCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = { viewModel.startInactivityMonitoring(selectedInactivitySecs) },
                                    border = BorderStroke(1.dp, NeonCyan),
                                    modifier = Modifier.height(32.dp).testTag("start_inactivity_button"),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Surveiller", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Slider(
                                value = selectedInactivitySecs.toFloat(),
                                onValueChange = { selectedInactivitySecs = it.toInt() },
                                valueRange = 10f..300f,
                                steps = 29, // steps of 10s
                                colors = SliderDefaults.colors(activeTrackColor = NeonCyan, thumbColor = NeonCyan)
                            )
                        }
                    }
                }
            }
        }

        // Section Geiger Feedback
        item {
            val isGeigerAudio by viewModel.isGeigerAudioEnabled.collectAsStateWithLifecycle()
            val isGeigerHaptic by viewModel.isGeigerHapticEnabled.collectAsStateWithLifecycle()

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Radar Sonore & Retours Geiger 🔊📳",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Retour haptique et sonore proportionnel au signal",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "En mode chasse, l'appareil émet des bips et des vibrations dont le rythme s'accélère à mesure que vous vous rapprochez de l'appareil traqué (effet Compteur Geiger).",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Audio feedback toggle
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setGeigerAudioEnabled(!isGeigerAudio) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isGeigerAudio) NeonGreen.copy(alpha = 0.05f) else ObsidianBg
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isGeigerAudio) NeonGreen.copy(alpha = 0.4f) else CardBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (isGeigerAudio) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = null,
                                    tint = if (isGeigerAudio) NeonGreen else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Bips Sonores", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Switch(
                                    checked = isGeigerAudio,
                                    onCheckedChange = { viewModel.setGeigerAudioEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianBg,
                                        checkedTrackColor = NeonGreen
                                    ),
                                    modifier = Modifier.scale(0.8f).testTag("settings_geiger_audio_switch")
                                )
                            }
                        }

                        // Haptic feedback toggle
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setGeigerHapticEnabled(!isGeigerHaptic) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isGeigerHaptic) NeonGreen.copy(alpha = 0.05f) else ObsidianBg
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isGeigerHaptic) NeonGreen.copy(alpha = 0.4f) else CardBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Vibration,
                                    contentDescription = null,
                                    tint = if (isGeigerHaptic) NeonGreen else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Vibrations", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Switch(
                                    checked = isGeigerHaptic,
                                    onCheckedChange = { viewModel.setGeigerHapticEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianBg,
                                        checkedTrackColor = NeonGreen
                                    ),
                                    modifier = Modifier.scale(0.8f).testTag("settings_geiger_haptic_switch")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Sliders
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sensibilité du signal (RSSI)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Ignore les appareils dont le signal est inférieur à ce seuil.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "$rssiVal dBm", fontWeight = FontWeight.Bold, color = NeonGreen, fontSize = 16.sp)
                        Text(text = if (rssiVal >= -60) "Très Sensible" else if (rssiVal >= -85) "Normal" else "Large", fontSize = 12.sp, color = TextSecondary)
                    }

                    Slider(
                        value = rssiVal.toFloat(),
                        onValueChange = { viewModel.updateRssiThreshold(it.toInt()) },
                        valueRange = -100f..-40f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = NeonGreen,
                            thumbColor = NeonGreen
                        ),
                        modifier = Modifier.testTag("rssi_threshold_slider")
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Délai d'absence (Départ)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Secondes sans réception avant d'estimer qu'un appareil est parti.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "$departureSecs secondes", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 16.sp)
                    }

                    Slider(
                        value = departureSecs.toFloat(),
                        onValueChange = { viewModel.updateDepartureDelay(it.toInt()) },
                        valueRange = 15f..120f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = NeonCyan,
                            thumbColor = NeonCyan
                        ),
                        modifier = Modifier.testTag("departure_delay_slider")
                    )
                }
            }
        }

        // Section Fonctionnalités & Confidentialité
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Fonctionnalités & Confidentialité",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Activez ou désactivez les modules système de l'application",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Détection Wi-Fi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Détection Réseaux Wi-Fi", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                            Text("Permet de scanner les points d'accès Wi-Fi & appareils mDNS à proximité.", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = isWifiScanningEnabled,
                            onCheckedChange = { viewModel.updateWifiScanning(it) },
                            modifier = Modifier.testTag("wifi_scan_toggle"),
                            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = NeonGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Géolocalisation (GPS)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Suivi de Position (GPS)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                            Text("Associe des coordonnées géographiques GPS aux événements d'arrivée et départ.", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = isGpsTrackingEnabled,
                            onCheckedChange = { viewModel.updateGpsTracking(it) },
                            modifier = Modifier.testTag("gps_tracking_toggle"),
                            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = NeonGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Historique automatique
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Journalisation de l'Historique", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                            Text("Enregistre automatiquement les détections d'appareils connus dans l'Historique.", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = isHistoryLoggingEnabled,
                            onCheckedChange = { viewModel.updateHistoryLogging(it) },
                            modifier = Modifier.testTag("history_logging_toggle"),
                            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = NeonGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. Notifications Système
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notifications Système", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                            Text("Affiche des notifications d'arrière-plan d'arrivée et d'alerte anti-oubli.", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = isSystemNotificationsEnabled,
                            onCheckedChange = { viewModel.updateSystemNotifications(it) },
                            modifier = Modifier.testTag("system_notifications_toggle"),
                            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = NeonGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. Boussole Magnétique
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Boussole Magnétique", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                            Text("Active la boussole d'orientation et d'alignement avec le Nord magnétique.", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = isCompassEnabled,
                            onCheckedChange = { viewModel.updateCompassEnabled(it) },
                            modifier = Modifier.testTag("compass_enabled_toggle"),
                            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = NeonGreen)
                        )
                    }
                }
            }
        }

        // Switch battery saver
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder)
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
                            text = "Mode Économie de Batterie",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Réduit la fréquence de scan pour maximiser l'autonomie.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = powerSaverEnabled,
                        onCheckedChange = { viewModel.updatePowerSaver(it) },
                        modifier = Modifier.testTag("power_saver_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = NeonGreen
                        )
                    )
                }
            }
        }

        // Android technical limits guide context card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Comprendre les limites d'Android",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Randomisation d'adresse MAC :\n" +
                                "La plupart des smartphones récents (iOS/Android) modifient constamment leur adresse MAC Bluetooth. Presence Radar gère cela en vous permettant de lier ou de chercher un appareil également par son nom SSID / Bluetooth annoncé.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Restrictions de Scan Wi-Fi en arrière-plan :\n" +
                                "Android bride les scans Wi-Fi (4 scans par tranche de 2 minutes en premier plan, et 1 scan toutes les 30 minutes en arrière-plan). En revanche, le Bluetooth Low Energy fonctionne en arrière-plan en continu sans limitation majeure.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun CompassWidget(viewModel: RadarViewModel) {
    val azimuth by viewModel.azimuth.collectAsStateWithLifecycle()
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()
    val roll by viewModel.roll.collectAsStateWithLifecycle()
    val isCompassAvailable by viewModel.isCompassAvailable.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("compass_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Boussole d'Orientation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }

                // Small indicator showing if device is leveled
                val isLeveled = kotlin.math.abs(pitch) < 10 && kotlin.math.abs(roll) < 10
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isLeveled) NeonGreen.copy(alpha = 0.15f) else OrangeAccent.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isLeveled) "Niveau à Plat 🟢" else "Incliner à plat ⚠️",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLeveled) NeonGreen else OrangeAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isCompassAvailable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ExploreOff,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Capteurs magnétiques non disponibles",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Compass dial Canvas
                    Box(
                        modifier = Modifier
                            .size(130.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val radius = size.width / 2 - 8.dp.toPx()

                            // Draw background circle outline
                            drawCircle(
                                color = CardBorder,
                                radius = radius,
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // Rotate canvas for dynamic heading
                            rotate(degrees = -azimuth, pivot = center) {
                                // Draw cardinal directions
                                val textPaint = android.graphics.Paint().apply {
                                    textSize = 11.dp.toPx()
                                    isAntiAlias = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                }

                                // North in Red
                                textPaint.color = android.graphics.Color.parseColor("#EF4444")
                                drawContext.canvas.nativeCanvas.drawText(
                                    "N",
                                    center.x,
                                    center.y - radius + 15.dp.toPx(),
                                    textPaint
                                )

                                // South
                                textPaint.color = android.graphics.Color.parseColor("#F9FAFB")
                                drawContext.canvas.nativeCanvas.drawText(
                                    "S",
                                    center.x,
                                    center.y + radius - 5.dp.toPx(),
                                    textPaint
                                )

                                // East
                                drawContext.canvas.nativeCanvas.drawText(
                                    "E",
                                    center.x + radius - 10.dp.toPx(),
                                    center.y + 4.dp.toPx(),
                                    textPaint
                                )

                                // West
                                drawContext.canvas.nativeCanvas.drawText(
                                    "O",
                                    center.x - radius + 10.dp.toPx(),
                                    center.y + 4.dp.toPx(),
                                    textPaint
                                )

                                // Draw ticks every 30 degrees
                                for (angle in 0 until 360 step 30) {
                                    if (angle % 90 != 0) {
                                        val angleRad = Math.toRadians(angle.toDouble())
                                        val startX = center.x + (radius - 6.dp.toPx()) * kotlin.math.sin(angleRad).toFloat()
                                        val startY = center.y - (radius - 6.dp.toPx()) * kotlin.math.cos(angleRad).toFloat()
                                        val endX = center.x + radius * kotlin.math.sin(angleRad).toFloat()
                                        val endY = center.y - radius * kotlin.math.cos(angleRad).toFloat()
                                        drawLine(
                                            color = TextSecondary.copy(alpha = 0.5f),
                                            start = Offset(startX, startY),
                                            end = Offset(endX, endY),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                }
                            }

                            // Draw static reference pointer at the top
                            val pointerPath = Path().apply {
                                moveTo(center.x, center.y - radius - 6.dp.toPx())
                                lineTo(center.x - 5.dp.toPx(), center.y - radius + 4.dp.toPx())
                                lineTo(center.x + 5.dp.toPx(), center.y - radius + 4.dp.toPx())
                                close()
                            }
                            drawPath(
                                path = pointerPath,
                                color = NeonGreen
                            )
                        }

                        // Digital reading inside the center
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val directionName = when {
                                azimuth >= 337.5 || azimuth < 22.5 -> "N"
                                azimuth >= 22.5 && azimuth < 67.5 -> "NE"
                                azimuth >= 67.5 && azimuth < 112.5 -> "E"
                                azimuth >= 112.5 && azimuth < 157.5 -> "SE"
                                azimuth >= 157.5 && azimuth < 202.5 -> "S"
                                azimuth >= 202.5 && azimuth < 247.5 -> "SO"
                                azimuth >= 247.5 && azimuth < 292.5 -> "O"
                                else -> "NO"
                            }
                            Text(
                                text = "${azimuth.roundToInt()}°",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Text(
                                text = directionName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // Digital Info & Tilt Metrics
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Axe d'orientation :",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        val directionText = when {
                            azimuth >= 337.5 || azimuth < 22.5 -> "Cap sur le Nord"
                            azimuth >= 22.5 && azimuth < 67.5 -> "Cap sur le Nord-Est"
                            azimuth >= 67.5 && azimuth < 112.5 -> "Cap sur l'Est"
                            azimuth >= 112.5 && azimuth < 157.5 -> "Cap sur le Sud-Est"
                            azimuth >= 157.5 && azimuth < 202.5 -> "Cap sur le Sud"
                            azimuth >= 202.5 && azimuth < 247.5 -> "Cap sur le Sud-Ouest"
                            azimuth >= 247.5 && azimuth < 292.5 -> "Cap sur l'Ouest"
                            else -> "Cap sur le Nord-Ouest"
                        }
                        Text(
                            text = directionText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Tilt meters
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Roulis (Roll) : ",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.width(75.dp)
                            )
                            Text(
                                text = "${roll.roundToInt()}°",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (kotlin.math.abs(roll) < 10) NeonGreen else OrangeAccent
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Tangage (Pitch) : ",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.width(75.dp)
                            )
                            Text(
                                text = "${pitch.roundToInt()}°",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (kotlin.math.abs(pitch) < 10) NeonGreen else OrangeAccent
                            )
                        }
                    }
                }
            }
        }
    }
}
