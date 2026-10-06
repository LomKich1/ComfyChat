package dev.comfychat

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val MAX_SCALE = 8f
private const val DOUBLE_TAP_SCALE = 3f

@Composable
fun ImageViewer(turn: Turn, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val file = turn.file ?: return
    val ctx = LocalContext.current
    val bitmap = rememberFileBitmap(file, 4096)

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var box by remember { mutableStateOf(IntSize.Zero) }
        var chrome by remember { mutableStateOf(true) }
        val img = remember(bitmap) { bitmap?.asImageBitmap() }

        fun clamp(o: Offset, s: Float): Offset {
            val mx = box.width * (s - 1f) / 2f
            val my = box.height * (s - 1f) / 2f
            return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { box = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { chrome = !chrome },
                        onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val c = Offset(box.width / 2f, box.height / 2f)
                                scale = DOUBLE_TAP_SCALE
                                offset = clamp((c - tap) * (DOUBLE_TAP_SCALE - 1f), DOUBLE_TAP_SCALE)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                        val c = Offset(box.width / 2f, box.height / 2f)
                        val rel = centroid - c
                        // зум вокруг точки между пальцами + перетаскивание
                        val moved = rel - (rel - offset) * (newScale / scale) + pan
                        scale = newScale
                        offset = clamp(moved, newScale)
                    }
                }
        ) {
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }

            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                    }

                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 8.dp)
                    ) {
                        Text(
                            turn.prompt,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState())
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ViewerAction("Сохранить", Color.White) {
                                toast(
                                    if (ImageActions.save(ctx, file)) "Сохранено в Pictures/ComfyChat"
                                    else "Не удалось сохранить"
                                )
                            }
                            ViewerAction("Копировать", Color.White) {
                                if (ImageActions.copyImage(ctx, file)) {
                                    // с Android 13 система сама показывает плашку
                                    if (Build.VERSION.SDK_INT < 33) toast("Скопировано")
                                } else toast("Не удалось скопировать")
                            }
                            ViewerAction("Поделиться", Color.White) {
                                runCatching { ImageActions.share(ctx, file) }
                                    .onFailure { toast("Не удалось поделиться") }
                            }
                            ViewerAction("Удалить", Color(0xFFFF8A80), onDelete)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerAction(label: String, color: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(label, fontSize = 13.sp)
    }
}
