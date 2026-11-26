package ec.edu.epn.petgo.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ec.edu.epn.petgo.BuildConfig
import ec.edu.epn.petgo.data.repository.FirebaseAuthRepository
import ec.edu.epn.petgo.data.repository.CloudinaryImageRepository
import ec.edu.epn.petgo.data.repository.FirebaseSessionRepository
import ec.edu.epn.petgo.data.repository.FirestoreApplicationRepository
import ec.edu.epn.petgo.data.repository.FirestorePetRepository
import ec.edu.epn.petgo.data.repository.FirestoreReviewRepository
import ec.edu.epn.petgo.data.repository.FirestoreTicketRepository
import ec.edu.epn.petgo.data.repository.FirestoreUserRepository
import ec.edu.epn.petgo.domain.repository.ApplicationRepository
import ec.edu.epn.petgo.domain.repository.AuthRepository
import ec.edu.epn.petgo.domain.repository.ImageRepository
import ec.edu.epn.petgo.domain.repository.PetRepository
import ec.edu.epn.petgo.domain.repository.ReviewRepository
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.repository.TicketRepository
import ec.edu.epn.petgo.domain.repository.UserRepository
import ec.edu.epn.petgo.data.image.CloudinaryConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

/** Equivale a HibernateUtil: punto único donde se crean los clientes de Firebase. */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    // 10.0.2.2 = localhost de la PC vista desde el emulador de Android.
    private const val EMULATOR_HOST = "10.0.2.2"

    @Provides @Singleton
    fun provideAuth(): FirebaseAuth = FirebaseAuth.getInstance().apply {
        if (BuildConfig.USE_FIREBASE_EMULATOR) useEmulator(EMULATOR_HOST, 9099)
        setLanguageCode("es")
    }

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        if (BuildConfig.USE_FIREBASE_EMULATOR) useEmulator(EMULATOR_HOST, 8080)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun provideCloudinaryConfig(): CloudinaryConfig =
        CloudinaryConfig(cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME, uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET)
}

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Inyectable para poder fijar "hoy" en los tests de validación de fechas. */
    @Provides @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindAuth(impl: FirebaseAuthRepository): AuthRepository
    @Binds abstract fun bindSession(impl: FirebaseSessionRepository): SessionRepository
    @Binds abstract fun bindUsers(impl: FirestoreUserRepository): UserRepository
    @Binds abstract fun bindImages(impl: CloudinaryImageRepository): ImageRepository
    @Binds abstract fun bindPets(impl: FirestorePetRepository): PetRepository
    @Binds abstract fun bindTickets(impl: FirestoreTicketRepository): TicketRepository
    @Binds abstract fun bindApplications(impl: FirestoreApplicationRepository): ApplicationRepository
    @Binds abstract fun bindReviews(impl: FirestoreReviewRepository): ReviewRepository
}
