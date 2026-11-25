package ec.edu.epn.petgo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import ec.edu.epn.petgo.core.designsystem.PetGoTheme
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.repository.SessionState
import ec.edu.epn.petgo.ui.navigation.PetGoRoot
import javax.inject.Inject

/** Única Activity. La splash se mantiene hasta saber si hay sesión; luego PetGoRoot elige el grafo por rol. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionRepository: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = sessionRepository.session
        splash.setKeepOnScreenCondition { session.value is SessionState.Loading }

        setContent {
            PetGoTheme {
                PetGoRoot(session)
            }
        }
    }
}
