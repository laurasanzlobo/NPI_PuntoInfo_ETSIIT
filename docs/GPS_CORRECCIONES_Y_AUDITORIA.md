# Correcciones GPS / transición interior (2026-10-20)

## Comparación dirigida con develop

Consulta exclusivamente de `/home/pablo/Projects/NPI/NPI_develop_original` (`develop`, limpio).
El árbol de trabajo está en `feat/gps-comedores`, con integración GPS previa sin commit.
`GrafoETSIIT.kt`, el JSON completo, `CampusLocationManager.kt`, `HomeScreen.kt` y
`ScheduleScreen.kt` coinciden byte a byte con esa copia. No se modifican el grafo,
Dijkstra, los planos, `AccelerometerManager.kt`, reservas ni dependencias.

### Comedor: regresión confirmada y recuperada

En develop, tanto el icono como el giro de muñeca de Comedores llamaban a
`Screen.Navigation.createRoute("Comedor Aynadamar", "Campus Aynadamar")`.
`NavigationScreen` resolvía ese texto con `buscarNodoPorDestino` y usaba Dijkstra
desde `Entrada_Exterior`. Además, voz devuelve explícitamente el mismo título
para el nodo del comedor.

| Referencia | Identidad comprobada en el grafo |
| --- | --- |
| Destino | `Comedor_PSS`, `n_1791914616902_89`, planta `P-1`, edificio `despachos` |
| Origen histórico / acceso elegido | `Entrada_Exterior`, `n_1791913273140_23`, planta `B` |
| Otro acceso, **no equivalente** | `Entrada_Exterior_P1`, `n_1791915815667_838`, planta `1` |

La integración había sustituido la llamada de Comedores por
`outdoor?destinationId=dining-aynadamar`, sin destino ni nodo interior final.
Por eso se mostraba la llegada genérica, no porque faltase Dijkstra.

Se recupera **esa correspondencia explícita de develop**, se comprueba la ruta
real antes de ofrecerla y el GPS se dirige a la referencia ETSIIT del acceso PB,
no al marcador del comedor. El tramo interior conserva `Comedor_PSS` por ID.
No se extrapola esa asociación a PTS, Cartuja, Fuentenueva ni nombres desconocidos.
El resolver histórico acepta cualquier texto con «comedor»; la integración filtra
otros comedores y reconoce los campus nombrados explícitamente por voz antes de
usar ese resolver. No cambia los datos ni el algoritmo del compañero.

**Límite de la verificación:** los datos prueban la correspondencia funcional de
la aplicación original, no un levantamiento geográfico. El catálogo dice «junto
a ETSIIT» y el grafo ubica el nodo en el bloque de despachos, planta -1. Deben
confirmarse físicamente el local real, la puerta PB y su disponibilidad. No se
inventan coordenadas de una puerta nueva ni se usa el acceso P1 como sustituto.

### Otras rutas interiores

- Aulas/laboratorios y horarios: mismos resolutores y nodos, incluidos sótanos;
  conservan nombre, ubicación e ID en la transición exterior → interior.
- Profesores: los tres destinos originales conservan su resolución histórica.
- Voz: se conserva el resultado resuelto (incluido ID) para comedor, cafetería,
  biblioteca, salón, secretaría, conserjería, profesores y aulas. Los servicios
  que antes abrían directamente el interior pueden usar ahora la misma transición
  y su alternativa manual. No se reimplementa su resolución.
- La auditoría automatizada compara la ruta histórica desde el origen
  predeterminado con el handoff para **todos los nodos alcanzables** del JSON.
- No se ha encontrado otra pérdida de destinos interiores. Había tratamientos
  distintos entre aulas/profesores (GPS) y otros servicios/voz (interior directo);
  ahora los destinos interiores válidos comparten la transición.

**Discrepancia previa pendiente del responsable de datos:** `D-08` resuelve a
`D_208` (planta 2), aunque la UI dice Planta Baja; `D-12` resuelve a `D_212`
(planta 2), aunque la UI dice Planta 1; `D-24` resuelve a `D_224` (planta 2).
Es idéntico a develop. Se conserva la resolución y se documenta, sin adivinar
otra correspondencia de profesores o modificar el grafo.

## Latencia: causa y cambio

