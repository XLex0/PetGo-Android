# PetGo → Android (Kotlin + Firebase) — Guía de migración

> **Estado (2026-11-12):** capas base, core/designsystem, domain (+tests), data, DI y reglas de Firebase creadas en este repo.
> **UI (2026-11-13):** capa `ui/` completa (sección 5.2). Pendiente: primera compilación en la PC del equipo, Fase 7 (tests de reglas, App Check) y Fase 8 (FCM).

> **Para quién es este documento:** para Claude (o cualquier dev) en sesiones futuras de migración.
> Léelo completo antes de escribir código. Es la fuente de verdad del *qué* existe hoy y del *cómo* se traslada.
> Marca las casillas `[x]` a medida que avances y anota decisiones nuevas en la sección **Bitácora** al final.

---

## 0. Reglas para mí (Claude) durante la migración

1. **Paridad funcional primero, mejoras después.** Cada funcionalidad de la sección 3 debe existir en Android antes de añadir cosas nuevas (excepto las correcciones de seguridad de la sección 8, que son obligatorias).
2. **No replicar bugs conocidos** (sección 8). Si encuentro uno nuevo en el código web, lo agrego a esa lista.
3. **Las reglas de negocio viven en la capa `domain`** (casos de uso puros en Kotlin, sin Android ni Firebase), para poder testearlas como hoy se testean los `*Service` con JUnit/Mockito.
4. **Nunca poner secretos en el repo.** `google-services.json` va en `.gitignore`; en CI se inyecta como secret.
5. **Toda operación que toca varios documentos** (aceptar postulación, calificar paseo, eliminar ticket) va en **transacción/batch de Firestore** o en **Cloud Function**, nunca en escrituras sueltas.
6. **Textos de UI en `strings.xml` en español**, copiando los mensajes actuales de los controladores/JSP (sección 3) para que la experiencia sea igual.
7. Antes de fijar versiones de librerías, **verificar la última versión estable** (Firebase BoM, Compose BoM, AGP, Kotlin) — no confiar en versiones de memoria.
8. Probar todo contra el **Firebase Emulator Suite** antes de tocar el proyecto real.

---

## 1. Radiografía del sistema actual

| Aspecto | Hoy (web) | Archivo(s) |
|---|---|---|
| Tipo | WAR Jakarta EE 10 (Servlets 6 + JSP/JSTL), Java 17, Maven | `pom.xml`, `web.xml` |
| Patrón | MVC: `controller` (Servlets) → `model.services` → `model.daos` → Hibernate | `src/main/java/**` |
| ORM | Hibernate 7.0.3, `hbm2ddl.auto=update`, sesión por hilo | `hibernate.cfg.xml`, `HibernateUtil.java` |
| Base de datos | **PostgreSQL en Railway** (`switchyard.proxy.rlwy.net:32428/railway`) | `hibernate.cfg.xml` |
| Archivos/imágenes | **Backblaze B2** vía AWS SDK S3, bucket `Pergiles`, máx 2 MB, JPG/PNG | `ImageUpload.java`, `application.properties` |
| Sesión | `HttpSession` con atributo `user` + cookie `email` 7 días ("Recordar sesión") | `LoginController.java` |
| Mensajes flash | `session.success` / `session.message` → SweetAlert2 | todos los controladores + JSP |
| UI | Bootstrap 5.3, Font Awesome 6.5.1, SweetAlert2 11, `styles.css` | `webapp/**` |
| Tests | JUnit 5 + Mockito (DAOs y Services), JaCoCo | `src/test/java/**` |
| CI | Azure Pipelines + SonarCloud (`Xelan_GR06-ISWD622-25A`) | `azure-pipelines.yml` |

### Roles
`Role { CLIENT, WALKER }` — el cliente (dueño de mascotas) publica solicitudes de paseo; el paseador se postula con un precio.

---

## 2. Entidades actuales → Modelo Firestore

### 2.1 Entidades JPA actuales

| Entidad (tabla) | Campos | Relaciones |
|---|---|---|
| `User` (`Users`) | id, name, lastname, email, **password (texto plano)**, phone, role (ordinal), experience (≤500, TEXT), available, active, imageKey (default `default-profile.jpg`) | — |
| `Pet` (`Pet`) | id, name(100), race(100), age, active, weight(float), behavior(TEXT), gender(10), userId, imageKey | N:1 User |
| `Ticket` (`ticket`) | id, date, hour, **duration (LocalTime)**, status (`TicketStatus` ordinal), deleted, userId | N:1 User (cliente), N:M Pet (`ticket_pet`), 1:1 Review |
| `Application` (`application`) | id, ticketId, userId (paseador), applicationDate, status (**String**: `pending/accepted/rejected/countered`), offerPrice | N:1 Ticket, N:1 User |
| `Review` (`review`) | id, stars, punctuality (**String** "1"-"5"), treatment (**String**), comment(250), createdAt, ticket_id (único) | 1:1 Ticket |
| `TicketStatus` | `PENDING, ASSIGNED, CANCELED, IN_PROGRESS, COMPLETED, REVIEWED` | |

> Ojo: el paseador asignado **no se guarda en el ticket**; se deduce de la postulación `accepted`. En Firestore lo desnormalizamos.

### 2.2 Modelo Firestore propuesto

