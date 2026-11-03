package com.okur.reader.share

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.okur.reader.data.BookRepository
import com.okur.reader.data.ImportResult
import com.okur.reader.data.db.BookEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Yakındaki paylaşım için gereken çalışma zamanı izinleri (Android sürümüne göre). */
object NearbyPermissions {
    val required: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            )
            Build.VERSION.SDK_INT >= 31 -> arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
            Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            else -> arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    fun granted(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

data class NearbyDevice(val id: String, val name: String)

data class IncomingRequest(val endpointId: String, val name: String, val code: String)

data class TransferItem(
    val title: String,
    val transferred: Long = 0,
    val total: Long = 0,
    val done: Boolean = false,
    val failed: Boolean = false,
    val note: String? = null,
    val bookId: String? = null,
)

enum class NearbyPhase { IDLE, SEARCHING, VISIBLE, CONNECTING, AWAITING_ACCEPT, TRANSFERRING, DONE, FAILED }

data class NearbyUiState(
    val phase: NearbyPhase = NearbyPhase.IDLE,
    val devices: List<NearbyDevice> = emptyList(),
    val peerName: String? = null,
    val code: String? = null,
    val incoming: IncomingRequest? = null,
    val items: List<TransferItem> = emptyList(),
    val message: String? = null,
)

/**
 * Okur'dan Okur'a kitap gönderme (Google Nearby Connections: Bluetooth + Wi-Fi, internetsiz).
 *
 * Her kitap için gönderilenler:
 *  1. Başlık (BYTES): {"t":"book","m":manifestId,"f":fileId,"c":coverId,"title":..,"size":..}
 *  2. Manifest (FILE): başlık, yazar, biçim, yer imleri ve notlar (JSON)
 *  3. Kapak (FILE, varsa) ve kitap dosyası (FILE)
 * Sonunda {"t":"end","n":adet} gönderilir.
 */
class NearbyShareManager(
    private val context: Context,
    private val repo: BookRepository,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val SERVICE_ID = "com.okur.reader.share"
    }

    private val client by lazy { Nearby.getConnectionsClient(context) }
    private val strategy = Strategy.P2P_POINT_TO_POINT

    private val _state = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = _state

    private var myName = ""
    private var connectedEndpoint: String? = null

    // Gönderen tarafı
    private var outgoing: List<BookEntity> = emptyList()
    private var includeNotes = true
    private val sendIndex = HashMap<Long, Int>()
    private val tempFiles = mutableListOf<File>()

    // Alan tarafı
    private class IncomingBook(val index: Int, val manifestId: Long, val fileId: Long, val coverId: Long, var finalized: Boolean = false)
    private val incomingBooks = mutableListOf<IncomingBook>()
    private val receivedFiles = HashMap<Long, Payload>()
    private val completedPayloads = HashSet<Long>()
    private var expectedCount = -1

    // ------------------------------------------------------------------ Başlatma

    fun startReceiving(name: String) {
        reset()
        myName = name
        _state.value = NearbyUiState(phase = NearbyPhase.VISIBLE)
        client.startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { fail("Görünür olunamadı: ${it.readable()}") }
    }

    fun startSending(books: List<BookEntity>, withNotes: Boolean, name: String) {
        reset()
        myName = name
        outgoing = books
        includeNotes = withNotes
        _state.value = NearbyUiState(phase = NearbyPhase.SEARCHING, items = books.map { TransferItem(it.title, total = it.fileSize) })
        client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { fail("Yakındaki cihazlar aranamadı: ${it.readable()}") }
    }

    fun connect(device: NearbyDevice) {
        _state.update { it.copy(phase = NearbyPhase.CONNECTING, peerName = device.name, message = null) }
        client.requestConnection(myName, device.id, lifecycle)
            .addOnFailureListener { fail("Bağlantı kurulamadı: ${it.readable()}") }
    }

    fun acceptIncoming() {
        val req = _state.value.incoming ?: return
        client.acceptConnection(req.endpointId, payloads)
        _state.update { it.copy(incoming = null, peerName = req.name, code = req.code, phase = NearbyPhase.CONNECTING) }
    }

    fun rejectIncoming() {
        val req = _state.value.incoming ?: return
        client.rejectConnection(req.endpointId)
        _state.update { it.copy(incoming = null, phase = NearbyPhase.VISIBLE) }
    }

    fun stop() {
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        reset()
        _state.value = NearbyUiState()
    }

    private fun reset() {
        connectedEndpoint = null
        sendIndex.clear()
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
        incomingBooks.clear()
        receivedFiles.values.forEach { deleteReceived(it) }
        receivedFiles.clear()
        completedPayloads.clear()
        expectedCount = -1
    }

    private fun fail(message: String) {
        _state.update { it.copy(phase = NearbyPhase.FAILED, message = message) }
    }

    private fun Exception.readable(): String = message?.substringAfter(": ")?.take(120) ?: javaClass.simpleName

    // ------------------------------------------------------------------ Keşif ve bağlantı

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _state.update { s ->
                if (s.devices.any { it.id == endpointId }) s
                else s.copy(devices = s.devices + NearbyDevice(endpointId, info.endpointName))
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _state.update { s -> s.copy(devices = s.devices.filterNot { it.id == endpointId }) }
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (info.isIncomingConnection) {
                // Alan taraf: kullanıcı onayı gerekir
                _state.update {
                    it.copy(
                        phase = NearbyPhase.AWAITING_ACCEPT,
                        incoming = IncomingRequest(endpointId, info.endpointName, info.authenticationDigits),
                    )
                }
            } else {
                // Gönderen taraf: kodu göster, karşı tarafın onayını bekle
                client.acceptConnection(endpointId, payloads)
                _state.update { it.copy(code = info.authenticationDigits, phase = NearbyPhase.CONNECTING) }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpoint = endpointId
                _state.update { it.copy(phase = NearbyPhase.TRANSFERRING) }
                if (outgoing.isNotEmpty()) {
                    runCatching { client.stopDiscovery() }
                    scope.launch(Dispatchers.IO) { sendAll(endpointId) }
                } else {
                    runCatching { client.stopAdvertising() }
                }
            } else {
                val reason = if (result.status.statusCode == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED) {
                    "Karşı taraf gönderimi reddetti."
                } else {
                    "Bağlantı kurulamadı."
                }
                fail(reason)
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoint = null
            val s = _state.value
            if (s.phase == NearbyPhase.TRANSFERRING && s.items.any { !it.done && !it.failed }) {
                fail("Bağlantı koptu. Cihazları birbirine yaklaştırıp tekrar dene.")
            }
        }
    }

    // ------------------------------------------------------------------ Gönderme

    private suspend fun sendAll(endpointId: String) {
        try {
            outgoing.forEachIndexed { index, book ->
                val file = File(book.filePath)
                val filePayload = Payload.fromFile(file)
                val coverPayload = book.coverPath?.let { File(it) }?.takeIf { it.exists() }?.let { Payload.fromFile(it) }

                val manifest = JSONObject().apply {
                    put("title", book.title)
                    put("author", book.author)
                    put("format", book.format)
                    put("originalName", book.originalName)
                    put("fileSize", book.fileSize)
                    book.language?.let { put("language", it) }
                    if (includeNotes) {
                        val arr = JSONArray()
                        repo.bookmarksOnce(book.id).forEach { b ->
                            arr.put(
                                JSONObject()
                                    .put("positionIndex", b.positionIndex)
                                    .put("positionFraction", b.positionFraction.toDouble())
                                    .put("progress", b.progress.toDouble())
                                    .put("label", b.label)
                                    .put("snippet", b.snippet)
                                    .put("note", b.note)
                                    .put("createdAt", b.createdAt)
                            )
                        }
                        put("bookmarks", arr)
                    }
                }
                val manifestFile = File(context.cacheDir, "okur-share-${System.nanoTime()}.json").apply { writeText(manifest.toString()) }
                tempFiles += manifestFile
                val manifestPayload = Payload.fromFile(manifestFile)

                val header = JSONObject()
                    .put("t", "book")
                    .put("m", manifestPayload.id)
                    .put("f", filePayload.id)
                    .put("c", coverPayload?.id ?: -1L)
                    .put("title", book.title)
                    .put("size", book.fileSize)
                sendIndex[filePayload.id] = index

                client.sendPayload(endpointId, Payload.fromBytes(header.toString().toByteArray()))
                client.sendPayload(endpointId, manifestPayload)
                coverPayload?.let { client.sendPayload(endpointId, it) }
                client.sendPayload(endpointId, filePayload)
            }
            client.sendPayload(endpointId, Payload.fromBytes(JSONObject().put("t", "end").put("n", outgoing.size).toString().toByteArray()))
        } catch (t: Throwable) {
            fail("Gönderilemedi: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    // ------------------------------------------------------------------ Alma ve ilerleme

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> handleMessage(payload.asBytes() ?: return)
                Payload.Type.FILE -> {
                    receivedFiles[payload.id] = payload
                    tryFinalize()
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val id = update.payloadId
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> progress(id, update.bytesTransferred, update.totalBytes)
                PayloadTransferUpdate.Status.SUCCESS -> {
                    progress(id, update.totalBytes, update.totalBytes)
                    completedPayloads += id
                    sendIndex[id]?.let { index -> markItem(index) { it.copy(done = true, transferred = it.total) } }
                    checkSenderDone()
                    tryFinalize()
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                    val index = sendIndex[id] ?: incomingBooks.firstOrNull { it.fileId == id }?.index
                    if (index != null) markItem(index) { it.copy(failed = true, note = "Aktarım başarısız") }
                }
            }
        }
    }

    private fun handleMessage(bytes: ByteArray) {
        val msg = runCatching { JSONObject(String(bytes)) }.getOrNull() ?: return
        when (msg.optString("t")) {
            "book" -> {
                val index = _state.value.items.size
                incomingBooks += IncomingBook(index, msg.getLong("m"), msg.getLong("f"), msg.optLong("c", -1L))
                _state.update { it.copy(items = it.items + TransferItem(msg.optString("title"), total = msg.optLong("size"))) }
            }
            "end" -> {
                expectedCount = msg.optInt("n", -1)
                tryFinalize()
            }
        }
    }

    private fun progress(payloadId: Long, transferred: Long, total: Long) {
        val index = sendIndex[payloadId] ?: incomingBooks.firstOrNull { it.fileId == payloadId }?.index ?: return
        markItem(index) { if (it.done) it else it.copy(transferred = transferred, total = if (total > 0) total else it.total) }
    }

    private fun markItem(index: Int, change: (TransferItem) -> TransferItem) {
        _state.update { s ->
            if (index !in s.items.indices) s
            else s.copy(items = s.items.toMutableList().also { it[index] = change(it[index]) })
        }
    }

    private fun checkSenderDone() {
        if (outgoing.isEmpty()) return
        val items = _state.value.items
        if (items.isNotEmpty() && items.all { it.done || it.failed }) {
            _state.update { it.copy(phase = NearbyPhase.DONE) }
            scope.launch {
                // Karşı tarafın son dosyayı işlemesine zaman tanı
                delay(2_000)
                connectedEndpoint?.let { runCatching { client.disconnectFromEndpoint(it) } }
            }
        }
    }

    private fun tryFinalize() {
        for (book in incomingBooks) {
            if (book.finalized) continue
            val needed = listOfNotNull(book.manifestId, book.fileId, book.coverId.takeIf { it != -1L })
            if (!needed.all { it in completedPayloads && it in receivedFiles }) continue
            book.finalized = true
            val manifest = receivedFiles[book.manifestId]!!
            val file = receivedFiles[book.fileId]!!
            val cover = if (book.coverId != -1L) receivedFiles[book.coverId] else null
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val meta = manifest.asFile()!!.asParcelFileDescriptor().use { pfd ->
                        java.io.FileInputStream(pfd.fileDescriptor).use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
                    }
                    val filePfd = file.asFile()!!.asParcelFileDescriptor()
                    val coverPfd = cover?.asFile()?.asParcelFileDescriptor()
                    try {
                        repo.importReceived(meta, filePfd, coverPfd)
                    } finally {
                        runCatching { filePfd.close() }
                        runCatching { coverPfd?.close() }
                    }
                }.getOrElse { ImportResult.Failed("", it.message ?: "Okunamadı") }
                listOf(manifest, file, cover).forEach { it?.let(::deleteReceived) }
                when (result) {
                    is ImportResult.Added -> markItem(book.index) { it.copy(done = true, transferred = it.total, bookId = result.book.id) }
                    is ImportResult.Duplicate -> markItem(book.index) {
                        it.copy(done = true, transferred = it.total, bookId = result.book.id, note = "Zaten kitaplığında; yer imleri eklendi")
                    }
                    is ImportResult.Failed -> markItem(book.index) { it.copy(failed = true, note = result.reason) }
                }
                checkReceiverDone()
            }
        }
    }

    private fun checkReceiverDone() {
        if (outgoing.isNotEmpty()) return
        val s = _state.value
        val finished = s.items.count { it.done || it.failed }
        if (expectedCount >= 0 && finished >= expectedCount) {
            _state.update { it.copy(phase = NearbyPhase.DONE) }
            connectedEndpoint?.let { runCatching { client.disconnectFromEndpoint(it) } }
        }
    }

    private fun deleteReceived(payload: Payload) {
        val f = payload.asFile() ?: return
        runCatching { f.asUri()?.let { context.contentResolver.delete(it, null, null) } }
        @Suppress("DEPRECATION")
        runCatching { f.asJavaFile()?.delete() }
    }
}
