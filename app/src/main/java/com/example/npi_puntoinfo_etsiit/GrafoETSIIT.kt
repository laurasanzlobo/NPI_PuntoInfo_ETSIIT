package com.example.npi_puntoinfo_etsiit

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.util.PriorityQueue
import kotlin.math.roundToInt

// 1. Data Classes que calcan la estructura del archivo JSON
data class Nodo(
    val id: String,
    val nombre: String,
    val planta: String,
    val edificio: String,
    val tipo: String,
    val x: Int,
    val y: Int
)

data class AristaJson(
    val origen: String,
    val destino: String,
    val distancia_metros: Int
)

data class EstructuraJson(
    val nodos: List<Nodo>,
    val aristas: List<AristaJson>
)

// Data class interna para el cálculo de Dijkstra
data class AristaDijkstra(val destinoId: String, val distancia: Int)

/**
 * Modelo que agrupa el resultado completo del cálculo de Dijkstra
 */
data class RutaCalculada(
    val origen: Nodo,
    val destino: Nodo,
    val nodos: List<Nodo>,
    val distanciaMetros: Int,
    val tiempoEstimadoSegundos: Int
) {
    val tiempoEstimadoFormateado: String
        get() {
            val minutos = (distanciaMetros / 70.0).roundToInt().coerceAtLeast(1)
            return if (minutos <= 1) "~1 min a pie" else "~$minutos min a pie"
        }
}

/**
 * Representa el resultado resuelto a partir de una locución por voz o texto natural
 */
data class DestinoVoz(
    val nodo: Nodo,
    val titulo: String,
    val ubicacion: String,
    val textoOriginal: String
)

sealed class ResultadoVoz {
    data class Exito(val destino: DestinoVoz) : ResultadoVoz()
    data class Error(val mensaje: String) : ResultadoVoz()
}

fun normalizarPlanta(planta: String): String {
    return when (planta.trim().uppercase()) {
        "B", "0", "PB" -> "PB"
        "P-1", "-1", "PSS" -> "PSS"
        "-2", "P-2" -> "-2"
        "1", "P1" -> "P1"
        "2", "P2" -> "P2"
        "3", "P3" -> "P3"
        "4", "P4" -> "P4"
        else -> planta.trim().uppercase()
    }
}

fun formatearPlanta(planta: String): String {
    return when (normalizarPlanta(planta)) {
        "PB" -> "Planta Baja"
        "PSS" -> "Planta -1"
        "-2" -> "Planta -2"
        "P1" -> "Planta 1"
        "P2" -> "Planta 2"
        "P3" -> "Planta 3"
        "P4" -> "Planta 4"
        else -> "Planta $planta"
    }
}

class GrafoETSIIT {
    private val conexiones = mutableMapOf<String, MutableList<AristaDijkstra>>()
    private val directorioNodos = mutableMapOf<String, Nodo>()

    // =========================================================================
    // FUNCIÓN A: Para usar dentro de la app Android
    // =========================================================================
    fun cargarDesdeAssets(context: Context, nombreArchivo: String) {
        val jsonString = context.assets.open(nombreArchivo).bufferedReader().use { it.readText() }
        procesarJson(jsonString)
    }

    // =========================================================================
    // FUNCIÓN B: Para usar en pruebas locales en mi ordenador (Scratch / Main)
    // =========================================================================
    fun cargarDesdeJsonLocal(rutaAbsoluta: String) {
        val jsonString = File(rutaAbsoluta).readText()
        procesarJson(jsonString)
    }

    // Función auxiliar oculta que hace el trabajo sucio para no repetir código
    private fun procesarJson(jsonString: String) {
        val datos = Gson().fromJson(jsonString, EstructuraJson::class.java)

        datos.nodos.forEach { nodo ->
            directorioNodos[nodo.id] = nodo
            if (!conexiones.containsKey(nodo.id)) {
                conexiones[nodo.id] = mutableListOf()
            }
        }

        datos.aristas.forEach { arista ->
            agregarArista(arista.origen, arista.destino, arista.distancia_metros)
        }

        println("¡Grafo cargado! ${directorioNodos.size} nodos procesados.")
    }

