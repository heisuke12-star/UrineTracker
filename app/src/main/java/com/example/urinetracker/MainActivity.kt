package com.example.urinetracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.urinetracker.ui.theme.UrineTrackerTheme
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// ── Models ────────────────────────────────────────────────────────────────────

data class UrineRecord(
    val id: String = UUID.randomUUID().toString(),
    val date: Date = Date(),
    val amount: Int
)

data class AppSettings(
    val warningEnabled: Boolean = true,
    val dailyMinWarning: Int = 500,
    val dailyMaxWarning: Int = 3000
)

// ── Persistence ───────────────────────────────────────────────────────────────

fun saveRecords(context: Context, records: List<UrineRecord>) {
    val arr = JSONArray()
    records.forEach { r ->
        arr.put(JSONObject().apply {
            put("id", r.id); put("date", r.date.time); put("amount", r.amount)
        })
    }
    context.getSharedPreferences("urine_tracker", Context.MODE_PRIVATE)
        .edit().putString("records", arr.toString()).apply()
}

fun loadRecords(context: Context): List<UrineRecord> {
    val json = context.getSharedPreferences("urine_tracker", Context.MODE_PRIVATE)
        .getString("records", null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            UrineRecord(id = o.getString("id"), date = Date(o.getLong("date")), amount = o.getInt("amount"))
        }
    } catch (e: Exception) { emptyList() }
}

fun saveSettings(context: Context, s: AppSettings) {
    context.getSharedPreferences("urine_tracker", Context.MODE_PRIVATE).edit()
        .putBoolean("warn_enabled", s.warningEnabled)
        .putInt("warn_min", s.dailyMinWarning)
        .putInt("warn_max", s.dailyMaxWarning)
        .apply()
}

fun loadSettings(context: Context): AppSettings {
    val p = context.getSharedPreferences("urine_tracker", Context.MODE_PRIVATE)
    return AppSettings(
        warningEnabled = p.getBoolean("warn_enabled", true),
        dailyMinWarning = p.getInt("warn_min", 500),
        dailyMaxWarning = p.getInt("warn_max", 3000)
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun isToday(date: Date): Boolean {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().also { it.time = date }
    return c.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
           c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
}

fun isSameMonth(date: Date, ref: Date): Boolean {
    val c1 = Calendar.getInstance().also { it.time = date }
    val c2 = Calendar.getInstance().also { it.time = ref }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
           c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)
}

fun groupByDay(records: List<UrineRecord>): List<Pair<Date, List<UrineRecord>>> {
    return records
        .groupBy { r ->
            Calendar.getInstance().also {
                it.time = r.date
                it.set(Calendar.HOUR_OF_DAY, 0)
                it.set(Calendar.MINUTE, 0)
                it.set(Calendar.SECOND, 0)
                it.set(Calendar.MILLISECOND, 0)
            }.time
        }
        .entries
        .sortedByDescending { it.key }
        .map { it.key to it.value.sortedByDescending { r -> r.date } }
}

// ── MainActivity ──────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UrineTrackerTheme {
                UrineTrackerApp()
            }
        }
    }
}

// ── Root App ──────────────────────────────────────────────────────────────────

@Composable
fun UrineTrackerApp() {
    val context = LocalContext.current
    var records by remember { mutableStateOf(loadRecords(context)) }
    var settings by remember { mutableStateOf(loadSettings(context)) }
    var selectedTab by remember { mutableStateOf(0) }

    val onAdd: (Int) -> Unit = { amount ->
        records = records + UrineRecord(amount = amount)
        saveRecords(context, records)
    }
    val onDelete: (UrineRecord) -> Unit = { record ->
        records = records.filter { it.id != record.id }
        saveRecords(context, records)
    }
    val onDeleteAll: () -> Unit = {
        records = emptyList()
        saveRecords(context, records)
    }
    val onSettingsChange: (AppSettings) -> Unit = { s ->
        settings = s
        saveSettings(context, s)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("홈") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.DateRange, null) },
                    label = { Text("기록") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("설정") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> HomeScreen(records, settings, onAdd, onDelete)
                1 -> HistoryScreen(records, onDelete)
                2 -> SettingsScreen(settings, onSettingsChange, onDeleteAll)
            }
        }
    }
}

