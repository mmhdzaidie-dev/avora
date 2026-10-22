package com.zai.avora.ui.components

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.zai.avora.data.playlist.Playlist
import com.zai.avora.ui.screens.PlaylistCollageIcon
import com.zai.avora.ui.theme.AvoraAppTheme
import com.zai.avora.ui.theme.Typography
import com.zai.avora.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistSheet(
    playlist: Playlist,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val colors = AvoraAppTheme.colors
    var name by remember { mutableStateOf(playlist.name) }
    var pendingCover by remember { mutableStateOf<Bitmap?>(null) }
    var removeCover by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Edit Playlist",
                style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .clickable { showPicker = true },
                contentAlignment = Alignment.Center
            ) {
                val bmp = pendingCover
                when {
                    bmp != null -> Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Foto playlist",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    !removeCover && playlist.coverPath != null -> AsyncImage(
                        model = java.io.File(playlist.coverPath),
                        contentDescription = "Foto playlist",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    playlist.tracks.isNotEmpty() -> PlaylistCollageIcon(
                        playlist = playlist.copy(coverPath = null),
                        size = 190
                    )
                    else -> Icon(
                        imageVector = Icons.Outlined.QueueMusic,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ubah foto",
                            style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            val hasCustom = pendingCover != null || (!removeCover && playlist.coverPath != null)
            if (hasCustom) {
                TextButton(onClick = {
                    pendingCover = null
                    removeCover = true
                }) {
                    Text("Hapus foto", color = colors.textMuted, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama playlist", color = colors.textMuted) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.card,
                    unfocusedContainerColor = colors.card,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.textPrimary,
                    focusedIndicatorColor = colors.accent,
                    unfocusedIndicatorColor = colors.border
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val cover = pendingCover
                    when {
                        cover != null -> viewModel.setPlaylistCover(playlist.id, cover)
                        removeCover -> viewModel.removePlaylistCover(playlist.id)
                    }
                    if (name.isNotBlank() && name.trim() != playlist.name) {
                        viewModel.renamePlaylist(playlist.id, name.trim())
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showPicker) {
        CoverPhotoFlow(
            onDismiss = { showPicker = false },
            onCropped = { bmp ->
                pendingCover = bmp
                removeCover = false
                showPicker = false
            }
        )
    }
}

@Composable
private fun CoverPhotoFlow(
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    val colors = AvoraAppTheme.colors
    var picked by remember { mutableStateOf<Uri?>(null) }

    Dialog(
        onDismissRequest = {
            if (picked != null) picked = null else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .systemBarsPadding()
        ) {
            val uri = picked
            if (uri == null) {
                GalleryGrid(onClose = onDismiss, onPick = { picked = it })
            } else {
                CoverCropper(
                    uri = uri,
                    onBack = { picked = null },
                    onDone = onCropped
                )
            }
        }
    }
}

private fun photoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasPhotoAccess(context: Context): Boolean {
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 34 ->
            granted(Manifest.permission.READ_MEDIA_IMAGES) || granted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_IMAGES)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun queryImages(context: Context, limit: Int = 600): List<Uri> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val out = ArrayList<Uri>()
    try {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext() && out.size < limit) {
                out.add(ContentUris.withAppendedId(collection, c.getLong(idCol)))
            }
        }
    } catch (_: Exception) {
    }
    return out
}

@Composable
private fun GalleryGrid(onClose: () -> Unit, onPick: (Uri) -> Unit) {
    val colors = AvoraAppTheme.colors
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasPhotoAccess(context)) }
    var askedOnce by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<Uri>?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasPhotoAccess(context)
        askedOnce = true
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(photoPermissions())
    }
    LaunchedEffect(granted) {
        if (granted) images = withContext(Dispatchers.IO) { queryImages(context) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = colors.textPrimary)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Pilih foto",
                style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
        }

        when {
            !granted -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Avora butuh izin untuk melihat fotomu",
                        style = Typography.titleMedium,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Foto hanya dipakai sebagai sampul playlist dan disalin ke dalam aplikasi.",
                        style = Typography.bodyMedium,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (askedOnce) {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.fromParts("package", context.packageName, null))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } else {
                                launcher.launch(photoPermissions())
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background)
                    ) {
                        Text(if (askedOnce) "Buka pengaturan izin" else "Izinkan akses foto", fontWeight = FontWeight.Bold)
                    }
                }
            }

            images == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }

            images!!.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tidak ada foto yang bisa dibuka",
                    style = Typography.bodyMedium,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(images!!, key = { it.toString() }) { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(colors.card)
                            .clickable { onPick(uri) }
                    )
                }
            }
        }
    }
}

