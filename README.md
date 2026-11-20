# Pausa

Una pausa entre el impulso y la app. Pausa frena, o bloquea, las apps que te distraen según las reglas que tú
decides: por horario, por tiempo al día, por número de aperturas o por una sección concreta (Shorts de YouTube,
Reels de Instagram). Todo ocurre en el teléfono: sin cuenta, sin permiso de internet y sin copias en la nube.

> Estado: primera versión, de uso personal. Probada en un Galaxy S26 Ultra con Android 17 (One UI 9).

## Qué hace

- **Reglas por app.** Bloqueo total, por horario (varios tramos y días), por tiempo diario, por aperturas al día,
  y una pausa antes de abrir (con ejercicio de respiración opcional).
- **Descanso.** Desde Inicio, 10, 15 o 30 minutos (o el tiempo que elijas) sin bloqueos ni pausas en las apps con
  reglas, con un temporizador de lo que falta y un botón para terminarlo antes. Las reglas estrictas no se saltan.
- **Modo concentración.** El motor bloquea todo lo que controlas durante un tiempo; por ahora no hay botón para
  iniciarlo en Inicio (se quitó, falta decidir dónde va).
- **Secciones dentro de una app.** Bloquea Shorts o Reels sin bloquear toda la app, y te devuelve a su pestaña de
  Inicio. Funciona leyendo la estructura de la pantalla; como las apps cambian sin avisar, es de mejor esfuerzo.
- **Excepciones.** Apps que siempre pasan. Las pantallas de bloqueo ya no ofrecen "permitir 10 minutos": para eso
  está el descanso.
- **Al bloquear una app** eliges si Pausa te lleva a Inicio o solo vuelve atrás (Ajustes).
- **Seguridad contra el impulso.** PIN y patrón opcionales (guardados solo como hash con sal), espera antes de
  apagar una regla y modo difícil: una regla que está bloqueando no se puede cambiar hasta que termine.
- **Patrón por app.** Puede pedir tu patrón de seguridad antes de abrir una app.
- **Estadísticas locales,** sin gráficas que abrumen: racha, aperturas evitadas, tiempo por app.

## Privacidad

Es la prioridad más alta y se puede comprobar:

- **No declara el permiso `INTERNET`.** Android impide por construcción que la app envíe algo fuera.
- **Sin cuenta, sin analíticas, sin rastreadores.**
- **Copias de seguridad desactivadas** (`allowBackup=false` y reglas de extracción que excluyen todo).
- **El único acceso que pide** es el servicio de accesibilidad, que activas tú a mano. Lo usa para saber qué app está
  en pantalla y mostrar la pausa o el bloqueo. Solo anota nombres de paquete y horas de las apps que controlas.
- **Borrar todos mis datos** está en Ajustes.

## Cómo está hecha

Tres módulos, con las dependencias en una sola dirección (`app` → `data` → `domain`):

| Módulo | Contenido |
|---|---|
| `:domain` | Kotlin puro, sin Android. Motor de reglas, decisiones de bloqueo, estadísticas, hashes. Se prueba en la JVM. |
| `:data` | Room (reglas, uso, eventos) y DataStore (ajustes), detrás de repositorios. |
| `:app` | Interfaz con Jetpack Compose, Hilt y el servicio de accesibilidad con las pantallas que se dibujan sobre otras apps. |

Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, coroutines y Haze. `minSdk 31`, `targetSdk 36`.

**Diseño: dos temas de cristal** (Ajustes → Apariencia: Sistema, Claro u Oscuro). **Claro:** estudio plateado con esferas
multicolor. **Oscuro:** cápsulas de cristal sobre un video de fondo en bucle (`res/raw/pausa_bg.mp4`, 7 MB, silenciado).
El desenfoque real del fondo usa [Haze](https://github.com/chrisbanes/haze). Tipografía Space Grotesk (incluida, con su
licencia en `licenses/`). La estrategia de diseño está en [`PRODUCT.md`](PRODUCT.md), las decisiones técnicas en
[`docs/DECISIONS.md`](docs/DECISIONS.md) y las vistas previas web de los diseños en `design/` (archivo de referencia,
no forman parte de la app).

## Compilar y probar

Hace falta Android Studio (o el SDK de Android con la plataforma 36) y JDK 17 o superior.

```bash
./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest   # pruebas
./gradlew :app:assembleDebug                                            # APK de depuración
./gradlew :app:lintDebug :data:lintDebug                                # lint
```

Después de instalar, activa **Pausa** en Ajustes → Accesibilidad. Si el interruptor sale gris, abre la información
de la app, toca ⋮ y elige «Permitir ajustes restringidos» (Android lo pide con apps instaladas fuera de la tienda).

## Límites conocidos

- Android no ofrece una API oficial para reconocer secciones dentro de otras apps: Shorts y Reels se detectan por
  su estructura y pueden dejar de funcionar cuando YouTube o Instagram cambian.
- Pausa no impide apagar su servicio ni desinstalarla, a propósito: busca poner fricción al impulso, no tomar el control
  del teléfono.
- Si Android detiene el servicio, se recupera la sesión hasta la última anotación (cada 30 s), no hasta el instante exacto.
- El video de fondo cuesta algo de batería; se pausa cuando la app sale de pantalla.
- Algunos fabricantes cierran los servicios en segundo plano; en Samsung conviene quitar Pausa de las apps en suspensión.

## Licencias

La licencia del código todavía no está definida (sin ella, por defecto, todos los derechos quedan reservados al autor).
La fuente Space Grotesk se distribuye bajo la SIL Open Font License 1.1
(`licenses/SpaceGrotesk-OFL.txt`).
