package com.aboni.n2kRouter

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.aboni.marine.link.LinkState
import com.aboni.marine.link.ble.requiredBlePermissions
import com.aboni.marine.ui.MarineScaffold
import com.aboni.marine.ui.devices.DeviceList
import com.aboni.marine.ui.cards.CardAction
import com.aboni.marine.ui.cards.CardPage
import com.aboni.marine.ui.cards.CardPager
import com.aboni.marine.ui.theme.Marine
import com.aboni.marine.ui.theme.MarineTheme
import com.aboni.marine.ui.theme.ThemeStore
import com.aboni.marine.ui.theme.next
import com.aboni.n2kRouter.ui.AdjustCard
import com.aboni.n2kRouter.ui.CalibrationCard
import com.aboni.n2kRouter.ui.SettingsCard
import com.aboni.n2kRouter.ui.WindCard
import com.aboni.n2kRouter.ui.rememberSender
import kotlinx.coroutines.launch

/** Wind, Calibration, Adjust, Settings, Devices; the status bar's ⚙ jumps to Devices. */
private const val PAGES = 5
private const val DEVICES_PAGE = PAGES - 1

class MainActivity : ComponentActivity() {

    private val vm: WindViewModel by viewModels()
    private val permissions = requiredBlePermissions(Build.VERSION.SDK_INT)
    private var granted by mutableStateOf(false)

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r.values.all { it }
        if (granted) vm.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themes = ThemeStore(this)
        val link = vm.link
        setContent {
            val mode by themes.mode.collectAsState()
            val status by link.status.collectAsState()
            val data by link.data.collectAsState()
            val devices by link.devices.collectAsState()
            val chosen by link.chosen.collectAsState()
            val scanning by vm.scanning.collectAsState()
            val increment by vm.increment.collectAsState()
            val pager = rememberPagerState { PAGES }
            val scope = rememberCoroutineScope()
            // First run: nothing chosen yet, so show the sensors being found.
            LaunchedEffect(granted, chosen) { if (granted && chosen == null) pager.scrollToPage(DEVICES_PAGE) }
            MarineTheme(mode) {
                MarineScaffold(
                    status = status,
                    onStatusSettings = { scope.launch { pager.animateScrollToPage(DEVICES_PAGE) } },
                    onCycleTheme = { themes.set(mode.next()) },
                    banner = if (granted) null else { { PermissionBanner { askPermissions.launch(permissions.toTypedArray()) } } },
                ) { padding ->
                    val send = rememberSender(vm::send)
                    val connected = status.state == LinkState.Connected || status.state == LinkState.Stale
                    CardPager(
                        listOf(
                            CardPage("wind", "Wind", CardAction.icon(android.R.drawable.ic_menu_view, "Wind")) {
                                WindCard(data, send)
                            },
                            CardPage("calibration", "Calibration", CardAction.icon(android.R.drawable.ic_menu_manage, "Calibration")) {
                                CalibrationCard(data, send)
                            },
                            CardPage("adjust", "Adjust", CardAction.icon(android.R.drawable.ic_menu_preferences, "Adjust")) {
                                AdjustCard(data, increment, vm::setIncrement, send)
                            },
                            CardPage("settings", "Settings", CardAction.icon(android.R.drawable.ic_menu_edit, "Settings")) {
                                SettingsCard(data, status.device?.name, connected, send)
                            },
                            CardPage("devices", "Devices", CardAction.icon(android.R.drawable.ic_menu_search, "Scan for devices", vm::scan)) {
                                DeviceList(
                                    devices = devices,
                                    status = status,
                                    chosenId = chosen,
                                    onSelect = link::select,
                                    scanning = scanning || status.state == LinkState.Scanning,
                                    emptyText = "No wind sensors found yet: tap the search icon to scan",
                                )
                            },
                        ),
                        Modifier.fillMaxSize().padding(padding),
                        state = pager,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        granted = permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) vm.start() else askPermissions.launch(permissions.toTypedArray())
    }

    override fun onStop() {
        vm.stop()
        super.onStop()
    }
}

@androidx.compose.runtime.Composable
private fun PermissionBanner(onAllow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Bluetooth permission is needed to reach the wind sensor.",
            style = Marine.type.body,
            color = Marine.colors.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAllow) { Text("Allow") }
    }
}
