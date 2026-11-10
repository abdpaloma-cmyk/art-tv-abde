package com.marcosarte.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.max

private const val PICK_IMAGES = 4101
private const val PICK_FOLDER = 4102

data class Art(val name: String, val assetPath: String? = null, val uri: Uri? = null) {
    val title: String get() = name.substringBeforeLast('.')
        .replace('_', ' ').replace('-', ' ')
        .replace(Regex("^\\d+\\s*"), "").trim().ifBlank { "Obra sin título" }
}

class MainActivity : Activity() {
    private lateinit var artView: FrameView
    private var arts: List<Art> = emptyList()
    private var index = 0
    private var styleIndex = 0
    private var infoVisible = true
    private var autoAdvance = true
    private var fillMode = false
    private var randomOrder = false
    private var intervalMs = 60_000L
    private var loadToken = 0

    private val handler = Handler(Looper.getMainLooper())
    private val decoder: ExecutorService = Executors.newSingleThreadExecutor()
    private var pendingDecode: Future<*>? = null
    private val prefs by lazy { getSharedPreferences("marcos_arte", MODE_PRIVATE) }

    private val ticker = object : Runnable {
        override fun run() {
            if (autoAdvance && arts.isNotEmpty()) move(1)
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        artView = FrameView(this)
        setContentView(artView)

        styleIndex = prefs.getInt("style", 0).coerceIn(STYLES.indices)
        infoVisible = prefs.getBoolean("info_visible", true)
        fillMode = prefs.getBoolean("fill", false)
        autoAdvance = prefs.getBoolean("auto", true)
        randomOrder = prefs.getBoolean("random", false)
        intervalMs = prefs.getLong("interval", 60_000L).coerceIn(10_000L, 1_800_000L)

        artView.style = STYLES[styleIndex]
        artView.fill = fillMode
        artView.infoVisible = infoVisible
        reloadCollection()
    }

    override fun onResume() { super.onResume(); restartTimer() }
    override fun onPause() { handler.removeCallbacks(ticker); super.onPause() }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        pendingDecode?.cancel(true)
        decoder.shutdownNow()
        super.onDestroy()
    }

