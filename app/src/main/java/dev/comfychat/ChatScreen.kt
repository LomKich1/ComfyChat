package dev.comfychat

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val cs = MaterialTheme.colorScheme
    val url by vm.serverUrl.collectAsStateWithLifecycle()
    val mode by vm.themeMode.collectAsStateWithLifecycle()
    val seedFixed by vm.seedFixed.collectAsStateWithLifecycle()
    val seedValue by vm.seedValue.collectAsStateWithLifecycle()
    val running = vm.turns.lastOrNull()?.running == true
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var viewingId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    // главный экран слегка уезжает вправо, пока галерея наезжает слева
    val shift by animateFloatAsState(
        targetValue = if (showGallery) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "shift"
    )

    BackHandler(enabled = showGallery) { showGallery = false }

    LaunchedEffect(vm.turns.size) {
        if (vm.turns.isNotEmpty()) listState.animateScrollToItem(vm.turns.lastIndex)
    }

    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = shift * size.width * 0.12f }
                .systemBarsPadding()
                .imePadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showGallery = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Галерея", tint = cs.onSurfaceVariant)
                }
                Text("ComfyChat", style = MaterialTheme.typography.titleLarge, color = cs.onBackground)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = cs.onSurfaceVariant)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (vm.turns.isEmpty()) {
                    Text(
                        "Что нарисуем?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(vm.turns, key = { it.id }) { t ->
                            TurnItem(
                                t,
                                seen = vm.animated,
                                onOpen = { viewingId = it },
                                onRetry = vm::retry,
                                onDelete = { confirmDeleteId = it }
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(24.dp),
                color = cs.surface,
                border = BorderStroke(1.dp, cs.outline)
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Опиши картинку по-русски…") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(onClick = { vm.cycleSize() }, label = { Text(vm.size.label) })
                        Spacer(Modifier.weight(1f))
                        FilledIconButton(
                            onClick = {
                                if (running) vm.stop() else {
                                    vm.send(input)
                                    input = ""
                                }
                            },
                            enabled = running || input.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = cs.primary,
                                contentColor = cs.onPrimary
                            )
                        ) {
                            Icon(
                                if (running) Icons.Filled.Close else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (running) "Стоп" else "Отправить"
                            )
                        }
                    }
                }
            }
        }

        // затемнение главного экрана под галереей
        AnimatedVisibility(
            visible = showGallery,
            enter = fadeIn(tween(380)),
            exit = fadeOut(tween(300))
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }

        // галерея наезжает слева
        AnimatedVisibility(
            visible = showGallery,
            enter = slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it },
            exit = slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it }
        ) {
            GalleryScreen(
                turns = vm.turns,
                onOpen = { viewingId = it },
                onDeleteMany = vm::deleteMany,
                onClose = { showGallery = false }
            )
        }
    }

    viewingId?.let { id ->
        vm.turns.firstOrNull { it.id == id }?.let { t ->
            ImageViewer(t, onDismiss = { viewingId = null }, onDelete = { confirmDeleteId = id })
        }
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Удалить?") },
            text = { Text("Сообщение и картинка будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(id)
                    if (viewingId == id) viewingId = null
                    confirmDeleteId = null
                }) { Text("Удалить", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text("Отмена") } }
        )
    }

    if (showSettings) {
        SettingsDialog(
            url = url,
            mode = mode,
            seedFixed = seedFixed,
            seedValue = seedValue,
            onSave = { u, m, f, sd ->
                vm.saveSettings(u, m, f, sd)
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }
}

/** Плавный выезд снизу с пружиной. Играет один раз на ключ, потом сразу рисуется на месте. */
@Composable
private fun SlideIn(
    key: String,
    seen: MutableSet<String>,
    delayMs: Long,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(if (key in seen) 0f else 1f) }
    val distance = with(LocalDensity.current) { 360.dp.toPx() }
    LaunchedEffect(Unit) {
        val play = progress.value > 0f
        seen.add(key)
        if (play) {
            delay(delayMs)
            progress.animateTo(
                0f,
                spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }
    Box(
        Modifier.graphicsLayer {
            translationY = progress.value * distance
            alpha = (1f - progress.value * 1.5f).coerceIn(0f, 1f)
        }
    ) { content() }
}

@Composable
private fun TurnItem(
    t: Turn,
    seen: MutableSet<String>,
    onOpen: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column {
        SlideIn("u${t.id}", seen, 0L) { UserBubble(t.prompt, onDelete = { onDelete(t.id) }) }
        Spacer(Modifier.height(12.dp))
        SlideIn("b${t.id}", seen, 150L) {
            Column {
                ImageCard(t, onOpen)
                if (!t.running && t.error != null && t.file == null) {
                    TextButton(onClick = { onRetry(t.id) }) { Text("Повторить") }
                }
                if (t.running) {
                    Text(
                        t.stage,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        color = cs.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                t.tags?.let { tags ->
                    var open by remember(t.id) { mutableStateOf(false) }
                    Text(
                        tags,
                        modifier = Modifier
                            .padding(top = 8.dp, start = 4.dp)
                            .clickable { open = !open },
                        color = cs.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = if (open) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Пузырь пользователя: зажатие подсвечивает его и открывает меню (как в Telegram). */
@Composable
private fun UserBubble(text: String, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current
    var pressed by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val color by animateColorAsState(
        if (pressed || menu) cs.outline else cs.surfaceVariant,
        label = "bubble"
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = color,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu = true
                            }
                        )
                    }
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = cs.onSurface
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Копировать") },
                    onClick = {
                        clipboard.setText(AnnotatedString(text))
                        menu = false
                        // с Android 13 система сама показывает плашку о копировании
                        if (Build.VERSION.SDK_INT < 33) {
                            Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Удалить", color = cs.error) },
                    onClick = {
                        menu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun ImageCard(t: Turn, onOpen: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fileBmp = rememberFileBitmap(t.file, 1280)
    val shown = fileBmp ?: t.preview
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(t.size.w.toFloat() / t.size.h)
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .clickable(enabled = t.file != null) { onOpen(t.id) }
    ) {
        if (shown != null) {
            val img = remember(shown) { shown.asImageBitmap() }
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else if (t.file == null) {
            Text(
                t.error ?: t.stage,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                color = if (t.error != null) cs.error else cs.onSurfaceVariant
            )
        }
        val p = t.percent
        if (t.running && p != null) {
            Text(
                "$p%",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
    }
    val err = t.error
    if (shown != null && err != null) {
        Text(err, color = cs.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
    }
}

@Composable
private fun SettingsDialog(
    url: String,
    mode: ThemeMode,
    seedFixed: Boolean,
    seedValue: String,
    onSave: (String, ThemeMode, Boolean, String) -> Unit,
    onDismiss: () -> Unit
) {
    var u by remember { mutableStateOf(url) }
    var m by remember { mutableStateOf(mode) }
    var fixed by remember { mutableStateOf(seedFixed) }
    var seed by remember { mutableStateOf(seedValue) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var searching by remember { mutableStateOf(false) }
    var findMsg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = u,
                    onValueChange = { u = it },
                    label = { Text("Адрес ComfyUI") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !searching,
                        onClick = {
                            searching = true
                            findMsg = null
                            scope.launch {
                                val found = LanDiscovery.find(LanDiscovery.portOf(normalizeUrl(u)))
                                searching = false
                                if (found != null) {
                                    u = found
                                    findMsg = "Нашёл: $found"
                                } else {
                                    findMsg = "Не нашёл. Проверь, что ComfyUI запущен с --listen 0.0.0.0, а VPN не режет локалку"
                                }
                            }
                        }
                    ) { Text(if (searching) "Ищу…" else "Найти ПК в сети") }
                    TextButton(onClick = {
                        clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { u = it }
                    }) { Text("Вставить") }
                }
                findMsg?.let { Text(it, fontSize = 13.sp) }
                Text("Тема")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.AUTO to "Авто",
                        ThemeMode.LIGHT to "Светлая",
                        ThemeMode.DARK to "Тёмная"
                    ).forEach { (k, label) ->
                        FilterChip(selected = m == k, onClick = { m = k }, label = { Text(label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Фиксированный сид", modifier = Modifier.weight(1f))
                    Switch(checked = fixed, onCheckedChange = { fixed = it })
                }
                if (fixed) {
                    OutlinedTextField(
                        value = seed,
                        onValueChange = { v -> seed = v.filter { it.isDigit() }.take(18) },
                        label = { Text("Сид") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { seed = Random.nextLong(0L, 1L shl 50).toString() }) {
                        Text("Случайный")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = if (fixed && seed.isBlank()) Random.nextLong(0L, 1L shl 50).toString() else seed
                onSave(u, m, fixed, s)
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
