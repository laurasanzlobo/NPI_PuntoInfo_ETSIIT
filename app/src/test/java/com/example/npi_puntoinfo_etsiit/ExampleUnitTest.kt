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
}