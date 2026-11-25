package ec.edu.epn.petgo.data.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudinaryUrlsTest {
    private val url = "https://res.cloudinary.com/demo/image/upload/v1731430000/petgo/users/u1_1731430000.jpg"

    @Test fun `inserta la transformacion de miniatura`() {
        assertEquals(
            "https://res.cloudinary.com/demo/image/upload/c_fill,w_200,h_200,g_auto,q_auto,f_auto/v1731430000/petgo/users/u1_1731430000.jpg",
            CloudinaryUrls.thumbnail(url, 200),
        )
    }

    @Test fun `urls que no son de cloudinary no se tocan`() {
        assertEquals("https://otro.com/a.jpg", CloudinaryUrls.thumbnail("https://otro.com/a.jpg", 200))
        assertNull(CloudinaryUrls.thumbnail(null, 200))
    }

    @Test fun `config incompleta`() {
        assertFalse(CloudinaryConfig("", "preset").isConfigured)
        assertTrue(CloudinaryConfig("demo", "preset").isConfigured)
        assertEquals("https://api.cloudinary.com/v1_1/demo/image/upload", CloudinaryConfig("demo", "p").uploadUrl)
    }
}
