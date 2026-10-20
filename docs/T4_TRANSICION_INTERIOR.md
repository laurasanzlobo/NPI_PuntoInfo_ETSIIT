# T4/T9 — Integración exterior → interior

Este documento actualiza el comportamiento y los resultados de `T4_EXTERIOR.md`.

## Uso

- **Profesores**: el botón y el giro de muñeca conservan despacho y profesor en los argumentos de navegación. Abren el mapa exterior con ETSIIT seleccionado, no con coordenadas de despachos.
- **Comedores**: se reutiliza el callback exterior existente, que selecciona Aynadamar (el comedor que muestra esa pantalla). El selector exterior permite ir a los otros comedores sin redirigirlos a ETSIIT.
- La primera solicitud muestra consentimiento explícito para enviar origen/destino a HeiGIT. Aceptarlo inicia la fijación GPS y el cálculo peatonal, solicitando permisos si faltan. **Ya estoy dentro** permite evitar el cálculo exterior, incluso sin Internet, clave o GPS.
- Durante una ruta calculada y con la pantalla visible se obtienen fijaciones nuevas, sin recalcular ni enviar esas actualizaciones a ORS. No hay servicio ni ubicación en segundo plano. Al salir se cancelan las comprobaciones y se borra la ruta/posición; se conservan la selección y el destino final. Al volver se puede recalcular.
- `ArrivalPolicy` centraliza el radio (45 m), precisión máxima (25 m), separación mínima (12 s), caducidad entre muestras (45 s) y mínimo de lecturas (2). Distancia **más** margen de precisión debe caber en el radio. No se afirma haber atravesado una puerta. El aviso se muestra una vez por recorrido/destino, también si se responde **Aún estoy fuera**, y su supresión se conserva en recreaciones.
- La confirmación abre el mapa interior con el ID de `Entrada_Exterior` (PB) como origen y el ID del despacho resuelto por el grafo existente. Se comprueba la existencia de una ruta antes de ofrecer el tramo interior. Nunca se usa `Entrada_Exterior_P1` como equivalente. **Volver al exterior** recupera el mapa exterior con el contexto del profesor.
- Cambiar el destino exterior cancela la asociación interior. Los comedores muestran aviso de proximidad, pero no **Continuar dentro**: no hay una relación fiable entre el catálogo de comedores y el comedor interior del grafo.
- El acceso directo a **Guiado**, las rutas de Horario/voz y los gestos interiores mantienen sus valores predeterminados.

## Interfaz

Se elimina únicamente la llamada a la tarjeta GPS de Inicio. No se retiran permisos, proveedores ni distancias. Se conserva el archivo local del componente para no descartar trabajo previo.

Se retiran del panel exterior la leyenda textual de colores, dirección descriptiva redundante, texto inicial de instrucciones de uso y explicaciones permanentes de los botones. Privacidad y limitaciones pasan a un diálogo accesible desde el icono de información; el primer envío mantiene consentimiento explícito. Atribuciones de MapLibre y crédito de ORS siguen visibles.

El mapa recibe la mayor parte del espacio disponible. El panel inferior es desplazable. Las instrucciones se despliegan bajo el resumen; muestran iconos según el tipo real ORS, distancia y texto con HTML interpretado, no etiquetas crudas. Los tipos ausentes/desconocidos son genéricos. No se destaca ningún paso como actual ni se simula seguimiento giro a giro; el recálculo es manual.

## Archivos de esta iteración

Rutas relativas a `app/src/main/java/com/example/npi_puntoinfo_etsiit/`:

- **Nuevos:** `outdoor/NavigationJourney.kt`, `outdoor/ArrivalDetector.kt`, `ui/components/WalkingStepRow.kt`.
- **Editados:** `outdoor/OutdoorDestination.kt`, `outdoor/OutdoorNavigator.kt`, `outdoor/WalkingRoute.kt`, `outdoor/OpenRouteService.kt`, `sensors/DeviceLocationProvider.kt`, `ui/screens/OutdoorNavigationScreen.kt`, `ui/screens/ProfessorsScreen.kt`, `ui/screens/NavigationScreen.kt`, `ui/screens/HomeScreen.kt`, `ui/navigation/AppNavigation.kt`.
- **Tests nuevos:** `ArrivalDetectorTest.kt`, `NavigationJourneyTest.kt`, `WalkingStepTest.kt` en `app/src/test/java/com/example/npi_puntoinfo_etsiit/outdoor/`; `WalkingStepPresentationTest.kt` en `app/src/androidTest/java/com/example/npi_puntoinfo_etsiit/`.
- **Tests ampliados:** `OutdoorNavigatorTest.kt` y `OutdoorNavigationIntegrationTest.kt`.
- **Documento nuevo:** `docs/T4_TRANSICION_INTERIOR.md`.

