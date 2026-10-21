package com.example.npi_puntoinfo_etsiit.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Gestor de persistencia de la sesión del estudiante en SharedPreferences.
 * Permite guardar el perfil validado por TUI + NFC y configurar/modificar
 * libremente las asignaturas matriculadas y sus subgrupos específicos.
 */
object UserSessionRepository {

    private const val PREFS_NAME = "npi_user_session"
    private const val KEY_PROFILE = "user_profile_json"
    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Obtiene el perfil actual guardado en persistencia.
     */
    fun getProfile(context: Context): UserProfile {
        val json = getPrefs(context).getString(KEY_PROFILE, null) ?: return UserProfile()
        return try {
            gson.fromJson(json, UserProfile::class.java) ?: UserProfile()
        } catch (_: Exception) {
            UserProfile()
        }
    }

    /**
     * Guarda el perfil completo del estudiante.
     */
    fun saveProfile(context: Context, profile: UserProfile) {
        val json = gson.toJson(profile)
        getPrefs(context).edit().putString(KEY_PROFILE, json).apply()
    }

    /**
     * Cierra la sesión activa borrando la persistencia del perfil.
     */
    fun logout(context: Context) {
        getPrefs(context).edit().remove(KEY_PROFILE).apply()
    }

    /**
     * Actualiza el grado, curso principal y grupo del estudiante.
     */
    fun updateAcademicInfo(
        context: Context,
        degreeId: String,
        degreeName: String,
        course: Int,
        group: String,
        cuatrimestre: Int = 1
    ) {
        val current = getProfile(context)
        saveProfile(
            context,
            current.copy(
                degreeId = degreeId,
                degreeName = degreeName,
                primaryCourse = course,
                primaryGroup = group,
                cuatrimestre = cuatrimestre
            )
        )
    }

    /**
     * Actualiza la matrícula de asignaturas del estudiante (acrónimo -> subgrupo de prácticas).
     */
    fun updateMatricula(context: Context, matricula: Map<String, String>) {
        val current = getProfile(context)
        saveProfile(context, current.copy(matricula = matricula))
    }

    /**
     * Asigna o cambia el subgrupo de prácticas para una asignatura concreta.
     */
    fun setSubjectSubgroup(context: Context, acronym: String, subgroup: String) {
        val current = getProfile(context)
        val nuevaMatricula = current.matricula.toMutableMap()
        nuevaMatricula[acronym] = subgroup
        saveProfile(context, current.copy(matricula = nuevaMatricula))
    }

    /**
     * Desmatricula una asignatura de la lista personalizada del estudiante.
     */
    fun removeSubject(context: Context, acronym: String) {
        val current = getProfile(context)
        val nuevaMatricula = current.matricula.toMutableMap()
        nuevaMatricula.remove(acronym)
        saveProfile(context, current.copy(matricula = nuevaMatricula))
    }
}
