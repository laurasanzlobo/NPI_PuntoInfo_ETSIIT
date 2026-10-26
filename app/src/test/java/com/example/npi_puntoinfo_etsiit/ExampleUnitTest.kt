package com.example.npi_puntoinfo_etsiit

import org.junit.Test
import org.junit.Assert.*
import java.io.File

class ExampleUnitTest {
    @Test
    fun testGrafoDijkstraRutas() {
        val grafo = GrafoETSIIT()
        val jsonFile = File("src/main/assets/grafo_etsiit_completo.json")
        assertTrue("El archivo JSON del grafo debe existir", jsonFile.exists())
        
        grafo.cargarDesdeJsonLocal(jsonFile.absolutePath)
        
        val inicio = grafo.obtenerNodoInicioPredeterminado()
        assertNotNull("Debe existir el nodo de inicio", inicio)
        assertEquals("Entrada_Exterior", inicio.nombre)
        
        // 1. Probar ruta hacia el Comedor
        val nodoComedor = grafo.buscarNodoPorDestino("Comedor Aynadamar")
        assertNotNull("Debe encontrar el nodo del comedor", nodoComedor)
        val rutaComedor = grafo.calcularRutaCompleta(inicio.id, nodoComedor!!.id)
        assertNotNull("Debe calcular una ruta válida al comedor", rutaComedor)
        assertTrue("La ruta debe tener al menos 2 nodos", rutaComedor!!.nodos.size >= 2)
        assertTrue("La distancia debe ser positiva", rutaComedor.distanciaMetros > 0)
        println("Ruta al comedor: ${rutaComedor.nodos.size} nodos, ${rutaComedor.distanciaMetros} metros")

        // 2. Probar ruta hacia el Aula 0.1
        val nodoAula = grafo.buscarNodoPorDestino("Aula 0.1")
        assertNotNull("Debe encontrar el nodo del Aula 0.1", nodoAula)
        val rutaAula = grafo.calcularRutaCompleta(inicio.id, nodoAula!!.id)
        assertNotNull("Debe calcular una ruta válida al aula", rutaAula)
        assertTrue("La ruta al aula debe tener nodos", rutaAula!!.nodos.size >= 2)
        println("Ruta a Aula 0.1: ${rutaAula.nodos.size} nodos, ${rutaAula.distanciaMetros} metros")

        // 3. Probar ruta hacia un Despacho
        val nodoDespacho = grafo.buscarNodoPorDestino("Despacho D-08")
        assertNotNull("Debe encontrar un despacho", nodoDespacho)
        val rutaDespacho = grafo.calcularRutaCompleta(inicio.id, nodoDespacho!!.id)
        assertNotNull("Debe calcular una ruta válida al despacho", rutaDespacho)
        println("Ruta a Despacho: ${rutaDespacho!!.nodos.size} nodos, ${rutaDespacho.distanciaMetros} metros")
    }

