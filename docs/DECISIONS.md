# Decisiones de Pausa

Breve y por orden de prioridad del proyecto: privacidad, seguridad, estabilidad, experiencia, funciones.
Cada punto dice qué se decidió y por qué, para poder cambiarlo con criterio.

## Privacidad

- **Sin internet, sin cuenta, sin analítica.** El manifiesto no pide `INTERNET`. Lo que Pausa sabe (reglas, uso,
  reacciones) vive en el teléfono y se borra desde Ajustes.
- **Lo que lee el servicio de accesibilidad es lo mínimo.** Qué app está al frente y, solo en apps con una regla de
  sección (Shorts, Reels), la estructura de la pantalla: ids de vistas y el nombre de la pestaña seleccionada. Nunca
  texto, mensajes ni contenido.
- **Solo se mide el uso de apps con una regla activa.** Una app que nadie pidió vigilar no se cronometra.

## Seguridad

- **PIN y patrón se guardan como hash salado** (PBKDF2-HMAC-SHA256, 120 000 iteraciones). Nunca en claro.
- **Los intentos se cuentan en un solo sitio para todo el proceso** (`AttemptLimiter`), no dentro de cada diálogo:
  cerrar y reabrir ya no da intentos nuevos. Tras 5 fallos hay una espera que se duplica (30 s hasta 5 min). Usa el
  reloj desde el arranque, así cambiar la fecha del teléfono no la acorta. El contador vive en memoria: se reinicia
  si matan el proceso. Frena el impulso y la curiosidad, no a alguien decidido.
- **Un patrón mal dibujado mientras otro se comprueba se ignora**, para que dos trazos a la vez no cuenten como un
  solo intento.
- **El modo estricto es una promesa dentro de la app.** Android no permite impedir que se apague el servicio ni que se
  desinstale, y Pausa no lo intenta. Lo que sí hace: cada acción que debilita una protección vuelve a preguntar, al
  momento de actuar y con lo guardado ahora (`RuleLockChecker`), si el modo estricto empezó a bloquear mientras la
  pantalla estaba abierta. Añadir una excepción y "Borrar todo" esperan mientras haya una concentración o un
  bloqueo estricto en curso.
- **Una regla ilegible bloquea, no desaparece.** Si una fila guardada no se puede leer, se trata como "siempre
  bloqueada", se avisa en la lista y se registra. Perder una regla en silencio dejaría pasar la app sin que nadie se
  entere. Nunca conserva el modo estricto, para que se pueda arreglar.
- **El servicio no se habilita solo.** Android pide que lo active la persona; ningún atajo por `adb` en el producto.

## Estabilidad

- **Falla cerrado donde protege, abierto donde encerraría.** Hasta cargar el uso del día no se aplica nada, para que
  un límite no empiece de cero tras un reinicio; pero un patrón que no se puede comprobar nunca deja a nadie fuera.
- **Todo lo que decide se lee antes de aplicar nada** (reglas, ajustes, concentración, pases, uso) y se reintenta si
  el almacenamiento falla. Cada observador y el chequeo periódico se reinician solos; una excepción suelta se
  registra, no tumba el proceso.
- **Si el sistema rechaza mostrar el bloqueo, se manda a inicio.** Un bloqueo que no aparece sigue debiendo cerrar la
  app, y solo cuenta en las estadísticas si se vio.
- **La sesión en curso se anota cada 30 s** (`SessionCheckpoint`). Si el proceso muere se recupera hasta la última
  anotación, nunca hasta "ahora" (el teléfono pudo estar apagado), con tope de 12 h y partida en la medianoche. Hay un
  caso residual de doble conteo si el proceso muere en los milisegundos entre guardar la sesión y borrar la nota.
- **Samsung deja el servicio sin conectar tras reinstalar** (y a veces tras matar el proceso). El inicio muestra
  "Sin respuesta" y explica cómo arreglarlo, porque aparecer activa en Ajustes no basta.
- **Ajustes dañados no rompen la app.** Si el archivo se corrompe, arranca vacío y se registra (se pierden PIN,
  patrón y excepciones, pero la app no falla en cada lectura).
- **Datos acotados.** Sesiones sin procesar 30 días, reacciones 180 días; se purgan al conectar el servicio.
- **Zona horaria dinámica.** El reloj inyectado pregunta la zona del teléfono en cada uso; "hoy" cambia si se viaja.