private const val COVER_SIZE_PX = 800

private fun decodeForCrop(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
            } ?: android.media.ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            android.media.ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return raw
        }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    } catch (_: Throwable) {
        null
    }
}

@Composable
private fun CoverCropper(uri: Uri, onBack: () -> Unit, onDone: (Bitmap) -> Unit) {
    val colors = AvoraAppTheme.colors
    val context = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    var userScale by remember(uri) { mutableStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(uri) {
        val decoded = withContext(Dispatchers.IO) { decodeForCrop(context, uri) }
        if (decoded == null) failed = true else bmp = decoded
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Kembali", tint = colors.textPrimary)
            }
            Text(
                text = "Atur foto",
                style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Button(
                enabled = bmp != null,
                onClick = {
                    val b = bmp ?: return@Button
                    onDone(renderCrop(b, cropViewport, userScale, offset))
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background)
            ) { Text("Selesai", fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val image = bmp
            when {
                failed -> Text(
                    "Foto ini tidak bisa dibuka",
                    style = Typography.bodyMedium,
                    color = colors.textMuted
                )
                image == null -> CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp)
                )
                else -> BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val viewportDp = min(maxWidth.value, maxHeight.value - 24f).dp - 32.dp
                    val viewportPx = with(LocalDensity.current) { viewportDp.toPx() }
                    cropViewport = viewportPx
                    val imageBitmap = remember(image) { image.asImageBitmap() }

                    Canvas(
                        modifier = Modifier
                            .size(viewportDp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.card)
                            .pointerInput(image, viewportPx) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (userScale * zoom).coerceIn(1f, 6f)
                                    val s = baseScale(image, viewportPx) * newScale
                                    userScale = newScale
                                    offset = clampOffset(offset + pan, image, viewportPx, s)
                                }
                            }
                    ) {
                        val s = baseScale(image, size.width) * userScale
                        val o = clampOffset(offset, image, size.width, s)
                        withTransform({
                            translate(
                                left = (size.width - image.width * s) / 2f + o.x,
                                top = (size.height - image.height * s) / 2f + o.y
                            )
                            scale(s, s, pivot = Offset.Zero)
                        }) {
                            drawImage(imageBitmap, filterQuality = FilterQuality.Medium)
                        }
                        val line = Color.White.copy(alpha = 0.35f)
                        for (i in 1..2) {
                            val x = size.width * i / 3f
                            val y = size.height * i / 3f
                            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        }
                    }
                }
            }
        }

        Text(
            text = "Cubit untuk zoom, geser untuk mengatur posisi",
            style = Typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )
    }
}

private var cropViewport: Float = 1f

private fun baseScale(b: Bitmap, viewport: Float): Float =
    max(viewport / b.width, viewport / b.height)

private fun clampOffset(o: Offset, b: Bitmap, viewport: Float, scale: Float): Offset {
    val maxX = max(0f, (b.width * scale - viewport) / 2f)
    val maxY = max(0f, (b.height * scale - viewport) / 2f)
    return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
}

private fun renderCrop(b: Bitmap, viewport: Float, userScale: Float, rawOffset: Offset): Bitmap {
    val s = baseScale(b, viewport) * userScale
    val o = clampOffset(rawOffset, b, viewport, s)
    val side = min(viewport / s, min(b.width, b.height).toFloat())
    val cx = b.width / 2f - o.x / s
    val cy = b.height / 2f - o.y / s
    val w = side.toInt().coerceAtLeast(1)
    val x = (cx - side / 2f).toInt().coerceIn(0, max(0, b.width - w))
    val y = (cy - side / 2f).toInt().coerceIn(0, max(0, b.height - w))
    val cut = Bitmap.createBitmap(b, x, y, w, w)
    return if (w > COVER_SIZE_PX) Bitmap.createScaledBitmap(cut, COVER_SIZE_PX, COVER_SIZE_PX, true) else cut
}