```
users/{uid}                      ← uid = Firebase Auth UID
  name, lastname, email, phone
  role: "CLIENT" | "WALKER"
  experience: string            (solo WALKER, ≤60 palabras)
  available: bool               (solo WALKER)
  active: bool
  photoUrl: string?             (secure_url de Cloudinary)
  createdAt, updatedAt: timestamp
  (las estadísticas del paseador se calculan desde reviews where walkerId — ver Bitácora)
  legacyId: number              (id de Postgres, solo para la migración de datos)

pets/{petId}
  ownerId: uid
  name, race, age:int, weight:double, behavior, gender: "Macho"|"Hembra"
  active: bool
  photoUrl: string              (Cloudinary: petgo/pets/{ownerUid}/{petId}_{ts})
  createdAt

tickets/{ticketId}
  clientId: uid
  client: { id, name, lastname, photoUrl }          ← snapshot para listar sin joins
  petIds: [petId]
  pets: [{ id, name, race, age, gender, behavior, photoUrl }]
  date: "YYYY-MM-DD", hour: "HH:mm"
  startAt: timestamp            (fecha+hora combinadas, para ordenar/filtrar)
  durationMinutes: int          (30–180)
  status: "PENDING"|"ASSIGNED"|"CANCELED"|"IN_PROGRESS"|"COMPLETED"|"REVIEWED"
  deleted: bool
  assignedWalkerId: uid?        ← NUEVO (desnormalizado)
  agreedPrice: double?          ← NUEVO
  createdAt, updatedAt

applications/{applicationId}    ← colección raíz (consultas por walker y por cliente)
  ticketId, clientId, walkerId
  client: { id, name, lastname, photoUrl }
  walker: { id, name, lastname, photoUrl, experience }
  ticket: { date, hour, durationMinutes, petNames }
  offerPrice: double            (precio vigente; cambia con la contraoferta)
  originalPrice: double         (lo que propuso el paseador)
  status: "PENDING"|"ACCEPTED"|"REJECTED"|"COUNTERED"
  createdAt, updatedAt
  ‑ Unicidad (walker, ticket): usar id determinístico  `${ticketId}_${walkerId}`

reviews/{ticketId}              ← id = ticketId garantiza 1 review por ticket
  ticketId, clientId, walkerId
  client: { id, name, lastname, photoUrl }
  stars:int(1-5), punctuality:int(1-5), treatment:int(1-5)
  comment: string(≤250)
  createdAt
```

**Índices compuestos:** ninguno por ahora — todas las consultas usan solo filtros de igualdad y el orden se aplica en el dominio. Si se agregan `orderBy` en consultas, añadir aquí (los de abajo quedan como referencia):
- `tickets`: `clientId ASC, deleted ASC, startAt DESC`
- `tickets`: `status ASC, deleted ASC, startAt DESC` (solicitudes disponibles para paseador)
- `applications`: `walkerId ASC, createdAt DESC`
- `applications`: `clientId ASC, createdAt DESC`
- `applications`: `ticketId ASC, status ASC`
- `pets`: `ownerId ASC, active ASC`
- `reviews`: `walkerId ASC, createdAt DESC`

### 2.3 Fuentes de datos: antes → después

| Hoy | Firebase | Notas |
|---|---|---|
| PostgreSQL (Railway) + Hibernate | **Cloud Firestore** | Offline cache incluido; listeners en tiempo real con `Flow` |
| Login propio con contraseña en texto plano + `HttpSession` | **Firebase Authentication** (Email/Password) | Sesión persistente por defecto → reemplaza "Recordar sesión" |
| Cookie `email` | (no hace falta) | Firebase Auth persiste el token |
| Backblaze B2 (S3) | **Cloudinary** (plan gratuito, subida *unsigned* con upload preset) | `petgo/users/{uid}_{ts}`, `petgo/pets/{ownerUid}/{petId}_{ts}`. Firebase Storage descartado: exige plan Blaze |
| Validación en `*Service` | `domain` UseCases + **Security Rules** + (opcional) **Cloud Functions** | Las rules son la validación "de servidor" |
| `session.message` + SweetAlert | `UiState` + Snackbar / AlertDialog | |
| — (nuevo) | **Cloud Messaging (FCM)** | Avisar: nueva postulación, contraoferta, aceptación, paseo iniciado/finalizado |
| — (nuevo) | **Crashlytics + Analytics** | |
| — (nuevo) | **App Check** (Play Integrity) | Protege Firestore |
| SonarCloud + Azure Pipelines | Igual, pero con Gradle | + Firebase App Distribution para QA |

> **Plan de facturación:** Cloud Functions requiere plan **Blaze**. Diseño en dos niveles:
> - **Nivel A (Spark, gratis, sin tarjeta):** Auth + Firestore con transacciones de cliente + Security Rules estrictas; imágenes en Cloudinary.
> - **Nivel B (Blaze):** mover a Functions las operaciones críticas (aceptar postulación, calificar, agregados de rating, FCM).
> Empezar por A; dejar los casos de uso detrás de interfaces para poder cambiar a B sin tocar la UI.

---

## 3. Mapa de funcionalidades (paridad)

Leyenda de origen: `Ctrl` = Servlet y `route`, `Svc` = método de servicio.

### 3.1 Autenticación y cuenta

| # | Funcionalidad | Origen | Reglas a conservar | Android/Firebase |
|---|---|---|---|---|
| A1 | Registro con rol | `UserController?route=create` → `UserService.createUser` | Todos los campos obligatorios (nombre, apellido ≥2, email válido, teléfono 7–15, contraseña + confirmación, términos, rol). Email único entre usuarios **activos**. | `createUserWithEmailAndPassword` + crear `users/{uid}` en la misma operación (si falla Firestore, borrar el usuario de Auth). |
| A2 | Login | `loginController` POST → `UserService.login` | Solo usuarios `active=true`. Mensajes: "Usuario ingresado correctamente" / "Correo o contraseña incorrectos, o usuario inactivo." Redirige por rol: WALKER→home paseador, CLIENT→home cliente. | `signInWithEmailAndPassword`, luego leer `users/{uid}`; si `active=false` → `signOut()` y mostrar el mensaje. |
| A3 | Recordar sesión | cookie `email` 7 días | — | Implícito en Firebase Auth. Opcional: checkbox que, si está desmarcado, cierra sesión al salir (DataStore). |
| A4 | ¿Olvidaste tu contraseña? | link sin implementar | — | `sendPasswordResetEmail` (mejora gratis). |
| A5 | Logout | `loginController?action=logout` (+ `components/logout.jsp` confirmación) | Confirmación previa | `AlertDialog` → `signOut()` → navegar a Welcome, limpiando back stack. |
| A6 | Suspender cuenta (cliente) | `UserController?route=suspend` → `UserDAO.suspendUser` | Soft delete `active=false` | Update `active=false` + `signOut()`. |
| A7 | Suspender cuenta (paseador) | `WalkerController?route=suspendAccount` | Debe ser WALKER | Igual que A6. |