// ── Home Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    records: List<UrineRecord>,
    settings: AppSettings,
    onAdd: (Int) -> Unit,
    onDelete: (UrineRecord) -> Unit
) {
    val todayList = records.filter { isToday(it.date) }.sortedByDescending { it.date }
    val todayTotal = todayList.sumOf { it.amount }
    var showCustomInput by remember { mutableStateOf(false) }
    var customAmountText by remember { mutableStateOf("") }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var recordToDelete by remember { mutableStateOf<UrineRecord?>(null) }

    val warningColor: Color? = if (settings.warningEnabled && todayTotal > 0) {
        when {
            todayTotal < settings.dailyMinWarning -> Color(0xFFF57C00)
            todayTotal > settings.dailyMaxWarning -> Color(0xFFD32F2F)
            else -> null
        }
    } else null

    val warningMessage: String? = if (settings.warningEnabled && todayTotal > 0) {
        when {
            todayTotal < settings.dailyMinWarning -> "오늘 소변량이 너무 적어요. 수분 섭취를 늘려보세요."
            todayTotal > settings.dailyMaxWarning -> "오늘 소변량이 매우 많아요. 의사와 상담해 보세요."
            else -> null
        }
    } else null

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHostState.showSnackbar(it); snackbarMessage = null }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("소변 트래커") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 경고 배너
            if (warningColor != null && warningMessage != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(warningColor, shape = MaterialTheme.shapes.medium)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 18.sp)
                        Text(warningMessage, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // 오늘 통계
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("오늘", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("총량", "$todayTotal", "ml", Modifier.weight(1f))
                        StatCard("횟수", "${todayList.size}", "회", Modifier.weight(1f))
                    }
                }
            }

            item { HorizontalDivider() }

            // 기록 버튼
            item {
                val amounts = listOf(200, 300, 400, 500, 600)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("기록하기", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (amount in amounts.take(3)) {
                            AmountButton(amount, {
                                onAdd(amount)
                                snackbarMessage = "${amount}ml 기록 완료!"
                            }, Modifier.weight(1f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (amount in amounts.drop(3)) {
                            AmountButton(amount, {
                                onAdd(amount)
                                snackbarMessage = "${amount}ml 기록 완료!"
                            }, Modifier.weight(1f))
                        }
                        Button(
                            onClick = { customAmountText = ""; showCustomInput = true },
                            modifier = Modifier.weight(1f).height(72.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                contentColor = Color(0xFF2E7D32)
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("직접", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                Text("입력", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                Text("오늘 기록", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }

            if (todayList.isEmpty()) {
                item {
                    Text(
                        "아직 기록이 없어요",
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(todayList, key = { it.id }) { record ->
                    RecordRow(record, onLongClick = { recordToDelete = record })
                }
            }
        }
    }

    // 직접 입력 다이얼로그
    if (showCustomInput) {
        AlertDialog(
            onDismissRequest = { showCustomInput = false },
            title = { Text("수치 입력") },
            text = {
                Column {
                    Text("소변량을 ml 단위로 입력하세요")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customAmountText,
                        onValueChange = { customAmountText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        placeholder = { Text("ml 단위로 입력") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = customAmountText.toIntOrNull()
                    if (v != null && v > 0) {
                        onAdd(v)
                        snackbarMessage = "${v}ml 기록 완료!"
                        showCustomInput = false
                    }
                }) { Text("기록") }
            },
            dismissButton = { TextButton(onClick = { showCustomInput = false }) { Text("취소") } }
        )
    }

    // 삭제 확인 다이얼로그
    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("기록 삭제") },
            text = { Text("${record.amount}ml 기록을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { onDelete(record); recordToDelete = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { recordToDelete = null }) { Text("취소") } }
        )
    }
}

// ── History Screen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(records: List<UrineRecord>, onDelete: (UrineRecord) -> Unit) {
    val monthFormat = SimpleDateFormat("yyyy년 M월", Locale.KOREAN)
    val dateFormat = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN)

    var selectedMode by remember { mutableStateOf(0) }
    var currentMonth by remember { mutableStateOf(Date()) }
    var startDate by remember { mutableStateOf(Calendar.getInstance().also { it.add(Calendar.MONTH, -1) }.time) }
    var endDate by remember { mutableStateOf(Date()) }
    var recordToDelete by remember { mutableStateOf<UrineRecord?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val filteredRecords = if (selectedMode == 0) {
        records.filter { isSameMonth(it.date, currentMonth) }
    } else {
        val endCal = Calendar.getInstance().also {
            it.time = endDate
            it.set(Calendar.HOUR_OF_DAY, 23)
            it.set(Calendar.MINUTE, 59)
            it.set(Calendar.SECOND, 59)
        }
        records.filter { it.date >= startDate && it.date <= endCal.time }
    }

    val grouped = groupByDay(filteredRecords)

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(title = { Text("기록") })

        TabRow(selectedTabIndex = selectedMode) {
            Tab(selected = selectedMode == 0, onClick = { selectedMode = 0 }, text = { Text("월별") })
            Tab(selected = selectedMode == 1, onClick = { selectedMode = 1 }, text = { Text("기간") })
        }

        if (selectedMode == 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    currentMonth = Calendar.getInstance().also { it.time = currentMonth; it.add(Calendar.MONTH, -1) }.time
                }) { Icon(Icons.Default.KeyboardArrowLeft, null) }
                Text(monthFormat.format(currentMonth), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = {
                    currentMonth = Calendar.getInstance().also { it.time = currentMonth; it.add(Calendar.MONTH, 1) }.time
                }) { Icon(Icons.Default.KeyboardArrowRight, null) }
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("시작일", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(48.dp))
                    TextButton(onClick = { showStartPicker = true }) { Text(dateFormat.format(startDate)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("종료일", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(48.dp))
                    TextButton(onClick = { showEndPicker = true }) { Text(dateFormat.format(endDate)) }
                }
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("총량", "${filteredRecords.sumOf { it.amount }}", "ml", Modifier.weight(1f))
            StatCard("횟수", "${filteredRecords.size}", "회", Modifier.weight(1f))
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        if (grouped.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("기록이 없어요", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (day, dayRecords) ->
                    stickyHeader {
                        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(dateFormat.format(day), style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "합계 ${dayRecords.sumOf { it.amount }}ml",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    items(dayRecords, key = { it.id }) { record ->
                        RecordRow(
                            record,
                            onLongClick = { recordToDelete = record },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startDate.time)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { startDate = Date(it) }
                    showStartPicker = false
                }) { Text("확인") }
            }
        ) { DatePicker(state = state) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endDate.time)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { endDate = Date(it) }
                    showEndPicker = false
                }) { Text("확인") }
            }
        ) { DatePicker(state = state) }
    }

    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("기록 삭제") },
            text = { Text("${record.amount}ml 기록을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { onDelete(record); recordToDelete = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { recordToDelete = null }) { Text("취소") } }
        )
    }
}

// ── Settings Screen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onDeleteAll: () -> Unit
) {
    var minText by remember { mutableStateOf("${settings.dailyMinWarning}") }
    var maxText by remember { mutableStateOf("${settings.dailyMaxWarning}") }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(title = { Text("설정") })

        ListItem(
            headlineContent = { Text("경고 알림 켜기") },
            trailingContent = {
                Switch(
                    checked = settings.warningEnabled,
                    onCheckedChange = { onSettingsChange(settings.copy(warningEnabled = it)) }
                )
            }
        )
        HorizontalDivider()

        if (settings.warningEnabled) {
            ListItem(
                headlineContent = { Text("하루 최소 정상 수치") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = minText,
                            onValueChange = {
                                minText = it
                                it.toIntOrNull()?.let { v -> onSettingsChange(settings.copy(dailyMinWarning = v)) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(80.dp)
                        )
                        Text("ml", color = MaterialTheme.colorScheme.outline)
                    }
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("하루 최대 정상 수치") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = maxText,
                            onValueChange = {
                                maxText = it
                                it.toIntOrNull()?.let { v -> onSettingsChange(settings.copy(dailyMaxWarning = v)) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(80.dp)
                        )
                        Text("ml", color = MaterialTheme.colorScheme.outline)
                    }
                }
            )
            HorizontalDivider()
        }

        ListItem(
            headlineContent = {
                Text(
                    "성인의 하루 평균 정상 소변량은 800~2000ml입니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            },
            leadingContent = { Text("ℹ️") }
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("전체 기록 삭제", color = MaterialTheme.colorScheme.error) },
            leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickable { showDeleteAllDialog = true }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("전체 기록을 삭제할까요?") },
            text = { Text("삭제된 기록은 복구할 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { onDeleteAll(); showDeleteAllDialog = false }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllDialog = false }) { Text("취소") } }
        )
    }
}

// ── Shared Composables ────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordRow(record: UrineRecord, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREAN)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(timeFormat.format(record.date), color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
            Text("${record.amount} ml", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        HorizontalDivider()
    }
}

@Composable
fun StatCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun AmountButton(amount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            contentColor = MaterialTheme.colorScheme.primary
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$amount", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("ml", fontSize = 11.sp)
        }
    }
}
