# DTOs de Firestore: se deserializan por reflexión (toObject), no deben ofuscarse.
-keep class ec.edu.epn.petgo.data.remote.dto.** { *; }
-keepattributes Signature,*Annotation*