### 3.2 Perfil

| # | Funcionalidad | Origen | Reglas | Android/Firebase |
|---|---|---|---|---|
| P1 | Ver perfil | `/profile` GET → `profile.jsp` | Navbar distinta por rol | `ProfileScreen` compartida |
| P2 | Editar datos | `UserController?route=updateImage` (imagen + datos) → `UserService.updateUser` | No vacíos; email no usado por otro usuario activo | Update `users/{uid}`; si cambia email → `verifyBeforeUpdateEmail()` en Auth. Contraseña → `updatePassword()` (con reautenticación), **ya no se guarda en Firestore**. |
| P3 | Cambiar foto | `ProfileService.updateUserImage` → B2 key `user_{id}_{ts}.jpg` | JPG/PNG, ≤2 MB | Photo Picker → comprimir → Cloudinary (unsigned) → guardar `photoUrl`. |
| P4 | Ver foto (propia / por email / por id) | `UserController?route=getImage*` (stream S3) | Fallback a imagen por defecto | Coil `AsyncImage` con la URL de Cloudinary (miniaturas vía `CloudinaryUrls.thumbnail`) + placeholder local. |
| P5 | Perfil profesional del paseador | `WalkerController?route=viewProfile/updateProfile` | Experiencia obligatoria, **≤60 palabras** (contador en vivo); disponible on/off | `WalkerProfileScreen` con contador de palabras. |
| P6 | Cambiar disponibilidad | `WalkerController?route=updateAvailability` | Solo WALKER | Switch → update `available`. |
| P7 | Cambiar experiencia | `WalkerController?route=updateExperience` | ≤60 palabras, no vacía ("Experience description cannot be empty.") | Update `experience`. |

### 3.3 Mascotas (cliente)

| # | Funcionalidad | Origen | Reglas | Android/Firebase |
|---|---|---|---|---|
| M1 | Registrar mascota | `mascotas?route=saveNew` (JSON) → `PetService.createPet` | name 1–100; race en **lista blanca** de 33 razas (ver abajo); age 0–30; weight >1.4 y ≤150; behavior 1–500; gender `Macho`/`Hembra`; **imagen obligatoria**. Errores por campo (`ValidationResult`). | `PetFormScreen` con `ExposedDropdownMenu` de razas, errores por campo. Crear doc + subir foto; si falla la foto, mostrar error "No se pudo subir la imagen de la mascota". |
| M2 | Listar mascotas activas | `mascotas?route=list` → `findActivePetsByUser` | Solo `active=true` del usuario | Query `pets where ownerId==uid && active==true` como `Flow`. |
| M3 | Dar de baja mascota | `mascotas?route=suspend` | Soft delete | Update `active=false`. |
| M4 | Ver foto de mascota | `mascotas?route=getImage&petId=` | — | Coil. |
| M5 | Detalle de mascota | modal en `pet/list.jsp` y en `walker/listTickets.jsp` | — | `ModalBottomSheet`. |

Razas válidas (copiar tal cual a `domain/PetBreeds.kt`):
`labrador, golden retriever, bulldog, beagle, poodle, rottweiler, husky, chihuahua, dálmata, pastor alemán, boxer, shih tzu, doberman, gran danés, akita, schnauzer, cocker spaniel, basset hound, pomerania, yorkshire terrier, border collie, pastor australiano, san bernardo, bull terrier, jack russell, samoyedo, bichón frisé, caniche, galgo, alaskan malamute, malinois, fox terrier, cane corso`

### 3.4 Solicitudes de paseo / Tickets

**Máquina de estados (la conduce el CLIENTE, no el paseador):**

```
            crear                 aceptar postulación
  (nuevo) ───────► PENDING ───────────────────────────► ASSIGNED
                     │  eliminar (deleted=true            │  start          cancel
                     │  + borra postulaciones)            ├────────► IN_PROGRESS ──finish──► COMPLETED ──review──► REVIEWED
                     ▼                                    │                │ cancel
                  (oculto)                                ▼ cancel         ▼
                                                       CANCELED         CANCELED
```

| # | Funcionalidad | Origen | Reglas | Android/Firebase |
|---|---|---|---|---|
| T1 | Formulario nueva solicitud | `TicketController?route=showRegisterForm` | Si no hay mascotas: estado vacío "No tienes mascotas registradas" + botón "Registrar Primera Mascota" | `TicketFormScreen` |
| T2 | Crear solicitud | `route=create` → `TicketService.createTicket` | fecha ≥ hoy; hora y duración obligatorias; ≥1 mascota; estado inicial PENDING | DatePicker/TimePicker M3, chips de mascotas multi-selección; **validar duración 30–180 min** (ver bug 8.9). |
| T3 | Mis solicitudes | `route=list` → `getTicketsByUser` | No borradas; orden: IN_PROGRESS, ASSIGNED, PENDING, COMPLETED, CANCELED, resto | Query por `clientId`, ordenar en `domain` con el mismo comparador. |
| T4 | Editar solicitud | `route=update` | Mismas validaciones de fecha/hora/duración | Diálogo de edición; permitir solo en PENDING (mejora recomendada). |
| T5 | Eliminar solicitud | `route=suspend` → `TicketService.suspendTicket` | Solo en PENDING (UI); `deleted=true` **y borrar sus postulaciones** | **Batch**: ticket.deleted=true + delete applications where ticketId. |
| T6 | Iniciar paseo | `route=start` | Solo ASSIGNED | Transacción: verificar estado → IN_PROGRESS. |
| T7 | Finalizar paseo | `route=finish` | Solo IN_PROGRESS | → COMPLETED. |
| T8 | Cancelar | `route=cancel` | Desde ASSIGNED o IN_PROGRESS | → CANCELED. |
| T9 | Solicitudes disponibles (paseador) | `route=listAll` → `findAllTickets` | `deleted=false && status=PENDING`, orden fecha desc. Muestra foto+nombre del cliente, fecha, hora, duración, chips de mascotas | Query `tickets where status=="PENDING" && deleted==false orderBy startAt desc`. Ocultar las de fecha pasada (mejora). |