    fun buscarIdPorNombre(nombreBuscado: String): String? {
        // Busca en todos los nodos y nos devuelve el ID del que coincida con el nombre
        return directorioNodos.values.find { it.nombre == nombreBuscado }?.id
    }

    fun obtenerNodoPorId(id: String): Nodo? {
        return directorioNodos[id]
    }

    /**
     * Nodo de inicio predeterminado (Entrada Principal Exterior en Planta Baja)
     */
    fun obtenerNodoInicioPredeterminado(): Nodo {
        return directorioNodos.values.find { it.nombre.equals("Entrada_Exterior", ignoreCase = true) }
            ?: directorioNodos.values.find { it.nombre.equals("Vestibulo", ignoreCase = true) }
            ?: directorioNodos.values.first()
    }

    /**
     * Busca de manera flexible un nodo a partir del texto de destino de la aplicación
     * (soporta aulas como "Aula 0.1", comedores como "Comedor", y despachos como "D-08" o "2.01").
     */
    fun buscarNodoPorDestino(query: String): Nodo? {
        if (query.isBlank()) return null
        val normalizado = query.trim().lowercase()

        // 1. Destinos temáticos directos
        if (normalizado.contains("comedor")) {
            return directorioNodos.values.find { it.nombre.equals("Comedor_PSS", ignoreCase = true) }
                ?: directorioNodos.values.find { it.nombre.contains("comedor", ignoreCase = true) }
        }
        if (normalizado.contains("cafeteria") || normalizado.contains("cafetería")) {
            return directorioNodos.values.find { it.nombre.equals("Cafeteria_PSS", ignoreCase = true) }
        }
        if (normalizado.contains("biblioteca")) {
            return directorioNodos.values.find { it.nombre.equals("Biblioteca", ignoreCase = true) }
                ?: directorioNodos.values.find { it.nombre.contains("biblioteca", ignoreCase = true) }
        }
        if (normalizado.contains("salon") || normalizado.contains("salón") || normalizado.contains("actos")) {
            return directorioNodos.values.find { it.nombre.equals("Salon_Actos_PSS", ignoreCase = true) }
        }

        // 2. Aulas y Laboratorios con número decimal o estándar: ej. "Aula 0.1", "0.1", "Aula 3.4", "Lab 3.4", "3.4", "1.2"
        val regexAula = Regex("""(?:aula\s*|clase\s*|laboratorio\s*|lab\s*)?([0-4])\.(\d+)""", RegexOption.IGNORE_CASE)
        val matchAula = regexAula.find(normalizado)
        if (matchAula != null) {
            val planta = matchAula.groupValues[1]
            val num = matchAula.groupValues[2]
            val candidatos = listOf(
                "A_${planta}${num}",
                "A_${planta}0${num}",
                "L_${planta}${num}"
            )
            for (cand in candidatos) {
                val nodo = directorioNodos.values.find { it.nombre.equals(cand, ignoreCase = true) }
                if (nodo != null) return nodo
            }
            // Si la consulta tiene formato o mención de aula/lab, NUNCA buscar como despacho
            return null
        }

        // 3. Aulas de sótano (ej. "Aula -1", "-1", "menos 1", "Aula -2", "-2", "menos 2")
        val esDespacho = normalizado.contains("despacho") || normalizado.startsWith("d-") || normalizado.startsWith("d_") || normalizado.startsWith("d ")
        if (!esDespacho) {
            val regexSotano = Regex("""(?:^|\b|aula\s*)(?:menos\s*|[-–])([12])\b""", RegexOption.IGNORE_CASE)
            val matchSotano = regexSotano.find(normalizado)
            if (matchSotano != null) {
                val numSotano = matchSotano.groupValues[1]
                val nodo = directorioNodos.values.find { it.nombre.equals("A_Menos$numSotano", ignoreCase = true) }
                if (nodo != null) return nodo
            }
        }

        // 4. Laboratorios: ej. "Laboratorio 2.1", "Lab 21", "L_21", "L_31", "L_310"
        val regexLab = Regex("""(?:laboratorio|lab|l)[-_\s]*([23])[\.\s_]*(\d{1,2})\b""", RegexOption.IGNORE_CASE)
        val matchLab = regexLab.find(normalizado)
        if (matchLab != null) {
            val p = matchLab.groupValues[1]
            val n = matchLab.groupValues[2]
            val nodoLab = directorioNodos.values.find { it.nombre.equals("L_$p$n", ignoreCase = true) }
            if (nodoLab != null) return nodoLab
        }

        // 5. Despachos: ej. "Despacho D-08", "D-08", "D08", "D_201", "D 208", "Despacho 420", "despacho 4 20"
        val noEsAula = !normalizado.contains("aula") && !normalizado.contains("clase") &&
            !normalizado.contains("lab")
        val numDespacho = extraerNumeroDespacho(query)
        if (numDespacho != null && noEsAula) {
            val candidatas = listOf(
                "D_$numDespacho",
                "D_${numDespacho.padStart(3, '0')}",
                "D_2${numDespacho.padStart(2, '0')}",
                "D_3${numDespacho.padStart(2, '0')}",
                "D_4${numDespacho.padStart(2, '0')}"
            )
            for (c in candidatas) {
                val nodo = directorioNodos.values.find { it.nombre.equals(c, ignoreCase = true) }
                if (nodo != null) return nodo
            }
        }

        // 6. Coincidencia exacta por nombre
        val exacto = directorioNodos.values.find { it.nombre.equals(query.trim(), ignoreCase = true) }
        if (exacto != null) return exacto

        // 7. Contiene subcadena (solo si no es patrón de aula)
        if (noEsAula) {
            val parcial = directorioNodos.values.find {
                it.nombre.contains(query.trim(), ignoreCase = true) ||
                query.trim().contains(it.nombre, ignoreCase = true)
            }
            if (parcial != null) return parcial
        }

        return null
    }