    @Test
    fun testResolverDestinoPorVoz() {
        val grafo = GrafoETSIIT()
        val jsonFile = File("src/main/assets/grafo_etsiit_completo.json")
        grafo.cargarDesdeJsonLocal(jsonFile.absolutePath)

        // 1. Detección por nombre de profesor
        val resProfesor = grafo.resolverDestinoPorVoz("Quiero ir con el profesor Alberto Ruiz")
        assertTrue("Debe resolver el destino por voz con éxito para Alberto Ruiz", resProfesor is ResultadoVoz.Exito)
        val destProfesor = (resProfesor as ResultadoVoz.Exito).destino
        assertEquals("Despacho D-08", destProfesor.titulo)
        assertTrue(destProfesor.ubicacion.contains("Alberto Ruiz"))

        // 2. Detección por aula hablada
        val resAula = grafo.resolverDestinoPorVoz("Llévame al aula cero punto uno")
        assertTrue("Debe resolver el aula hablada con éxito", resAula is ResultadoVoz.Exito)
        val destAula = (resAula as ResultadoVoz.Exito).destino
        assertEquals("Aula 0.1", destAula.titulo)
        assertEquals("Planta Baja", destAula.ubicacion)

        // 3. Prueba crítica: Aulas y laboratorios se pueden nombrar indistintamente ("aula 3.4", "laboratorio 3.4", "3.4")
        val queriesAula34 = listOf(
            "aula 3.4",
            "aula 3 punto 4",
            "aula 3 4",
            "aula tres punto cuatro",
            "laboratorio 3.4",
            "lab 3.4",
            "3.4"
        )
        for (q in queriesAula34) {
            val res = grafo.resolverDestinoPorVoz(q)
            assertTrue("La consulta '$q' debe devolver éxito hacia el espacio 3.4", res is ResultadoVoz.Exito)
            val dest = (res as ResultadoVoz.Exito).destino
            assertEquals("L_34", dest.nodo.nombre)
            assertTrue("No debe ser un despacho", !dest.nodo.nombre.startsWith("D_"))
            assertEquals("Planta 3", dest.ubicacion)
        }

        // Probar también que "aula 3.7" y "aula 2.1" resuelven a sus respectivos laboratorios sin ir a despachos
        val res37 = grafo.resolverDestinoPorVoz("aula 3.7")
        assertTrue("Aula 3.7 debe resolver con éxito", res37 is ResultadoVoz.Exito)
        assertEquals("L_37", (res37 as ResultadoVoz.Exito).destino.nodo.nombre)

        val res21 = grafo.resolverDestinoPorVoz("aula 2.1")
        assertTrue("Aula 2.1 debe resolver con éxito", res21 is ResultadoVoz.Exito)
        assertEquals("L_21", (res21 as ResultadoVoz.Exito).destino.nodo.nombre)

        // Una sala que realmente no existe (ej. 3.99) debe devolver Error
        val resInexistente = grafo.resolverDestinoPorVoz("aula 3.99")
        assertTrue("Aula 3.99 debe devolver error", resInexistente is ResultadoVoz.Error)
        assertTrue((resInexistente as ResultadoVoz.Error).mensaje.contains("no existe"))

        // 4. Despacho explícito y combinaciones de voz ("despacho 420", "despacho 4 20", "cuatrocientos veinte", etc.)
        val resDespacho = grafo.resolverDestinoPorVoz("Quiero ir al despacho 8")
        assertTrue("Debe resolver el despacho explícito", resDespacho is ResultadoVoz.Exito)
        val destDespacho = (resDespacho as ResultadoVoz.Exito).destino
        assertEquals("Despacho D-08", destDespacho.titulo)

        val queriesDespacho420 = listOf(
            "despacho 420",
            "despacho 4 20",
            "despacho cuatrocientos veinte",
            "despacho cuatro veinte",
            "despacho 4.20",
            "despacho 4-20",
            "d 420",
            "d-420"
        )
        for (q in queriesDespacho420) {
            val res = grafo.resolverDestinoPorVoz(q)
            assertTrue("La consulta '$q' debe resolver con éxito hacia el Despacho D_420", res is ResultadoVoz.Exito)
            val dest = (res as ResultadoVoz.Exito).destino
            assertEquals("D_420", dest.nodo.nombre)
            assertEquals("Despacho D-420", dest.titulo)
            assertEquals("Planta 4", dest.ubicacion)
        }

        // Otras plantas y combinaciones habladas
        val queriesOtrosDespachos = mapOf(
            "despacho trescientos nueve" to ("D_309" to "Planta 3"),
            "despacho 3 09" to ("D_309" to "Planta 3"),
            "despacho 3 9" to ("D_309" to "Planta 3"),
            "despacho doscientos quince" to ("D_215" to "Planta 2"),
            "despacho 2 15" to ("D_215" to "Planta 2"),
            "despacho cuatrocientos cinco" to ("D_405" to "Planta 4"),
            "despacho 4 0 5" to ("D_405" to "Planta 4")
        )
        for ((q, esperado) in queriesOtrosDespachos) {
            val res = grafo.resolverDestinoPorVoz(q)
            assertTrue("La consulta '$q' debe resolver hacia ${esperado.first}", res is ResultadoVoz.Exito)
            val dest = (res as ResultadoVoz.Exito).destino
            assertEquals(esperado.first, dest.nodo.nombre)
            assertEquals(esperado.second, dest.ubicacion)
        }

        // 5. Detección de comedor
        val resComedor = grafo.resolverDestinoPorVoz("Quiero comer algo en el comedor")
        assertTrue("Debe resolver el comedor", resComedor is ResultadoVoz.Exito)
        val destComedor = (resComedor as ResultadoVoz.Exito).destino
        assertTrue(destComedor.titulo.contains("Comedor"))

        // 6. Detección de cafetería
        val resCafeteria = grafo.resolverDestinoPorVoz("Vamos a la cafetería a tomar un café")
        assertTrue("Debe resolver la cafetería", resCafeteria is ResultadoVoz.Exito)
        val destCafeteria = (resCafeteria as ResultadoVoz.Exito).destino
        assertEquals("Cafetería", destCafeteria.titulo)

        // 7. Detección de biblioteca
        val resBiblioteca = grafo.resolverDestinoPorVoz("Dónde está la biblioteca")
        assertTrue("Debe resolver la biblioteca", resBiblioteca is ResultadoVoz.Exito)
        val destBiblioteca = (resBiblioteca as ResultadoVoz.Exito).destino
        assertEquals("Biblioteca", destBiblioteca.titulo)

        // 8. Prueba de cálculo completo desde el inicio hasta el destino por voz
        val inicio = grafo.obtenerNodoInicioPredeterminado()
        val ruta = grafo.calcularRutaCompleta(inicio.id, destProfesor.nodo.id)
        assertNotNull("Debe calcular la ruta hacia el profesor", ruta)
        assertTrue("La ruta debe tener nodos y metros", ruta!!.distanciaMetros > 0 && ruta.nodos.isNotEmpty())
        println("Ruta calculada por voz a ${destProfesor.titulo}: ${ruta.distanciaMetros}m en ${ruta.tiempoEstimadoFormateado}")

        // 9. Comprobación de detección del comedor más cercano según GPS
        // Cerca de la ETSIIT -> Aynadamar
        val nearAynadamar = com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager.findNearestDiningHall(37.1971, -3.6244)
        assertEquals("aynadamar", nearAynadamar.id)

        // Cerca de Ciencias / Severo Ochoa -> Fuentenueva
        val nearFuentenueva = com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager.findNearestDiningHall(37.1815, -3.6090)
        assertEquals("fuentenueva", nearFuentenueva.id)

        // Cerca de Filosofía / Cartuja -> Cartuja
        val nearCartuja = com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager.findNearestDiningHall(37.1935, -3.5980)
        assertEquals("cartuja", nearCartuja.id)

        // Cerca de Medicina / PTS -> PTS
        val nearPts = com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager.findNearestDiningHall(37.1475, -3.6020)
        assertEquals("pts", nearPts.id)

        // Sin señal GPS -> Aynadamar por defecto
        val fallbackDefault = com.example.npi_puntoinfo_etsiit.sensors.CampusLocationManager.findNearestDiningHall(null, null)
        assertEquals("aynadamar", fallbackDefault.id)
    }