## Diseño

- **Dos temas de cristal (Claro y Oscuro), elegibles en Ajustes** (`ThemeMode`: Sistema, Claro, Oscuro; las pantallas
  sobre otras apps lo leen del servicio). Ver "Diseño elegido para la app". Ámbar para lo que hay que arreglar, nunca
  rojo de alarma. Las pantallas sobre otra app muestran el icono de esa app dentro de una esfera de cristal.
- **Adaptable.** Con poca altura (teléfono de lado) o pantalla ancha, la navegación pasa a una barra lateral, Inicio
  se parte en dos columnas y las pantallas de pausa, bloqueo y patrón se compactan y hacen scroll. El contenido no
  pasa de 720 dp de ancho.
- **Sin botón flotante en Apps:** tapaba filas al girar. "Añadir app" va junto al título.
- **Bienvenida:** una idea por página, acción abajo a la derecha (etiqueta + botón redondo), Atrás del sistema vuelve
  a la página anterior.
- **Accesibilidad:** objetivos de 48 dp o más, interruptores con nombre ("Controlar X"), títulos como encabezados,
  respeta "quitar animaciones" y el flag de atrás predictivo.

## Dependencias

Compose, Room, DataStore, Hilt y Navigation (fijada a 2.9.8 por compatibilidad con AGP). Haze 1.7.2
(`dev.chrisbanes.haze`) para el desenfoque real del fondo: es la única dependencia añadida por el diseño. Sin analítica,
sin bibliotecas de imágenes ni de red. Una caché de iconos propia (64 entradas) en lugar de una biblioteca.

## Pendiente a propósito

- Probar en teléfono los bloqueos tras cada reinstalación exige apagar y encender el servicio a mano.
- La licencia del código no está definida.
- Solo para depuración, hay que acordarse de que existen: sin espera de confirmación (`Friction.kt`) y descanso de 1 min
  (`DashboardScreen.kt`), ambos con `BuildConfig.DEBUG`.
- Decidir dónde se inicia una sesión de concentración: se quitó de Inicio y no hay otro punto de entrada.
- Verificar con el servicio activo: pantalla de bloqueo/pausa con video sobre otra app, descanso en uso, salida con
  "volver atrás" e icono en la pantalla de inicio.
- El Laboratorio (pestaña de depuración) se quitará más adelante.
- El video de fondo: si pesa en batería, limitarlo a dentro de la app.

## Propuesta de diseño v2 (vista previa en `design/preview`)

Parte del paquete de Stitch "Obsidian Mineral Glass" y de una referencia de estadísticas por categoría, filtrada por
`PRODUCT.md` (nada gamificado, pocas cifras, tono firme sin regañar).

- **Se toma:** el orbe de vidrio con anillo de degradado lavanda a cian como estado de la pantalla de inicio; el dock
  flotante en píldora (el único sitio con desenfoque, porque el contenido se desplaza debajo); el anillo de puntos y el
  carrusel de tarjetas por app para las estadísticas, con una fila de detalle debajo; "mantener pulsado" para terminar
  una concentración antes; la variante clara de la pantalla de bloqueo con el orbe grande; el ámbar para lo estricto.
- **Se deja fuera:** la "Puntuación Opal", los niveles y la recompensa mineral, las frases motivacionales y las tres
  cifras de sueño, enfoque y calma. Son lo que `PRODUCT.md` descarta (gamificado, lleno de números, regañón).
- **Se ajusta:** las tarjetas redondeadas de 32 a 48 px pasan a 20 px y las filas de apps ya no son tarjetas; las
  etiquetas en mayúsculas con tracking se quitan (frases normales); el texto con degradado no se usa, el degradado solo
  va en trazos de anillo; el color base sigue siendo la lavanda de Noche, con el cian solo en el degradado.
- **Pendiente de decidir con el usuario:** si el orbe sustituye al anillo plano en la app real y si se adopta el dock
  flotante en lugar de la barra inferior de Material.
## Diseño elegido para la app (vista previa en `design/preview`)

Tras comparar cinco propuestas (archivadas en `design/preview-v1`) quedan dos temas, que el usuario aprobó en la vista previa:

