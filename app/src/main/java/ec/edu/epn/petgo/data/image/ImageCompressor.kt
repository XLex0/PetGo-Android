package ec.edu.epn.petgo.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.data.remote.abort
import ec.edu.epn.petgo.di.IoDispatcher
import ec.edu.epn.petgo.domain.model.LocalImage
import ec.edu.epn.petgo.domain.validation.Fields
import ec.edu.epn.petgo.domain.validation.ValidationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Redimensiona y comprime a JPEG antes de subir (la web aceptaba JPG/PNG ≤ 2 MB sin procesar).
 * ImageDecoder (API 28+) respeta la orientación EXIF; en API 26–27 se usa BitmapFactory.
 */
@Singleton
class ImageCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun compressToJpeg(image: LocalImage): ByteArray = withContext(io) {
        val uri = Uri.parse(image.uri)
        val bitmap = decode(uri) ?: abort(imageError("No se pudo leer la imagen seleccionada"))
        var quality = 85
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= 10
        } while (bytes.size > MAX_BYTES && quality >= 40)
        if (bytes.size > MAX_BYTES) abort(imageError("El archivo excede el tamaño máximo permitido de 2 MB."))
        bytes
    }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val largest = max(info.size.width, info.size.height)
                if (largest > MAX_DIMENSION) {
                    val scale = MAX_DIMENSION.toFloat() / largest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_DIMENSION) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }.getOrNull()

    private fun imageError(message: String) = AppError.Validation(ValidationResult(mapOf(Fields.IMAGE to message)))

    private companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        const val MAX_DIMENSION = 1280
    }
}
