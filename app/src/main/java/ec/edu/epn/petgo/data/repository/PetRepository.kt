package ec.edu.epn.petgo.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.data.mapper.toDomain
import ec.edu.epn.petgo.data.mapper.toDto
import ec.edu.epn.petgo.data.remote.dto.Collections
import ec.edu.epn.petgo.data.remote.dto.PetDto
import ec.edu.epn.petgo.data.remote.firebaseCall
import ec.edu.epn.petgo.domain.model.Pet
import ec.edu.epn.petgo.domain.repository.PetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestorePetRepository @Inject constructor(
    private val db: FirebaseFirestore,
) : PetRepository {

    private val pets get() = db.collection(Collections.PETS)

    override fun newPetId(): String = pets.document().id

    /** PetDAO.findActivePetsByUser */
    override fun observeActivePets(ownerId: String): Flow<List<Pet>> =
        pets.whereEqualTo("ownerId", ownerId).whereEqualTo("active", true)
            .snapshots()
            .map { snap -> snap.toObjects(PetDto::class.java).mapNotNull { it.toDomain() }.sortedBy { it.name.lowercase() } }

    override suspend fun getActivePets(ownerId: String): AppResult<List<Pet>> = firebaseCall {
        pets.whereEqualTo("ownerId", ownerId).whereEqualTo("active", true).get().await()
            .toObjects(PetDto::class.java).mapNotNull { it.toDomain() }
    }

    override suspend fun createPet(pet: Pet): AppResult<Unit> = firebaseCall {
        pets.document(pet.id).set(pet.toDto()).await()
        Unit
    }

    /** PetDAO.suspendPet (soft delete) */
    override suspend fun deactivate(petId: String): AppResult<Unit> = firebaseCall {
        pets.document(petId).update("active", false).await()
        Unit
    }
}