    /**
     * Normaliza palabras numéricas habladas en español (unidades, decenas, centenas)
     * para que coincidan con representaciones numéricas ("cuatrocientos veinte" -> "420").
     */
    fun normalizarTextoVoz(texto: String): String {
        var t = texto.trim().lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")

        // 1. Decenas compuestas con 'y' (ej. "treinta y cinco" -> "35")
        val decenas = mapOf(
            "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60,
            "setenta" to 70, "ochenta" to 80, "noventa" to 90
        )
        val unidades = mapOf(
            "uno" to 1, "un" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
            "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9
        )
        for ((decW, decV) in decenas) {
            for ((uniW, uniV) in unidades) {
                t = t.replace(Regex("""\b$decW\s+y\s+$uniW\b"""), (decV + uniV).toString())
            }
        }

        // 2. Reemplazos directos de palabras numéricas individuales
        val palabrasDirectas = mapOf(
            "veintiuno" to 21, "veintidos" to 22, "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25,
            "veintiseis" to 26, "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29,
            "veinte" to 20, "diecinueve" to 19, "dieciocho" to 18, "diecisiete" to 17, "dieciseis" to 16,
            "quince" to 15, "catorce" to 14, "trece" to 13, "doce" to 12, "once" to 11, "diez" to 10,
            "nueve" to 9, "ocho" to 8, "siete" to 7, "seis" to 6, "cinco" to 5, "cuatro" to 4, "tres" to 3,
            "dos" to 2, "uno" to 1, "un" to 1, "una" to 1, "cero" to 0,
            "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90,
            "cuatrocientos" to 400, "cuatrocientas" to 400, "trescientos" to 300, "trescientas" to 300,
            "doscientos" to 200, "doscientas" to 200, "ciento" to 100, "cien" to 100
        )
        for ((palabra, num) in palabrasDirectas) {
            t = t.replace(Regex("""\b$palabra\b"""), num.toString())
        }

        // 3. Centenas seguidas de decenas/unidades: "400 20" -> "420", "400 5" -> "405", "300 9" -> "309"
        t = t.replace(Regex("""\b([1-4])00\s+(\d{1,2})\b""")) { m ->
            m.groupValues[1] + m.groupValues[2].padStart(2, '0')
        }

        // 4. Palabras "punto" y "coma" para aulas o dictados:
        t = t.replace(Regex("""\bpunto\b"""), ".")
            .replace(Regex("""\bcoma\b"""), ".")

        return t
    }

