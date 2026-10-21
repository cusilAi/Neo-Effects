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
