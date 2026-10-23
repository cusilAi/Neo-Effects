package com.cusil.neoeffects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

fun loadBitmap(ctx: Context, uri: Uri, maxSide: Int = 2048): Bitmap? {
    val cr = ctx.contentResolver
    val bounds = BitmapFactory.Options()
    bounds.inJustDecodeBounds = true
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) {
        sample *= 2
    }
    val opts = BitmapFactory.Options()
    opts.inSampleSize = sample
    opts.inPreferredConfig = Bitmap.Config.ARGB_8888
    val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

    var orientation = ExifInterface.ORIENTATION_NORMAL
    try {
        cr.openInputStream(uri)?.use {
            orientation = ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
    } catch (e: Exception) {
    }
    val deg = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (deg == 0f) return bmp
    val m = Matrix()
    m.postRotate(deg)
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

fun saveBitmapToGallery(ctx: Context, bmp: Bitmap, name: String, png: Boolean): String {
    return try {
        val fmt = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val mime = if (png) "image/png" else "image/jpeg"
        val fileName = name + (if (png) ".png" else ".jpg")
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val v = android.content.ContentValues()
            v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/NEOeffects")
            val uri = ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
            if (uri == null) {
                "Could not create the file"
            } else {
                ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(fmt, 95, it) }
                "Saved to Pictures/NEOeffects/" + fileName
            }
        } else {
            val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: ctx.filesDir
            val f = java.io.File(dir, fileName)
            java.io.FileOutputStream(f).use { bmp.compress(fmt, 95, it) }
            "Saved to " + f.absolutePath
        }
    } catch (e: Exception) {
        "Save failed: " + (e.message ?: "unknown error")
    }
}

fun shareBitmap(ctx: Context, bmp: Bitmap, name: String, png: Boolean) {
    try {
        val dir = java.io.File(ctx.cacheDir, "share")
        dir.mkdirs()
        val f = java.io.File(dir, name + (if (png) ".png" else ".jpg"))
        java.io.FileOutputStream(f).use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val i = android.content.Intent(android.content.Intent.ACTION_SEND)
        i.type = if (png) "image/png" else "image/jpeg"
        i.putExtra(android.content.Intent.EXTRA_STREAM, uri)
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = android.content.Intent.createChooser(i, "Share")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    } catch (e: Exception) {
    }
}
