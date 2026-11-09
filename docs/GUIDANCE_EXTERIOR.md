# Especificación Técnica: Guiado Exterior Paso a Paso (`guidance_exterior.json`)

Este documento describe la estructura de datos, el significado de cada campo y las pautas matemáticas y de implementación de la ruta exterior entre el **Edificio Principal de la ETSIIT** y el **Edificio Auxiliar de la ETSIIT** (Campus Aynadamar, UGR).

Está redactado de forma exhaustiva para que **cualquier desarrollador o modelo de IA** pueda implementar o mantener el módulo de guiado en la aplicación Android sin ambigüedades.

---

## 1. Ubicación de Archivos en el Repositorio

* **Archivo JSON de datos:**  
  `app/src/main/assets/guidance_exterior.json`
* **Fotografías panorámicas de campo:**
  * **Ida:** `app/src/main/assets/rutas/ida/ida2.jpg` a `ida9.jpg`
  * **Vuelta:** `app/src/main/assets/rutas/vuelta/vuelta9.jpg` a `vuelta3.jpg`
* **Herramienta visual de calibración:**  
  `tools/calibrador.py` y `tools/calibrador.html`

---

## 2. Esquema Global del JSON

El JSON está estructurado en un objeto raíz con metadatos y dos colecciones independientes: `"ida"` y `"vuelta"`.

```json
{
  "version": "1.0",
  "ruta_id": "etsiit_edificio_auxiliar",
  "titulo": "Ruta Exterior: ETSIIT Principal <-> Edificio Auxiliar",
  "ida": {
    "origen": "ETSIIT Edificio Principal",
    "destino": "Edificio Auxiliar ETSIIT",
    "nodos": [ ... ]
  },
  "vuelta": {
    "origen": "Edificio Auxiliar ETSIIT",
    "destino": "ETSIIT Edificio Principal",
    "nodos": [ ... ]
  }
}
```

* **`version`** (`String`): Versión del esquema de guiado (`"1.0"`).
* **`ruta_id`** (`String`): Identificador único de la ruta para el gestor de navegación.
* **`titulo`** (`String`): Título legible para la cabecera de la UI.
* **`ida`** / **`vuelta`** (`Object`): Contiene `origen`, `destino` y el array ordenado de `nodos`.

---

## 3. Detalle de Campos de Cada Nodo

Cada elemento del array `nodos` representa un hito físico o punto de decisión en el camino:

```json
{
  "id": "ext_ida_02",
  "orden": 2,
  "nombre": "Salida Puerta Principal ETSIIT (Nodo 2)",
  "lat": 37.196873,
  "lng": -3.624742,
  "rumbo": 337,
  "foto": "rutas/ida/ida2.jpg",
  "accion": "RECTO",
  "instruccion": "Sal por la puerta principal de la ETSIIT y avanza de frente hacia la calle Periodista Daniel Saucedo Aranda.",
  "eje_x_camino": 0.570
}
```

### Tabla de Campos

| Campo | Tipo | ¿Puede ser `null`? | Descripción y Uso |
|---|:---:|:---:|---|
| **`id`** | `String` | No | Identificador único del paso (ej. `"ext_ida_01"`, `"ext_vuelta_03"`). Usado como clave única en Compose y en el estado de navegación. |
| **`orden`** | `Int` | No | Número correlativo del paso en ese sentido (de $1$ a $9$). Define la secuencia de avance. |
| **`nombre`** | `String` | No | Título corto del hito para mostrar en la barra superior o lista de pasos (ej. *"Cruce Gómez Montero"*). |
| **`lat`** | `Double` | No | Latitud geográfica decimal en formato WGS84 con 6 decimales de precisión métrica. |
| **`lng`** | `Double` | No | Longitud geográfica decimal en formato WGS84 con 6 decimales de precisión métrica. |
| **`rumbo`** | `Int?` | **Sí** | Orientación magnética en grados ($0^\circ$ a $360^\circ$, donde $0^\circ$=Norte, $90^\circ$=Este, $180^\circ$=Sur, $270^\circ$=Oeste). Es el rumbo hacia el que mira la foto y hacia donde debe caminar el usuario. Es `null` en nodos donde aún no se ha tomado la medición (se medirá y añadirá próximamente). |
| **`foto`** | `String?` | **Sí** | Ruta relativa dentro de la carpeta `assets/` a la panorámica correspondiente. Es `null` en fotos pendientes de tomar (obras o interior); se capturarán e incorporarán en una próxima sesión. |
| **`accion`** | `String` | No | Código semántico de maniobra para mostrar el icono adecuado en la interfaz. Valores estándar: `"INICIO"`, `"RECTO"`, `"GIRO_IZQUIERDA"`, `"GIRO_DERECHA"`, `"CRUCE"`, `"LLEGADA"`. |
| **`instruccion`** | `String` | No | Texto natural para mostrar al usuario y para alimentar el motor de síntesis de voz (Text-to-Speech / TTS). |
| **`eje_x_camino`** | `Double` | No | Proporción horizontal normalizada ($0.0$ a $1.0$) dentro de la fotografía donde se encuentra el camino real a seguir. Calibrado visualmente sobre cada foto. Por defecto $0.5$ (centro). |

