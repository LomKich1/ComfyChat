package dev.comfychat

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val cs = MaterialTheme.colorScheme
    val url by vm.serverUrl.collectAsStateWithLifecycle()
    val mode by vm.themeMode.collectAsStateWithLifecycle()
    val running = vm.turns.lastOrNull()?.running == true
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Bitmap?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(vm.turns.size) {
        if (vm.turns.isNotEmpty()) listState.animateScrollToItem(vm.turns.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                        TurnItem(t, seen = vm.animated, onOpen = { viewing = it }, onRetry = vm::retry)
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

    viewing?.let { bmp -> ImageViewer(bmp, onDismiss = { viewing = null }) }

    if (showSettings) {
        SettingsDialog(
            url = url,
            mode = mode,
            onSave = { u, m ->
                vm.saveSettings(u, m)
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
    onOpen: (Bitmap) -> Unit,
    onRetry: (Long) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column {
        SlideIn("u${t.id}", seen, 0L) { UserBubble(t.prompt) }
        Spacer(Modifier.height(12.dp))
        SlideIn("b${t.id}", seen, 150L) {
            Column {
                ImageCard(t, onOpen)
                if (!t.running && t.error != null && t.result == null) {
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

/** Пузырь пользователя: зажатие подсвечивает его и открывает меню «Копировать» (как в Telegram). */
@Composable
private fun UserBubble(text: String) {
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
            }
        }
    }
}

@Composable
private fun ImageCard(t: Turn, onOpen: (Bitmap) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shown = t.result ?: t.preview
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(t.size.w.toFloat() / t.size.h)
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .clickable(enabled = t.result != null) { t.result?.let(onOpen) }
    ) {
        if (shown != null) {
            val img = remember(shown) { shown.asImageBitmap() }
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
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
    onSave: (String, ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    var u by remember { mutableStateOf(url) }
    var m by remember { mutableStateOf(mode) }
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
            }
        },
        confirmButton = { TextButton(onClick = { onSave(u, m) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