    private fun isImage(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in
            setOf("jpg", "jpeg", "png", "webp", "avif", "heic")

    private fun reloadCollection() {
        val found = mutableListOf<Art>()
        assets.list("obras")?.forEach { name ->
            if (isImage(name)) found += Art(name, assetPath = "obras/$name")
        }
        prefs.getStringSet("image_uris", emptySet()).orEmpty().forEach { raw ->
            runCatching {
                val uri = Uri.parse(raw)
                val name = displayName(uri) ?: "Imagen"
                if (isImage(name)) found += Art(name, uri = uri)
            }
        }
        prefs.getStringSet("tree_uris", emptySet()).orEmpty().forEach { raw ->
            runCatching { collectTree(Uri.parse(raw), found, 0) }
        }
        arts = found.distinctBy { it.uri?.toString() ?: it.assetPath }
            .sortedBy { it.name.lowercase() }
        if (arts.isEmpty()) {
            artView.bitmap = null
            artView.message = "Abre el menú para añadir pinturas"
            artView.invalidate()
        } else {
            index = prefs.getInt("index", 0).coerceIn(0, arts.lastIndex)
            showCurrent()
        }
        restartTimer()
    }

    private fun collectTree(treeUri: Uri, output: MutableList<Art>, depth: Int) {
        if (depth > 12 || output.size >= 20_000) return
        val documentId = runCatching { DocumentsContract.getDocumentId(treeUri) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext() && output.size < 20_000) {
                val id = cursor.getString(idCol) ?: continue
                val name = cursor.getString(nameCol) ?: continue
                val mime = cursor.getString(mimeCol) ?: ""
                val child = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collectTree(child, output, depth + 1)
                } else if (mime.startsWith("image/") || isImage(name)) {
                    output += Art(name, uri = child)
                }
            }
        }
    }

    private fun displayName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0)
        }
        return name
    }

    private fun openArt(art: Art): InputStream =
        if (art.assetPath != null) assets.open(art.assetPath)
        else contentResolver.openInputStream(art.uri ?: throw FileNotFoundException())

    private fun decode(art: Art): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openArt(art).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        openArt(art).use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: Throwable) { null }

    private fun showCurrent() {
        if (arts.isEmpty()) return
        val art = arts[index]
        val token = ++loadToken
        pendingDecode?.cancel(true)
        prefs.edit().putInt("index", index).apply()
        artView.message = "Cargando obra…"
        pendingDecode = decoder.submit {
            val bitmap = decode(art)
            runOnUiThread {
                if (token != loadToken || isFinishing || isDestroyed) {
                    bitmap?.takeUnless { it.isRecycled }?.recycle()
                    return@runOnUiThread
                }
                val previous = artView.bitmap
                artView.bitmap = bitmap
                if (previous != null && previous !== bitmap && !previous.isRecycled) previous.recycle()
                artView.infoText = art.title
                artView.message = if (bitmap == null) "No se pudo abrir: ${art.title}" else ""
                artView.invalidate()
            }
        }
    }

    private fun move(direction: Int) {
        if (arts.isEmpty()) return
        index = if (randomOrder && direction > 0 && arts.size > 1) {
            arts.indices.filter { it != index }.random()
        } else (index + direction + arts.size) % arts.size
        showCurrent()
    }

    private fun restartTimer() {
        handler.removeCallbacks(ticker)
        if (autoAdvance && arts.isNotEmpty()) handler.postDelayed(ticker, intervalMs)
    }

    private fun changeFrame(direction: Int) {
        styleIndex = (styleIndex + direction + STYLES.size) % STYLES.size
        artView.style = STYLES[styleIndex]
        artView.invalidate()
        prefs.edit().putInt("style", styleIndex).apply()
        toast(STYLES[styleIndex].name)
    }

    private fun toggleFit() {
        fillMode = !fillMode
        artView.fill = fillMode
        artView.invalidate()
        prefs.edit().putBoolean("fill", fillMode).apply()
        toast(if (fillMode) "Llenar pantalla" else "Ajustar con paspartú")
    }

    private fun toggleAuto() {
        autoAdvance = !autoAdvance
        prefs.edit().putBoolean("auto", autoAdvance).apply()
        restartTimer()
        toast(if (autoAdvance) "Cambio automático activado" else "Obra fija")
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun openMenu() {
        val entries = arrayOf(
            "Añadir imágenes (incluye proveedores en la nube)",
            "Añadir carpeta completa y subcarpetas",
            "Actualizar colección",
            "Tiempo entre obras",
            if (randomOrder) "Cambiar a orden alfabético" else "Cambiar a orden aleatorio",
            "Quitar fuentes añadidas",
            "Ayuda del mando"
        )
        AlertDialog.Builder(this).setTitle("Marcos de Arte · Colecciones")
            .setItems(entries) { _, which ->
                when (which) {
                    0 -> pickImages()
                    1 -> pickFolder()
                    2 -> reloadCollection()
                    3 -> chooseInterval()
                    4 -> {
                        randomOrder = !randomOrder
                        prefs.edit().putBoolean("random", randomOrder).apply()
                        toast(if (randomOrder) "Orden aleatorio" else "Orden alfabético")
                    }
                    5 -> clearSources()
                    6 -> showHelp()
                }
            }.show()
    }

    private fun pickImages() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_IMAGES)
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_FOLDER)
    }

    @Deprecated("Usa el selector de documentos Android para compatibilidad con proveedores")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            PICK_IMAGES -> {
                val selected = mutableListOf<Uri>()
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) selected += clip.getItemAt(i).uri
                } ?: data.data?.let { selected += it }
                val saved = prefs.getStringSet("image_uris", emptySet()).orEmpty().toMutableSet()
                selected.forEach { uri ->
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        saved += uri.toString()
                    }
                }
                prefs.edit().putStringSet("image_uris", saved).apply()
                reloadCollection()
                toast("${selected.size} imagen(es) seleccionada(s)")
            }
            PICK_FOLDER -> data.data?.let { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val trees = prefs.getStringSet("tree_uris", emptySet()).orEmpty().toMutableSet()
                trees += uri.toString()
                prefs.edit().putStringSet("tree_uris", trees).apply()
                reloadCollection()
            }
        }
    }

    private fun chooseInterval() {
        val labels = arrayOf("10 segundos", "30 segundos", "1 minuto", "2 minutos", "5 minutos", "10 minutos", "30 minutos")
        val values = longArrayOf(10_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000)
        val selected = values.indexOf(intervalMs).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Tiempo entre pinturas")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                intervalMs = values[which]
                prefs.edit().putLong("interval", intervalMs).apply()
                restartTimer()
                dialog.dismiss()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun clearSources() {
        AlertDialog.Builder(this).setTitle("Quitar fuentes añadidas")
            .setMessage("Se eliminarán las selecciones guardadas, no las imágenes incluidas en la aplicación.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Quitar") { _, _ ->
                prefs.edit().remove("image_uris").remove("tree_uris").apply()
                reloadCollection()
            }.show()
    }

    private fun showHelp() {
        AlertDialog.Builder(this).setTitle("Controles del mando")
            .setMessage(
                "← / →  Pintura anterior / siguiente\n" +
                "↑ / ↓  Cambiar marco\n" +
                "OK corto  Mostrar / ocultar título\n" +
                "OK mantenido  Ajustar / llenar\n" +
                "Play/Pausa o 1  Obra fija / automático\n" +
                "MENU  Colecciones y ajustes\n" +
                "Botón verde  Añadir imágenes\n" +
                "Botón azul  Añadir carpeta\n" +
                "Botón rojo  Ajustar / llenar"
            ).setPositiveButton("Cerrar", null).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> { move(1); restartTimer() }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { move(-1); restartTimer() }
            KeyEvent.KEYCODE_DPAD_UP -> changeFrame(1)
            KeyEvent.KEYCODE_DPAD_DOWN -> changeFrame(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> event.startTracking()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> openMenu()
            KeyEvent.KEYCODE_PROG_GREEN -> pickImages()
            KeyEvent.KEYCODE_PROG_BLUE -> pickFolder()
            KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_BUTTON_Y -> toggleFit()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_1 -> toggleAuto()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            toggleFit()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && !event.isCanceled) {
            infoVisible = !infoVisible
            artView.infoVisible = infoVisible
            artView.invalidate()
            prefs.edit().putBoolean("info_visible", infoVisible).apply()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