---

## 4. Cómo Implementar el Eje X y el Visor de Realidad Aumentada (AR)

El campo **`eje_x_camino`** resuelve el problema de que en las fotos panorámicas la dirección hacia la que hay que andar no siempre queda en el centro exacto de la foto.

### A. Cálculo del píxel objetivo en la imagen
Dada una foto de ancho total $W_{img}$ (en píxeles) y altura $H_{img}$:
$$\text{Pixel Objetivo } X = W_{img} \times \text{eje\_x\_camino}$$

*Ejemplo: si la imagen mide $10.000\text{ px}$ de ancho y `eje_x_camino` es `0.570`, el camino exacto está en el píxel horizontal $5.700$.*

### B. Vinculación con el Sensor de Brújula / Orientación del Teléfono
Cuando el usuario sostiene el móvil en vertical y gira sobre sí mismo:
1. El sensor de orientación del teléfono reporta el rumbo actual del usuario: $\theta_{usuario} \in [0^\circ, 360^\circ)$.
2. El rumbo ideal del nodo es `rumbo` (ej. $337^\circ$).
3. La desviación angular $\Delta\theta$ es:
   $$\Delta\theta = ((\theta_{usuario} - \text{rumbo} + 180^\circ) \pmod{360^\circ}) - 180^\circ$$
   * Si $\Delta\theta \approx 0^\circ$, el usuario está apuntando exactamente hacia el camino correcto.
   * Si $\Delta\theta > 0^\circ$, el usuario se ha desviado hacia la derecha $\rightarrow$ debe girar a la izquierda.
   * Si $\Delta\theta < 0^\circ$, el usuario se ha desviado hacia la izquierda $\rightarrow$ debe girar a la derecha.

### C. Desplazamiento horizontal de la panorámica (Scroll reactivo)
Suponiendo un campo de visión horizontal de la cámara del móvil ($FOV_{horizontal} \approx 65^\circ$):
$$\text{Offset Visual } X = \text{Pixel Objetivo } X + \left(\frac{\Delta\theta}{FOV_{horizontal}}\right) \times W_{pantalla}$$
Al desplazar la imagen horizontalmente con esta fórmula, la calle en la foto se moverá al compás del giro del usuario como si fuera una ventana transparente hacia el mundo real.

### D. Flecha guía en pantalla
Sobre el eje `eje_x_camino` se dibuja una flecha flotante (icono o Canvas):
* Si el eje está dentro de la pantalla, la flecha se dibuja sobre la calle indicando *"Avanza por aquí"*.
* Si el usuario se gira demasiado y el camino se sale de la pantalla, se muestra una flecha lateral en el borde de la pantalla: *"Gira hacia la derecha/izquierda"* para recuperar la dirección.

---

## 5. Detección de Llegada a Cada Paso (GPS Geofencing)

