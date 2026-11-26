package ec.edu.epn.petgo.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ec.edu.epn.petgo.domain.model.ApplicationStatus
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.usecase.application.ObserveClientApplicationsUseCase
import ec.edu.epn.petgo.domain.usecase.application.ObserveWalkerApplicationsUseCase
import ec.edu.epn.petgo.domain.usecase.application.ReconcileClientApplicationsUseCase
import ec.edu.epn.petgo.ui.common.userIdFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Vive mientras el cliente tiene la app abierta:
 *  - badge con postulaciones pendientes de decidir;
 *  - reconciliación de postulaciones cuando un paseador aceptó una contraoferta (Nivel A, ver bitácora).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClientShellViewModel @Inject constructor(
    session: SessionRepository,
    observeApplications: ObserveClientApplicationsUseCase,
    private val reconcile: ReconcileClientApplicationsUseCase,
) : ViewModel() {

    private val applications = session.userIdFlow()
        .flatMapLatest { uid -> observeApplications(uid) }
        .catch { emit(emptyList()) }
        .onEach { list -> viewModelScope.launch { reconcile(list) } }

    val pendingDecisions: StateFlow<Int> = applications
        .map { list -> list.count { it.status == ApplicationStatus.PENDING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/** Badge del paseador: contraofertas esperando su respuesta. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WalkerShellViewModel @Inject constructor(
    session: SessionRepository,
    observeApplications: ObserveWalkerApplicationsUseCase,
) : ViewModel() {
    val counterOffers: StateFlow<Int> = session.userIdFlow()
        .flatMapLatest { uid -> observeApplications(uid) }
        .map { list -> list.count { it.status == ApplicationStatus.COUNTERED } }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
