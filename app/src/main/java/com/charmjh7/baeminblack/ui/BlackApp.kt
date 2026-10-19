@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.charmjh7.baeminblack.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.charmjh7.baeminblack.accessibility.BaeminAccessibilityService
import com.charmjh7.baeminblack.data.*
import java.time.LocalDate
import java.util.UUID
import android.net.Uri

@Composable private fun accessibilityEnabled(): Boolean {
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current
    fun query(): Boolean = context.getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == ComponentName(context, BaeminAccessibilityService::class.java)
        }
    var enabled by remember { mutableStateOf(query()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) enabled = query() }
        lifecycle.lifecycle.addObserver(observer)
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val listener = AccessibilityManager.AccessibilityStateChangeListener { enabled = query() }
        manager.addAccessibilityStateChangeListener(listener)
        onDispose { lifecycle.lifecycle.removeObserver(observer); manager.removeAccessibilityStateChangeListener(listener) }
    }
    return enabled
}
private fun openAccessibility(context: Context) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

@Composable fun BlackApp(vm: BlackViewModel) {
    val route by vm.route.collectAsStateWithLifecycle(); val selected by vm.selected.collectAsStateWithLifecycle(); val noteId by vm.noteId.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle(); val enabled = accessibilityEnabled()
    val snackbar = remember { SnackbarHostState() }
    val screenState = rememberSaveableStateHolder()
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    val current = rows?.firstOrNull { it.shop.id == selected }
    Box(Modifier.fillMaxSize()) {
        when (route) {
            "main" -> screenState.SaveableStateProvider("main") { MainScreen(vm, rows, enabled, snackbar) }
            "settings" -> SettingsScreen(vm, enabled, snackbar)
            "shop" -> if (selected.isNotBlank() && current == null) MissingScreen(vm, rows == null, snackbar) else key(selected, vm.field("draft_id")) { ShopEditor(vm, current?.shop, snackbar) }
            "note" -> if (current != null) key(selected, noteId) { NoteEditor(vm, current.shop, current.notes.firstOrNull { it.id == noteId }, snackbar) } else MissingScreen(vm, rows == null, snackbar)
            "detail" -> if (current != null) DetailScreen(vm, current, snackbar) else MissingScreen(vm, rows == null, snackbar)
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().statusBarsPadding().align(Alignment.TopCenter))
    }
}
@Composable private fun MainScreen(vm: BlackViewModel, rows: List<ShopWithNotes>?, enabled: Boolean, snack: SnackbarHostState) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }; var filter by rememberSaveable { mutableStateOf("ALL") }; var byName by rememberSaveable { mutableStateOf(false) }
    val visible = remember(rows, query, filter, byName) {
        val q = query.trim().lowercase()
        val filtered = rows.orEmpty().filter { r ->
            (filter == "ALL" || r.shop.status.name == filter) && (q.isEmpty() || (listOf(r.shop.name, r.shop.branch) + r.shop.aliases + r.notes.flatMap { listOf(it.body, it.title, it.menu) }).any { it.lowercase().contains(q) })
        }
        if (byName) filtered.sortedBy { it.shop.name } else filtered.sortedByDescending { it.shop.updatedAt }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("배민 블랙", fontWeight = FontWeight.SemiBold) }, actions = { IconButton(onClick = { vm.go("settings") }) { Icon(Icons.Outlined.Settings, "설정") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { vm.newShop() }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("가게 등록") }) }, snackbarHost = { SnackbarHost(snack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Surface(color = if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.Info, null, Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(if (enabled) "접근성 서비스 켜짐" else "접근성 서비스 꺼짐", style = MaterialTheme.typography.labelLarge)
                        if (!enabled) Text("가게 이름과 위치를 읽어 배지를 표시해요. 메모는 권한 없이도 쓸 수 있어요.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!enabled) TextButton(onClick = { openAccessibility(context) }) { Text("켜기") }
                }
            }
            OutlinedTextField(query, { query = it }, label = { Text("가게·지점·별칭·메모 검색") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "검색어 지우기") } }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "전체", "BLOCKED" to "블랙리스트", "CAUTION" to "주의", "FAVORITE" to "즐겨찾기").forEach { (key, label) -> FilterChip(filter == key, onClick = { filter = key }, label = { Text(label) }) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${visible.size}개 가게", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { byName = !byName }) { Icon(Icons.Outlined.Sort, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (byName) "이름순" else "최근 수정순") }
            }
            if (rows == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (visible.isEmpty()) EmptyState(if (rows.isEmpty()) "아직 등록한 가게가 없어요" else "검색 결과가 없어요", if (rows.isEmpty()) "배민에서 공유하거나 직접 가게를 등록해 보세요." else "검색어나 필터를 바꿔 보세요.")
            else LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                items(visible, key = { it.shop.id }) { row ->
                    ListItem(headlineContent = { Text(row.shop.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) }, supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (row.shop.branch.isNotBlank()) Text(row.shop.branch)
                            val latest = row.notes.maxWithOrNull(compareBy<Note> { it.date }.thenBy { it.createdAt })
                            Text(latest?.body?.ifBlank { latest.title.ifBlank { latest.menu.ifBlank { "사진 기록" } } } ?: "기록 없음", maxLines = 2, overflow = TextOverflow.Ellipsis)
                            row.shop.lastRecordDate?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        }
                    }, trailingContent = { StatusLabel(row.shop.status) }, modifier = Modifier.clickable { vm.go("detail", row.shop.id) })
                }
            }
        }
    }
}
@Composable private fun StatusLabel(status: ShopStatus) { Text(status.label, style = MaterialTheme.typography.labelMedium, color = statusColor(status)) }
@Composable private fun EmptyState(title: String, body: String) { Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun BackButton(action: () -> Unit) { IconButton(onClick = action) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로") } }
@Composable private fun MissingScreen(vm: BlackViewModel, loading: Boolean, snack: SnackbarHostState) {
    Scaffold(topBar = { TopAppBar(title = { Text("가게 기록") }, navigationIcon = { BackButton { vm.go("main") } }) }, snackbarHost = { SnackbarHost(snack) }) { padding -> Box(Modifier.padding(padding)) { EmptyState(if (loading) "기록을 불러오고 있어요" else "가게를 찾지 못했어요", if (loading) "잠시만 기다려 주세요." else "목록에서 가게를 선택하거나 새로 등록해 주세요.") } }
    BackHandler { vm.go("main") }
}
@Composable private fun DetailScreen(vm: BlackViewModel, row: ShopWithNotes, snack: SnackbarHostState) {
    val shop = row.shop; var deleteShop by remember { mutableStateOf(false) }; var deleteNote by remember { mutableStateOf<Note?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    BackHandler { vm.go("main") }
    Scaffold(topBar = { TopAppBar(title = { Text(shop.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { BackButton { vm.go("main") } }, actions = { IconButton(onClick = { vm.go("shop", shop.id) }) { Icon(Icons.Outlined.Edit, "가게 정보 수정") }; IconButton(onClick = { deleteShop = true }, enabled = !vm.busy) { Icon(Icons.Outlined.DeleteOutline, "가게 삭제") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { vm.go("note", shop.id) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("메모 추가") }) }, snackbarHost = { SnackbarHost(snack) }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 100.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shop.branch.isNotBlank()) Text(shop.branch, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { ShopStatus.entries.forEach { status -> FilterChip(shop.status == status, { vm.status(shop, status) }, enabled = !vm.busy, label = { Text(status.label) }) } }
                    Text("내 주문 경험 ${row.notes.size}건", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp))
                    if (row.notes.isEmpty()) Text("메모를 남겨 두면 다음 주문 때 확인할 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(row.notes.sortedWith(compareByDescending<Note> { it.date }.thenByDescending { it.createdAt }), key = { it.id }) { note ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(note.date + (note.rating?.let { " · 만족도 $it/5" } ?: ""), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { vm.go("note", shop.id, note.id) }) { Icon(Icons.Outlined.Edit, "기록 수정") }
                        IconButton(onClick = { deleteNote = note }, enabled = !vm.busy) { Icon(Icons.Outlined.DeleteOutline, "기록 삭제") }
                    }
                    if (note.title.isNotBlank()) Text(note.title, style = MaterialTheme.typography.titleMedium)
                    if (note.menu.isNotBlank()) Text(note.menu, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    if (note.body.isNotBlank()) Text(note.body, style = MaterialTheme.typography.bodyLarge)
                    if (note.photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(note.photos) { name -> AsyncImage(vm.repo.media.file(name), "첨부 사진 열기", Modifier.size(92.dp).clickable { preview = name }) } }
                    HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    if (deleteShop) ConfirmDelete("가게를 삭제할까요?", "이 가게의 모든 기록과 첨부 사진도 삭제돼요.", { deleteShop = false }) { vm.deleteShop(shop.id); deleteShop = false }
    deleteNote?.let { n -> ConfirmDelete("기록을 삭제할까요?", "이 기록의 첨부 사진도 삭제돼요.", { deleteNote = null }) { vm.deleteNote(n); deleteNote = null } }
    preview?.let { name -> Dialog(onDismissRequest = { preview = null }) { Surface(shape = RoundedCornerShape(12.dp)) { Column { AsyncImage(vm.repo.media.file(name), "첨부 사진", Modifier.fillMaxWidth().heightIn(max = 480.dp)); TextButton(onClick = { preview = null }, Modifier.align(Alignment.End)) { Text("닫기") } } } } }
}
@Composable private fun ConfirmDelete(title: String, body: String, cancel: () -> Unit, confirm: () -> Unit) { AlertDialog(onDismissRequest = cancel, title = { Text(title) }, text = { Text(body) }, dismissButton = { TextButton(onClick = cancel) { Text("취소") } }, confirmButton = { TextButton(onClick = confirm) { Text("삭제", color = MaterialTheme.colorScheme.error) } }) }

@Composable private fun ShopEditor(vm: BlackViewModel, existing: Shop?, snack: SnackbarHostState) {
    val isNew = existing == null
    val id = existing?.id ?: vm.field("draft_id")
    var name by rememberSaveable { mutableStateOf(existing?.name ?: vm.field("draft_name")) }
    var branch by rememberSaveable { mutableStateOf(existing?.branch ?: vm.field("draft_branch")) }
    var no by rememberSaveable { mutableStateOf(existing?.shopNo ?: vm.field("draft_no")) }
    var link by rememberSaveable { mutableStateOf(existing?.shareLink ?: vm.field("draft_link")) }
    var aliases by rememberSaveable { mutableStateOf(existing?.aliases?.joinToString("\n").orEmpty()) }
    var status by rememberSaveable { mutableStateOf(existing?.status?.name ?: "NORMAL") }
    var first by rememberSaveable { mutableStateOf("") }
    val back = { if (isNew) vm.go("main") else vm.go("detail", existing!!.id) }
    val dirty = name != (existing?.name ?: vm.field("draft_name")) || branch != (existing?.branch ?: vm.field("draft_branch")) || no != (existing?.shopNo ?: vm.field("draft_no")) || link != (existing?.shareLink ?: vm.field("draft_link")) || aliases != existing?.aliases?.joinToString("\n").orEmpty() || status != (existing?.status?.name ?: "NORMAL") || first.isNotEmpty()
    var discard by remember { mutableStateOf(false) }
    val close = { if (!vm.busy) { if (dirty) discard = true else back() } }
    BackHandler(onBack = close)
    EditorFrame(if (isNew) "가게 등록" else "가게 정보", vm.busy, snack, close, save = {
        val number = no.trim().ifBlank { com.charmjh7.baeminblack.share.ShareLinkParser.parse(link).identity.shopNo.orEmpty() }.ifBlank { null }
        val base = existing ?: Shop(id = id.ifBlank { UUID.randomUUID().toString() }, name = name)
        vm.saveShop(base.copy(name = name, branch = branch, shopNo = number, shareLink = link, shareText = existing?.shareText ?: vm.field("draft_text"), aliases = aliases.lines().map(String::trim).filter(String::isNotBlank), status = ShopStatus.valueOf(status)), if (isNew) first else "")
    }) {
        Field(name, { name = it }, "가게명 *", true)
        Field(branch, { branch = it }, "지점명", true)
        Text("지점명은 구분해서 입력해 주세요. 같은 이름의 다른 지점과 연결되지 않도록 사용해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusSelector(status) { status = it }
        Field(no, { no = it }, "배민 가게 고유번호 (선택)", true, KeyboardType.Number)
        Field(link, { link = it }, "원본 공유 링크 (선택)")
        Field(aliases, { aliases = it }, "별칭 (한 줄에 하나)")
        Text("별칭에는 배민 화면에 표시되는 이름 전체를 입력해 주세요. 필요한 경우 지점명도 포함해 주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (isNew) Field(first, { first = it }, "첫 메모 (선택)", minLines = 4)
        val original = existing?.shareText ?: vm.field("draft_text")
        if (original.isNotBlank()) { Text("받은 공유 내용", style = MaterialTheme.typography.labelLarge); Text(original, style = MaterialTheme.typography.bodySmall) }
    }
    if (discard) DiscardDialog({ discard = false }, { discard = false; back() })
}
@Composable private fun NoteEditor(vm: BlackViewModel, shop: Shop, existing: Note?, snack: SnackbarHostState) {
    val context = LocalContext.current
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var date by rememberSaveable { mutableStateOf(existing?.date ?: LocalDate.now().toString()) }
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }; var menu by rememberSaveable { mutableStateOf(existing?.menu.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(existing?.body.orEmpty()) }; var rating by rememberSaveable { mutableStateOf(existing?.rating?.toString().orEmpty()) }
    var photos by rememberSaveable { mutableStateOf(ArrayList(existing?.photos.orEmpty())) }
    var additions by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var discard by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        uris.forEach { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        additions = ArrayList((additions + uris.map(Uri::toString)).distinct())
    }
    val dirty = date != (existing?.date ?: LocalDate.now().toString()) || title != existing?.title.orEmpty() || menu != existing?.menu.orEmpty() || body != existing?.body.orEmpty() || rating != existing?.rating?.toString().orEmpty() || photos != existing?.photos.orEmpty() || additions.isNotEmpty()
    val close = { if (!vm.busy) { if (dirty) discard = true else vm.go("detail", shop.id) } }
    BackHandler(onBack = close)
    EditorFrame(if (existing == null) "메모 추가" else "메모 수정", vm.busy, snack, close, save = {
        if (runCatching { LocalDate.parse(date) }.isFailure) vm.message = "날짜를 YYYY-MM-DD 형식으로 입력해 주세요."
        else if (rating.isNotEmpty() && rating.toIntOrNull() !in 1..5) vm.message = "만족도는 1~5로 입력해 주세요."
        else vm.saveNote((existing ?: Note(id = id, shopId = shop.id, date = date)).copy(date = date, title = title, menu = menu, body = body, rating = rating.toIntOrNull(), photos = photos), additions.map(Uri::parse))
    }) {
        Text(shop.name + if (shop.branch.isBlank()) "" else " · ${shop.branch}", style = MaterialTheme.typography.titleSmall)
        Field(date, { date = it }, "날짜 (YYYY-MM-DD)", true)
        Field(menu, { menu = it }, "주문 메뉴 (선택)")
        Field(title, { title = it }, "제목 (선택)")
        Field(body, { body = it }, "자유 메모", minLines = 7)
        Text("만족도 (선택)", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("") + (1..5).map(Int::toString)).forEach { r -> FilterChip(rating == r, { rating = r }, label = { Text(r.ifBlank { "없음" }) }) }
        }
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !vm.busy) { Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("사진 첨부") }
        if (photos.isNotEmpty() || additions.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photos, key = { "saved:$it" }) { name -> PhotoTile(vm.repo.media.file(name), { photos = ArrayList(photos - name) }) }
            items(additions, key = { "new:$it" }) { uri -> PhotoTile(Uri.parse(uri), { additions = ArrayList(additions - uri) }) }
        }
    }
    if (discard) DiscardDialog({ discard = false }, { discard = false; vm.go("detail", shop.id) })
}
@Composable private fun PhotoTile(model: Any, remove: () -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally) { AsyncImage(model, "첨부 사진", Modifier.size(96.dp)); TextButton(onClick = remove) { Text("첨부 해제") } } }
@Composable private fun DiscardDialog(cancel: () -> Unit, leave: () -> Unit) { AlertDialog(onDismissRequest = cancel, title = { Text("작성을 그만둘까요?") }, text = { Text("저장하지 않은 변경사항은 사라져요.") }, dismissButton = { TextButton(onClick = cancel) { Text("계속 작성") } }, confirmButton = { TextButton(onClick = leave) { Text("나가기") } }) }
@Composable private fun EditorFrame(title: String, busy: Boolean, snack: SnackbarHostState, close: () -> Unit, save: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(Modifier.imePadding(), topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = close, enabled = !busy) { Icon(Icons.Outlined.Close, "취소") } }, actions = { TextButton(onClick = save, enabled = !busy) { Text("저장", fontWeight = FontWeight.Bold) } }) }, snackbarHost = { SnackbarHost(snack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable private fun Field(value: String, change: (String) -> Unit, label: String, singleLine: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, minLines: Int = 1) { OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, minLines = minLines, keyboardOptions = KeyboardOptions(keyboardType = keyboard)) }
@Composable private fun StatusSelector(status: String, change: (String) -> Unit) {
    Text("분류", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ShopStatus.entries.forEach { s -> FilterChip(status == s.name, { change(s.name) }, label = { Text(s.label) }) } }
}
@Composable private fun SettingsScreen(vm: BlackViewModel, enabled: Boolean, snack: SnackbarHostState) {
    val context = LocalContext.current; val settings by vm.settings.collectAsStateWithLifecycle()
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }
    val zip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let { uri -> vm.backup(uri, true) } }
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> vm.backup(uri, false) } }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri ->
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; pendingRestore = uri.toString()
    } }
    BackHandler { vm.go("main") }
    Scaffold(topBar = { TopAppBar(title = { Text("설정") }, navigationIcon = { BackButton { vm.go("main") } }) }, snackbarHost = { SnackbarHost(snack) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("배민 화면 표시", style = MaterialTheme.typography.titleMedium)
            ListItem(headlineContent = { Text("접근성 서비스") }, supportingContent = { Text(if (enabled) "활성화됨" else "비활성화됨") }, trailingContent = { TextButton(onClick = { openAccessibility(context) }) { Text("설정 열기") } })
            SettingSwitch("목록 블랙리스트 배지", settings.badges) { vm.updateSettings(settings.copy(badges = it)) }
            SettingSwitch("상세화면 블랙리스트 경고", settings.warnings) { vm.updateSettings(settings.copy(warnings = it)) }
            SettingSwitch("상세화면 메모 바로가기", settings.memo) { vm.updateSettings(settings.copy(memo = it)) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("데이터 백업·복원", style = MaterialTheme.typography.titleMedium)
            Text("개인 메모와 공유 원문이 파일에 포함돼요. ZIP 백업에는 첨부 사진도 함께 저장해요.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { zip.launch("BaeminBlack-${LocalDate.now()}.zip") }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("사진 포함 ZIP 백업") }
            OutlinedButton(onClick = { json.launch("BaeminBlack-${LocalDate.now()}.json") }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("사진 제외 JSON 백업") }
            OutlinedButton(onClick = { restore.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "text/plain")) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("백업 파일 복원") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("사용 방법", style = MaterialTheme.typography.titleMedium)
            Text("1. 가게를 직접 등록하거나 배민 가게의 공유 메뉴에서 ‘배민 블랙’을 선택해 주세요.\n\n2. 가게 기록에서 분류를 바꾸고 주문 경험을 남겨 주세요. 지점명과 화면에 보이는 전체 이름을 별칭에 입력하면 정확한 연결에 도움이 돼요.\n\n3. 접근성 설정에서 배민 블랙을 켜면 식별 가능한 배민 가게 카드에 작은 배지가 표시돼요. 스크롤 중에는 숨기고 멈춘 뒤 다시 표시해요.\n\n4. 상세화면의 경고나 메모 버튼을 누르면 가게 기록이 열려요. 이름을 읽지 못하는 화면에서는 공유 기능이나 직접 등록을 이용해 주세요.", style = MaterialTheme.typography.bodyMedium)
            Text("모든 평가는 나만의 개인 기록이에요. 배민의 공식 제재나 평가와 관계없어요. 화면 캡처와 외부 서버 전송은 하지 않아요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    pendingRestore?.let { uri -> AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("백업을 복원할까요?") }, text = { Text("기존 기록은 유지하고 가져온 기록을 병합해요. 가게 고유번호가 같으면 같은 가게로 연결하고, 기존 분류는 유지해요.") }, dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("취소") } }, confirmButton = { TextButton(onClick = { pendingRestore = null; vm.restore(Uri.parse(uri)) }) { Text("복원") } }) }
}
@Composable private fun SettingSwitch(title: String, checked: Boolean, change: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().minimumInteractiveComponentSize(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f)); Switch(checked, change) } }