Antes no había `requestLocationUpdates`: el bucle observaba el estado, esperaba
12 s y **después** hacía `getCurrentLocation` con duración máxima de 15 s y
timeout de 20 s. El intervalo efectivo era `12 s + tiempo de adquisición`, más
la posible espera de fase al acercarse. Las dos lecturas exigían al menos 12 s
de separación. Esto explica esperas de unos 20 s, aunque la contribución exacta
de cada adquisición física anterior no puede reconstruirse sin sus registros.
No había debounce extra. El timeout del mapa de 20 s es independiente.
La petición ya usaba `maxUpdateAge=0`: no se atribuye el problema a una caché
que el código no estaba solicitando.

Ahora una ruta exterior calculada y visible mantiene una suscripción Fused
cancelable: 12 s lejos del acceso y 2 s a unos 150 m. No hay batching ni espera
adicional de Fused a una primera posición especialmente precisa. El detector
sigue exigiendo dos posiciones fiables y separadas, precisión ≤25 m y
`distancia + error ≤45 m`. La separación mínima es 2 s; no se amplía el radio.
Se usan timestamps monotónicos del receptor: no se cuenta dos veces una misma
fijación ni se acepta una posición de más de 10 s o con timestamp futuro.
Las posiciones inválidas interrumpen la evidencia.

La suscripción se retira al salir de STARTED, abandonar el exterior, sustituir
el recorrido o recalcular su ruta. No hay servicio, permiso de segundo plano,
historial ni nuevos envíos a ORS por cada actualización GPS. Las consultas
puntuales de «Mi ubicación» y el consentimiento de rutas se conservan.

### Diagnóstico físico

```
adb logcat -v time -s NavigationGps:D NavigationArrival:D
```

- `NavigationGps`: intervalo solicitado/efectivo entre timestamps, edad y precisión
  de la fijación, sin latitud ni longitud.
- `firstValid`: timestamp de primera evidencia válida y momento de recepción.
- `confirmed`: tiempo desde esa primera fijación hasta confirmar proximidad.
- `promptVisible`: momento tras componer el aviso y una oportunidad de frame;
  diferencia respecto a confirmación. No es una medición fotométrica de pantalla.

La prueba de flujo simulado recorre fuente → lector → navegador → detector →
estado de viaje con fijaciones en 100000/102000 ms y recepción 50 ms después:
solicita el aviso a los **2050 ms** de la primera fijación. Verifica dos lecturas,
ninguna llamada extra a ORS y retirada de la suscripción al cancelarla.
Esto **no demuestra** que el S22 reciba siempre una posición buena cada 2 s.
Con mala cobertura o permiso aproximado puede no haber confirmación automática.

## Estado durante el recorrido

`NavigationTrip` vive en `AppNavigation` mediante `rememberSaveable` y un Saver;
no depende de la instancia exterior ni del receptor GPS. Se guarda solo identidad
del viaje, acceso, destino interior y estado de UI, nunca ubicaciones GPS.

- Nuevo viaje: ID nuevo, OUTDOOR / WAITING, sin aviso ni botón automático.
- Primera llegada fiable: PROMPT; al presentarlo se registra SHOWN. Un diálogo
  local conserva su visibilidad actual, pero no se vuelve a abrir automáticamente
  al recrear/reentrar. SHOWN ya permite el botón compacto al regresar.
- «Aún estoy fuera»: DEFERRED y botón compacto persistente.
- Entrada confirmada o manual: INDOOR / CONTINUED, mismo destino por ID.
- Regreso por botón o Atrás del sistema: OUTDOOR / RETURNED y botón compacto.
- Finalizar/cancelar o sustituir viaje: se descarta el estado anterior. Cancelar
  la ruta interior elimina también su entrada exterior del back stack.

La vía manual discreta sigue en Información → «Continuar dentro manualmente»,
incluso sin una sola fijación GPS. Los destinos solo exteriores no reciben esa
acción ni el botón rojo.

## Archivos de esta corrección

Rutas relativas a `app/src/`:

- `main/.../ui/navigation/AppNavigation.kt`: integración y estado del recorrido.
- `main/.../ui/screens/OutdoorNavigationScreen.kt`: suscripción visible, presentación,
  botón y diagnóstico.