### 3.5 Postulaciones y negociación de precio

| # | Funcionalidad | Origen | Reglas | Android/Firebase |
|---|---|---|---|---|
| N1 | Postularse con precio | `ApplicationController route=apply` → `applyToTicket` | Una postulación por (paseador, ticket) → "Ya te has postulado a este ticket."; estado `pending`; fecha = hoy | `set` en `applications/{ticketId}_{walkerId}` con `create`-only en rules. |
| N2 | Mis postulaciones (paseador) | `route=listByWalker` | Muestra cliente, precio ofrecido/contraofertado, estado | Query por `walkerId`. |
| N3 | Postulaciones recibidas (cliente) | `route=listByClient` | Muestra foto y datos del paseador; botones Aceptar/Rechazar/Contraofertar habilitados **solo si `pending`** | Query por `clientId`. |
| N4 | Contraoferta (cliente) | `route=counteroffer` → `updateOfferPriceAndStatus(id, nuevoPrecio, "countered")` | Reemplaza `offerPrice` | Diálogo con precio → update status COUNTERED. Mejora: guardar `originalPrice` y `counterPrice`. |
| N5 | Responder (cliente o paseador) | `route=respond` + `action=accepted/rejected` + `origin=walker` | Al aceptar: **aceptar esta, rechazar las demás del ticket y ticket→ASSIGNED**. El paseador solo puede responder si está `countered`. Mensaje especial "¡Tu contraoferta fue aceptada por el paseador!" | **Transacción** (o Function `acceptApplication`): app→ACCEPTED, otras→REJECTED, ticket→ASSIGNED + `assignedWalkerId` + `agreedPrice`. |

### 3.6 Calificaciones

| # | Funcionalidad | Origen | Reglas | Android/Firebase |
|---|---|---|---|---|
| R1 | Calificar paseo | `TicketController route=review` → `ReviewService.rateTicket` | Solo COMPLETED; una vez; estrellas 1–5, puntualidad 1–5, trato 1–5 (sliders con emojis, texto "Regular" en 3), comentario ≤250; ticket→REVIEWED | `RateWalkDialog` (estrellas + sliders con emojis). Transacción: create `reviews/{ticketId}` + ticket→REVIEWED + sumar agregados en `users/{walkerId}`. |
| R2 | Mis calificaciones (paseador) | `route=reviewsWalker` → `getStatsForWalker` | Promedios de estrellas, puntualidad, trato; total; lista de reseñas con nombre del cliente y comentario | Leer agregados del user (o `AggregateQuery` average/count de Firestore) + lista `reviews where walkerId==uid`. |

### 3.7 Componentes transversales

| JSP | Equivalente Compose |
|---|---|
| `components/loading.jsp` | `LoadingOverlay()` / `CircularProgressIndicator` |
| `components/message.jsp` + SweetAlert2 (toast con timer) | `SnackbarHost` + `UiEvent.ShowMessage` (success/error) |
| `components/logout.jsp` | `ConfirmLogoutDialog` |
| Navbar por rol | `NavigationBar` inferior por rol (ver 5.2) |

---

## 4. Arquitectura Android

**Stack:** Kotlin · Jetpack Compose + Material 3 · MVVM + Clean Architecture · Hilt · Coroutines/Flow · Navigation Compose (rutas type-safe) · Coil · DataStore · Firebase BoM (Auth, Firestore, Messaging, Crashlytics, Analytics, App Check) · Cloudinary vía OkHttp · minSdk 26.

### 4.1 Correspondencia de capas

| Web actual | Android |
|---|---|
| JSP (`webapp/jsp/**`) | `ui/feature/**/XxxScreen.kt` (Composables) |
| Servlets (`controller/**`) | `ui/feature/**/XxxViewModel.kt` (+ `UiState`, `UiEvent`) |
| `model.services/**` (reglas de negocio) | `domain/usecase/**` |
| `ValidationResult` | `domain/model/ValidationResult.kt` (mismo concepto, `Map<Field, String>`) |
| `model.entities/**` | `domain/model/**` (puros) + `data/remote/dto/**` (Firestore) + mappers |
| `model.daos/**` | `data/repository/**Impl` sobre `data/remote/**DataSource` |
| `HibernateUtil` | Módulos Hilt (`di/FirebaseModule.kt`) |
| `ImageUpload` | `data/repository/CloudinaryImageRepository.kt` + `data/image/ImageCompressor.kt` |
| `HttpSession.user` | `SessionRepository` → `StateFlow<SessionUser?>` (Auth + `users/{uid}`) |

### 4.2 Estructura de paquetes

