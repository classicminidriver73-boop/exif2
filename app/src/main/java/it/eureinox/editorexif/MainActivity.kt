package it.eureinox.editorexif

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Xml
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.text.DecimalFormat
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min

data class Photo(val uri: Uri, val name: String, val bytes: Long, val mime: String?)
data class LensRow(val model: String, val focal: String, val equivalent: String, val aperture: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { EditorScreen() } }
    }
}

@Composable
fun EditorScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("editor_exif", Context.MODE_PRIVATE) }
    var folder by remember { mutableStateOf(prefs.getString("folder", null)?.let(Uri::parse)) }
    var destination by remember { mutableStateOf<Uri?>(null) }
    var destinationMode by remember { mutableStateOf("move") }
    var photos by remember { mutableStateOf(listOf<Photo>()) }
    var selected by remember { mutableStateOf(setOf<Uri>()) }
    var current by remember { mutableStateOf<Photo?>(null) }
    var sort by remember { mutableStateOf(prefs.getString("sort", "Nome") ?: "Nome") }
    var ascending by remember { mutableStateOf(prefs.getBoolean("ascending", true)) }
    var lenses by remember { mutableStateOf(listOf<LensRow>()) }
    var lensIndex by remember { mutableIntStateOf(0) }
    var aperture by remember { mutableStateOf("") }
    var focal by remember { mutableStateOf("") }
    var equivalent by remember { mutableStateOf("") }
    var exifText by remember { mutableStateOf("Seleziona una fotografia") }
    var message by remember { mutableStateOf("") }
    var showEdit by remember { mutableStateOf(false) }
    var showViewer by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var prefix by remember { mutableStateOf("foto_") }
    var number by remember { mutableStateOf("1") }
    var busy by remember { mutableStateOf(false) }

    fun reload(uri: Uri) {
        folder = uri
        prefs.edit().putString("folder", uri.toString()).apply()
        val root = DocumentFile.fromTreeUri(ctx, uri)
        photos = root?.listFiles()?.filter { it.isFile && (it.type?.startsWith("image/") == true || it.name.orEmpty().matches(Regex("(?i).*\\.(jpg|jpeg|png|tif|tiff|webp|dng|nef|arw|cr2|cr3)$"))) }
            ?.map { Photo(it.uri, it.name ?: "immagine", it.length(), it.type) } ?: emptyList()
        selected = emptySet(); current = null
        message = "${photos.size} immagini caricate"
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            reload(uri)
        }
    }
    val destPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            destination = uri
        }
    }
    val excelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { lenses = withContext(Dispatchers.IO) { readXlsx(ctx, uri) }; message = "${lenses.size} obiettivi importati" }
            catch (e: Exception) { message = "Errore Excel: ${e.message}" }
            busy = false
        }
    }

    val ordered = remember(photos, sort, ascending) {
        val list = when (sort) {
            "Dimensione" -> photos.sortedBy { it.bytes }
            "Estensione" -> photos.sortedBy { it.name.substringAfterLast('.', "").lowercase() }
            else -> photos.sortedBy { it.name.lowercase() }
        }
        if (ascending) list else list.reversed()
    }
    val wide = LocalConfiguration.current.screenWidthDp >= 700

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { folderPicker.launch(folder) }) { Text("Cartella") }
            Button(onClick = { excelPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")) }) { Text("Excel") }
            Button(onClick = { sort = if (sort == "Nome") "Dimensione" else if (sort == "Dimensione") "Estensione" else "Nome"; prefs.edit().putString("sort", sort).apply() }) { Text("Ordina: $sort") }
            TextButton(onClick = { ascending = !ascending; prefs.edit().putBoolean("ascending", ascending).apply() }) { Text(if (ascending) "↑" else "↓") }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.weight(1f)) {
            LazyVerticalGrid(columns = GridCells.Adaptive(if (wide) 140.dp else 112.dp), modifier = Modifier.weight(if (wide) 1.4f else 1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ordered, key = { it.uri.toString() }) { p ->
                    Card(colors = CardDefaults.cardColors(containerColor = if (p.uri in selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.clickable {
                            current = p
                            selected = if (p.uri in selected) selected - p.uri else selected + p.uri
                            scope.launch { exifText = readExif(ctx, p) }
                        }.padding(5.dp)) {
                            AsyncImage(p.uri, contentDescription = p.name, Modifier.fillMaxWidth().height(if (wide) 112.dp else 88.dp), contentScale = ContentScale.Crop)
                            Text(p.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Text(formatBytes(p.bytes), style = MaterialTheme.typography.labelSmall)
                            if (p.uri in selected) Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { current = p; showViewer = true }) { Text("Apri") }
                        }
                    }
                }
            }
            if (wide) {
                VerticalDivider(Modifier.fillMaxHeight().padding(horizontal = 8.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text("Dati EXIF", style = MaterialTheme.typography.titleMedium)
                    Text(current?.name ?: "Nessuna foto")
                    Text(exifText, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showEdit = true }, enabled = current != null) { Text("Modifica EXIF") }
                }
            }
        }
        if (!wide && current != null) {
            Text(current!!.name, style = MaterialTheme.typography.labelMedium)
            Text(exifText, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            TextButton(onClick = { showEdit = true }) { Text("Modifica EXIF") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { selected = ordered.map { it.uri }.toSet() }) { Text("Seleziona tutte") }
            Button(onClick = { showExport = true }, enabled = selected.isNotEmpty()) { Text("Esporta JPG") }
            Button(onClick = { destinationMode = "move"; destPicker.launch(null) }, enabled = selected.isNotEmpty()) { Text("Sposta") }
            Button(onClick = {
                val targets = selected.ifEmpty { setOfNotNull(current?.uri) }
                scope.launch {
                    val count = withContext(Dispatchers.IO) { targets.count { DocumentFile.fromSingleUri(ctx, it)?.delete() == true } }
                    photos = photos.filterNot { it.uri in targets }; selected = emptySet(); current = null
                    message = "$count file eliminati"
                }
            }, enabled = selected.isNotEmpty() || current != null) { Text("Elimina") }
        }
        Text("${photos.size} immagini · ${selected.size} selezionate${if (busy) " · Operazione in corso…" else ""}", style = MaterialTheme.typography.labelSmall)
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.labelSmall)
    }

    LaunchedEffect(destination) {
        val dest = destination ?: return@LaunchedEffect
        val root = DocumentFile.fromTreeUri(ctx, dest)
        val targets = selected.toList()
        val result = withContext(Dispatchers.IO) {
            var count = 0
            var next = number.toIntOrNull() ?: 1
            targets.forEach { uri ->
                val photo = photos.find { it.uri == uri } ?: return@forEach
                if (destinationMode == "export") {
                    val bitmap = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@forEach
                    val ratio = min(2000f / bitmap.width, 2000f / bitmap.height).coerceAtMost(1f)
                    val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                    val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                    val scaled = if (w != bitmap.width || h != bitmap.height) Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
                    val fileName = "${prefix}${next.toString().padStart(4, '0')}.jpg"
                    val target = root?.createFile("image/jpeg", fileName) ?: return@forEach
                    val ok = ctx.contentResolver.openOutputStream(target.uri)?.use { scaled.compress(Bitmap.CompressFormat.JPEG, 70, it) } ?: false
                    if (ok) { count++; next++ } else target.delete()
                    if (scaled !== bitmap) scaled.recycle()
                    bitmap.recycle()
                } else {
                    val target = root?.createFile(photo.mime ?: "image/jpeg", photo.name) ?: return@forEach
                    val copied = try {
                        ctx.contentResolver.openInputStream(uri)?.use { input -> ctx.contentResolver.openOutputStream(target.uri)?.use { output -> input.copyTo(output) } }
                        true
                    } catch (_: Exception) { target.delete(); false }
                    if (copied && DocumentFile.fromSingleUri(ctx, uri)?.delete() == true) count++
                }
            }
            next
        }
        if (destinationMode == "move") photos = photos.filterNot { p -> targets.contains(p.uri) }
        else number = result.toString()
        selected = emptySet(); destination = null
        message = if (destinationMode == "move") "Spostamento completato" else "Esportazione completata; prossimo numero: $number"
    }

    if (showEdit && current != null) {
        AlertDialog(onDismissRequest = { showEdit = false }, title = { Text("Modifica dati EXIF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Importa prima il file Excel per scegliere un obiettivo.")
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }) { Text(lenses.getOrNull(lensIndex)?.model ?: "Seleziona obiettivo") }
                        DropdownMenu(expanded, { expanded = false }) {
                            lenses.forEachIndexed { i, l -> DropdownMenuItem(text = { Text(l.model) }, onClick = {
                                lensIndex = i; focal = l.focal; equivalent = l.equivalent; aperture = l.aperture; expanded = false
                            }) }
                        }
                    }
                    OutlinedTextField(focal, { focal = it }, label = { Text("Focale (mm)") })
                    OutlinedTextField(equivalent, { equivalent = it }, label = { Text("Focale equivalente 35 mm") })
                    OutlinedTextField(aperture, { aperture = it }, label = { Text("Diaframma (es. 2.8)") })
                }
            },
            confirmButton = { TextButton(onClick = {
                val p = current!!
                scope.launch {
                    busy = true
                    message = withContext(Dispatchers.IO) { writeExif(ctx, p, lenses.getOrNull(lensIndex)?.model.orEmpty(), focal, equivalent, aperture) }
                    exifText = readExif(ctx, p); busy = false; showEdit = false
                }
            }) { Text("Scrivi EXIF") } },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("Annulla") } })
    }

    if (showExport) AlertDialog(onDismissRequest = { showExport = false }, title = { Text("Esporta JPG") },
        text = { Column {
            OutlinedTextField(prefix, { prefix = it }, label = { Text("Prefisso") })
            OutlinedTextField(number, { number = it }, label = { Text("Numero iniziale") })
            Text("Scegli la cartella di destinazione dopo aver confermato.")
        } },
        confirmButton = { TextButton(onClick = { showExport = false; destinationMode = "export"; destPicker.launch(null) }) { Text("Scegli cartella") } },
        dismissButton = { TextButton(onClick = { showExport = false }) { Text("Annulla") } })

    if (showViewer && current != null) PhotoViewer(current!!, onClose = { showViewer = false }, onNext = { delta ->
        val i = ordered.indexOfFirst { it.uri == current?.uri }
        val next = ordered.getOrNull((i + delta).coerceIn(0, ordered.lastIndex))
        if (next != null) current = next
    })
}

