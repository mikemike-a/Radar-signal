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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.HistoryEntry
import com.example.data.KnownDevice
import com.example.scanning.DetectedDevice
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
                "hunt" -> HuntScreen(viewModel = viewModel)
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Custom interactive canvas Radar widget
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Brush.verticalGradient(listOf(ObsidianBg, DarkSurface))),
            contentAlignment = Alignment.Center
        ) {
            RadarVisualizerWidget(
                isScanning = isScanning,
                devices = detectedDevices
            )
        }

        // Baromètre & Simulateur Altimètre
        BarometerWidget(viewModel = viewModel)

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

// --- Radial Sweeping Radar Widget ---
@Composable
fun RadarVisualizerWidget(isScanning: Boolean, devices: List<DetectedDevice>) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Smooth angle animation for sweeping radar line
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Pulse circles animation
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = Modifier.size(190.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.width / 2

        // Draw pulsing beacon glow in the background
        if (isScanning) {
            drawCircle(
                color = NeonGreen.copy(alpha = 0.15f * (1f - pulseScale)),
                radius = maxRadius * pulseScale,
                center = center
            )
        }

        // Draw static grid rings
        drawCircle(color = CardBorder, radius = maxRadius, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = CardBorder, radius = maxRadius * 0.66f, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = CardBorder, radius = maxRadius * 0.33f, center = center, style = Stroke(width = 1.dp.toPx()))

        // Draw crosshair axes
        drawLine(
            color = CardBorder,
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = CardBorder,
            start = Offset(center.x, 0f),
            end = Offset(center.x, size.height),
            strokeWidth = 1.dp.toPx()
        )

        // Draw sweeping radar line
        if (isScanning) {
            val sweepRad = Math.toRadians(sweepAngle.toDouble())
            val endX = center.x + maxRadius * cos(sweepRad).toFloat()
            val endY = center.y + maxRadius * sin(sweepRad).toFloat()

            // Draw primary sweep line
            drawLine(
                color = NeonGreen.copy(alpha = 0.8f),
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Map devices to the canvas as glowing beacons
        devices.forEach { device ->
            // Use stable hashcode of identifier to locate device at a fixed random angle
            val angleDeg = (device.identifier.hashCode() % 360).toDouble()
            val angleRad = Math.toRadians(angleDeg)

            // Convert RSSI to normalized distance from center (clamped between -40 dBm and -100 dBm)
            val clampedRssi = device.rssi.coerceIn(-100, -40)
            val normalizedDist = (100 + clampedRssi) / 60.0 // 0f (far) to 1f (close)
            val finalRadius = maxRadius * (1f - normalizedDist.toFloat() * 0.85f).coerceIn(0.1f, 0.95f)

            val x = center.x + finalRadius * cos(angleRad).toFloat()
            val y = center.y + finalRadius * sin(angleRad).toFloat()

            val beaconColor = if (device.isKnown) NeonGreen else NeonCyan
            
            // Glowing effect
            drawCircle(
                color = beaconColor.copy(alpha = 0.3f),
                radius = 8.dp.toPx(),
                center = Offset(x, y)
            )
            drawCircle(
                color = beaconColor,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, if (device.isKnown) NeonGreen.copy(alpha = 0.4f) else CardBorder),
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
                        color = if (device.isKnown) NeonGreen.copy(alpha = 0.1f) else CardBorder,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        device.type == "WIFI" -> Icons.Default.Wifi
                        device.isKnown -> Icons.Default.Devices
                        else -> Icons.Default.Bluetooth
                    },
                    contentDescription = null,
                    tint = if (device.isKnown) NeonGreen else TextSecondary,
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
                    if (device.isKnown) {
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
                        onStartHunt = { viewModel.startHunting(mockDevice) }
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
    onStartHunt: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
    val isSimulatingBarometer by viewModel.isSimulatingBarometer.collectAsStateWithLifecycle()
    val simulatedPressure by viewModel.simulatedPressure.collectAsStateWithLifecycle()

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

            // Reference details info
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
                
                // Sensor status / Toggle simulation
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isBarometerAvailable) "Capteur Réel" else "Simulation (Manuel)",
                        fontSize = 11.sp,
                        color = if (isBarometerAvailable && !isSimulatingBarometer) NeonGreen else NeonCyan,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isSimulatingBarometer || !isBarometerAvailable,
                        onCheckedChange = { viewModel.setSimulatingBarometer(it) },
                        modifier = Modifier.scale(0.7f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = NeonCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CardBorder
                        )
                    )
                }
            }

            // Simulated control slider
            if (isSimulatingBarometer || !isBarometerAvailable) {
                Spacer(modifier = Modifier.height(6.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ajuster la pression simulée :", fontSize = 11.sp, color = TextSecondary)
                        Text(String.format("%.1f hPa", simulatedPressure), fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = simulatedPressure,
                        onValueChange = { viewModel.updateSimulatedPressure(it) },
                        valueRange = 980f..1040f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = CardBorder
                        ),
                        modifier = Modifier.height(24.dp)
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
    val currentFloor by viewModel.estimatedFloor.collectAsStateWithLifecycle()
    val detectedDevices by viewModel.detectedDevices.collectAsStateWithLifecycle()

    // Animation for pulsing radar ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // Smooth pulse speed adjusting automatically to distance!
    val pulseDuration = remember(huntDistanceProgress) {
        val baseDuration = 1800
        val reduction = (huntDistanceProgress * 1400).toInt()
        (baseDuration - reduction).coerceIn(400, 1800)
    }

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

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
                                            imageVector = if (device.type == "WIFI") Icons.Default.Wifi else Icons.Default.Bluetooth,
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
                    .weight(1f),
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
                                imageVector = if (device.type == "WIFI") Icons.Default.Wifi else Icons.Default.Bluetooth,
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
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Immersive Pulsing Target Widget
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(Brush.radialGradient(listOf(DarkSurface, ObsidianBg)), CircleShape)
                        .border(1.dp, CardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!huntSignalLost) {
                        // Drawing animated pulse ring
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = NeonGreen.copy(alpha = pulseAlpha),
                                radius = (size.width / 2) * pulseScale,
                                center = Offset(size.width / 2, size.height / 2),
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

                Spacer(modifier = Modifier.height(24.dp))

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
                            Text("Étage de l'appareil", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (savedKnown == null) "Non configuré" else if (targetFloor == 0) "RDC (0)" else "Étage $targetFloor",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = NeonCyan
                            )
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
    val rssiVal by viewModel.rssiThreshold.collectAsStateWithLifecycle()
    val powerSaverEnabled by viewModel.isPowerSaver.collectAsStateWithLifecycle()
    val departureSecs by viewModel.departureDelaySec.collectAsStateWithLifecycle()

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