    /**
     * Extrae de forma inteligente el número de despacho a partir de cualquier locución o texto
     * (soporta "despacho 420", "despacho 4 20", "despacho cuatrocientos veinte", "despacho 4.20", "despacho 4 0 5", "D-420", etc.)
     */
    fun extraerNumeroDespacho(texto: String): String? {
        val normalizada = normalizarTextoVoz(texto)

        val tieneIndicadorDespacho = normalizada.contains("despacho") ||
            Regex("""\bd[-_\s]*\d+""").containsMatchIn(normalizada)
        if (!tieneIndicadorDespacho) {
            val soloD = Regex("""^d[-_\s]*(\d{1,3})$""").find(normalizada.trim())
            if (soloD != null) return soloD.groupValues[1]
            return null
        }

        var t = normalizada
        // 1. Planta (2, 3 o 4) + 0 + dígito (ej. "despacho 4 0 5" o "despacho 4 05") -> "despacho 405"
        t = t.replace(Regex("""(despacho\s*(?:d[-_\s]*)?|\bd[-_\s]+|\bd)\s*([234])[\s\.\-]+0[\s\.\-]*(\d)\b""")) {
            "${it.groupValues[1]}${it.groupValues[2]}0${it.groupValues[3]}"
        }
        // 2. Planta (2, 3 o 4) + dos dígitos (ej. "despacho 4 20", "despacho 4.20", "despacho 2 15") -> "despacho 420"
        t = t.replace(Regex("""(despacho\s*(?:d[-_\s]*)?|\bd[-_\s]+|\bd)\s*([234])[\s\.\-]+(\d{2})\b""")) {
            "${it.groupValues[1]}${it.groupValues[2]}${it.groupValues[3]}"
        }
        // 3. Planta (2, 3 o 4) + un dígito suelto (ej. "despacho 4 5" -> "despacho 405", "despacho 3 9" -> "despacho 309", "despacho 2 4" -> "despacho 204")
        t = t.replace(Regex("""(despacho\s*(?:d[-_\s]*)?|\bd[-_\s]+|\bd)\s*([234])[\s\.\-]+(\d)\b""")) {
            "${it.groupValues[1]}${it.groupValues[2]}0${it.groupValues[3]}"
        }

        // Extraer el número resultante de 1 a 3 dígitos tras 'despacho' o 'd'
        val regexMatch = Regex("""(?:despacho\s*(?:d[-_\s]*)?|\bd[-_\s]+|\bd)(\d{1,3})\b""").find(t)
        return regexMatch?.groupValues?.get(1)
    }