@Composable
fun PhotoViewer(photo: Photo, onClose: () -> Unit, onNext: (Int) -> Unit) {
    var scale by remember(photo.uri) { mutableFloatStateOf(1f) }
    Dialog(onDismissRequest = onClose) {
        var drag by remember { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(photo.uri) {
            detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f) }
        }.pointerInput(photo.uri) {
            detectHorizontalDragGestures(onHorizontalDrag = { _, amount -> drag += amount }, onDragEnd = {
                if (drag > 70) onNext(-1) else if (drag < -70) onNext(1)
                drag = 0f
            })
        }, contentAlignment = Alignment.Center) {
            AsyncImage(photo.uri, photo.name, Modifier.fillMaxWidth().fillMaxHeight().graphicsLayer { scaleX = scale; scaleY = scale }, contentScale = ContentScale.Fit)
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) { Text("Chiudi") }
            Text("${(scale * 100).toInt()}%", modifier = Modifier.align(Alignment.BottomCenter), color = Color.White)
        }
    }
}

suspend fun readExif(context: Context, p: Photo): String = withContext(Dispatchers.IO) {
    try {
        val exif = context.contentResolver.openInputStream(p.uri)?.use { ExifInterface(it) } ?: return@withContext "EXIF non disponibile"
        listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL to "Data scatto",
            ExifInterface.TAG_EXPOSURE_TIME to "Esposizione",
            ExifInterface.TAG_F_NUMBER to "Diaframma",
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to "ISO",
            ExifInterface.TAG_FOCAL_LENGTH to "Focale",
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM to "Equivalente 35 mm",
            ExifInterface.TAG_LENS_MODEL to "Obiettivo",
            ExifInterface.TAG_MAKE to "Marca",
            ExifInterface.TAG_MODEL to "Modello",
            ExifInterface.TAG_FLASH to "Flash"
        ).mapNotNull { (tag, label) -> exif.getAttribute(tag)?.let { "$label: $it" } }.joinToString("\n").ifBlank { "Nessun dato EXIF" }
    } catch (e: Exception) { "Lettura EXIF non riuscita: ${e.message}" }
}

