package com.example.npi_puntoinfo_etsiit.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.InputStreamReader
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Representa una clase lectiva del horario oficial de la ETSIIT.
 * Distingue explícitamente entre Teoría (fondo gris) y Prácticas (fondo blanco).
 */
data class ClassItem(
    val time: String,
    val subject: String,
    val acronym: String,
    val room: String,
    val professor: String = "",
    val isPractice: Boolean,
    val isCurrent: Boolean = false,
    val subgroup: String? = null
)

data class DayScheduleInfo(
    val dayName: String,
    val dayLabel: String,
    val classes: List<ClassItem>
)

object ScheduleRepository {

    private val gson = Gson()
    private var cachedJsonData: JsonObject? = null

    // Horario de respaldo por defecto (3º GII)
    val fallbackMondayClasses = listOf(
        ClassItem(
            time = "09:30 - 11:30",
            subject = "Programación Técnica y Científica",
            acronym = "PTC",
            room = "Aula 1.8 · Planta 1",
            professor = "Ignacio Requena",
            isPractice = false
        ),
        ClassItem(
            time = "11:30 - 13:30",
            subject = "Simulación de Sistemas",
            acronym = "SS",
            room = "Aula 2.7 · Planta 2",
            professor = "Javier Martínez",
            isPractice = true,
            subgroup = "A1"
        )
    )

    val fallbackTuesdayClasses = listOf(
        ClassItem(
            time = "09:30 - 11:30",
            subject = "Teoría de la Información y la Codificación",
            acronym = "TIC",
            room = "Aula 2.3 · Planta 2",
            professor = "Manuel Gómez",
            isPractice = true,
            subgroup = "A1"
        ),
        ClassItem(
            time = "11:30 - 13:30",
            subject = "Simulación de Sistemas",
            acronym = "SS",
            room = "Aula 0.1 · Planta Baja",
            professor = "Javier Martínez",
            isPractice = false
        )
    )

    val fallbackWednesdayClasses = listOf(
        ClassItem(
            time = "09:30 - 11:30",
            subject = "Teoría de la Información y la Codificación",
            acronym = "TIC",
            room = "Aula 1.1 · Planta 1",
            professor = "Manuel Gómez",
            isPractice = false
        ),
        ClassItem(
            time = "11:30 - 13:30",
            subject = "Programación Técnica y Científica",
            acronym = "PTC",
            room = "Aula 3.5 · Planta 3",
            professor = "Ignacio Requena",
            isPractice = true,
            subgroup = "A1"
        )
    )

    val fallbackThursdayClasses = listOf(
        ClassItem(
            time = "08:30 - 10:30",
            subject = "Nuevos Paradigmas de Interacción",
            acronym = "NPI",
            room = "Aula 0.5 · Planta Baja",
            professor = "Roberto Pérez",
            isPractice = false
        ),
        ClassItem(
            time = "10:30 - 12:30",
            subject = "Visión por Computador",
            acronym = "VC",
            room = "Aula 3.8 · Planta 3",
            professor = "Nicolás Pérez",
            isPractice = true,
            subgroup = "A1"
        ),
        ClassItem(
            time = "12:30 - 14:30",
            subject = "Visión por Computador",
            acronym = "VC",
            room = "Aula 0.5 · Planta Baja",
            professor = "Nicolás Pérez",
            isPractice = false
        )
    )

    val fallbackFridayClasses = listOf(
        ClassItem(
            time = "08:30 - 10:30",
            subject = "Procesadores de Lenguajes",
            acronym = "PL",
            room = "Aula 2.8 · Planta 2",
            professor = "Juan Carlos Cubero",
            isPractice = true,
            subgroup = "A1"
        ),
        ClassItem(
            time = "10:30 - 12:30",
            subject = "Procesadores de Lenguajes",
            acronym = "PL",
            room = "Aula 0.5 · Planta Baja",
            professor = "Juan Carlos Cubero",
            isPractice = false
        ),
        ClassItem(
            time = "12:30 - 14:30",
            subject = "Nuevos Paradigmas de Interacción",
            acronym = "NPI",
            room = "Aula 2.7 · Planta 2",
            professor = "Roberto Pérez",
            isPractice = true,
            subgroup = "A1"
        )
    )