    /**
     * Resuelve búsquedas en lenguaje natural / dictado por voz hacia destinos del campus.
     * Mapea profesores ("Alberto Ruiz" -> Despacho D-08), aulas ("aula cero uno", "aula 0.1"),
     * comedores, cafetería, biblioteca, etc.
     */
    fun resolverDestinoPorVoz(texto: String): ResultadoVoz {
        if (texto.isBlank()) {
            return ResultadoVoz.Error("No se ha detectado ninguna locución.")
        }
        val limpia = texto.trim().lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")

        // 1. Detección de profesores
        if (limpia.contains("alberto") || limpia.contains("ruiz")) {
            val nodo = buscarNodoPorDestino("D-08")
            if (nodo != null) {
                return ResultadoVoz.Exito(DestinoVoz(nodo, "Despacho D-08", "Alberto Ruiz · Planta Baja", texto))
            }
        }
        if (limpia.contains("roberto") || limpia.contains("perez")) {
            val nodo = buscarNodoPorDestino("D-24")
            if (nodo != null) {
                return ResultadoVoz.Exito(DestinoVoz(nodo, "Despacho D-24", "Roberto Pérez · Planta 2", texto))
            }
        }
        if (limpia.contains("maria") || limpia.contains("gomez")) {
            val nodo = buscarNodoPorDestino("D-12")
            if (nodo != null) {
                return ResultadoVoz.Exito(DestinoVoz(nodo, "Despacho D-12", "María Gómez · Planta 1", texto))
            }
        }
        if (limpia.contains("profesor") || limpia.contains("profesora") || limpia.contains("docente")) {
            return ResultadoVoz.Error("Profesor/a no encontrado/a. Actualmente están registrados: Alberto Ruiz (D-08), Roberto Pérez (D-24) y María Gómez (D-12).")
        }

        // 2. Destinos y servicios del campus
        if (limpia.contains("comedor") || limpia.contains("comer") || limpia.contains("almorzar") || limpia.contains("cantina")) {
            val nodo = buscarNodoPorDestino("comedor")
            if (nodo != null) return ResultadoVoz.Exito(DestinoVoz(nodo, "Comedor Aynadamar", "Planta -1", texto))
        }
        if (limpia.contains("cafeteria") || limpia.contains("cafe") || limpia.contains("desayunar") || limpia.contains("bar")) {
            val nodo = buscarNodoPorDestino("cafeteria")
            if (nodo != null) return ResultadoVoz.Exito(DestinoVoz(nodo, "Cafetería", "Planta -1", texto))
        }
        if (limpia.contains("biblioteca") || limpia.contains("estudiar") || limpia.contains("libros")) {
            val nodo = buscarNodoPorDestino("biblioteca")
            if (nodo != null) return ResultadoVoz.Exito(DestinoVoz(nodo, "Biblioteca", "Planta Baja", texto))
        }
        if (limpia.contains("salon") || limpia.contains("actos") || limpia.contains("conferencia") || limpia.contains("eventos")) {
            val nodo = buscarNodoPorDestino("salon")
            if (nodo != null) return ResultadoVoz.Exito(DestinoVoz(nodo, "Salón de Actos", "Planta -1", texto))
        }
        if (limpia.contains("secretaria") || limpia.contains("matricula") || limpia.contains("expediente")) {
            val nodo = buscarNodoPorDestino("secretaria")
            if (nodo != null) return ResultadoVoz.Exito(DestinoVoz(nodo, "Secretaría", "Planta Baja", texto))
        }
        if (limpia.contains("conserjeria") || limpia.contains("conserje") || limpia.contains("informacion")) {
            val nodo = buscarNodoPorDestino("conserjeria")
            if (nodo != null) return ResultadoVoz.Exito(DestinoVoz(nodo, "Conserjería", "Planta Baja", texto))
        }
        if (limpia.contains("direccion") || limpia.contains("director") || limpia.contains("jefe de estudios")) {
            val nodo = buscarNodoPorDestino("director")
            if (nodo != null) return ResultadoVoz.Exito(DestinoVoz(nodo, "Dirección", "Planta Baja", texto))
        }

        // 3. Normalizar números hablados (unidades, decenas, centenas y decimales)
        val normalizadaNumeros = normalizarTextoVoz(limpia)

        // 4. Despachos (ej: "despacho 420", "despacho 4 20", "despacho cuatrocientos veinte", "despacho 208", "d 24", "d-08")
        val numDespacho = extraerNumeroDespacho(texto)
        val esConsultaAula = normalizadaNumeros.contains("aula") || normalizadaNumeros.contains("clase") ||
            normalizadaNumeros.contains("laboratorio") || normalizadaNumeros.contains("lab")
        if (numDespacho != null && !esConsultaAula) {
            val nodo = buscarNodoPorDestino("D-$numDespacho")
            if (nodo != null) {
                val plantaFormateada = formatearPlanta(nodo.planta)
                val titulo = if (numDespacho.length <= 2) "Despacho D-${numDespacho.padStart(2, '0')}" else "Despacho D-${nodo.nombre.removePrefix("D_")}"
                return ResultadoVoz.Exito(DestinoVoz(nodo, titulo, plantaFormateada, texto))
            } else {
                return ResultadoVoz.Error("El Despacho D-$numDespacho no se ha encontrado en la ETSIIT.")
            }
        }

        // 5. AULAS Y LABORATORIOS (Sótano, Planta Baja, Planta 1, Planta 2 y Planta 3)
        // Pueden referenciarse indistintamente como "aula X.Y" o "laboratorio X.Y" (ej: "aula 3.4" = Lab 3.4)
        // Aulas sótano
        if (normalizadaNumeros.contains("-1") || normalizadaNumeros.contains("menos 1") || normalizadaNumeros.contains("menos1")) {
            val nodo = buscarNodoPorDestino("-1")
            if (nodo != null) return ResultadoVoz.Exito(DestinoVoz(nodo, "Aula -1", "Planta -1", texto))
        }
        if (normalizadaNumeros.contains("-2") || normalizadaNumeros.contains("menos 2") || normalizadaNumeros.contains("menos2")) {
            val nodo = buscarNodoPorDestino("-2")
            if (nodo != null) return ResultadoVoz.Exito(DestinoVoz(nodo, "Aula -2", "Planta -1", texto))
        }

        // Aulas o Laboratorios con formato explícito "aula/clase/laboratorio/lab X.Y", "aula 3.4", "lab 3.4", "aula 3 4", etc.:
        val regexEspacioDocente = Regex("""(?:aula|aulas|clase|clases|laboratorio|laboratorios|lab|labs)\s*([0-4])[\.\s]*(\d+)""")
        val matchEspacio = regexEspacioDocente.find(normalizadaNumeros)
        if (matchEspacio != null) {
            val planta = matchEspacio.groupValues[1]
            val num = matchEspacio.groupValues[2]
            val nodo = buscarNodoPorDestino("$planta.$num")
            if (nodo != null) {
                val esLab = nodo.nombre.startsWith("L_")
                val titulo = if (esLab) "Laboratorio $planta.$num" else "Aula $planta.$num"
                val plantaStr = if (planta == "0") "Planta Baja" else "Planta $planta"
                return ResultadoVoz.Exito(DestinoVoz(nodo, titulo, plantaStr, texto))
            } else {
                return ResultadoVoz.Error("El Aula o Laboratorio $planta.$num no existe en la ETSIIT. Las aulas se encuentran en Semisótano (-1 y -2), Planta Baja (0.1 a 0.8), Planta 1 (1.1 a 1.8), y los laboratorios en Planta 2 (2.1 a 2.10) y Planta 3 (3.1 a 3.11).")
            }
        }

        // Formato numérico decimal en cualquier parte del texto: ej. "3.4", "3.7", "0.1", "1.2", "ir a la 3.4"
        val regexCualquierDecimal = Regex("""\b([0-4])\.(\d+)\b""")
        val matchCualquierDecimal = regexCualquierDecimal.find(normalizadaNumeros)
        if (matchCualquierDecimal != null) {
            val planta = matchCualquierDecimal.groupValues[1]
            val num = matchCualquierDecimal.groupValues[2]
            val nodo = buscarNodoPorDestino("$planta.$num")
            if (nodo != null) {
                val esLab = nodo.nombre.startsWith("L_")
                val titulo = if (esLab) "Laboratorio $planta.$num" else "Aula $planta.$num"
                val plantaStr = if (planta == "0") "Planta Baja" else "Planta $planta"
                return ResultadoVoz.Exito(DestinoVoz(nodo, titulo, plantaStr, texto))
            } else {
                return ResultadoVoz.Error("El Aula o Laboratorio $planta.$num no existe en la ETSIIT. Las aulas se encuentran en Semisótano (-1 y -2), Planta Baja (0.1 a 0.8), Planta 1 (1.1 a 1.8), y los laboratorios en Planta 2 (2.1 a 2.10) y Planta 3 (3.1 a 3.11).")
            }
        }

        // Si la frase contenía la palabra "aula", "clase", "laboratorio" o "lab" pero no coincidió con ningún aula o lab existente
        if (normalizadaNumeros.contains("aula") || normalizadaNumeros.contains("clase") ||
            normalizadaNumeros.contains("laboratorio") || normalizadaNumeros.contains("lab")) {
            return ResultadoVoz.Error("No se ha encontrado el aula o laboratorio solicitado. Las aulas van de la 0.1 a la 0.8 (Planta Baja), de la 1.1 a la 1.8 (Planta 1), Semisótano (-1 y -2), y los laboratorios en Planta 2 (2.1 a 2.10) y Planta 3 (3.1 a 3.11).")
        }

        // 7. Fallback buscando en el grafo general por coincidencia exacta
        val nodoGenerico = buscarNodoPorDestino(texto)
        if (nodoGenerico != null) {
            val titulo = nodoGenerico.nombre.replace('_', ' ')
            return ResultadoVoz.Exito(DestinoVoz(nodoGenerico, titulo, formatearPlanta(nodoGenerico.planta), texto))
        }

        return ResultadoVoz.Error("No se ha encontrado \"$texto\". Prueba diciendo: Comedor, Cafetería, Aula 0.1 o un profesor como Alberto Ruiz.")
    }