suspend fun writeExif(context: Context, p: Photo, model: String, focal: String, eq: String, aperture: String): String {
    return try {
        val temp = File(context.cacheDir, "exif_edit_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(p.uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
        val exif = ExifInterface(temp.absolutePath)
        if (model.isNotBlank()) exif.setAttribute(ExifInterface.TAG_LENS_MODEL, model)
        focal.toDoubleOrNull()?.let { exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "${(it*100).toInt()}/100") }
        eq.toIntOrNull()?.let { exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, it.toString()) }
        aperture.replace(",", ".").toDoubleOrNull()?.let { exif.setAttribute(ExifInterface.TAG_F_NUMBER, "${(it*100).toInt()}/100") }
        exif.saveAttributes()
        context.contentResolver.openOutputStream(p.uri, "wt")?.use { out -> temp.inputStream().use { it.copyTo(out) } }
        temp.delete()
        "Dati EXIF aggiornati"
    } catch (e: Exception) { "Errore scrittura EXIF: ${e.message}" }
}

fun readXlsx(context: Context, uri: Uri): List<LensRow> {
    val temp = File(context.cacheDir, "lenses.xlsx")
    context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
    ZipFile(temp).use { zip ->
        val strings = mutableListOf<String>()
        zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
            val parser = Xml.newPullParser(); parser.setInput(zip.getInputStream(entry), "UTF-8")
            var inSi = false; var value = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "si") { inSi = true; value = StringBuilder() }
                else if (event == XmlPullParser.START_TAG && parser.name == "t" && inSi) value.append(parser.nextText())
                else if (event == XmlPullParser.END_TAG && parser.name == "si") { strings.add(value.toString()); inSi = false }
                event = parser.next()
            }
        }
        val sheet = zip.getEntry("xl/worksheets/sheet1.xml") ?: return emptyList()
        val parser = Xml.newPullParser(); parser.setInput(zip.getInputStream(sheet), "UTF-8")
        val rows = mutableListOf<List<String>>(); var row = mutableMapOf<Int,String>(); var col = 0; var type = ""; var cellValue = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "row") row = mutableMapOf()
            if (event == XmlPullParser.START_TAG && parser.name == "c") {
                val ref = parser.getAttributeValue(null, "r") ?: "A1"
                col = ref.takeWhile { it.isLetter() }.fold(0) { a, c -> a*26 + (c.uppercaseChar()-'A'+1) } - 1
                type = parser.getAttributeValue(null, "t") ?: ""; cellValue = ""
            }
            if (event == XmlPullParser.START_TAG && parser.name == "v") cellValue = parser.nextText()
            if (event == XmlPullParser.END_TAG && parser.name == "c") {
                val value = if (type == "s") strings.getOrNull(cellValue.toIntOrNull() ?: -1).orEmpty() else cellValue
                row[col] = value
            }
            if (event == XmlPullParser.END_TAG && parser.name == "row") rows.add((0..3).map { row[it].orEmpty() })
            event = parser.next()
        }
        return rows.drop(1).filter { it[0].isNotBlank() }.map { LensRow(it[0],it[1],it[2],it[3]) }
    }
}

fun formatBytes(b: Long): String = if (b >= 1024*1024) "${DecimalFormat("0.00").format(b / 1024.0 / 1024.0)} MB" else "${b/1024} KB"
