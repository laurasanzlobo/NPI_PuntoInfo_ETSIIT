# T4, etapa 2 — mapa y rutas peatonales

> **Histórico, sustituido:** el flujo actual no usa mapas, claves ni proveedores de rutas.
> Consulta [GUIADO_EXTERIOR_FIJO.md](GUIADO_EXTERIOR_FIJO.md). No seguir la configuración ORS de este documento.

## Uso y configuración

1. Crea una cuenta y una clave para **Directions** en <https://account.heigit.org/> (openrouteservice/HeiGIT). El servicio ofrece acceso gratuito con cuota, no es un servidor de demostración. Consulta los límites vigentes de tu cuenta: <https://account.heigit.org/info/plans> y sus condiciones: <https://account.heigit.org/info/tos>. No se ha creado una cuenta ni aceptado condiciones en nombre del equipo.
2. Añade al `local.properties` de la raíz, **sin borrar `sdk.dir` ni otras propiedades**:

   ```properties
   ORS_API_KEY=tu_clave_personal
   ```

   Alternativamente, configura `ORS_API_KEY` en el entorno de Gradle (tiene prioridad). No añadas la clave a Git, a capturas ni a comandos compartidos. `local.properties` ya está ignorado. Sin clave, el mapa funciona y aparece el bloqueo de rutas; el botón de cálculo queda deshabilitado.
3. Compila e instala:

   ```bash
   ./gradlew :app:assembleDebug :app:testDebugUnitTest
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

La clave se incorpora al APK mediante `BuildConfig`: evita subir también APK, directorios de compilación y cachés con credenciales. **Una clave dentro de una aplicación Android es extraíble**. Este mecanismo es para la práctica y dispositivos de confianza; una publicación requeriría un proxy propio con control de cuota, no una clave privilegiada distribuida a usuarios.

## Prueba en el Samsung S22

- Abre **Comedores**, pulsa el icono «¡Llévame!» o realiza el giro de muñeca existente. Ambos llaman a la misma función y abren **Mapa exterior · A pie**, con Aynadamar seleccionado (es el comedor que muestra actualmente esta pantalla).
- Comprueba zoom con dos dedos, desplazamiento, selección por marcadores o desplegable y **Centrar mapa**. Hay cuatro comedores y ETSIIT, todos con coordenadas del catálogo existente.
- Pulsa **Mi ubicación**; concede permiso preciso o aproximado. Comprueba la marca azul y la precisión indicada. Si se deniega permanentemente, usa **Abrir permisos en Ajustes**; si la localización está desactivada, **Activar ubicación**. Volver de Ajustes no envía posiciones automáticamente.
- Con Internet y una clave válida, lee el aviso y pulsa **Calcular ruta a pie**. Se obtiene una fijación nueva y se solicita un recorrido `foot-walking`. Deben aparecer la geometría azul del proveedor, distancia de ruta, tiempo estimado si existe y las instrucciones en español. La distancia **en línea recta** está etiquetada por separado y nunca se dibuja como ruta.
- Camina y vuelve a pulsar para recalcular. No hay seguimiento continuo, avance automático de instrucciones, avisos de desvío ni voz. Es un mapa de guiado con instrucciones consultables y actualización manual, no un navegador giro a giro en tiempo real.
- Cambia de destino: desaparece la ruta anterior y se cancela su petición. Vuelve a calcular únicamente cuando quieras compartir la nueva posición.
- Prueba permiso denegado, localización desactivada, falta de fijación, Internet desactivado y clave inválida. Un destino no accesible por caminos cercanos debe producir error, nunca una recta sustitutiva.
- Pulsa Atrás: debe regresar a Comedores con su calendario. Prueba físicamente que la inclinación para reservar un **día futuro** sigue mostrando la confirmación. No se ha cambiado ese reconocimiento ni la persistencia.
- Abre **Guiado**: continúa siendo el explorador y navegador interior original. Los destinos interiores de Horario/Profesores continúan usando `navigation?...`.

Las pruebas instrumentadas instalan paquetes de prueba y pueden afectar al estado de instalación según la configuración de AGP. No las uses como prueba de conservación de datos reales; para la comprobación manual usa `adb install -r`. Durante las pruebas deja el teléfono desbloqueado y sin interactuar con otras aplicaciones:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

## Tecnología, condiciones y disponibilidad

- **MapLibre Native Android 13.7.1**, versión estable publicada en Maven Central, sin SDK comercial ni tarjeta. Integración con Compose mediante `AndroidView`, con ciclo de vida `start/resume/pause/stop/destroy`. Capas GeoJSON para puntos y ruta; no se usan anotaciones antiguas de MapLibre ni se sustituye ningún mapa interior.
- **OpenFreeMap**, estilo <https://tiles.openfreemap.org/styles/liberty>. Su [guía](https://openfreemap.org/quick_start/) admite expresamente clientes móviles MapLibre; la [instancia pública](https://openfreemap.org/) permite uso gratuito sin clave y no es solo una demo. [Condiciones](https://openfreemap.org/tos/): servicio sin garantía/SLA. No se implementan descargas offline masivas. Se conservan las atribuciones del mapa y se ofrecen enlaces a OpenFreeMap, OpenMapTiles y OpenStreetMap.
- **openrouteservice**, `POST https://api.openrouteservice.org/v2/directions/foot-walking/geojson`. HTTPS, coordenadas `[longitud, latitud]`, metros, segundos e instrucciones en español. [Contrato](https://giscience.github.io/openrouteservice/api-reference/endpoints/directions/requests-and-return-types) y [errores](https://giscience.github.io/openrouteservice/api-reference/error-codes). Se muestra atribución a openrouteservice/HeiGIT y a OpenStreetMap. No se usa OSRM de coches ni un endpoint público de demostración.
- Se han comprobado el estilo público y la accesibilidad de la API de rutas (HTTP 401 sin credenciales). **Esto no prueba un recorrido autenticado**. La obtención de una clave, las condiciones/cuota de la cuenta y la validación de rutas reales en Granada quedan a cargo del equipo. No hay disponibilidad garantizada de ninguno de los dos proveedores.
- Los puntos existentes son referencias del catálogo, no entradas peatonales verificadas. No se han inventado ni corregido coordenadas. El ajuste a la red se limita a **100 m** en cada extremo; la ruta puede comenzar/terminar en el camino cercano, sin dibujar conectores ficticios hasta el edificio. Hay que comprobar accesos y restricciones físicas. Los tiempos fijos de `walkingTimeMinutes` del catálogo **no** se usan como tiempos de ruta.

