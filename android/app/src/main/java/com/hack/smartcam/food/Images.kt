package com.hack.smartcam.food

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.exifinterface.media.ExifInterface
import com.hack.smartcam.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File

object Images {
    private val cache = LruCache<String, ImageBitmap>(60)

    suspend fun load(src: String): ImageBitmap? = cache.get(src) ?: withContext(Dispatchers.IO) {
        runCatching {
            val bmp = if (src.startsWith("http")) {
                Http.client.newCall(Request.Builder().url(src).build()).execute().use { BitmapFactory.decodeStream(it.body!!.byteStream()) }
            } else BitmapFactory.decodeFile(src)
            bmp?.asImageBitmap()?.also { cache.put(src, it) }
        }.getOrNull()
    }

    /** Decodes a photo, fixes its rotation and scales the long side down to [max] px. */
    fun decode(context: Context, uri: Uri, max: Int): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= max) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = context.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, max.toFloat() / maxOf(bmp.width, bmp.height))
        val m = Matrix().apply { postRotate(rotation); postScale(scale, scale) }
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }.getOrNull()

    fun jpegBase64(bmp: Bitmap, quality: Int = 82): String =
        ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it); Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }

    fun saveThumb(context: Context, bmp: Bitmap, id: String): String {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val f = File(dir, "$id.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 78, it) }
        return f.path
    }
}

@Composable
fun AsyncImage(src: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val img by produceState<ImageBitmap?>(null, src) { value = Images.load(src) }
    img?.let { Image(it, null, modifier, contentScale = contentScale) }
}