- **Claro:** estudio plateado con **esferas multicolor** (estado de Inicio, hora de Enfoque, respiración de Pausa, app de Bloqueo, apps de Estadísticas), cada una con su halo de colores, sobre una malla de color que se mueve despacio. **Sin cápsulas**: botones, filas y selectores son rectángulos redondeados; la forma protagonista es la esfera.
- **Oscuro:** composiciones de cristal con **la cápsula como forma de casi todo** (chips de duración, filas de apps, estado, botones secundarios, barras, dock) y **cápsula 3D de cristal** en la acción principal de cada pantalla; el fondo es el video con rejilla y cruces, dentro de cada pantalla.
- Los botones 3D usan WebGL: se crean al verse y se liberan al salir (máximo 4 contextos). Con movimiento reducido no hay animación ni video automático.

## Diseño en la app (Compose)

Los dos temas ya están portados. Lo que se hizo y por qué:

- **Material de cristal** (`ui/common/Glass.kt`): esfera (cuerpo translúcido, brillo, luz de color), borde prismático
  (degradado en barrido), halo difuminado, y `glassSurface` con desenfoque real del fondo (Haze, 24 dp) más tinte y
  borde. El cristal real con refracción no existe en Compose: es una aproximación. La cápsula 3D de WebGL no se portó;
  la acción principal usa el mismo cristal con un tinte (`emphasized`).
- **Fondo** (`ui/common/Backdrop.kt`, `BackdropVideo.kt`): Oscuro lleva un video en bucle (MediaPlayer + TextureView,
  recorte centrado, silenciado, en pausa al salir de pantalla) sobre una aurora animada que sirve de respaldo; Claro lleva
  una malla de color que deriva despacio. Haze funciona sobre el TextureView (comprobado). Con "quitar animaciones" no
  hay movimiento.
- **Controles unificados** (`GlassControls.kt`, `GlassDialog.kt`): los botones, interruptores y diálogos de Material se
  sustituyeron por envoltorios con el mismo nombre, para que ningún botón quede con relleno blanco y los modales
  (PIN, patrón) tengan el mismo diseño. Los diálogos son opacos: nada de la pantalla de atrás debe verse tras el texto.
- **Colores de texto:** las pantallas son transparentes sobre el fondo, así que `PausaTheme` entrega `LocalContentColor`
  (si no, el texto sale negro en Oscuro).
- **Descanso:** en Inicio, 10/15/30 min u "Otro tiempo", con cuenta atrás en "Terminar descanso". Es un permiso
  temporal para todas las apps con reglas; no anula las reglas estrictas ni la concentración.
- **Menos texto en Ajustes** y permisos plegables; la pantalla de bloqueo ya no ofrece "Permitir 10 min".
- **Estadísticas por intentos, no por minutos.** "Hoy intentaste abrir" cuenta por app: aperturas (sesiones) + bloqueos +
  pausas canceladas. Una pausa que se atravesó no suma otra vez, porque la sesión que sigue ya la cuenta. Los minutos por
  app salieron de la pantalla porque se calculaban a partir de sesiones y eran difíciles de explicar; la estimación de
  "tiempo ahorrado" también.
- **Con la regla en "Siempre" se ocultan** patrón, tiempo al día, aperturas, pausa y secciones: nunca se alcanzarían. Los
  valores se conservan en el borrador por si se cambia de modo.
- **Al terminar un descanso, o terminarlo antes,** la app abierta se vuelve a evaluar (antes se quedaba abierta hasta
  salir): la sección (Shorts) se vuelve a leer y la pausa se pide otra vez. El chequeo cae justo al vencer el permiso.

## Idiomas

- Inglés por defecto (`res/values`) y español (`res/values-es`); `locales_config.xml` los declara para el selector de
  idioma por app de Android 13+. Los textos viven en recursos (`strings_*.xml`, con prefijo por pantalla) y, en código sin
  pantalla, se leen con `Context`: `RuleText`, `BlockText` y los avisos (`RuleMessages`, ids de recurso).
- Los nombres de día salen de `java.time` con la configuración regional del teléfono.
- Textos de confirmación (`Friction`) se leen con `friction.text()`/`quantity()`, porque el lint prohíbe leer recursos
  desde `LocalContext` dentro de las pantallas.
- Sin traducir a propósito: el Laboratorio, que se quitará. "Shorts" y "Reels" son nombres propios.
- **Icono nuevo:** esfera y escudo sobre la aurora (adaptativo, con capa monocromática para iconos temáticos).
- **Costo:** el video pesa 7 MB y gasta algo de batería; Haze exige `minSdk 31` (ya lo es).
