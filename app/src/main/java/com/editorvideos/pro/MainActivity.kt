package com.editorvideos.pro

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Composition
import java.io.File
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import org.json.JSONArray
import org.json.JSONObject

private val Bg = Color(0xFF101218)
private val Panel = Color(0xFF191E29)
private val Accent = Color(0xFF8B78FF)
private val Mint = Color(0xFF49D6B4)
private val TextMain = Color(0xFFF1F2F8)
private val TextMuted = Color(0xFFA0A9BC)

data class EditClip(val uri: String, val name: String, val kind: String = "VIDEO", val startMs: Long = 0L, val endMs: Long = 0L)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VideoEditorApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("video_editor", 0) }
    val clips = remember { mutableStateListOf<EditClip>() }
    var selected by remember { mutableIntStateOf(-1) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var brightness by remember { mutableFloatStateOf(1f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var status by remember { mutableStateOf("Importa un vídeo para empezar un proyecto local.") }
    var showAbout by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }
    var exporting by remember { mutableStateOf(false) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }
    LaunchedEffect(Unit) {
        val saved = prefs.getString("project_json", null)
        if (!saved.isNullOrBlank()) runCatching {
            val arr = JSONArray(saved)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                clips.add(EditClip(o.getString("uri"), o.getString("name"), o.optString("kind", "VIDEO"), o.optLong("startMs", 0L), o.optLong("endMs", 0L)))
            }
            if (clips.isNotEmpty()) { selected = 0; status = "Proyecto local restaurado. Vuelve a seleccionar el medio si el permiso ya no está disponible." }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        if (uri == null) { status = "Exportación cancelada por el usuario." }
        if (uri != null) {
            val clip = clips.getOrNull(selected)
            if (clip == null) { status = "Importa y selecciona un vídeo antes de exportar." }
            else {
                val source = MediaItem.Builder().setUri(Uri.parse(clip.uri)).setClippingConfiguration(
                    ClippingConfiguration.Builder().setStartPositionMs(clip.startMs).apply { if (clip.endMs > clip.startMs) setEndPositionMs(clip.endMs) }.build()
                ).build()
                val out = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
                exporting = true
                val t = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        runCatching { context.contentResolver.openOutputStream(uri)?.use { output -> out.inputStream().use { input -> input.copyTo(output) } } ?: error("No se pudo abrir el destino") }
                            .onSuccess { status = "Exportación MP4 completada y guardada." }
                            .onFailure { status = "Renderizado completado, pero no se pudo copiar el archivo: ${it.message}" }
                        out.delete(); exporting = false; transformer = null
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        status = "Error al exportar: ${exportException.message ?: "formato no compatible"}"; out.delete(); exporting = false; transformer = null
                    }
                }).build()
                transformer = t
                status = "Exportando el clip seleccionado a MP4…"
                t.start(EditedMediaItem.Builder(source).build(), out.absolutePath)
            }
        }
    }
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release(); transformer?.cancel() } }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                    val clip = clips.getOrNull(selected)
                    if (clip != null && clip.endMs <= clip.startMs) trimEndMs = durationMs
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val importVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else "Vídeo importado"
                } ?: "Vídeo importado"
            }.getOrDefault("Vídeo importado")
            val newClip = EditClip(uri.toString(), name)
            clips.add(newClip)
            selected = clips.lastIndex
            trimStartMs = 0L; trimEndMs = 0L; durationMs = 0L
            player.setMediaItem(MediaItem.fromUri(uri)); player.prepare(); player.playWhenReady = false
            status = "Vídeo importado. La reproducción usa el reproductor nativo de Android."
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondary = Mint, background = Bg, surface = Panel, onBackground = TextMain, onSurface = TextMain)) {
        Scaffold(containerColor = Bg, topBar = {
            TopAppBar(title = { Column { Text("VIDEO STUDIO", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp); Text("EDITOR PROFESIONAL · ANDROID", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp) } }, actions = {
                IconButton(onClick = { showAbout = true }) { Icon(Icons.Default.Settings, "Acerca de") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = TextMain))
        }) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Mi proyecto", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Edición local · Sin suscripción", color = Mint, fontSize = 12.sp) }
                        Button(onClick = { importVideo.launch(arrayOf("video/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Importar") }
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF080B12), modifier = Modifier.fillMaxWidth().height(220.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (clips.isNotEmpty() && selected in clips.indices) {
                                PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
                            } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Movie, null, tint = Accent, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp)); Text("Tu estudio creativo", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Importa un vídeo para ver la previsualización", color = TextMuted, fontSize = 12.sp)
                            }
                            if (clips.isNotEmpty()) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC090B12)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play(); playing = player.isPlaying }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White) }
                                Text(clips.getOrNull(selected)?.name ?: "Vista previa", modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, color = Color.White)
                                Text(formatTime(player.currentPosition), color = Mint, fontSize = 11.sp)
                            }
                        }
                    }
                }
                item { SectionTitle("LÍNEA DE TIEMPO", "${clips.size} clip(s)") }
                if (clips.isEmpty()) item {
                    Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.FolderOpen, null, tint = TextMuted, modifier = Modifier.size(30.dp)); Text("Tu línea de tiempo está vacía", fontWeight = FontWeight.SemiBold); Text("Pulsa Importar para añadir el primer vídeo.", color = TextMuted, fontSize = 12.sp) } }
                }
                itemsIndexed(clips) { index, clip ->
                    Surface(color = if (selected == index) Color(0xFF292447) else Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().clickable { selected = index; trimStartMs = clip.startMs; trimEndMs = clip.endMs; player.setMediaItem(MediaItem.fromUri(Uri.parse(clip.uri))); player.prepare(); status = "Clip seleccionado: ${clip.name}" }) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF35305A)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Movie, null, tint = Accent) }
                            Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(clip.name, fontWeight = FontWeight.SemiBold, maxLines = 1); Text("V${index + 1} · vídeo local", color = TextMuted, fontSize = 11.sp) }
                            if (selected == index) Text("SELECCIONADO", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item { SectionTitle("EDICIÓN DE CLIP", if (selected in clips.indices) "CLIP ${selected + 1}" else "SIN SELECCIÓN") }
                item {
                    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Recorte no destructivo", fontWeight = FontWeight.Bold)
                            Text("Inicio: ${formatTime(trimStartMs)}", color = TextMuted, fontSize = 12.sp)
                            Slider(value = trimStartMs.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()), onValueChange = { trimStartMs = it.toLong().coerceAtMost((trimEndMs - 500).coerceAtLeast(0L)) }, valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(), enabled = selected in clips.indices && durationMs > 0)
                            Text("Fin: ${formatTime(if (trimEndMs > 0) trimEndMs else durationMs)}", color = TextMuted, fontSize = 12.sp)
                            Slider(value = (if (trimEndMs > 0) trimEndMs else durationMs).toFloat().coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()), onValueChange = { trimEndMs = it.toLong().coerceAtLeast(trimStartMs + 500).coerceAtMost(durationMs) }, valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(), enabled = selected in clips.indices && durationMs > 0)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val c = clips.getOrNull(selected)
                                    if (c != null && trimEndMs > trimStartMs) { clips[selected] = c.copy(startMs = trimStartMs, endMs = trimEndMs); status = "Recorte aplicado al proyecto. La exportación usa estos límites." }
                                }, modifier = Modifier.weight(1f), enabled = selected in clips.indices && trimEndMs > trimStartMs) { Text("Aplicar recorte") }
                                OutlinedButton(onClick = {
                                    val c = clips.getOrNull(selected)
                                    val end = if (c != null && c.endMs > c.startMs) c.endMs else durationMs
                                    val start = c?.startMs ?: 0L
                                    val cut = start + (end - start) / 2
                                    if (c != null && cut > start && end > cut) { clips[selected] = c.copy(endMs = cut); clips.add(selected + 1, c.copy(name = c.name + " (parte 2)", startMs = cut, endMs = end)); status = "Clip dividido en dos segmentos del proyecto." }
                                }, modifier = Modifier.weight(1f), enabled = selected in clips.indices && durationMs > 1000) { Text("Dividir al medio") }
                            }
                            OutlinedButton(onClick = { if (selected in clips.indices) { clips.removeAt(selected); selected = (selected - 1).coerceAtLeast(-1); status = "Clip quitado de la línea de tiempo; el archivo original permanece intacto." } }, enabled = selected in clips.indices, modifier = Modifier.fillMaxWidth()) { Text("Eliminar clip del proyecto") }
                        }
                    }
                }
                item { SectionTitle("HERRAMIENTAS", "PREVISUALIZACIÓN") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        ToolChip("Recortar", Icons.Default.ContentCut) { status = "Ajusta inicio y fin en el panel de edición de clip." }
                        ToolChip("Velocidad", Icons.Default.Speed) { status = "La velocidad afecta la previsualización; todavía no se aplica al renderizado exportado." }
                        ToolChip("Color", Icons.Default.Tune) { status = "Los controles de color son de previsualización y aún no se incorporan al MP4 final." }
                    }
                }
                item {
                    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Ajustes de previsualización", fontWeight = FontWeight.Bold)
                            SliderRow("Brillo", brightness, { brightness = it }, 0.5f..1.5f)
                            SliderRow("Contraste", contrast, { contrast = it }, 0.5f..1.5f)
                            SliderRow("Velocidad de reproducción", speed, { speed = it; player.setPlaybackSpeed(it) }, 0.25f..2f)
                            Text("Nota: brillo y contraste son controles demostrativos; aún no se aplican al renderizado exportado.", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            val json = JSONArray()
                            clips.forEach { c -> json.put(JSONObject().put("uri", c.uri).put("name", c.name).put("kind", c.kind).put("startMs", c.startMs).put("endMs", c.endMs)) }
                            prefs.edit().putString("project_json", json.toString()).apply()
                            status = "Proyecto guardado localmente. Se restaurará al abrir la aplicación."
                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Guardar proyecto") }
                        Button(onClick = { if (selected in clips.indices) exportPicker.launch("editor_video_${System.currentTimeMillis()}.mp4") else status = "Importa y selecciona un vídeo antes de exportar." }, modifier = Modifier.weight(1f), enabled = !exporting, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text(if (exporting) "Exportando…" else "Exportar MP4") }
                    }
                }
                item { Surface(color = Color(0xFF172A29), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) { Text("●", color = Mint); Spacer(Modifier.width(8.dp)); Column { Text("Estado", color = Mint, fontWeight = FontWeight.Bold); Text(status, color = TextMain, fontSize = 12.sp) } } } }
                item { Text("VERSIÓN 0.3.0 · ANDROID · ENFOQUE OFFLINE", color = TextMuted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth()) }
            }
        }
        if (showAbout) AlertDialog(onDismissRequest = { showAbout = false }, title = { Text("Editor de Videos Profesional") }, text = { Text("Prototipo nativo Android gratuito. Incluye selección y recorte temporal por clip, división lógica, guardado local y exportación MP4 de un clip seleccionado. No incluye aún composición multipista final ni filtros aplicados al renderizado.") }, confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Cerrar") } }, containerColor = Panel)
    }
}

@Composable private fun SectionTitle(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(title, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp); Text(detail, color = Accent, fontSize = 11.sp) }
}

@Composable private fun ToolChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp)) { Icon(icon, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(label) }
}

@Composable private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontSize = 12.sp); Text(if (label == "Velocidad de reproducción") "%.2fx".format(value) else "${(value * 100).toInt()}%", color = Accent, fontSize = 12.sp) }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) / 1000)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