## Privacidad y errores

No se guarda historial, no se piden permisos de segundo plano y no hay polling GPS/red. Abrir el mapa o elegir destino no solicita rutas. **Mi ubicación** consulta solo el proveedor GPS existente. **Calcular ruta a pie** obtiene una posición y envía solo origen/destino al proveedor; su política de privacidad es <https://heigit.org/privacy-policy/>. OpenFreeMap recibe las peticiones correspondientes al área visible e información habitual de red; no se le envía una marca de posición GPS como parámetro independiente.

El estado de posición/ruta se borra al abandonar la pantalla o pasar a segundo plano. Las peticiones se cancelan y los resultados cancelados no se publican. El transporte usa IO, tiempos de espera finitos (10 s conexión, 20 s lectura), respuesta limitada, sin logs de posiciones/claves ni redirección de credenciales. Una operación HTTPS bloqueante ya iniciada puede tardar hasta su timeout en liberar el transporte tras la cancelación; no genera nuevas peticiones. No hay reintentos automáticos ni solicitudes duplicadas mientras hay una pendiente.

Estados diferenciados: permisos, localización apagada, fijación no disponible, red, autenticación, cuota (429), proveedor caído, respuesta inválida, ruta inexistente y punto sin camino próximo. El mapa tiene estado de carga, error y reintento explícito; una carga de estilo sin completar muestra error a los 20 s. El SDK puede mantener teselas previamente descargadas en su caché: no se promete funcionamiento offline ni detección individual de todos los fallos de teselas.

## Cambios de integración, exactamente

Archivos nuevos, bajo `app/src/main/java/com/example/npi_puntoinfo_etsiit/`:

- `outdoor/OutdoorDestination.kt`: catálogo adaptado, coordenadas y ruta de pantalla exterior.
- `outdoor/WalkingRoute.kt`: resultados, errores y formatos.
- `outdoor/OpenRouteService.kt`: contrato, transporte y decodificación peatonal.
- `outdoor/OutdoorNavigator.kt`: selección, fijación y peticiones puntuales cancelables.
- `ui/components/OutdoorMap.kt`: mapa y capas.
- `ui/screens/OutdoorNavigationScreen.kt`: interfaz exterior y acciones de permisos/ajustes.

Archivos compartidos modificados:

- `ui/navigation/Screen.kt`: solo añade `Screen.Outdoor` con `destinationId`.
- `ui/navigation/AppNavigation.kt`: rama `outdoor` en el manejador, destino Compose, título y botón Atrás. No modifica la rama `navigation`, estado de destinos interiores, tabs, Dijkstra ni sus mapas.
- `ui/screens/DiningScreen.kt`: import del catálogo, cambio de la ruta emitida en `navigateToDining()` y comentario de ese gesto. No cambia menús, calendario, fechas, reservas, callbacks del acelerómetro ni persistencia.
- `app/build.gradle.kts`: MapLibre, lectura local de clave y generación de `BuildConfig`. Conserva la dependencia GPS que ya estaba modificada localmente.
- `app/src/main/AndroidManifest.xml`: únicamente añade Internet y consulta de conectividad sobre los permisos GPS locales ya existentes.

Pruebas nuevas:

- `app/src/test/java/com/example/npi_puntoinfo_etsiit/outdoor/OutdoorRoutingTest.kt`.
- `app/src/test/java/com/example/npi_puntoinfo_etsiit/outdoor/OutdoorNavigatorTest.kt`.
- `app/src/androidTest/java/com/example/npi_puntoinfo_etsiit/OutdoorNavigationIntegrationTest.kt`.

`HomeScreen.kt`, archivos GPS de la primera etapa y sus tests tenían cambios locales al comenzar; no se han editado. **No se modifican `AccelerometerManager.kt`, `GrafoETSIIT.kt`, mapas/JSON interiores, horarios, profesores ni identificación.** No se hacen commits ni cambios de rama.

## Validación

- `:app:assembleDebug`: compilación Debug completa superada. Advertencia no bloqueante del empaquetado de símbolos de `libmaplibre.so`.
- `:app:testDebugUnitTest`: **51 pruebas, 0 fallos**: 28 nuevas de T4 exterior y las 23 anteriores. Cubren catálogo, aislamiento de rutas, contrato GeoJSON, errores, unidades, cancelación, ubicación denegada/apagada/no disponible, peticiones puntuales y limpieza de estado. Las respuestas de los tests son fixtures sintéticas, no recorridos autenticados.
- `:app:assembleDebugAndroidTest`: compilación superada.
- S22 (`SM-S901U1`, Android 16), segunda ejecución instrumentada: el XML y el informe HTML registran **3 pruebas, 0 fallos**, incluidas apertura exterior desde el callback de Comedores, selección de PTS, regreso a Comedores, acceso separado a Guiado y una ruta real del grafo interior hacia Biblioteca. No se realizan reservas ni se simula el acelerómetro.
- **Discrepancia pendiente del ejecutor:** `:app:connectedDebugAndroidTest` devuelve código 1 incluso en esa segunda ejecución con los tres casos registrados como correctos (`test-result-exit-code.txt` también contiene 1). No se afirma que la tarea Gradle de instrumentación esté superada. Informes: `app/build/reports/androidTests/connected/debug/index.html` y `app/build/outputs/androidTest-results/connected/debug/TEST-SM-S901U1 - 16.xml`. En la primera ejecución hubo una aserción de visibilidad; se observó otra app en primer plano y se añadió una espera explícita. Tras dos ejecuciones se detienen los reintentos, conforme al límite solicitado; no se cambian AGP ni componentes ajenos para intentar resolver la discrepancia.
- Se ha confirmado que `ORS_API_KEY` está vacía en esta compilación. **No se ha validado una respuesta de ruta real autenticada**; los tests de servicio usan un transporte inyectado. La comprobación HTTP 401 solo confirma accesibilidad del endpoint.
- Pendiente físico: giro de muñeca e inclinación de reserva, fijación al caminar, exactitud de accesos, recorrido autenticado y comportamiento en cambios reales de cobertura. No se presenta la automatización ADB como una prueba física completa.