    @Test
    fun testEstimacionHorarioYBarometro() {
        // 1. Probar estimación de horario durante una clase en curso (jueves 09:00 -> NPI en Aula 0.5)
        val thursday = java.time.LocalDate.of(2025, 2, 20) // Jueves
        val ongoingTime = java.time.LocalTime.of(9, 0)
        val estimatedOngoing = com.example.npi_puntoinfo_etsiit.data.ScheduleRepository.estimateCurrentOrRecentClassroom(
            context = null,
            date = thursday,
            time = ongoingTime
        )
        assertNotNull("Debe estimar la clase de NPI en curso a las 09:00 los jueves", estimatedOngoing)
        assertEquals("Aula 0.5", estimatedOngoing!!.roomName)
        assertEquals(com.example.npi_puntoinfo_etsiit.data.ClassroomStatus.ONGOING, estimatedOngoing.status)

        // 2. Probar estimación de horario para una clase recién terminada (jueves 10:40 -> NPI finalizó a las 10:30)
        val endedTime = java.time.LocalTime.of(10, 40)
        val estimatedEnded = com.example.npi_puntoinfo_etsiit.data.ScheduleRepository.estimateCurrentOrRecentClassroom(
            context = null,
            date = thursday,
            time = endedTime
        )
        assertNotNull("Debe estimar la clase recién terminada a las 10:40", estimatedEnded)
        assertEquals("Aula 0.5", estimatedEnded!!.roomName)
        assertEquals(com.example.npi_puntoinfo_etsiit.data.ClassroomStatus.JUST_ENDED, estimatedEnded.status)

        // 3. Probar horario fuera de clases (jueves 22:00) -> Debe ser null
        val nightTime = java.time.LocalTime.of(22, 0)
        val estimatedNight = com.example.npi_puntoinfo_etsiit.data.ScheduleRepository.estimateCurrentOrRecentClassroom(
            context = null,
            date = thursday,
            time = nightTime
        )
        assertNull("Fuera de clases debe ser null", estimatedNight)

        // 4. Probar estimación del barómetro según la presión atmosférica
        val pbEstimate = com.example.npi_puntoinfo_etsiit.sensors.BarometerManager.estimateFloorFromPressure(935.0f, 700.0f)
        assertEquals("PB", pbEstimate.floorCode)

        val p1Estimate = com.example.npi_puntoinfo_etsiit.sensors.BarometerManager.estimateFloorFromPressure(933.8f, 703.5f)
        assertEquals("P1", p1Estimate.floorCode)

        val pssEstimate = com.example.npi_puntoinfo_etsiit.sensors.BarometerManager.estimateFloorFromPressure(937.0f, 696.0f)
        assertEquals("PSS", pssEstimate.floorCode)
    }
}