```
app/src/main/java/ec/edu/epn/petgo/
├── PetGoApp.kt                       (@HiltAndroidApp)
├── MainActivity.kt                   (NavHost, tema)
├── core/
│   ├── designsystem/ (Color.kt, Theme.kt, Type.kt, Shape.kt, components/)
│   ├── image/ImageCompressor.kt
│   ├── result/AppResult.kt
│   └── util/ (DateTimeFormat, WordCounter)
├── di/ (FirebaseModule, RepositoryModule, DispatcherModule)
├── domain/
│   ├── model/ (User, Role, Pet, Ticket, TicketStatus, Application, ApplicationStatus, Review, WalkerStats, ValidationResult, PetBreeds)
│   ├── repository/ (AuthRepository, UserRepository, PetRepository, TicketRepository, ApplicationRepository, ReviewRepository, ImageRepository)
│   └── usecase/
│       ├── auth/ (RegisterUser, Login, Logout, ResetPassword, SuspendAccount)
│       ├── profile/ (UpdateProfile, UpdateProfilePhoto, UpdateWalkerProfile, ToggleAvailability)
│       ├── pet/ (ValidatePet, CreatePet, ObserveActivePets, SuspendPet)
│       ├── ticket/ (ValidateTicket, CreateTicket, UpdateTicket, DeleteTicket, StartWalk, FinishWalk, CancelWalk, ObserveMyTickets, ObserveAvailableTickets, SortTicketsByStatus)
│       ├── application/ (ApplyToTicket, CounterOffer, AcceptApplication, RejectApplication, ObserveWalkerApplications, ObserveClientApplications)
│       └── review/ (RateWalk, GetWalkerStats)
├── data/
│   ├── remote/ (dto/, AuthDataSource, FirestoreDataSource por colección, StorageDataSource)
│   ├── mapper/
│   └── repository/ (*RepositoryImpl)
├── notifications/ (PetGoMessagingService)   ← FCM
└── ui/
    ├── navigation/ (Routes.kt, PetGoNavHost.kt, RoleGraph)
    └── feature/
        ├── welcome/  auth/login  auth/register
        ├── client/home  client/pets  client/petform  client/tickets  client/ticketform  client/applications
        ├── walker/home  walker/available  walker/applications  walker/profile  walker/reviews
        └── profile/
```

### 4.3 Proyecto Firebase (en el repo)

```
firebase/
├── firebase.json
├── firestore.rules
├── firestore.indexes.json
├── (storage.rules ya no se usa)
├── functions/        (Nivel B, TypeScript)
└── migration/        (scripts de migración Postgres → Firebase)
```

---

## 5. Diseño (UI/UX)

### 5.1 Design tokens (de `styles.css`)

| Token web | Valor | Compose (`Color.kt`) |
|---|---|---|
| `--petgo-primary` | `#28A745` | `primary` |
| `--petgo-secondary` | `#20C997` | `secondary` / `tertiary` |
| `--petgo-dark` | `#1E7E34` | `primaryContainer` (dark) / pressed |
| `--petgo-light` | `#F8FFF9` | `background` / `surface` |
| Warning (botones amarillos) | `#FFC107` / hover `#E0A800` | color extendido `warning` |
| Danger | `#DC3545` | `error` |
| Info / azul | `#007BFF` / `#0D6EFD` | color extendido `info` |
| Chip "Hora/Duración" paseador | `#66BB6A` | color extendido `chipWalk` |
| Gris texto | `#6C757D` | `onSurfaceVariant` |
| Bordes | `#E9ECEF` | `outlineVariant` |

- **Header degradado** `linear-gradient(135deg, primary, secondary)` → `Brush.linearGradient` en `TopAppBar` y cabeceras de tarjeta.
- **Botones píldora** (`border-radius: 25px`) → `RoundedCornerShape(50)`; elevación al presionar.
- **Cards** radio 12–15 dp, sombra suave; *role-card* con borde que pasa a primario al seleccionar.
- **Avatares circulares** 80/100/120 dp con borde verde.
- **Animación bounce** del icono principal → `rememberInfiniteTransition`.
- Tipografía: Bootstrap usa system font → `FontFamily.Default` (opcional: Nunito/Poppins).
- Íconos Font Awesome → Material Symbols (`Pets`, `DirectionsWalk`, `ConfirmationNumber`, `Star`, `Schedule`, `Handshake`, `Person`, `Logout`).
- Logo `images/PetGo-logo.png` (1.3 MB) → convertir a **adaptive icon** + vector/WebP para splash (`SplashScreen API`).
- Soportar **modo oscuro** (generar esquema dark a partir del primario).

### 5.2 Pantallas JSP → Compose (implementadas)

| JSP | Pantalla Android | Rol | Cambio de flujo |
|---|---|---|---|
| `index.jsp` | `WelcomeScreen` | público | Dos tarjetas "Tengo una mascota" / "Quiero pasear" que llevan al registro con el rol preelegido |
| `auth/login.jsp` | `LoginScreen` | público | "¿Olvidaste tu contraseña?" ahora funciona (correo de Firebase) |
| `auth/registro.jsp` | `RegisterScreen` | público | Rol primero; al registrarse entra directo (sin volver a loguearse) |
| `client/client.jsp` | `ClientHomeScreen` | CLIENT | Dashboard: ofertas por decidir, paseos por calificar, próximo paseo, mascotas |
| `client/pet/list.jsp` | `PetsScreen` + sheet de detalle | CLIENT | — |
| `client/pet/register.jsp` | `PetFormScreen` | CLIENT | Buscador de raza, sexo con botones segmentados, vista previa de foto |
| `client/ticket/register.jsp` + modal editar | `TicketFormScreen` | CLIENT | Fecha sin días pasados, chips de duración, mascotas como tarjetas; si no hay mascotas, atajo para registrar |
| `client/ticket/list.jsp` | `TicketsScreen` (Activos / Historial) | CLIENT | Cada tarjeta muestra cuántas ofertas hay por revisar |
| `client/listApplications.jsp` | **dentro de** `TicketDetailScreen` | CLIENT | Las ofertas se ven en el paseo al que pertenecen; línea de tiempo del estado; paseador asignado con botón **Llamar**; al finalizar se abre la calificación |
| `profile/profile.jsp` + `walker/profileWalker.jsp` | `ProfileScreen` (unificado) | ambos | Datos, foto, perfil profesional (si es paseador), contraseña, cerrar sesión, suspender cuenta |
| `walker/home.jsp` | `WalkerHomeScreen` | WALKER | Switch de disponibilidad, contraofertas por responder, **mis paseos confirmados (nuevo)**, resumen de reseñas |
| `walker/listTickets.jsp` | `AvailableTicketsScreen` | WALKER | Si ya ofertaste se muestra tu oferta/estado; si no estás disponible, banner para activarte |
| `walker/listApplications.jsp` | `WalkerApplicationsScreen` | WALKER | Filtros: Por responder / Pendientes / Cerradas; contraoferta con precio original vs. nuevo |
| `walker/reviews.jsp` | `WalkerReviewsScreen` | WALKER | — |
| — | `WalkDetailScreen` (nuevo) | WALKER | Datos del paseo, mascotas con su comportamiento y botón para llamar al dueño |