    private fun loadJsonData(context: Context): JsonObject? {
        if (cachedJsonData != null) return cachedJsonData
        return try {
            context.assets.open("horarios_etsiit.json").use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    val data = gson.fromJson(reader, JsonObject::class.java)
                    cachedJsonData = data
                    data
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retorna la lista de grados disponibles extraídos del archivo JSON.
     */
    fun getAvailableDegrees(context: Context): List<Pair<String, String>> {
        val data = loadJsonData(context) ?: return listOf("gii" to "Grado en Ingeniería Informática")
        val gradosArr = data.getAsJsonArray("grados") ?: return emptyList()
        val list = mutableListOf<Pair<String, String>>()
        for (g in gradosArr) {
            val obj = g.asJsonObject
            val id = obj.get("id").asString
            val nombre = obj.get("nombre").asString
            list.add(id to nombre)
        }
        return list.distinctBy { it.first }
    }

    /**
     * Retorna todas las asignaturas disponibles para un grado y curso concreto,
     * detectando los subgrupos de prácticas existentes.
     */
    fun getCatalogSubjects(context: Context, degreeId: String, course: Int): List<SubjectOption> {
        val data = loadJsonData(context) ?: return emptyList()
        val gradosArr = data.getAsJsonArray("grados") ?: return emptyList()
        
        val subjectMap = mutableMapOf<String, SubjectOption>()

        for (g in gradosArr) {
            val gObj = g.asJsonObject
            if (gObj.get("id").asString != degreeId) continue

            val cursosArr = gObj.getAsJsonArray("cursos") ?: continue
            for (c in cursosArr) {
                val cObj = c.asJsonObject
                if (cObj.get("curso").asInt != course) continue

                val horarioObj = cObj.getAsJsonObject("horario") ?: continue
                for (entry in horarioObj.entrySet()) {
                    val clasesArr = entry.value.asJsonArray
                    for (item in clasesArr) {
                        val itemObj = item.asJsonObject
                        val acronym = itemObj.get("acronym").asString
                        val subjectName = itemObj.get("subject").asString
                        val subgroup = if (itemObj.has("subgroup") && !itemObj.get("subgroup").isJsonNull) {
                            itemObj.get("subgroup").asString
                        } else null

                        val currentOpt = subjectMap.getOrPut(acronym) {
                            SubjectOption(
                                acronym = acronym,
                                name = subjectName,
                                curso = course,
                                degreeId = degreeId,
                                subgruposDisponibles = emptyList()
                            )
                        }

                        if (!subgroup.isNullOrBlank() && !currentOpt.subgruposDisponibles.contains(subgroup)) {
                            subjectMap[acronym] = currentOpt.copy(
                                subgruposDisponibles = (currentOpt.subgruposDisponibles + subgroup).sorted()
                            )
                        }
                    }
                }
            }
        }

        return subjectMap.values.sortedBy { it.acronym }
    }

    /**
     * Retorna los grupos o menciones disponibles para un grado y curso concreto.
     */
    fun getAvailableGroups(context: Context, degreeId: String, course: Int): List<String> {
        val data = loadJsonData(context) ?: return listOf("A")
        val gradosArr = data.getAsJsonArray("grados") ?: return listOf("A")
        val groupsSet = mutableSetOf<String>()

        for (g in gradosArr) {
            val gObj = g.asJsonObject
            if (!gObj.get("id").asString.equals(degreeId, ignoreCase = true)) continue

            val cursosArr = gObj.getAsJsonArray("cursos") ?: continue
            for (c in cursosArr) {
                val cObj = c.asJsonObject
                if (cObj.get("curso").asInt == course) {
                    val grp = cObj.get("grupo").asString
                    val menc = if (cObj.has("mencion") && !cObj.get("mencion").isJsonNull) cObj.get("mencion").asString else ""
                    if (menc.isNotBlank() && (grp.isBlank() || grp.equals(menc, ignoreCase = true))) {
                        groupsSet.add(menc)
                    } else if (grp.isNotBlank()) {
                        groupsSet.add(grp)
                    }
                }
            }
        }
        return if (groupsSet.isNotEmpty()) groupsSet.sorted() else listOf("A")
    }

    /**
     * Obtiene las clases para un día de la semana concreto.
     * Si el perfil tiene matrícula configurada, filtra automáticamente sus asignaturas
     * y subgrupos elegidos, incluyendo asignaturas repetidas.
     */
    fun getClassesForDay(
        context: Context? = null,
        dayOfWeek: DayOfWeek = LocalDate.now().dayOfWeek,
        profile: UserProfile = if (context != null) UserSessionRepository.getProfile(context) else UserProfile()
    ): List<ClassItem> {
        val dayNameSpanish = when (dayOfWeek) {
            DayOfWeek.MONDAY -> "Lunes"
            DayOfWeek.TUESDAY -> "Martes"
            DayOfWeek.WEDNESDAY -> "Miércoles"
            DayOfWeek.THURSDAY -> "Jueves"
            DayOfWeek.FRIDAY -> "Viernes"
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> return emptyList()
        }

        val data = if (context != null) loadJsonData(context) else null
        if (data == null) {
            return when (dayOfWeek) {
                DayOfWeek.MONDAY -> fallbackMondayClasses
                DayOfWeek.TUESDAY -> fallbackTuesdayClasses
                DayOfWeek.WEDNESDAY -> fallbackWednesdayClasses
                DayOfWeek.THURSDAY -> fallbackThursdayClasses
                DayOfWeek.FRIDAY -> fallbackFridayClasses
                else -> emptyList()
            }
        }

        val gradosArr = data.getAsJsonArray("grados") ?: return emptyList()
        val degreeId = profile.degreeId.ifBlank { "gii" }
        val course = profile.primaryCourse
        val group = profile.primaryGroup.ifBlank { "A" }
        val matricula = profile.matricula

        val resultClasses = mutableListOf<ClassItem>()

        for (g in gradosArr) {
            val gObj = g.asJsonObject
            if (gObj.get("id").asString != degreeId) continue

            val cursosArr = gObj.getAsJsonArray("cursos") ?: continue
            for (c in cursosArr) {
                val cObj = c.asJsonObject
                val cursoNum = cObj.get("curso").asInt
                val grupoStr = cObj.get("grupo").asString
                val cuatrimestreNum = cObj.get("cuatrimestre").asInt

                // Si no hay matrícula personalizada, mostramos el horario del grupo principal
                val matchesGeneralGroup = (cursoNum == course && grupoStr.equals(group, ignoreCase = true) && cuatrimestreNum == profile.cuatrimestre)

                val horarioObj = cObj.getAsJsonObject("horario") ?: continue
                val clasesDelDia = horarioObj.getAsJsonArray(dayNameSpanish) ?: continue

                for (item in clasesDelDia) {
                    val itemObj = item.asJsonObject
                    val acronym = itemObj.get("acronym").asString
                    val isPractice = itemObj.get("isPractice").asBoolean
                    val subg = if (itemObj.has("subgroup") && !itemObj.get("subgroup").isJsonNull) {
                        itemObj.get("subgroup").asString
                    } else null

                    val classItem = ClassItem(
                        time = itemObj.get("time").asString,
                        subject = itemObj.get("subject").asString,
                        acronym = acronym,
                        room = itemObj.get("room").asString,
                        professor = if (itemObj.has("professor")) itemObj.get("professor").asString else "",
                        isPractice = isPractice,
                        subgroup = subg
                    )

                    if (matricula.isNotEmpty()) {
                        // El usuario tiene asignaturas personalizadas
                        if (matricula.containsKey(acronym)) {
                            val targetSubgroup = matricula[acronym]
                            if (!isPractice) {
                                // Para teoría, añadir si coincide con su grupo o si no hay duplicado
                                if (!resultClasses.any { it.acronym == acronym && it.time == classItem.time }) {
                                    resultClasses.add(classItem)
                                }
                            } else {
                                // Para prácticas: si es grupo único (subg == null o elegido "Único"),
                                // o si coincide con el subgrupo elegido (ej. "A1", "1", etc.)
                                val isSinglePractice = subg == null || targetSubgroup.isNullOrBlank() || targetSubgroup.equals("Único", ignoreCase = true)
                                val matchesPractice = isSinglePractice || targetSubgroup.equals(subg, ignoreCase = true)
                                if (matchesPractice) {
                                    resultClasses.add(classItem)
                                }
                            }
                        }
                    } else if (matchesGeneralGroup) {
                        resultClasses.add(classItem)
                    }
                }
            }
        }

        // Ordenar por hora de inicio
        resultClasses.sortBy { it.time.substringBefore("-").trim() }
        return if (resultClasses.isNotEmpty()) resultClasses else {
            // Fallback en caso de que el grupo seleccionado aún no tenga clases registradas en ese día
            when (dayOfWeek) {
                DayOfWeek.MONDAY -> fallbackMondayClasses
                DayOfWeek.TUESDAY -> fallbackTuesdayClasses
                DayOfWeek.WEDNESDAY -> fallbackWednesdayClasses
                DayOfWeek.THURSDAY -> fallbackThursdayClasses
                DayOfWeek.FRIDAY -> fallbackFridayClasses
                else -> emptyList()
            }
        }
    }

    /**
     * Devuelve la clase en curso o la próxima clase con un margen de 30 minutos antes.
     */
    fun getCurrentOrUpcomingClass(
        context: Context? = null,
        date: LocalDate = LocalDate.now(),
        time: LocalTime = LocalTime.now(),
        profile: UserProfile = if (context != null) UserSessionRepository.getProfile(context) else UserProfile()
    ): ClassItem? {
        val classes = getClassesForDay(context, date.dayOfWeek, profile)
        if (classes.isEmpty()) return null

        for (i in classes.indices) {
            val item = classes[i]
            val parts = item.time.split("-").map { it.trim() }
            if (parts.size != 2) continue

            val start = try { LocalTime.parse(parts[0]) } catch (_: Exception) { continue }
            val end = try { LocalTime.parse(parts[1]) } catch (_: Exception) { continue }

            val windowStart = start.minusMinutes(30)
            val windowEnd = if (i + 1 < classes.size) {
                val nextParts = classes[i + 1].time.split("-").map { it.trim() }
                val nextStart = try { LocalTime.parse(nextParts[0]) } catch (_: Exception) { null }
                if (nextStart != null) {
                    val nextWindowStart = nextStart.minusMinutes(30)
                    if (nextWindowStart.isBefore(end)) nextWindowStart else end
                } else {
                    end
                }
            } else {
                end
            }

            if (!time.isBefore(windowStart) && time.isBefore(windowEnd)) {
                return item
            }
        }

        return null
    }
}
