package ec.edu.epn.petgo.data.image

/** Datos públicos de la cuenta Cloudinary (vienen de local.properties → BuildConfig). */
data class CloudinaryConfig(
    val cloudName: String,
    val uploadPreset: String,
) {
    val isConfigured: Boolean get() = cloudName.isNotBlank() && uploadPreset.isNotBlank()
    val uploadUrl: String get() = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"
}

/**
 * Transformaciones de entrega de Cloudinary sobre una URL `.../image/upload/...`.
 * Ej.: thumbnail(url, 200) → `.../image/upload/c_fill,w_200,h_200,g_auto,q_auto,f_auto/...`
 * Así las listas descargan miniaturas y no la foto completa (ahorra créditos de ancho de banda).
 */
object CloudinaryUrls {
    private const val MARKER = "/image/upload/"

    fun thumbnail(url: String?, sizePx: Int): String? = transform(url, "c_fill,w_$sizePx,h_$sizePx,g_auto,q_auto,f_auto")

    fun limit(url: String?, maxPx: Int): String? = transform(url, "c_limit,w_$maxPx,h_$maxPx,q_auto,f_auto")

    private fun transform(url: String?, transformation: String): String? {
        if (url.isNullOrBlank() || !url.contains("res.cloudinary.com") || !url.contains(MARKER)) return url
        return url.replaceFirst(MARKER, "$MARKER$transformation/")
    }
}