    /**
     * Calcula la distancia total en metros acumulando las aristas consecutivas del camino
     */
    fun calcularDistanciaRuta(ruta: List<Nodo>): Int {
        if (ruta.size < 2) return 0
        var total = 0
        for (i in 0 until ruta.size - 1) {
            val origen = ruta[i].id
            val destino = ruta[i + 1].id
            val arista = conexiones[origen]?.find { it.destinoId == destino }
            total += arista?.distancia ?: 0
        }
        return total
    }

    /**
     * Ejecuta Dijkstra y devuelve el objeto RutaCalculada con origen, destino, nodos y distancia
     */
    fun calcularRutaCompleta(origenId: String, destinoId: String): RutaCalculada? {
        val origen = directorioNodos[origenId] ?: return null
        val destino = directorioNodos[destinoId] ?: return null
        val nodos = calcularRutaDijkstra(origenId, destinoId)
        if (nodos.isEmpty()) return null
        val distancia = calcularDistanciaRuta(nodos)
        val tiempoSegundos = (distancia / 1.2).toInt()
        return RutaCalculada(origen, destino, nodos, distancia, tiempoSegundos)
    }

    // La función principal de Dijkstra
    fun calcularRutaDijkstra(origenId: String, destinoId: String): List<Nodo> {
        val distancias = mutableMapOf<String, Int>().withDefault { Int.MAX_VALUE }
        val nodosPrevios = mutableMapOf<String, String?>()
        val colaPrioridad = PriorityQueue<Pair<Int, String>>(compareBy { it.first })

        distancias[origenId] = 0
        colaPrioridad.add(Pair(0, origenId))

        while (colaPrioridad.isNotEmpty()) {
            val nodoExtraido = colaPrioridad.poll() ?: break
            val distanciaActual = nodoExtraido.first
            val nodoActual = nodoExtraido.second

            if (nodoActual == destinoId) break

            val vecinos = conexiones[nodoActual] ?: continue

            for (arista in vecinos) {
                val nuevaDistancia = distanciaActual + arista.distancia
                val distanciaConocida = distancias.getValue(arista.destinoId)

                if (nuevaDistancia < distanciaConocida) {
                    distancias[arista.destinoId] = nuevaDistancia
                    nodosPrevios[arista.destinoId] = nodoActual
                    colaPrioridad.add(Pair(nuevaDistancia, arista.destinoId))
                }
            }
        }

        val secuenciaOrdenada = mutableListOf<Nodo>()
        var actual: String? = destinoId

        while (actual != null) {
            directorioNodos[actual]?.let { secuenciaOrdenada.add(it) }
            actual = nodosPrevios[actual]
        }

        return secuenciaOrdenada.reversed()
    }

    private fun agregarArista(origenId: String, destinoId: String, distancia: Int) {
        conexiones[origenId]?.add(AristaDijkstra(destinoId, distancia))
        conexiones[destinoId]?.add(AristaDijkstra(origenId, distancia))
    }
}

/**
 * Singleton repositorio para cargar el grafo de la ETSIIT una única vez en memoria
 */
object GrafoRepository {
    private var instance: GrafoETSIIT? = null

    fun getGrafo(context: Context): GrafoETSIIT {
        return instance ?: synchronized(this) {
            instance ?: GrafoETSIIT().apply {
                cargarDesdeAssets(context, "grafo_etsiit_completo.json")
            }.also { instance = it }
        }
    }
}