**Navegación:** `PetGoRoot` escoge el grafo según la sesión (sin sesión / cliente / paseador); login, logout y suspensión cambian de grafo solos.
- Barra inferior CLIENTE: Inicio · Mascotas · Paseos (badge: ofertas pendientes) · Perfil
- Barra inferior PASEADOR: Inicio · Disponibles · Ofertas (badge: contraofertas) · Reseñas · Perfil

---|---|---|
| `index.jsp` (landing + modales) | `WelcomeScreen` | público |
| `auth/login.jsp` | `LoginScreen` (mostrar/ocultar contraseña) | público |
| `auth/registro.jsp` | `RegisterScreen` (role cards, confirmación, términos) | público |
| `client/client.jsp` | `ClientHomeScreen` (3 tarjetas: Mascotas, Postulaciones, Solicitudes) | CLIENT |
| `client/pet/list.jsp` | `PetListScreen` + sheet de detalle | CLIENT |
| `client/pet/register.jsp` | `PetFormScreen` | CLIENT |
| `client/ticket/register.jsp` | `TicketFormScreen` | CLIENT |
| `client/ticket/list.jsp` | `MyTicketsScreen` (acciones por estado, diálogo editar, diálogo calificar) | CLIENT |
| `client/listApplications.jsp` | `ReceivedApplicationsScreen` (aceptar/rechazar/contraofertar) | CLIENT |
| `profile/profile.jsp` | `ProfileScreen` | ambos |
| `walker/home.jsp` | `WalkerHomeScreen` (Solicitudes, Postulaciones, Calificaciones) | WALKER |
| `walker/listTickets.jsp` | `AvailableTicketsScreen` (diálogo postularse con precio) | WALKER |
| `walker/listApplications.jsp` | `MyApplicationsScreen` (responder contraoferta) | WALKER |
| `walker/profileWalker.jsp` | `WalkerProfessionalProfileScreen` | WALKER |
| `walker/reviews.jsp` | `WalkerReviewsScreen` (3 stat cards + lista) | WALKER |

**Navegación:** grafo `auth` → al loguear se elige `clientGraph` o `walkerGraph` según `role`.
- Bottom bar CLIENT: Inicio · Mascotas · Solicitudes · Postulaciones · Perfil
- Bottom bar WALKER: Inicio · Disponibles · Postulaciones · Calificaciones · Perfil

---

## 6. Seguridad (Security Rules — borrador)

```js
// firestore.rules (resumen; completar y testear con el emulador)
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    function signedIn() { return request.auth != null; }
    function me() { return get(/databases/$(db)/documents/users/$(request.auth.uid)).data; }
    function isClient() { return signedIn() && me().role == 'CLIENT' && me().active; }
    function isWalker() { return signedIn() && me().role == 'WALKER' && me().active; }

    match /users/{uid} {
      allow read: if signedIn();
      allow create: if request.auth.uid == uid && request.resource.data.role in ['CLIENT','WALKER'];
      allow update: if request.auth.uid == uid
        && request.resource.data.role == resource.data.role          // no cambiar rol
        && !request.resource.data.diff(resource.data).affectedKeys()
              .hasAny(['ratingCount','starsSum','punctualitySum','treatmentSum']); // agregados solo vía transacción de review/Function
    }
    match /pets/{petId} {
      allow read: if signedIn();
      allow create, update: if isClient() && request.resource.data.ownerId == request.auth.uid
        && request.resource.data.gender in ['Macho','Hembra']
        && request.resource.data.age >= 0 && request.resource.data.age <= 30;
    }
    match /tickets/{id} {
      allow read: if signedIn();
      allow create: if isClient() && request.resource.data.clientId == request.auth.uid
        && request.resource.data.status == 'PENDING';
      allow update: if (isClient() && resource.data.clientId == request.auth.uid)
        /* + validar transiciones de estado permitidas */;
    }
    match /applications/{id} {
      allow read: if signedIn() && (resource.data.walkerId == request.auth.uid || resource.data.clientId == request.auth.uid);
      allow create: if isWalker() && request.resource.data.walkerId == request.auth.uid
        && id == request.resource.data.ticketId + '_' + request.auth.uid
        && request.resource.data.status == 'PENDING';
      allow update: if signedIn() /* cliente: COUNTERED/ACCEPTED/REJECTED si PENDING; walker: ACCEPTED/REJECTED si COUNTERED */;
    }
    match /reviews/{ticketId} {
      allow read: if signedIn();
      allow create: if isClient() && get(/databases/$(db)/documents/tickets/$(ticketId)).data.status == 'COMPLETED'
        && get(/databases/$(db)/documents/tickets/$(ticketId)).data.clientId == request.auth.uid;
      allow update, delete: if false;
    }
  }
}
```

Storage rules: no aplican (imágenes en Cloudinary). La validación equivalente está en el upload preset (formatos/tamaño) y en `isCloudinaryUrl()` de firestore.rules.

---

## 7. Migración de datos (Postgres → Firebase)

Script en `firebase/migration/` (Node + `firebase-admin` + `pg`), ejecutar primero contra el emulador:

1. **Users** → por cada fila activa/inactiva: `auth.createUser({ email, password, disabled: !active })` (las contraseñas están en texto plano; se pueden importar así y quedan hasheadas por Firebase). Guardar mapa `legacyUserId → uid`. Crear `users/{uid}` (sin password, `role` desde el ordinal: 0=CLIENT, 1=WALKER).
2. **Imágenes** → descargar de B2 cada `image_key` y subir a Cloudinary con el Admin API (firmado, desde el script); guardar `photoUrl`.
3. **Pets** → `pets/{autoId}`, mapa `legacyPetId → petId`, `ownerId` vía mapa de users.
4. **Tickets** → `durationMinutes = hour*60+min` desde `LocalTime`; `status` desde ordinal (0 PENDING, 1 ASSIGNED, 2 CANCELED, 3 IN_PROGRESS, 4 COMPLETED, 5 REVIEWED); `petIds` desde `ticket_pet`; snapshots.
5. **Applications** → id `${ticketId}_${walkerId}`; status a MAYÚSCULAS. Si `accepted`, escribir `assignedWalkerId` y `agreedPrice` en el ticket.
6. **Reviews** → `reviews/{ticketId}`, `punctuality/treatment` a `int`, `walkerId` desde la postulación aceptada; recalcular agregados en `users/{walkerId}`.
7. Validar conteos origen vs destino y un muestreo manual.

---

## 8. Bugs y deudas del sistema actual (NO replicar)

| # | Problema | Dónde | Acción en Android |
|---|---|---|---|
| 8.1 | **🔴 Credenciales en el repo**: contraseña de Postgres de Railway y 2 pares de llaves de Backblaze B2 en texto plano | `hibernate.cfg.xml`, `application.properties`, `ImageUpload.java` | **Rotar ya** esas credenciales; borrarlas del historial git (`git filter-repo`). En Android: nada de secretos en código. |
| 8.2 | Contraseñas guardadas y comparadas en texto plano | `UserService.login`, `User.password` | Firebase Auth. Nunca guardar password en Firestore. |
| 8.3 | `findAvailableTickets` usa `t.assigned`, campo que no existe (falla en runtime) | `TicketDAO` (`route=listAvailable`) | Usar solo la query de `listAll` (status PENDING). |
| 8.4 | `findAllTickets` filtra `status = 0` (depende del ordinal del enum) | `TicketDAO` | Guardar estados como string. |
| 8.5 | `ProfileController` POST solo cambia la sesión, no la BD | `ProfileController.doPost` | Ignorar ese endpoint; P2 sí persiste. |
| 8.6 | `UserService.updateUser` envía `experience=null, available=false` → borra datos del paseador si edita desde perfil general | `UserService.updateUser` | Updates parciales (`update(mapOf(...))`) solo de los campos editados. |
| 8.7 | `punctuality`/`treatment` como String y parseados con `Integer.parseInt` | `Review`, `ReviewService` | `Int`. |
| 8.8 | Duración como `LocalTime` | `Ticket.duration` | `durationMinutes: Int`. |
| 8.9 | `isValidDuration` (30–180 min) existe pero **no se llama** en crear/actualizar | `TicketService` | Aplicarla en `ValidateTicket`. |
| 8.10 | Paseador asignado no se guarda en el ticket; reviews dependen de un JOIN con applications | `ReviewDAO.findByWalkerId` | `assignedWalkerId` + `walkerId` en review. |
| 8.11 | Aceptar postulación hace 3 escrituras sin transacción común | `ApplicationController.respond` | Transacción/Function. |
| 8.12 | Imágenes siempre con extensión `.jpg` aunque sean PNG; `Content-Type` fijo `image/jpeg` | `ImageUpload`, controllers | Guardar `contentType` real (o convertir todo a JPEG/WebP al comprimir). |
| 8.13 | `Review.owner` mapea columna `userId` inexistente en `review` | `Review.java` | Usar `clientId` explícito. |
| 8.14 | Sin control de autorización por recurso (cualquier usuario logueado puede pasar `ticketId`/`petId` ajenos) | Todos los controllers | Security Rules (sección 6). |
| 8.15 | Mezcla de mensajes en inglés y español | `WalkerController` | Unificar en español en `strings.xml`. |
| 8.16 | Link "¿Olvidaste tu contraseña?" sin función | `login.jsp` | A4. |

---

## 9. Plan por fases (checklist)

### Fase 0 — Preparación
- [ ] Rotar credenciales de Railway y B2 (8.1) y limpiar historial.
- [ ] Crear proyecto Firebase (`petgo-dev` y `petgo-prod`), registrar app Android `ec.edu.epn.petgo`, añadir SHA-1/SHA-256.
- [ ] Habilitar Auth (Email/Password) y Firestore (modo producción, `us-east1`). Crear cuenta Cloudinary + upload preset unsigned (README).
- [ ] Repo: nuevo módulo/proyecto Android (`android/` en este repo o repo aparte — decidir y anotar en Bitácora).
- [ ] Configurar Firebase Emulator Suite (`firebase/firebase.json`).

### Fase 1 — Esqueleto
- [ ] Proyecto Gradle (Kotlin DSL, version catalog `libs.versions.toml`), Compose, Hilt, Navigation, Firebase BoM.
- [ ] `core/designsystem`: colores, tipografía, formas, componentes (PetGoButton, PetGoCard, GradientTopBar, AvatarImage, StatusChip, LoadingOverlay).
- [ ] `MainActivity` + Splash + NavHost con grafos por rol.
- [ ] Modelos `domain` + `ValidationResult` + `PetBreeds`.

### Fase 2 — Auth y perfil (A1–A7, P1–P7)
- [ ] Repos Auth/User, `SessionRepository`.
- [ ] Welcome, Login, Register, Profile, Walker profile.
- [ ] Cloudinary + compresión de imagen + Photo Picker.
- [ ] Tests de `RegisterUser`, `Login`, `UpdateWalkerProfile` (≤60 palabras) — portar `UserServiceTest`, `WalkerServiceTest`, `ProfileServiceTest`.

### Fase 3 — Mascotas (M1–M5)
- [ ] `ValidatePet` (portar `PetServiceTest` y `ValidationResultTest`).
- [ ] PetList, PetForm, detalle.

### Fase 4 — Tickets (T1–T9)
- [ ] `ValidateTicket`, `SortTicketsByStatus` (portar `TicketServiceTest`).
- [ ] TicketForm, MyTickets (acciones por estado), AvailableTickets.
- [ ] Batch para eliminar ticket + postulaciones.

