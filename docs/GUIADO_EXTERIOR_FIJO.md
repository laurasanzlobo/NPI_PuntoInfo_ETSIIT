# Guiado exterior propio: edificio auxiliar → ETSIIT

## Estado actual

El exterior es una función normal de Guiado: **no hay botón ni pantalla de demo**. **Abrir la aplicación o Inicio no solicita ubicación para guiado ni activa el exterior.** La comprobación se hace al pulsar Guiado o «¡Llévame!» desde profesores, horario/asignaturas, servicios y Comedor Aynadamar. Si se confirma que el usuario está fuera de la ETSIIT, se abre el único itinerario exterior: **edificio auxiliar → edificio principal**.

La pantalla comienza el seguimiento automáticamente y, con la **primera posición reciente**, muestra el paso asociado al **nodo GPS guardado más cercano**. No espera a confirmar un segmento ni exige estar sobre una línea estrecha. Mantiene **todas las instrucciones**, con las anteriores como completadas y solo la vigente resaltada. Si el usuario está a mitad del camino recibe la indicación de continuación; los metros pendientes se muestran únicamente cuando se pueden estimar razonablemente. La lista se desplaza hacia la instrucción activa.

Durante la marcha, cuando otro nodo corresponde a una instrucción distinta, el GPS **propone** cambiar de paso mediante una ventana emergente. El paso resaltado no cambia hasta pulsar **Sí, cambiar de paso**. Con **Ahora no** se mantiene el actual, sin repetir esa misma propuesta mientras se siga en él.

Los botones **Guiado interior / Guiado exterior** están siempre visibles en ambos modos. La selección manual de interior detiene la detección automática para no devolver al usuario al exterior. El destino interior solicitado (profesor, aula o comedor) se conserva en todos los cambios de modo y en la restauración del estado.

El itinerario tiene **seis waypoints de instrucción y 48 puntos de geometría peatonal guardados localmente**. No hay mapa, descarga cartográfica, búsqueda de caminos exterior ni peticiones de rutas desde Android.

## Fuente y comprobación del itinerario

Consulta realizada el **30 de octubre de 2026**, en Google Maps, en navegador, modo **a pie**. Se comprobaron por nombre e identidad ambos lugares:

| Lugar | Referencia cartográfica | Identificador de lugar |
|---|---|---|
| E.T.S. de Ingenierías Informática y de Telecomunicación | `37.197055, -3.6245507` | `ChIJi5JfAlX8cQ0RCK3c7wnKu00` |
| Edificio Auxiliar de la ETSIIT, C. Periodista Juan Osorio Bueno | `37.1980819, -3.6297409` | `ChIJ_0eJ54f9cQ0RoIXjEIb8Km4` |

