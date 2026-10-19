@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.charmjh7.baeminblack.ui

import android.content.Intent
import android.provider.Settings
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charmjh7.baeminblack.diagnostics.ServiceHealth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun time(value: Long): String = if (value == 0L) "아직 없음" else DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
private fun healthText(state: ServiceHealth, enabled: Boolean): String =
    (if (!enabled) "접근성 서비스 꺼짐" else if (state.service.value == "SERVICE_CONNECTED") "접근성 서비스 연결됨" else "접근성 설정 켜짐 · 서비스 연결 대기") +
        "\n" + (if (state.target.value == "TARGET_DETECTED") "배민 감지 중" else "배민 대기 중") + " · " +
        (state.shopLabel.ifBlank { "가게 인식 대기" }) + "\n오버레이: " + state.overlay.value

@Composable fun ServiceHealthPanel(vm: BlackViewModel, enabled: Boolean, openSettings: () -> Unit) {
    val state by vm.serviceHealth.collectAsStateWithLifecycle()
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable { vm.go("diagnostics") }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(healthText(state, enabled).substringBefore('\n'), style = MaterialTheme.typography.labelLarge)
            Text(healthText(state, enabled).substringAfter('\n'), style = MaterialTheme.typography.bodySmall)
            if (state.error.isNotBlank()) Text("최근 사유: ${state.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text("마지막 정상 동작 ${time(state.lastHealthy)} · 눌러서 진단 보기", style = MaterialTheme.typography.labelSmall)
            if (!enabled || state.service.value != "SERVICE_CONNECTED") TextButton(onClick = openSettings) { Text("접근성 설정 열기") }
        }
    }
}
@Composable fun DiagnosticScreen(vm: BlackViewModel, enabled: Boolean, snack: SnackbarHostState) {
    val health by vm.serviceHealth.collectAsStateWithLifecycle()
    val data by vm.diagnosticView.collectAsStateWithLifecycle()
    val until by vm.detailedUntil.collectAsStateWithLifecycle()
    val diskError by vm.diagnostics.storageError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var includeText by rememberSaveable { mutableStateOf(false) }
    var includeSnapshots by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var trace by remember { mutableStateOf<String?>(null) }
    var snapshotPreview by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { vm.exportDiagnostics(it, includeSnapshots) } }
    LaunchedEffect(Unit) { while (true) { vm.refreshDiagnostics(); delay(3000) } }
    BackHandler { vm.go("settings") }
    Scaffold(topBar = { TopAppBar(title = { Text("진단 및 오류 기록") }, navigationIcon = { TextButton(onClick = { vm.go("settings") }) { Text("뒤로") } }) },
        snackbarHost = { SnackbarHost(snack) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(healthText(health, enabled), style = MaterialTheme.typography.bodyMedium)
                Text("배민 감지 ${time(health.lastTarget)}\n가게 인식 성공 ${time(health.lastShop)}\n표시 성공 ${time(health.lastOverlay)}\n최근 실패 ${health.error.ifBlank { "없음" }}", style = MaterialTheme.typography.bodySmall)
                if (!enabled || health.service.value != "SERVICE_CONNECTED") TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("접근성 설정 열기") }
                diskError?.let { Text("진단 파일 저장 오류: $it", color = MaterialTheme.colorScheme.error) }
                Text("보관 ${"%.1f".format(data.bytes / 1024.0)}KB · 최대 10MB · 7일", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Text("상세 접근성 진단", style = MaterialTheme.typography.titleSmall)
                Text("배민 화면의 노드 구조를 5분간 수집해요. 화면 원문을 포함하면 전화번호·주소·주문·인증정보가 담길 수 있어요. 필요한 화면에서만 시작해 주세요.", style = MaterialTheme.typography.bodySmall)
                Row { Checkbox(includeText, { includeText = it }, enabled = until == 0L); Text("실제 화면 텍스트 포함", Modifier.padding(top = 12.dp)) }
                if (until > SystemClock.elapsedRealtime()) TextButton(onClick = { vm.stopDetailed() }) { Text("상세 진단 중지 (최대 5분)") }
                else OutlinedButton(onClick = { vm.startDetailed(includeText) }) { Text("상세 진단 시작") }
                Row { Checkbox(includeSnapshots, { includeSnapshots = it }); Text("내보내기에 상세 스냅샷 포함", Modifier.padding(top = 12.dp)) }
                Text("기본 ZIP에는 화면 원문과 개인 메모가 포함되지 않아요. 스냅샷은 직접 확인하고 선택해서 내보내세요.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { exporter.launch("BaeminBlack-diagnostics-${System.currentTimeMillis()}.zip") }, enabled = !vm.busy) { Text("진단 기록 내보내기") }
                Row { TextButton(onClick = { confirmClear = true }, enabled = !vm.busy) { Text("전체 로그 삭제") }
                    TextButton(onClick = { vm.deleteSnapshots() }, enabled = !vm.busy) { Text("스냅샷 삭제") } }
            }
            if (data.snapshots.isNotEmpty()) item { Text("상세 스냅샷 ${data.snapshots.size}개", style = MaterialTheme.typography.titleSmall) }
            items(data.snapshots.take(30), key = { "snapshot:$it" }) { name -> TextButton(onClick = { scope.launch { snapshotPreview = vm.readSnapshot(name) } }) { Text(name, maxLines = 1) } }
            item { Text("보관 기간 내 단계별 집계", style = MaterialTheme.typography.titleSmall)
                Text(data.counts.entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { "기록 없음" }, style = MaterialTheme.typography.bodySmall)
                Text(data.reasons.entries.joinToString("\n") { "${it.key}: ${it.value}" }, style = MaterialTheme.typography.bodySmall) }
            item { Text("최근 이벤트 (눌러서 처리 흐름 보기)", style = MaterialTheme.typography.titleSmall) }
            items(data.events.asReversed(), key = { it.eventId }) { event ->
                Column(Modifier.fillMaxWidth().clickable { trace = event.traceId }.padding(vertical = 6.dp)) {
                    Text("${event.time.take(23)} · ${event.stage}", style = MaterialTheme.typography.labelSmall)
                    Text("${event.status} ${event.reason}", style = MaterialTheme.typography.bodySmall, color = if (event.success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    trace?.let { id -> AlertDialog(onDismissRequest = { trace = null }, title = { Text("처리 흐름") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(id, style = MaterialTheme.typography.labelSmall)
            data.events.filter { it.traceId == id }.sortedBy { it.elapsedRealtime }.forEach { event ->
                Text("${event.time}\n${event.stage} · ${event.status}\n${event.reason}\n${event.details}\n${event.stack}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }, confirmButton = { TextButton(onClick = { trace = null }) { Text("닫기") } }) }
    snapshotPreview?.let { text -> AlertDialog(onDismissRequest = { snapshotPreview = null }, title = { Text("수집한 스냅샷") }, text = { Text(text, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) }, confirmButton = { TextButton(onClick = { snapshotPreview = null }) { Text("닫기") } }) }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("진단 기록을 모두 삭제할까요?") }, text = { Text("이벤트와 상세 스냅샷이 삭제돼요. 가게와 메모는 유지돼요.") }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } }, confirmButton = { TextButton(onClick = { vm.stopDetailed(); vm.clearDiagnostics(); confirmClear = false }) { Text("삭제") } })
}
