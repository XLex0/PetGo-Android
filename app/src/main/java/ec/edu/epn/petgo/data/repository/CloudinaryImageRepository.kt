package ec.edu.epn.petgo.data.repository

import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.data.image.CloudinaryConfig
import ec.edu.epn.petgo.data.image.ImageCompressor
import ec.edu.epn.petgo.data.remote.abort
import ec.edu.epn.petgo.data.remote.businessAbort
import ec.edu.epn.petgo.data.remote.firebaseCall
import ec.edu.epn.petgo.di.IoDispatcher
import ec.edu.epn.petgo.domain.model.LocalImage
import ec.edu.epn.petgo.domain.repository.ImageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reemplaza utils.ImageUpload (Backblaze B2) con subida directa "unsigned" a Cloudinary.
 * La app solo conoce cloud name + nombre del preset; el API secret NUNCA va en el APK.
 *
 * public_id (sin extensión):
 *  - petgo/users/{uid}_{timestamp}
 *  - petgo/pets/{ownerUid}/{petId}_{timestamp}
 * El timestamp evita choques: un preset unsigned no puede sobrescribir assets existentes.
 */
@Singleton
class CloudinaryImageRepository @Inject constructor(
    private val client: OkHttpClient,
    private val config: CloudinaryConfig,
    private val compressor: ImageCompressor,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ImageRepository {

    override suspend fun uploadUserPhoto(uid: String, image: LocalImage): AppResult<String> =
        upload(publicId = "petgo/users/${uid}_${System.currentTimeMillis()}", tags = "petgo,user,$uid", image = image)

    override suspend fun uploadPetPhoto(ownerId: String, petId: String, image: LocalImage): AppResult<String> =
        upload(publicId = "petgo/pets/$ownerId/${petId}_${System.currentTimeMillis()}", tags = "petgo,pet,$ownerId", image = image)

    private suspend fun upload(publicId: String, tags: String, image: LocalImage): AppResult<String> = firebaseCall {
        if (!config.isConfigured) businessAbort("Falta configurar Cloudinary (cloudinary.cloudName / cloudinary.uploadPreset en local.properties).")
        val bytes = compressor.compressToJpeg(image)

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", config.uploadPreset)
            .addFormDataPart("public_id", publicId)
            .addFormDataPart("tags", tags)
            .addFormDataPart("file", "photo.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val request = Request.Builder().url(config.uploadUrl).post(body).build()

        withContext(io) {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (!response.isSuccessful) {
                    val message = json?.optJSONObject("error")?.optString("message").orEmpty()
                    when (response.code) {
                        401, 403 -> abort(AppError.PermissionDenied)
                        420, 429 -> businessAbort("Se alcanzó el límite del plan gratuito de imágenes. Intenta más tarde.")
                        else -> businessAbort(if (message.isNotBlank()) "No se pudo subir la imagen: $message" else "No se pudo subir la imagen.")
                    }
                }
                json?.optString("secure_url")?.takeIf { it.isNotBlank() } ?: abort(AppError.Unknown())
            }
        }
    }
}