`DiningScreen.kt`, `Screen.kt`, `app/build.gradle.kts`, manifiesto, MapLibre y otros archivos GPS ya tenían modificaciones locales al iniciar esta iteración; se han conservado. La conexión exterior de Comedores ya estaba implementada y se reutiliza sin nuevas modificaciones en esa pantalla. `HomeScreen.kt` vuelve a coincidir con HEAD al retirar exclusivamente la tarjeta local de prueba.

## Validación

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`: código **0**. **73 tests unitarios, 0 fallos, 0 errores, 0 omitidos** (51 existentes + 22 nuevos).
- Cobertura añadida: precisión, intervalo y caducidad, lecturas inválidas, supresión/restauración del aviso, rutas de los tres profesores sobre el JSON real, origen PB, preservación/codificación del destino, otros campus sin transición, refresco sin llamadas ORS adicionales y tipos/valores de las instrucciones.
- Tests instrumentados compilados para confirmación/rechazo, recreación y retorno al exterior, cancelación al cambiar de campus, Comedores, acceso interior existente y presentación sin HTML crudo.
- `connectedDebugAndroidTest`: código **1**, `DeviceException: No connected devices!`. `adb devices -l` está vacío. No se consideran ejecutados ni superados los tests de dispositivo; este bloqueo no permite reproducir ni diagnosticar la discrepancia histórica del ejecutor en el S22 conectado.
- La clave local ORS está configurada; no se modifica ni publica. Petición real `foot-walking` con origen público fijo de Granada (no una posición del dispositivo) al punto ETSIIT del catálogo: **HTTP 200**, 148 puntos, 34 instrucciones, 2267,7 m y 1632,7 s. Confirma servicio/contrato, **no** el acceso físico ni una navegación caminando.
- Logs de esta ejecución: `/tmp/opencode/npi-validation-final.log`, `/tmp/opencode/npi-instrumentation.log`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`. Contiene la clave de la práctica, extraíble del APK; no publicarlo.

## Limitaciones verificadas y comprobación en el S22

1. **Acceso exterior pendiente de validar:** las únicas coordenadas disponibles son las referencias del catálogo (`37.19708, -3.62446`); el JSON interior tiene píxeles, no georreferenciación de puertas. Dos consultas a fuentes OSM no dieron datos utilizables (HTTP 406 y timeout). Se conservan las coordenadas existentes, sin inventar una puerta. No puede garantizarse todavía que ORS termine exactamente en el acceso PB; verificarlo sobre el terreno antes de dar por cerrada la integración física.
2. **Datos heredados de despachos:** el resolvedor existente asocia D-08 → `D_208`, D-12 → `D_212` y D-24 → `D_224`; los tres nodos están en planta 2, aunque las etiquetas actuales de Alberto y María dicen PB y planta 1. Se reutiliza el mapeo sin cambiar datos de profesores ni nodos. El equipo debe confirmar estas correspondencias antes de considerarlas destinos físicos fiables.
3. Conectar/desbloquear el S22 y activar depuración USB. Instalar sin borrar datos: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Probar cada profesor: apertura exterior, consentimiento/permisos, destino conservado tras girar/recrear pantalla, acceso manual sin GPS, **Aún estoy fuera**, confirmación, plano/ruta, postura para guiado y **Volver al exterior**.
5. Con ruta activa caminar hacia el acceso PB: fijaciones precisas separadas, un único aviso, ninguna transición sin confirmar. Probar ubicación aproximada, GPS apagado, permiso denegado y ausencia de red.
6. Comedores: comprobar gesto y botón, selección de PTS/otros campus, proximidad sin transición interior y conservación del calendario/encargos. Probar físicamente la inclinación de reserva en un día futuro; la automatización no simula los sensores.
7. Verificar legibilidad, desplazamiento de instrucciones y proporción del mapa en el S22; no hay todavía validación visual en el dispositivo.
8. Ejecutar con el teléfono conectado: `./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`. Comprobar el código del comando, no solo el informe HTML. La instrumentación puede afectar a la instalación; no usarla para verificar conservación de reservas reales.

No se modifican Dijkstra, grafo/JSON/planos, `AccelerometerManager.kt`, reservas/fechas/persistencia, datos de profesores, horarios ni autenticación. No se hacen commits, push, cambios de rama ni descartes de trabajo local.