### Fase 5 — Postulaciones (N1–N5)
- [ ] Apply, CounterOffer, Accept (transacción), Reject (portar `ApplicationServiceTest`).
- [ ] ReceivedApplications, MyApplications.

### Fase 6 — Calificaciones (R1–R2)
- [ ] RateWalk (transacción + agregados), WalkerStats.
- [ ] Diálogo de calificación con estrellas y sliders de emojis; WalkerReviews.

### Fase 7 — Endurecimiento
- [ ] `firestore.rules` completas + tests con `@firebase/rules-unit-testing`.
- [ ] Índices desplegados.
- [ ] App Check activado (enforcement).
- [ ] (Nivel B) Cloud Functions: `acceptApplication`, `onReviewCreated` (agregados), triggers FCM.

### Fase 8 — Extras
- [ ] FCM: nueva postulación → cliente; contraoferta → paseador; aceptación → paseador; paseo iniciado/finalizado → paseador.
- [ ] Modo oscuro, accesibilidad (contentDescription, tamaños táctiles 48 dp).
- [ ] Analytics de eventos clave.

### Fase 9 — Datos y salida
- [ ] Script de migración (sección 7) contra emulador → contra `petgo-dev` → contra `prod`.
- [ ] CI: Azure Pipelines con Gradle (`./gradlew lint testDebugUnitTest assembleRelease`), JaCoCo + SonarCloud, `google-services.json` como secure file, App Distribution para QA.
- [ ] Pruebas E2E manuales con 1 cliente y 2 paseadores (flujo completo: crear ticket → 2 postulaciones → contraoferta → aceptar → iniciar → finalizar → calificar → ver stats).
- [ ] Publicación en Play Console (track interno).

---

## 10. Pruebas

| Hoy | Android |
|---|---|
| `*ServiceTest` (JUnit5 + Mockito) | Tests unitarios de `usecase` (JUnit + MockK + Turbine para Flows) — **portar cada caso** |
| `*DAOTest` | Tests de repositorio contra **Firestore Emulator** (instrumentados o con Robolectric) |
| — | Tests de Security Rules (`firebase/rules-tests`) |
| — | Tests de UI Compose (`createAndroidComposeRule`) para formularios con validación |
| JaCoCo + Sonar (excluye controller/entities) | JaCoCo + Sonar (excluir `ui/**/Screen*`, `di/**`, `dto/**`) |

---

## 11. Bitácora de decisiones

| Fecha | Decisión | Motivo |
|---|---|---|
| 2026-11-12 | Firestore como BD y Firebase Auth | Solicitud del equipo; integración nativa con Android |
| 2026-11-12 | Empezar en Nivel A (Spark) con transacciones de cliente; Functions opcional | Evitar costo del plan Blaze al inicio |
| 2026-11-12 | Repo aparte `PetGo-Android`, paquete `ec.edu.epn.petgo`, módulo único `:app` con paquetes core/domain/data/di/ui | Más simple para el equipo; se puede modularizar después |
| 2026-11-12 | Toolchain: AGP 9.3.3 (Kotlin integrado, sin `kotlin-android`), Gradle 9.5.0, Kotlin 2.3.20, KSP 2.3.6, Hilt 2.59, Compose BOM 2026.08.00, Firebase BoM 35.1.0, compileSdk 37 / target 36 / min 26 | Versiones estables verificadas a nov-2026; Hilt 2.59+ y androidx.hilt 1.4 exigen AGP 9 |
| 2026-11-12 | Se guarda `photoUrl` (URL de descarga) en vez de `photoPath` | Coil la carga directo, sin resolver referencias |
| 2026-11-12 | Sin agregados de rating en `users`; `WalkerStats` se calcula de la lista de reviews | Evita reglas que dejen a un cliente escribir el perfil del paseador |
| 2026-11-12 | La foto de la mascota se sube ANTES de crear el doc | Garantiza que ninguna mascota quede sin foto |
| 2026-11-12 | **Imágenes en Cloudinary (unsigned preset) en vez de Firebase Storage** | Storage exige plan Blaze (tarjeta) desde 2024; Cloudinary free no la pide. Mitigación: preset limitado a jpg/png ≤2 MB y Firestore solo acepta URLs de `res.cloudinary.com`. La app no puede borrar imágenes (requiere secret) |
| 2026-11-12 | Aceptar postulación: si acepta el cliente, una transacción rechaza las demás; si acepta el paseador (contraoferta) no puede tocar postulaciones ajenas → `ReconcileClientApplicationsUseCase` las rechaza desde el dispositivo del cliente | Nivel A sin Cloud Functions; en Nivel B lo hace un trigger `onUpdate(tickets)` |
| 2026-11-13 | UI: las postulaciones del cliente se muestran dentro del detalle de cada paseo (no en una lista aparte) | Decidir una oferta requiere el contexto del paseo; menos navegación |
| 2026-11-13 | UI: perfil y perfil profesional unificados; disponibilidad también como switch en el inicio del paseador | La web los tenía en dos páginas distintas |
| 2026-11-13 | NUEVO: "Mis paseos" para el paseador (`observeWalkerTickets`) y botón Llamar en ambos lados | En la web el paseador no veía sus paseos confirmados ni tenía cómo contactar al dueño |
| 2026-11-13 | Textos de UI escritos directamente en español en los Composables (no en strings.xml) | App de un solo idioma; mover a recursos si se internacionaliza |
| 2026-11-12 | Emails normalizados a minúsculas (registro y reglas) | El token de Auth guarda el email en minúsculas |
| 2026-11-12 | Mensajes de validación en español dentro de `domain/validation` (no en strings.xml) | El dominio es Kotlin puro y testeable en JVM; si se internacionaliza, cambiar a códigos de error |
| 2026-11-12 | Crashlytics/Analytics/App Check/FCM se añaden en Fases 7–8 | Crashlytics requiere su plugin de Gradle; no bloquear el arranque |