[Consulta cartográfica original de referencia (ETSIIT → auxiliar)](https://www.google.com/maps/dir/?api=1&origin=ETSIIT+Granada&origin_place_id=ChIJi5JfAlX8cQ0RCK3c7wnKu00&destination=Edificio+Auxiliar+de+la+ETSIIT+Granada&destination_place_id=ChIJ_0eJ54f9cQ0RoIXjEIb8Km4&travelmode=walking).

En la consulta original Google Maps presentaba **8 min / 600 m**, por C. Periodista Eduardo Molina Fajardo. La revisión actual **recorre esos mismos puntos en orden inverso**, a petición del usuario. No se ha solicitado una nueva ruta ni se ha invertido ciegamente el texto: se adaptan los giros y se separa la rotonda de su aproximación. No se mantiene «quinta salida», que pertenecía al sentido anterior.

1. Desde el acceso del auxiliar, recorrer **C. Periodista Juan Osorio Bueno**, unos 58 m.
2. Girar a la izquierda hacia **C. Periodista Eduardo Molina Fajardo** y continuar hasta la rotonda, aproximadamente 290 m.
3. Seguir el itinerario peatonal de la rotonda hasta **C. Periodista Rafael Gómez Mont**, aproximadamente 72 m.
4. Continuar por **C. Periodista Rafael Gómez Mont**, unos 61 m según la referencia de indicaciones (65 m en la geometría guardada).
5. Girar a la derecha hacia el acceso peatonal de la **ETSIIT**, unos 110 m.
6. Llegada al edificio principal; el usuario puede pasar al guiado interior de su destino original.

La longitud geométrica total es aproximadamente **599 m**. Las longitudes orientativas heredadas de las indicaciones y las calculadas sobre la geometría pueden diferir unos metros. El progreso y las distancias GPS se calculan con los subsegmentos guardados, no con los valores redondeados de los textos.

**No se usó Comedor Aynadamar como origen o destino.** Una primera consulta por las coordenadas de la página oficial de contacto se resolvía en Google como comedor y se descartó. La consulta finalmente incorporada usa los identificadores de lugar correctos. El catálogo original `37.19708, -3.62446` no se ha modificado, para no alterar otras funciones.

### Referencia del inmueble frente a extremos peatonales

Los centros de lugar no se convierten en puertas inventadas. El recorrido activo empieza en `37.1978519, -3.6301486`, junto al auxiliar desde Juan Osorio Bueno, y termina en `37.1967175, -3.6247391`, en el extremo peatonal de la ETSIIT. Se conservan esos extremos y sus curvas, sin añadir conectores ficticios al centro del edificio. La llegada GPS es proximidad al extremo peatonal, no prueba de haber cruzado la puerta.

Los datos están **contrastados cartográficamente** (`cartographyVerified = true`) y habilitan el seguimiento GPS. **No se ha realizado una caminata física de validación** (`fieldValidated = false`). La segunda bandera no se ha marcado como cierta para aparentar una comprobación que no se ha realizado.

## Definición local y estructura

Archivo principal: `app/src/main/java/com/example/npi_puntoinfo_etsiit/outdoor/FixedOutdoorRoute.kt`.

- ID exterior exclusivo: `auxiliary-etsiit`, diferente del anterior para no restaurar por error progreso de la dirección opuesta.
- `RouteWaypoint`: ID único, coordenadas, instrucción, maniobra, distancia al siguiente punto, referencia física opcional, `pathToNext` y texto opcional `continuationInstruction` para quien ya ha pasado la maniobra.
- El waypoint i contiene la instrucción que se ejecuta desde su posición hasta i+1; el último es `ARRIVAL`.
- `pathToNext` es una lista de posiciones intermedias para seguir curvas y la rotonda **sin convertirlas en instrucciones adicionales**. Sus extremos deben coincidir con ambos waypoints.
- Los datos están escritos directamente en Kotlin. Ajustar puntos o indicaciones no requiere cambiar el motor.
- El modelo valida coordenadas, IDs, distancias, extremos de cada tramo y presencia de una instrucción final de llegada.

No se dibuja `pathToNext`: solo sirve para cálculos geométricos locales. No hay un grafo exterior ni un algoritmo que escoja entre caminos.

## Detección de exterior e interior manual

`EtsiitPresenceDetector` utiliza la referencia contrastada del edificio principal. Umbrales centralizados en `PresencePolicy`:

| Parámetro | Valor inicial |
|---|---:|
| Zona de proximidad | 80 m |
| Distancia para confirmar exterior | 130 m |
| Precisión máxima | 25 m |
| Edad máxima de fijación | 10 s |
| Intervalo mínimo / separación máxima | 1,5 s / 8 s |
| Lecturas de confirmación | 3 |
| Intervalo solicitado al proveedor | 2 s |

Exterior exige `distancia − precisión > 130 m` durante tres lecturas distintas y recientes. Proximidad exige `distancia + precisión < 80 m`. La banda intermedia evita oscilaciones. Proximidad **no certifica estar dentro**; por eso el texto indica «Cerca de la ETSIIT».

Solo al solicitar **Guiado** se pide permiso si hace falta. Inicio no monta el detector. Las pantallas de comedores mantienen su uso original de ubicación para identificar el comedor cercano, pero no lanzan navegación hasta pulsar su acción de guiado. Sin permiso/señal, ambos modos se pueden seleccionar manualmente y las instrucciones siguen disponibles.

Al pulsar **Guiado interior**, la elección manual bloquea la reapertura automática aunque el GPS siga indicando exterior. Se puede volver con **Guiado exterior**, conservando el paso, o volver a habilitar la detección. Seleccionar un nuevo profesor, aula o comedor ETSIIT inicia una petición nueva y comprueba de nuevo el GPS. Finalizar o volver a Inicio no reactiva el guiado.

Las solicitudes a aulas, profesores, servicios y Comedor Aynadamar conservan su destino interior durante el recorrido auxiliar → ETSIIT. No se sustituyen por un viaje al auxiliar. Los comedores de otros campus continúan mostrando «Recorrido no disponible», sin conducir incorrectamente a la ETSIIT. El cambio al interior usa el acceso del grafo existente y es explícito; no se declara automáticamente que la fijación GPS corresponde a una puerta exacta.

## Motor de seguimiento propio

### Corrección del bloqueo al localizar el tramo

El sistema anterior rechazaba precisiones superiores a 15 m y exigía tres lecturas del mismo segmento dentro de un corredor de 18 m, sumando además la incertidumbre. Por ejemplo, una posición con precisión de 25 m nunca podía superar esas condiciones, aunque estuviese sobre el recorrido. Se ha eliminado ese mecanismo de selección y sustituido por la comparación directa de nodos solicitada por el usuario.

`RouteNodeLocator` construye una lista de **48 `RouteGuidanceNode(id, point, stepIndex)`**, usando exactamente las coordenadas ya guardadas en `FixedOutdoorRoute.kt`. Cada punto intermedio hereda el paso de su `pathToNext`. Un punto compartido entre pasos pertenece a la instrucción que empieza allí. No se añaden coordenadas ni se modifica el camino.

```text
posición = última fijación reciente del dispositivo
nodo = argmin(Haversine(posición, coordenadas_de_cada_nodo_guardado))
paso_actual = nodo.stepIndex
```

Es una comparación O(n), sin búsquedas de caminos, petición de rutas ni mapas. En el primer dato GPS no hay espera por corroboración del tramo, radio máximo ni requisito de partir del auxiliar. Si la posición está alejada de la ruta, se ofrece igualmente el paso más cercano y se muestra el aviso de desviación; eso no calcula cómo volver.

### Geometría auxiliar para distancias y desviación

- Haversine, reutilizando `OutdoorDistances`, mide distancias entre coordenadas con radio terrestre `6 371 008,8 m`.
- Cada subsegmento utiliza proyección equirectangular local en metros:

```text
x = R · cos(latitud_media) · diferencia_longitud_en_radianes
y = R · diferencia_latitud_en_radianes
t = limitar(((P−A) · (B−A)) / |B−A|², 0, 1)
distancia_al_segmento = |P − (A + t(B−A))|
```

- El paso se elige exclusivamente por cercanía a los nodos. La proyección sobre subsegmentos **no permite ni bloquea esa selección**: sirve para estimar distancias y avisar de desvíos.
- El progreso del paso es la longitud de sus subsegmentos anteriores más la fracción recorrida del subsegmento actual.
- La distancia restante suma lo pendiente del paso actual y las longitudes de los pasos siguientes. No se utiliza la línea recta al inmueble como si fuera la distancia peatonal.

La geometría conserva la rotonda; su longitud no se sustituye por la cuerda entre sus extremos. Son cálculos apropiados para este recorrido corto, no un motor geográfico universal.

### Primera selección, actualizaciones y llegada

1. Se comprueban coordenadas y precisión finitas, sello de tiempo monotónico y antigüedad máxima de 30 s. Una fijación reciente almacenada por Fused también sirve; antes se rechazaban algunas que el propio proveedor admitía como iniciales.
2. Con ese primer dato se selecciona **inmediatamente** el nodo más cercano y se activa su paso. Una precisión baja no deja el sistema buscando: se advierte que el paso es orientativo y se ocultan distancias poco fiables.
3. Durante el seguimiento se descartan repeticiones, lecturas fuera de orden y saltos incompatibles con `velocidad_máxima · Δt + precisión_actual + precisión_anterior`.
4. Para proponer otro paso, su nodo debe resultar al menos 5 m más cercano que los nodos del paso actual y aparecer en dos lecturas distintas. Se guarda `suggestedStepIndex` y aparece el diálogo con el número y la instrucción propuesta. **`stepIndex` no cambia sin confirmación**. Se puede proponer avanzar o regresar, siempre indicándolo expresamente.
5. Tras pausa o una separación larga se siguen comparando los nodos sin exigir volver al origen. Si ya había un paso seleccionado, cualquier cambio requiere consentimiento; solo la primera localización de un recorrido nuevo es inmediata.
6. El nodo final no marca por sí solo «Has llegado»: tres lecturas precisas, recientes y dentro del radio final habilitan una **propuesta de llegada**, que también necesita confirmación.

Los botones Anterior/Siguiente y la selección manual tienen prioridad: el GPS no los deshace en segundo plano. Para volver a seleccionar automáticamente el paso se pausa/reactiva GPS. Se conserva el texto de continuación para no repetir un giro ya superado.

### Ventana emergente de cambio de paso

- **Cuándo aparece:** el nodo más próximo pertenece a otra instrucción y supera la corroboración de dos lecturas. Cambiar entre nodos de la misma instrucción no genera avisos.
- **Contenido:** «¿Pasar al siguiente paso?» (o el número concreto si se propone otro paso), explicación de la estimación GPS y vista previa de la instrucción.
- **Confirmar:** actualiza el paso, lo resalta y desplaza la lista. No activa el modo manual ni apaga el GPS: el sistema podrá proponer después el siguiente avance.
- **Ahora no / cerrar el diálogo:** conserva el paso. Esa propuesta queda silenciada durante el paso actual, también al recrear la pantalla. Se puede avanzar con el control manual cuando se quiera. Confirmar otro cambio, cambiar manualmente de paso o reiniciar limpia las decisiones del contexto anterior.
- **Vigencia:** pausa, pérdida de ubicación, saltos inválidos o desviación confirmada retiran el diálogo pendiente. Si dos lecturas sitúan de nuevo al usuario claramente junto al paso actual, también se retira. La propuesta de llegada se cancela si se pierde la proximidad/precisión necesarias.
- Las propuestas pendientes no se restauran como evidencia GPS después de recrear la pantalla; solo se guardan el progreso y las decisiones de «Ahora no».

Si el proveedor no entrega ninguna posición durante 15 s se explica «No se recibe ubicación del móvil» y se mantiene disponible el avance manual. La suscripción sigue activa: una posición posterior seleccionará su nodo sin reiniciar. Posiciones inválidas, permisos denegados o ubicación desactivada también tienen mensajes específicos en vez de un «Localizando…» indefinido.

### Umbrales de seguimiento

Configuración única en `TrackingPolicy`:

| Parámetro | Valor inicial |
|---|---:|
| Aviso de precisión baja (no impide seleccionar paso) | Más de 60 m |
| Edad máxima para seleccionar un paso | 30 s |
| Intervalo mínimo / separación para relocalizar | 1,5 s / 20 s |
| Primera selección | 1 fijación reciente |
| Propuesta de paso posterior | 2 lecturas, ventaja de 5 m y confirmación del usuario |
| Propuesta de llegada: lecturas / radio / precisión máxima / edad máxima | 3 / 8 m / 8 m / 10 s y confirmación del usuario |
| Corredor solo para estimar distancias | 18 m |
| Desviación / retorno | 35 m / 20 m |
| Velocidad máxima antes de márgenes de precisión | 4 m/s |
| Actualización solicitada | 2 s |
| Aviso si no llega la primera posición | 15 s |
| Margen para considerar superada la maniobra | 18 m, descontando precisión |

La propuesta de llegada requiere `distancia_al_destino + precisión/2 ≤ 8 m`, sin ampliar el radio por una precisión mala. Una única lectura no basta; después de tres fijaciones fiables se pregunta al usuario antes de marcar la llegada.

Tres posiciones con `distancia_al_recorrido − precisión > 35 m` generan el aviso de desviación. Se retira después de tres posiciones con `distancia_al_recorrido + precisión < 20 m`. No se calcula cómo volver ni se ofrece otra ruta.

## Interfaz, ciclo de vida y conservación

- Lista completa, sin desplegables y sin eliminar pasos hechos: completados, actual y pendientes. La actual tiene borde/color destacado y semántica de selección accesible.
- `BringIntoViewRequester` desplaza la lista al confirmar el cambio propuesto por GPS o usar los controles; el usuario puede consultar libremente el resto.
- Selector interior/exterior fijo arriba en ambos modos; Anterior/Siguiente, GPS, Reiniciar y Finalizar fuera de la lista desplazable.
- Avance manual etiquetado expresamente; llegada manual distinta de llegada estimada por GPS.
- El índice y las propuestas rechazadas se conservan en recreación y al cambiar de modo. Las posiciones/evidencia no se restauran; al volver a recibir GPS se puede proponer una actualización por cercanía. Una elección manual se conserva hasta reactivar GPS.
- El GPS solo se suscribe mientras la pantalla correspondiente está `RESUMED`. Las suscripciones de detección y seguimiento se suceden, no requieren seguimiento en segundo plano.
- Un temporizador caduca la estimación cuando el proveedor deja de emitir; descuenta la antigüedad que ya tenía la fijación.
- Se conserva Google Play Services Location **solo como fuente de posiciones**, no como proveedor de rutas.
- No hay MapLibre, OpenFreeMap, ORS, clave de rutas, HTTP de guiado ni dependencia de Google Maps en la aplicación. Google Maps se utilizó únicamente al preparar los datos locales a petición del usuario.
- El grafo, Dijkstra y los planos interiores permanecen intactos. También horario, directorio, voz, gestos, encargos y perfil; se adaptan únicamente sus puntos de entrada compartidos a navegación.

## Pruebas de esta revisión

Se limita la ejecución a los componentes retocados, como se pidió; no se repite toda la batería de la aplicación.

**Resultado de esta revisión: 45 pruebas unitarias y 7 instrumentadas, sin fallos; compilación Debug y APK de pruebas correctos.** Las instrumentadas simulan movimiento y verifican aceptar/rechazar el diálogo, no repetir avisos rechazados, confirmar llegada y continuar siguiendo GPS después de aceptar; además conservan las regresiones de localización inicial, precisión baja y cambio interior/exterior. No se repite la batería global de la aplicación.

- Compilación Debug y APK de pruebas con JDK 21.
- Pruebas de `RouteNodeLocatorTest`, `FixedRouteTrackerTest`, `OutdoorNavigatorTest` y `ActiveLocationTest`.
- Primera lectura en cada uno de los 48 nodos; asociación explícita a pasos; elección por mínimo Haversine; coordenadas de la ruta intactas.
- Propuesta sin cambio automático, aceptación, rechazo sin reiteración, restauración del rechazo, retirada de propuestas obsoletas y continuación GPS tras aceptar.
- Caminata simulada por la ruta completa con consentimiento antes de cada paso y de la llegada; lectura inicial inmediata, precisión baja, oscilaciones, duplicados y saltos siguen cubiertos.
- Proveedor inicialmente silencioso: aviso sin bloquear la recepción posterior. Modo manual y cancelación de suscripciones conservados.
- Compose: `OutdoorStepConfirmationTest` (3 casos), `NearestNodeGuidanceTest` (3 regresiones) y el caso de modos/restauración de `OutdoorNavigationIntegrationTest` (1 caso).

Comando unitario utilizado:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest \
  --tests '*outdoor.RouteNodeLocatorTest' --tests '*outdoor.FixedRouteTrackerTest' \
  --tests '*outdoor.OutdoorNavigatorTest' --tests '*outdoor.ActiveLocationTest'
```

Las pruebas de dispositivo se ejecutan por clase o método con `:app:connectedDebugAndroidTest --no-configuration-cache -Pandroid.testInstrumentationRunnerArguments.class=...`, en SM-S901U1 / Android 16. El dispositivo debe estar despierto y sin la pantalla de bloqueo encima de Compose.

Las pantallas de prueba usan `KeepTestScreenAwake` solo durante su composición para evitar falsos fallos de jerarquía cuando el teléfono se bloquea por inactividad; se restaura el valor anterior al terminar. Esto no cambia el comportamiento ni los ajustes de la aplicación real.

## Qué falta comprobar físicamente

El recorrido ya no es una plantilla vacía: sus datos son cartográficos y el algoritmo se ha probado con ellos. Falta caminarlo para comprobar accesos reales, pasos peatonales de la rotonda, señalización/ordinal de salida, obras o restricciones, lado del acceso final y precisión GPS junto a edificios. Los umbrales son configurables y requieren calibración en campo. Esta validación no se sustituye con una consulta a Maps ni con pruebas simuladas.

## Archivos principales de esta revisión

- Nuevos en esta revisión: `ui/components/OutdoorStepConfirmationDialog.kt` y `OutdoorStepConfirmationTest.kt`.
- Modificados: `outdoor/FixedRouteTracker.kt`, `outdoor/OutdoorNavigator.kt`, `ui/screens/OutdoorNavigationScreen.kt`, pruebas asociadas y este documento. No se alteran las coordenadas ni las instrucciones de la ruta.
- Los componentes retirados en la revisión anterior (`OutdoorMap`, `OpenRouteService`, `RouteConsentStore`) continúan fuera del flujo y no se han recuperado.

No se han realizado commits ni pushes.