- `main/.../outdoor/{NavigationJourney,OutdoorDestination,OutdoorNavigator,ArrivalDetector,ArrivalPresentation}.kt`.
- Nuevo `main/.../outdoor/NavigationTrip.kt`.
- `main/.../sensors/{DeviceLocationProvider,GpsLocationReader}.kt`.
- Nuevos `test/.../outdoor/{NavigationTripTest,IndoorRegressionTest,ActiveLocationTest}.kt`.
- Ampliado `test/.../outdoor/ArrivalDetectorTest.kt`.
- Ampliados `androidTest/.../{OutdoorNavigationIntegrationTest,IndoorArrivalPresentationTest}.kt`.
- Este documento. Los demás cambios que ya mostraba `git status` son previos.

## Lista de comprobación en la ETSIIT con el S22

1. Desde Comedores, iniciar Aynadamar con consentimiento y una ruta peatonal ORS.
   Confirmar la puerta PB y el destino real del plano de planta -1.
2. Acercarse caminando desde fuera de 150 m. Capturar los registros anteriores:
   frecuencia efectiva, precisión, primera evidencia, confirmación y presentación.
3. Comprobar aviso único → «Aún estoy fuera» → botón rojo → interior → exterior,
   varias veces, manteniendo el comedor. Repetir con aula y profesor.
4. Repetir entrando directamente desde el primer aviso y recreando la actividad.
   Cancelar e iniciar otro viaje: no debe conservar aviso ni botón del anterior.
5. Probar cobertura mala/ubicación aproximada: no debe confirmar con una lectura
   imprecisa; debe quedar disponible la entrada manual. Otros campus sin interior.
6. Bloquear/desbloquear y cambiar de app: comprobar retirada/reanudación de la
   suscripción, sin seguimiento en segundo plano. Verificar también gestos físicos,
   planos, instrucciones de marcha y reservas/calendario sin cambios de comportamiento.

## Resultados de validación

- Base comprobada: 86 pruebas unitarias existentes. No se elimina ninguna.
- Ejecución final `./gradlew assembleDebug testDebugUnitTest --rerun-tasks`:
  **BUILD SUCCESSFUL**, 44 tareas ejecutadas, **109 pruebas / 0 fallos / 0 omitidas**
  (86 originales + 23 nuevas). APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Samsung S22 `SM-S901U1`, Android 16, conectado mediante ADB: se ejecutan las
  instrumentadas dos veces, sin borrar datos ni cambiar permisos/configuración.
  Última ejecución: **13 pruebas, 12 correctas y 1 fallida**.
- Pasan comedor, despacho, restauración de actividad, múltiples transiciones,
  cancelación/reinicio, selección de otro campus, contrato del grafo y presentación
  con llegada simulada. No equivalen a una caminata GPS ni a gestos físicos.
- Sigue fallando
  `OutdoorNavigationIntegrationTest.scheduleClassroomOpensGpsAndPreservesTargetThroughRecreationAndIndoorHandoff`:
  `No compose hierarchies found in the app` al buscar la tarjeta del horario,
  antes de iniciar su navegación. En el primer intento también falló selección
  de otro campus con el mismo error; ese caso pasa en el segundo.
  No se ha demostrado la causa de la desaparición de la jerarquía y **no se da
  por validado el flujo UI de Horario**. Se detienen los reintentos según el límite
  solicitado; no se omite ni se falsea el test.
- Informe instrumentado:
  `app/build/reports/androidTests/connected/debug/index.html`.
  Registro de las dos suites: `/tmp/opencode/npi-validation.log` y
  `/tmp/opencode/npi-validation-final.log`. Compilación/unitarias finales:
  `/tmp/opencode/npi-build-unit-final.log`.
- Tras la última suite instrumentada se añade la salvaguarda para rechazar un
  alias de comedor con ID de otro servicio y evitar el fallback interior de
  nombres de comedor no verificados, además de logs de alta/baja GPS. Esos
  últimos ajustes se recompilan y pasan las 109 unitarias; no se hace un tercer
  intento instrumentado.
- `git diff --check` sin errores. Se revisan diff/status; se conserva la rama
  `feat/gps-comedores`, sin commits, push, merge ni sobrescritura de cambios previos.
  La copia original de develop sigue limpia.
