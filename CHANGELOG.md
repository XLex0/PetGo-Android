# Changelog — PetGo Android

Historial de versiones de la migración de la webapp PetGo (Jakarta EE) a Android nativo
(Kotlin · Jetpack Compose · Hilt · Firebase · Cloudinary). Las fases hacen referencia a
`docs/MIGRACION_ANDROID.md` §9.

## [1.0.0] — Postulaciones, calificaciones y paseador
Fases 5 y 6 · funcionalidades N1–N5, R1–R2
- Repositorios de postulaciones y reseñas (aceptación de postulación en transacción).
- Casos de uso: postular, contraoferta, aceptar, rechazar; calificar paseo y estadísticas del paseador.
- Módulo del paseador: tickets disponibles, mis postulaciones, reseñas y estadísticas.
- Tests de casos de uso (login, registro con rollback, aceptación/rechazo y contraofertas).

## [0.4.0] — Solicitudes de paseo (tickets)
Fase 4 · funcionalidades T1–T9
- `TicketRepository` con batch para eliminar ticket + postulaciones.
- Validación de ticket, máquina de estados y orden de listas por estado.
- Pantallas del cliente: inicio, mis paseos, formulario de ticket y acciones por estado.

## [0.3.0] — Gestión de mascotas
Fase 3 · funcionalidades M1–M5
- `PetRepository` sobre Firestore (la foto se sube antes de crear el documento).
- Casos de uso y validación de mascotas.
- Pantallas: lista, formulario (crear/editar) y detalle de mascota.

## [0.2.0] — Firebase, autenticación y perfil
Fase 2 · funcionalidades A1–A7, P1–P7
- Reglas e índices de Firestore y configuración de emuladores.
- DTOs, mappers y traducción de errores de Firebase.
- Repositorios Auth/User y subida de imágenes a Cloudinary con compresión ≤ 2 MB.
- Contratos de repositorio, casos de uso de autenticación y perfil, módulos Hilt.
- Pantallas: bienvenida, login, registro, perfil y perfil de paseador.
- Navegación type-safe con grafo por sesión y barra inferior por rol.

## [0.1.0] — Esqueleto del proyecto y dominio
Fase 1
- Proyecto Gradle (Kotlin DSL, version catalog), AGP 9.3, Gradle 9.5.0.
- Sistema de diseño PetGo (colores, tipografía, formas, componentes).
- `AppResult` / `AppError` con mensajes en español.
- Modelos de dominio, razas de mascotas y validadores portados de los `*Service` web.
- Guía de migración y README.
