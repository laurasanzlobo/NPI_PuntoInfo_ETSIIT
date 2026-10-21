# Parser de Horarios Oficiales ETSIIT - UGR

Herramienta en Python para descargar y parsear automáticamente los horarios oficiales de la **Escuela Técnica Superior de Ingenierías Informática y de Telecomunicación (ETSIIT)** desde la web oficial de la UGR:
[https://etsiit.ugr.es/docencia/grados/horarios](https://etsiit.ugr.es/docencia/grados/horarios)

Genera una base de datos estructurada en formato **JSON** lista para ser integrada en la aplicación Android (Jetpack Compose).

---

## 🎯 Características
- **Detección automática del último curso académico:** Obtiene siempre los horarios más recientes publicados (ej. *Curso Académico 2026-2027*).
- **Soporte multitítulo (Generalidad):**
  - Grado en Ingeniería Informática (`gii`)
  - Grado en Ingeniería de Tecnologías de Telecomunicación (`gitt`)
  - Doble Grado en Ingeniería Informática y Matemáticas (`giim`)
  - Doble Grado en Ingeniería Informática y ADE (`giiade`)
  - Grado en Ciencia de Datos e Inteligencia Artificial (`gcdia`)
- **Extracción precisa por Bounding Boxes:** Emplea `pdftotext -bbox-layout` para obtener las coordenadas espaciales milimétricas de cada celda horaria, permitiendo asociar de forma unívoca:
  - Día de la semana (Lunes a Viernes).
  - Franja horaria y duración (ej. `08:30 - 09:30`, `09:30 - 11:30`).
  - Asignatura y resolución del acrónimo completo según la leyenda oficial.
  - Subgrupo de prácticas (`A1`, `A2`, `B1`, etc.) y diferenciación Teoría/Prácticas (`isPractice`).
  - Aula/Laboratorio y planta correspondiente (`PB`, `P1`, `P2`, `P3`, `PSS`) para integración con el grafo indoor de la escuela.
- **Exportación directa a Android:** Genera el archivo final en `app/src/main/assets/horarios_etsiit.json`.

---

## 🚀 Requisitos
- Python 3.9+
- Herramienta de sistema `pdftotext` (incluida en `poppler-utils`):
  ```bash
  # Arch Linux
  sudo pacman -S poppler
  # Ubuntu / Debian
  sudo apt install poppler-utils
  ```
- Dependencias de Python:
  ```bash
  pip install beautifulsoup4
  ```

---

## 💻 Uso

### 1. Ejecución estándar (descarga y parseo completo)
```bash
python3 parser.py
```
Descarga los PDFs de todos los grados disponibles, los procesa y guarda el resultado en `output/horarios_etsiit.json` y en `app/src/main/assets/horarios_etsiit.json`.

### 2. Parsear grados específicos
```bash
python3 parser.py --grados gii gcdia
```

### 3. Modo offline (usar PDFs ya descargados en la carpeta `downloads/`)
```bash
python3 parser.py --offline
```

### 4. Forzar re-descarga de PDFs
```bash
python3 parser.py --force-download
```

---

## 📦 Estructura del JSON generado (`horarios_etsiit.json`)

```json
{
  "curso_academico": "Curso Académico 2026-2027",
  "fecha_extraccion": "2026-10-21T21:27:18",
  "total_grados": 5,
  "total_clases": 2102,
  "grados": [
    {
      "id": "gii",
      "nombre": "Grado en Ingeniería Informática",
      "cursos": [
        {
          "curso": 1,
          "grupo": "A",
          "cuatrimestre": 1,
          "horario": {
            "Lunes": [
              {
                "time": "08:30 - 09:30",
                "hora_inicio": "08:30",
                "hora_fin": "09:30",
                "subject": "Fundamentos Físicos y Tecnológicos",
                "acronym": "FFT",
                "subgroup": null,
                "room": "Aula 0.7 · Planta Baja",
                "room_code": "0.7",
                "planta": "PB",
                "tipo_espacio": "aula",
                "professor": "",
                "isPractice": false,
                "isCurrent": false
              }
            ],
            "Martes": [],
            "Miércoles": [],
            "Jueves": [],
            "Viernes": []
          }
        }
      ]
    }
  ]
}
```

Cada elemento de clase es compatible 1-a-1 con el modelo `ClassItem` de la aplicación Android.
