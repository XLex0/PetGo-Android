# PetGo Android

App Android nativa de **PetGo** (paseo de mascotas), migrada desde la webapp Jakarta EE `GR06-ISWD622-25A`.
Kotlin · Jetpack Compose (Material 3) · MVVM + Clean Architecture · Hilt · Firebase (Auth, Firestore) · Cloudinary (imágenes).

> La guía completa de la migración (funcionalidades, modelo de datos, bugs a no replicar, fases) está en
> [`docs/MIGRACION_ANDROID.md`](docs/MIGRACION_ANDROID.md). **Léela antes de tocar el código.**

## Estado por capas

| Capa | Paquete | Estado |
|---|---|---|
| Proyecto Gradle | `/`, `app/` | ✅ |
| Sistema de diseño | `core/designsystem` | ✅ colores/tipografía/formas de `styles.css` + componentes |
| Dominio | `domain/` | ✅ modelos, validadores, contratos, casos de uso + tests |
| Datos | `data/` | ✅ repositorios Firebase, subida a Cloudinary, DTOs, mappers, compresión de imágenes |
| DI | `di/` | ✅ módulos Hilt (+ switch al emulador) |
| Reglas Firebase | `firebase/` | ✅ Firestore + emuladores |
| UI | `ui/` | ✅ navegación por rol + 15 pantallas (ver guía §5.2) |

## Requisitos

- Android Studio reciente con soporte para **AGP 9.3** (JDK 17).
- Proyecto en [Firebase Console](https://console.firebase.google.com) (plan **Spark**, gratis) con **Authentication (Email/Password)** y **Firestore** habilitados.
- Cuenta gratuita de [Cloudinary](https://cloudinary.com) (sin tarjeta) para las fotos.
- Firebase CLI (`npm i -g firebase-tools`) para emuladores y despliegue de reglas.

## Puesta en marcha

1. **Abrir** la carpeta en Android Studio. Si falta el wrapper, ejecuta `gradle wrapper --gradle-version 9.5.0`
   (o deja que Android Studio lo regenere).
2. **Registrar la app** en Firebase con el paquete `ec.edu.epn.petgo`, añadir los SHA-1/SHA-256 de debug
   (`./gradlew signingReport`) y descargar **`google-services.json` a `app/`**. Este archivo está en `.gitignore`: no lo subas.
3. **Configurar Cloudinary** (Console → Settings → Upload → *Upload presets* → *Add upload preset*):
   - **Signing mode: Unsigned**, nombre p. ej. `petgo_unsigned`.
   - *Allowed formats*: `jpg,png` · *Max file size*: 2 MB · *Overwrite*: desactivado.
   - (Opcional) *Incoming transformation*: `c_limit,w_1280,h_1280,q_auto` para que nunca se guarde una foto gigante.
   - Copia tu **Cloud name** (Dashboard) y agrega a `local.properties` (no se sube a git):
     ```properties
     cloudinary.cloudName=tu_cloud_name
     cloudinary.uploadPreset=petgo_unsigned
     ```
4. **Desplegar reglas de Firestore:**
   ```bash
   cd firebase
   firebase use --add            # elige tu proyecto
   firebase deploy --only firestore:rules,firestore:indexes
   ```
5. **Correr la app** (`Run ▶` o `gradlew.bat assembleDebug` + `adb install`). Abre la pantalla de bienvenida.

### Desarrollo con emuladores (recomendado)

```bash
cd firebase && firebase emulators:start      # UI en http://localhost:4000
```
y en `app/build.gradle.kts` cambia `USE_FIREBASE_EMULATOR` del build `debug` a `"true"`.
Desde el emulador de Android la PC se ve como `10.0.2.2` (ya configurado en `FirebaseModule` y `network_security_config.xml`).

## Tests

```bash
./gradlew testDebugUnitTest
```
Cubre validadores (port de `PetServiceTest`, `TicketServiceTest`, `WalkerServiceTest`, `ValidationResultTest`),
máquina de estados del ticket, orden de listas, estadísticas del paseador y casos de uso (login, registro con rollback,
aceptación/rechazo de postulaciones y contraofertas).

## Estructura

```
app/src/main/java/ec/edu/epn/petgo/
├── core/designsystem   Theme, Color, Type, Shape, components/
├── core/result         AppResult / AppError (+ mensajes en español)
├── domain/model        User, Pet, Ticket, Application, Review, enums, PetBreeds
├── domain/validation   ValidationResult + validadores (reglas de los *Service web)
├── domain/repository   Contratos (antes: DAOs)
├── domain/usecase      auth, profile, pet, ticket, application, review
├── data/remote         DTOs Firestore + traducción de errores Firebase
├── data/mapper         DTO ↔ dominio
├── data/repository     Auth/Firestore (transacciones y batches) + CloudinaryImageRepository
├── data/image          Compresión JPEG ≤ 2 MB + config/URLs de Cloudinary
├── di/                 Módulos Hilt
├── ui/common           ViewModel base, Snackbar, formatos, selectores (fecha/hora/duración/raza/foto)
├── ui/navigation       Rutas type-safe, PetGoRoot (grafo por sesión), shells con barra inferior por rol
└── ui/feature          auth · client · walker · profile
firebase/               firestore.rules, firestore.indexes.json, firebase.json
docs/                   Guía de migración
```

## Sobre Cloudinary (leer)

- El plan gratuito da 25 créditos/mes (1 crédito ≈ 1 GB almacenado o 1 GB de descarga o 1.000 transformaciones).
  En las listas usa `CloudinaryUrls.thumbnail(url, px)` para descargar miniaturas y no la foto completa.
- Riesgo del preset *unsigned*: quien extraiga su nombre del APK podría subir archivos a la cuenta. Mitigaciones:
  formatos y tamaño limitados en el preset, y Firestore solo acepta URLs de `res.cloudinary.com`.
  Si se necesita más control, el siguiente paso es firmar las subidas con un backend.
- La app no puede borrar imágenes (requiere el API secret). Las fotos reemplazadas quedan huérfanas;
  se limpian desde la consola (filtrando por tag `petgo`) o con un script con el secret.
