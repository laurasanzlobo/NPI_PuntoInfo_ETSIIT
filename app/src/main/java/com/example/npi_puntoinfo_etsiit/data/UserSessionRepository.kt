package com.example.npi_puntoinfo_etsiit.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private const val KEY_SESSION_V2 = "session_v2_initialized"
    private val gson = Gson()

    // Estado reactivo de Compose para que toda la UI (AppNavigation, TopAppBar, sensores, etc.)
    // se recomponga al instante al iniciar o cerrar sesión.
    private var currentProfileState by mutableStateOf<UserProfile?>(null)

    val officialMatricula = mapOf(
        "PTC" to "A1",
        "SS" to "A1",
        "TIC" to "A1",
        "NPI" to "A1",
        "VC" to "A1",
        "PL" to "A1"
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Obtiene el perfil actual guardado en persistencia y suscribe a Compose a cambios de sesión.
     * Por defecto la aplicación arranca SIN iniciar sesión (isLoggedIn = false).
     */
    fun getProfile(context: Context): UserProfile {
        currentProfileState?.let { return it }

        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_SESSION_V2, false)) {
            prefs.edit().remove(KEY_PROFILE).putBoolean(KEY_SESSION_V2, true).apply()
            val loggedOut = UserProfile(isLoggedIn = false)
            currentProfileState = loggedOut
            return loggedOut
        }

        val json = prefs.getString(KEY_PROFILE, null)
        if (json == null) {
            val loggedOut = UserProfile(isLoggedIn = false)
            currentProfileState = loggedOut
            return loggedOut
        }

        val systemLanguage = if (java.util.Locale.getDefault().language.equals("en", ignoreCase = true)) "en" else "es"
        val loadedWithSysLang = (try {
            gson.fromJson(json, UserProfile::class.java) ?: UserProfile(isLoggedIn = false)
        } catch (_: Exception) {
            UserProfile(isLoggedIn = false)
        }).copy(language = systemLanguage)

        if (!loadedWithSysLang.isLoggedIn) {
            currentProfileState = loadedWithSysLang
            return loadedWithSysLang
        }

        // Si está logueado, garantizamos los datos oficiales de Elena Morales Vega y sus 6 asignaturas
        val hasAllSix = officialMatricula.keys.all { loadedWithSysLang.matricula.containsKey(it) }
        val finalProfile = if (!hasAllSix || loadedWithSysLang.name.contains("José", ignoreCase = true) || loadedWithSysLang.name.contains("Pedregosa", ignoreCase = true) || loadedWithSysLang.dni == "48291054") {
            val updated = loadedWithSysLang.copy(
                name = "Elena Morales Vega",
                dni = "48291054R",
                primaryCourse = 4,
                primaryGroup = "A",
                cuatrimestre = 1,
                matricula = officialMatricula,
                language = systemLanguage,
                isLoggedIn = true
            )
            saveProfile(context, updated)
            updated
        } else {
            loadedWithSysLang
        }
        currentProfileState = finalProfile
        return finalProfile
    }

    /**
     * Inicia sesión con el perfil oficial preconfigurado de la estudiante tras escanear TUI / QR.
     */
    fun loginWithOfficialProfile(context: Context, isAccessibleRouting: Boolean = false): UserProfile {
        val currentLang = getProfile(context).language
        val newProfile = UserProfile(
            name = "Elena Morales Vega",
            dni = "48291054R",
            tuiCardNumber = "5083180029381024",
            nfcUid = "EA:76:97:6E",
            colectivo = "ESTUDIANTE",
            degreeId = "gii",
            degreeName = "Grado en Ingeniería Informática",
            primaryCourse = 4,
            primaryGroup = "A",
            cuatrimestre = 1,
            matricula = officialMatricula,
            isAccessibleRouting = isAccessibleRouting,
            language = currentLang,
            isLoggedIn = true
        )
        saveProfile(context, newProfile)
        return newProfile
    }

    /**
     * Actualiza el idioma preferido de la app ("es" / "en").
     */
    fun updateLanguage(context: Context, language: String) {
        val current = getProfile(context)
        saveProfile(context, current.copy(language = language))
    }

    /**
     * Guarda el perfil completo del estudiante y actualiza el estado reactivo de Compose.
     */
    fun saveProfile(context: Context, profile: UserProfile) {
        currentProfileState = profile
        val json = gson.toJson(profile)
        getPrefs(context).edit()
            .putBoolean(KEY_SESSION_V2, true)
            .putString(KEY_PROFILE, json)
            .apply()
    }

    /**
     * Cierra la sesión activa borrando la persistencia del perfil y notificando a toda la UI.
     */
    fun logout(context: Context) {
        currentProfileState = UserProfile(isLoggedIn = false)
        getPrefs(context).edit()
            .putBoolean(KEY_SESSION_V2, true)
            .remove(KEY_PROFILE)
            .apply()
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