Para avanzar automáticamente de un paso al siguiente mientras el usuario camina:
1. Leer la ubicación actual del dispositivo: $(lat_{actual}, lng_{actual})$.
2. Calcular la distancia métrica al nodo actual $(lat_{nodo}, lng_{nodo})$ usando la fórmula de Haversine o `Location.distanceBetween()`.
3. **Criterio de llegada al paso:**  
   $$\text{distancia} \le \text{UMBRAL\_LLEGADA}$$
   * En exteriores se recomienda un radio de **$8$ a $12$ metros** (para absorber imprecisiones del GPS entre edificios).
   * Al cumplirse la condición, el asistente puede reproducir un sonido o vibración háptica, leer la nueva instrucción por voz y cargar la siguiente fotografía.

---

## 6. Manejo de Nodos Temporales con Campos `null` (Fotos y Orientaciones Pendientes)

> [!IMPORTANT]
> Los campos que actualmente tienen valor `null` (`foto: null` y `rumbo: null`) **NO son omisiones definitivas**, sino que corresponden a fotos y mediciones que **aún no se han podido realizar, pero que se van a tomar e incorporar próximamente**.
>
> Por este motivo:
> 1. La implementación debe ser **tolerante a `null`** en este momento (no debe lanzar `NullPointerException` ni bloquear la navegación).
> 2. El código debe estar preparado para que, en cuanto se añadan los archivos de imagen y los grados de rumbo en el JSON, el visor los muestre automáticamente de forma natural como en el resto de nodos.

### Detalle de los nodos actualmente pendientes:
* **Nodo 1 (Punto de Información / Hall ETSIIT - Ida y Vuelta):**  
  Corresponde al inicio y fin dentro del edificio (vestíbulo). La foto panorámica interior y su orientación se tomarán próximamente.  
  *Comportamiento actual en UI:* Mostrar tarjeta informativa o icono del vestíbulo/kiosco junto al texto TTS, omitiendo el visor de fotos hasta que se añada la panorámica.
* **Nodo 2 en la Vuelta (Llegada a la Entrada Principal ETSIIT):**  
  La rampa/fachada principal de la ETSIIT se encontraba en obras durante la primera toma de campo, por lo que se pospuso la fotografía de llegada de frente. Se capturará e integrará en cuanto sea posible.  
  *Comportamiento actual en UI:* Mantener la tarjeta de llegada indicando el acceso y la instrucción por voz, o mostrar la aproximación de la acera (`vuelta3.jpg`), sin romper el flujo de navegación.

---

## 7. Modelos de Datos Recomendados en Kotlin

Para deserializar directamente este JSON con **Gson** o **Kotlinx Serialization**, se recomienda usar estas clases:

```kotlin
package com.example.npi_puntoinfo_etsiit.outdoor.model

import com.google.gson.annotations.SerializedName

data class GuidanceExteriorData(
    val version: String,
    @SerializedName("ruta_id") val rutaId: String,
    val titulo: String,
    val ida: GuidanceTramo,
    val vuelta: GuidanceTramo
)

data class GuidanceTramo(
    val origen: String,
    val destino: String,
    val nodos: List<GuidanceNodo>
)

data class GuidanceNodo(
    val id: String,
    val orden: Int,
    val nombre: String,
    val lat: Double,
    val lng: Double,
    val rumbo: Int?,
    val foto: String?,
    val accion: String,
    val instruccion: String,
    @SerializedName("eje_x_camino") val ejeXCamino: Double = 0.5
)
```

---

## 8. Rendimiento de Memoria en Android (Importante)

> [!WARNING]
> Las fotos originales en `app/src/main/assets/rutas/` miden hasta $14.240 \times 3.840$ píxeles y pesan más de $15\text{ MB}$ cada una.  
> Cargar un `Bitmap` de estas dimensiones directamente en Android consume más de **$200\text{ MB}$ de memoria RAM por foto**, lo que puede provocar un `OutOfMemoryError` en dispositivos con poca memoria.

**Solución recomendada para la carga de imágenes:**
1. Usar **Coil** o **Glide** configurando submuestreo (`inSampleSize` o limitando el tamaño a la altura de la pantalla, por ejemplo máx. $2048\text{ px}$ de alto).
2. O alternativamente, redimensionar previamente las imágenes en disco si se desea aligerar el APK final.

