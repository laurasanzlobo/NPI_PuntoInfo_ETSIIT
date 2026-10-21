#!/usr/bin/env python3
"""
Herramienta de extracción y parseo de horarios oficiales de la ETSIIT - UGR.

Descarga los PDFs de horarios oficiales del curso académico más reciente
desde https://etsiit.ugr.es/docencia/grados/horarios y genera un JSON
estructurado listo para ser consumido por la aplicación Android (Jetpack Compose).
"""

import os
import sys
import re
import json
import shutil
import argparse
import subprocess
import urllib.request
import urllib.parse
from datetime import datetime
import xml.etree.ElementTree as ET
from bs4 import BeautifulSoup

BASE_URL = "https://etsiit.ugr.es/docencia/grados/horarios"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DOWNLOAD_DIR = os.path.join(SCRIPT_DIR, "downloads")
DEFAULT_OUTPUT_FILE = os.path.join(SCRIPT_DIR, "output", "horarios_etsiit.json")
DEFAULT_ASSETS_FILE = os.path.join(SCRIPT_DIR, "..", "..", "app", "src", "main", "assets", "horarios_etsiit.json")

# Diccionario maestro de asignaturas oficiales de la ETSIIT
# Asegura nombres 100% limpios sin artefactos tipográficos del PDF
DICCIONARIO_MAESTRO_ASIGNATURAS = {
    # 1º Curso GII / Comunes
    "ALEM": "Álgebra Lineal y Estructuras Matemáticas",
    "CA": "Cálculo",
    "FFT": "Fundamentos Físicos y Tecnológicos",
    "FP": "Fundamentos de Programación",
    "FS": "Fundamentos del Software",
    "ES": "Estadística",
    "IES": "Ingeniería, Empresa y Sociedad",
    "LMD": "Lógica y Métodos Discretos",
    "MP": "Metodología de la Programación",
    "TOC": "Tecnología y Organización de los Computadores",
    # 2º Curso GII
    "ED": "Estructura de Datos",
    "EC": "Estructura de Computadores",
    "PDOO": "Programación y Diseño Orientado a Objetos",
    "SO": "Sistemas Operativos",
    "SCD": "Sistemas Concurrentes y Distribuidos",
    "ALG": "Algorítmica",
    "AC": "Arquitectura de Computadores",
    "FIS": "Fundamentos e Ingeniería del Software",
    "FBD": "Fundamentos de Bases de Datos",
    "BD": "Bases de Datos",
    "FR": "Fundamentos de Redes",
    "IA": "Inteligencia Artificial",
    # 3º y 4º Curso GII (Troncales, Optativas y Especialidades)
    "NPI": "Nuevos Paradigmas de Interacción",
    "VC": "Visión por Computador",
    "PL": "Procesadores de Lenguajes",
    "PTC": "Programación Técnica y Científica",
    "SS": "Simulación de Sistemas",
    "TIC": "Teoría de la Información y la Codificación",
    "AA": "Aprendizaje Automático",
    "IC": "Ingeniería del Conocimiento",
    "MAC": "Modelos Avanzados de Computación",
    "MH": "Metaheurísticas",
    "TSI": "Técnicas de los Sistemas Inteligentes",
    "SG": "Sistemas Gráficos",
    "IG": "Informática Gráfica",
    "ISE": "Ingeniería de Servidores",
    "DDSI": "Diseño y Desarrollo de Sistemas de Información",
    "DSI": "Desarrollo de Sistemas de Información",
    "ABD": "Administración de Bases de Datos",
    "BDD": "Bases de Datos Distribuidas",
    "AS": "Arquitectura de Sistemas",
    "ACAP": "Arquitectura y Computación de Altas Prestaciones",
    "MC": "Modelos de Computación",
    "PDM": "Programación de Dispositivos Móviles",
    "TW": "Tecnologías Web",
    "PW": "Programación Web",
    "DAI": "Desarrollo de Aplicaciones para Internet",
    "SIBW": "Sistemas de Información Basados en Web",
    "DSD": "Desarrollo de Sistemas Distribuidos",
    "SE": "Sistemas Empotrados",
    "SMP": "Sistemas con Microprocesadores",
    "DHD": "Desarrollo de Hardware Digital",
    "DSE": "Diseño de Sistemas Electrónicos",
    "CII": "Circuitos Integrados e Impresos",
    "TDRC": "Transmisión de Datos y Redes de Computadores",
    "TR": "Tecnologías de Red",
    "RMS": "Redes Multiservicio",
    "RSC": "Redes y Sistemas Complejos",
    "SPSI": "Seguridad y Protección de Sistemas Informáticos",
    "SSO": "Seguridad en Sistemas Operativos",
    "SWAP": "Servidores Web de Altas Prestaciones",
    "TID": "Tratamiento de Imágenes Digitales",
    "CRIM": "Compresión y Recuperación de Información Multimedia",
    "CRIP": "Criptografía y Computación",
    "CUIA": "Computación Ubicua e Inteligencia Ambiental",
    "DBA": "Desarrollo Basado en Agentes",
    "DGP": "Dirección y Gestión de Proyectos",
    "DI": "Derecho Informático",
    "DIU": "Diseño de Interfaces de Usuario",
    "DS": "Desarrollo de Software",
    "EISI": "Ética, Informática y Sociedad de la Información",
    "CEGE": "Creación de Empresas y Gestión Emprendedora",
    "GRD": "Gestión de Recursos Digitales",
    "II": "Informática Industrial",
    "IN": "Inteligencia de Negocio",
    "ISI": "Ingeniería de Sistemas de Información",
    "IV": "Infraestructura Virtual",
    "LP": "Lógica y Programación",
    "MDA": "Metodologías de Desarrollo Ágil",
    "MEI": "Mantenimiento de Equipos Informáticos",
    "NTP": "Nuevas Tecnologías de la Programación",
    "PDS": "Procesamiento Digital de Señales",
    "PDIH": "Periféricos y Dispositivos de Interfaz Humana",
    "PGV": "Programación Gráfica de Videojuegos",
    "PLD": "Programación Lúdica",
    "PPR": "Programación Paralela",
    "RI": "Robótica Industrial",
    "SCGC": "Sistemas Cooperativos y Gestión de Contenidos",
    "SIE": "Sistemas de Información para Empresas",
    "SIG": "Sistemas de Información Geográficos",
    "SMD": "Sistemas Multidimensionales",
    "SMM": "Sistemas Multimedia",
    "TE": "Tecnologías Emergentes",
    # Telecomunicación (GITT)
    "AM": "Análisis Matemático",
    "AL": "Álgebra Lineal y Geometría",
    "EDCN": "Ecuaciones Diferenciales y Cálculo Numérico",
    "EO": "Estadística y Optimización",
    "FFI": "Fundamentos Físicos de la Ingeniería",
    "FIE": "Fundamentos de Informática y Empresa",
    "FTE": "Fundamentos de Telecomunicación y Electrónica",
    "SL": "Sistemas Lineales",
    "C1": "Comunicaciones 1",
    "EA": "Electrónica Analógica",
    "IRC": "Infraestructuras y Redes de Comunicación",
    "CCE": "Componentes y Circuitos Electrónicos",
    "SD": "Señales Digitales",
    "ST": "Sistemas Telemáticos",
    "TC": "Teoría de la Comunicación",
    "TO": "Transmisión de Ondas",
    "AP": "Antenas y Propagación",
    "MCO": "Medios y Componentes Ópticos",
    "SCA": "Sistemas de Codificación y Almacenamiento",
    "SR": "Sistemas de Radiocomunicación",
    "TDS": "Tratamiento Digital de Señales",
    "C2": "Comunicaciones 2",
    "EP": "Electrónica de Potencia",
    "SC": "Sistemas de Conmutación",
    "SED": "Sistemas Electrónicos Digitales",
    "CERF": "Circuitos Electrónicos para Radiofrecuencia",
    "DCSE": "Dispositivos y Sistemas Electrónicos",
    "EM": "Electromagnetismo",
    "IE": "Instrumentación Electrónica",
    "SAL": "Sistemas de Audio y Líneas",
    "AET": "Arquitecturas Especializadas para Telecomunicaciones",
    "CAM": "Complementos de Análisis Matemático",
    "FAT": "Física Aplicada a las Telecomunicaciones",
    "FF": "Fundamentos de Fotónica",
    "LT": "Laboratorio de Telemática",
    "PSETR": "Programación de Sistemas Empotrados y de Tiempo Real",
    "PVD": "Procesamiento de Vídeo Digital",
    "TCI": "Tecnología de Circuitos Impresos",
    "TH": "Tecnologías del Habla",
    "CI": "Comunicaciones Inalámbricas",
    "CO": "Comunicaciones Ópticas",
    "TRD": "Televisión y Radio Digital",
    "DDR": "Diseño y Dimensionado de Redes",
    "RAC": "Redes de Acceso y Corporativas",
    "RM": "Redes Multimedia",
    "EE": "Equipos Electrónicos",
    "CIC": "Circuitos Integrados para Comunicaciones",
    "DAR": "Desarrollo de Aplicaciones en Red",
    "GR": "Gestión de Redes",
    "RIM": "Redes Inalámbricas y Movilidad",
    "SRC": "Seguridad en Redes de Comunicación",
    "RD": "Redes de Datos",
    "CE": "Campos Electromagnéticos",
    "SELE": "Sistemas Electrónicos",
    # Ciencia de Datos e Inteligencia Artificial (GCDIA)
    "AL1": "Álgebra Lineal 1",
    "CA1": "Cálculo 1",
    "AEDV": "Análisis Exploratorio de Datos y Visualización",
    "ICD": "Introducción a la Ciencia de Datos",
    "IP": "Introducción a la Programación",
    "CP": "Cálculo de Probabilidades",
    "PED": "Programación y Estructuras de Datos",
    "FMI": "Fundamentos Matemáticos para la Inteligencia Artificial",
    # Dobles Grados y Docencia Externa
    "AM/AL": "Análisis Matemático / Álgebra Lineal",
    "DE": "Docencia Externa (Facultad de Ciencias)"
}

def limpiar_codigo_aula(room_code: str) -> str:
    """Corrige artefactos del PDF (ej. '0.12' que es Aula 0.1 subgrupo 2)."""
    clean = room_code.strip()
    m0 = re.match(r"^0\.([1-8])\d+$", clean)
    if m0:
        return f"0.{m0.group(1)}"
    m1 = re.match(r"^1\.([1-8])\d+$", clean)
    if m1:
        return f"1.{m1.group(1)}"
    return clean

def formatear_aula_y_planta(room_code: str) -> tuple[str, str, str]:
    """
    Recibe un código de aula como '0.7', '1.5', '2.6', '3.8', '-1.1', '-1'
    y retorna (room_label, planta, tipo_espacio)
    """
    clean = limpiar_codigo_aula(room_code)
    if clean.startswith("-"):
        num = clean.replace("-", "").strip()
        return f"Aula -{num} · Planta Semisótano", "PSS", "aula"
    
    parts = clean.split(".")
    if len(parts) >= 2:
        planta_num = parts[0]
        aula_num = parts[1]
        if planta_num == "0":
            return f"Aula 0.{aula_num} · Planta Baja", "PB", "aula"
        elif planta_num == "1":
            return f"Aula 1.{aula_num} · Planta 1", "P1", "aula"
        elif planta_num == "2":
            return f"Laboratorio 2.{aula_num} · Planta 2", "P2", "laboratorio"
        elif planta_num == "3":
            return f"Laboratorio 3.{aula_num} · Planta 3", "P3", "laboratorio"
        elif planta_num == "4":
            return f"Despacho/Seminario 4.{aula_num} · Planta 4", "P4", "seminario"

    return f"Espacio {clean}", "PB", "aula"


def scrape_horarios_links(url: str = BASE_URL) -> tuple[str, list[dict]]:
    """Descarga la página principal y extrae los enlaces a los PDFs del último curso académico."""
    print(f"[*] Consultando página oficial: {url}...")
    headers = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) NPI-ETSIIT-Parser/1.0"}
    req = urllib.request.Request(url, headers=headers)
    html = urllib.request.urlopen(req).read().decode("utf-8")
    soup = BeautifulSoup(html, "html.parser")

    h2 = soup.find("h2")
    year_title = h2.get_text(strip=True) if h2 else "Curso Académico Actual"
    print(f"[+] Detectado: {year_title}")

    pdf_links = []
    curr = h2
    while curr and curr.next_sibling:
        curr = curr.next_sibling
        if getattr(curr, "name", None) == "h2":
            break
        if getattr(curr, "find_all", None):
            for a in curr.find_all("a"):
                href = a.get("href", "")
                if href.lower().endswith(".pdf"):
                    text = a.get_text(strip=True)
                    full_link = urllib.parse.urljoin(url, href)
                    
                    grado_id = "otro"
                    if "informática y matemáticas" in text.lower():
                        grado_id = "giim"
                    elif "informática y admin" in text.lower() or "giiade" in text.lower():
                        grado_id = "giiade"
                    elif "ciencia de datos" in text.lower():
                        grado_id = "gcdia"
                    elif "telecomunicación" in text.lower():
                        grado_id = "gitt"
                    elif "informática" in text.lower():
                        grado_id = "gii"

                    pdf_links.append({
                        "title": text,
                        "url": full_link,
                        "grado_id": grado_id,
                        "filename": os.path.basename(urllib.parse.unquote(href))
                    })

    return year_title, pdf_links


def download_pdf(url: str, dest_path: str, force: bool = False) -> str:
    """Descarga un PDF si no existe en local o si se fuerza la descarga."""
    if os.path.exists(dest_path) and not force:
        print(f"    [Cache] Usando archivo local existente: {os.path.basename(dest_path)}")
        return dest_path

    print(f"    [Descarga] {url} -> {dest_path}")
    headers = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) NPI-ETSIIT-Parser/1.0"}
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req) as resp, open(dest_path, "wb") as out_file:
        shutil.copyfileobj(resp, out_file)
    return dest_path


def pdf_to_bbox_xml(pdf_path: str) -> str:
    """Convierte un PDF a XML con coordenadas de bounding boxes usando pdftotext."""
    cmd = ["pdftotext", "-bbox-layout", pdf_path, "-"]
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    return res.stdout.decode("utf-8", errors="replace")


def parse_page_words(page_elem) -> list[dict]:
    """Extrae todos los elementos <word> de una página XML con sus coordenadas float."""
    words = []
    for word in page_elem.iter("{http://www.w3.org/1999/xhtml}word"):
        txt = word.text
        if txt and txt.strip():
            words.append({
                "text": txt.strip(),
                "xMin": float(word.get("xMin", 0)),
                "yMin": float(word.get("yMin", 0)),
                "xMax": float(word.get("xMax", 0)),
                "yMax": float(word.get("yMax", 0)),
            })
    return words


def parse_legend_items(words: list[dict], min_x: float = 570.0) -> dict[str, str]:
    """
    Extrae la leyenda de asignaturas de la página de forma limpia.
    Filtra cualquier artefacto que provenga de celdas de la tabla.
    """
    legend_words = [w for w in words if w["xMin"] >= min_x]
    if not legend_words:
        return {}

    lines = []
    curr = []
    for w in sorted(legend_words, key=lambda w: (w["yMin"], w["xMin"])):
        if not curr or abs(w["yMin"] - curr[-1]["yMin"]) < 3.5:
            curr.append(w)
        else:
            lines.append(curr)
            curr = [w]
    if curr:
        lines.append(curr)

    mapping = {}
    for line in lines:
        line_text = " ".join(w["text"] for w in line)
        m = re.match(r"^\s*([A-ZÁÉÍÓÚ0-9/]{2,8})\.?\s+([A-ZÁÉÍÓÚ][a-záéíóúÁÉÍÓÚñÑ\w\s,\(\)]+)", line_text)
        if m:
            acr = m.group(1).strip()
            name = m.group(2).strip()
            # Si el texto concatena otro acrónimo o número de celda, cortar limpiamente
            name = re.split(r"\s+[A-Z0-9/]{2,8}\.", name)[0].strip()
            name = re.sub(r"\s+\d+.*$", "", name).strip()
            # Corregir artefactos comunes del PDF
            name = name.replace("delab. Conocimiento", "del Conocimiento")
            name = name.replace("delab", "del")
            name = name.rstrip(".")
            if len(name) > 3 and not name.startswith("Aula") and not name.startswith("Lab"):
                mapping[acr] = name

    return mapping


def tokenize_subject_line(words: list[dict]) -> list[dict]:
    """
    Tokeniza las palabras de una línea de asignaturas evitando que dos acrónimos
    contiguos se unan en una sola palabra (ej. nunca 'ALEMALEM' ni 'ALEMFP').
    """
    tokens = []
    curr = None

    for w in sorted(words, key=lambda x: x["xMin"]):
        txt = w["text"].strip()
        # Si es un subgrupo suelto ej. '(A1)', '(D2)'
        if re.match(r"^\([A-Za-z0-9-]+\)$", txt) and curr:
            curr["subgroup"] = txt.strip("()")
            curr["xMax"] = w["xMax"]
            curr["text"] += " " + txt
            curr["x_center"] = (curr["xMin"] + curr["xMax"]) / 2.0
            tokens.append(curr)
            curr = None
        else:
            if curr:
                tokens.append(curr)
                curr = None
            
            # Buscar acrónimo con o sin subgrupo pegado
            m_sub = re.match(r"^([A-ZÁÉÍÓÚ0-9/]{2,8})(?:\(([A-Za-z0-9-]+)\))?$", txt)
            if m_sub:
                acr = m_sub.group(1)
                subg = m_sub.group(2)
                # Separar subgrupo numérico pegado en asignaturas de 4º (ej. DBA1, DBA2, DGP3, MDA1)
                m_num = re.match(r"^([A-ZÁÉÍÓÚ]{2,6})(\d)$", acr)
                if m_num and acr not in DICCIONARIO_MAESTRO_ASIGNATURAS and m_num.group(1) in DICCIONARIO_MAESTRO_ASIGNATURAS:
                    acr = m_num.group(1)
                    subg = subg or m_num.group(2)
                # Ignorar notas de ubicación externa en dobles grados
                if acr in ["FACULTAD", "CIENCIAS", "CAMPUS"]:
                    continue
                curr = {
                    "text": txt,
                    "acronym": acr,
                    "subgroup": subg,
                    "xMin": w["xMin"],
                    "xMax": w["xMax"],
                    "yMin": w["yMin"],
                    "x_center": (w["xMin"] + w["xMax"]) / 2.0
                }
                if subg:
                    tokens.append(curr)
                    curr = None
    if curr:
        tokens.append(curr)
    return tokens


def parse_table_section(words: list[dict], y_start: float, y_end: float,
                        header_info: dict, page_legend: dict) -> dict:
    """Parsea una sección de tabla (cuatrimestre) comprendida entre y_start e y_end."""
    section_words = [w for w in words if y_start <= w["yMin"] < y_end]
    if not section_words:
        return {}

    # Buscar cabeceras de días: Lunes, Martes, Miércoles, Jueves, Viernes
    day_names = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes"]
    day_anchors = {}
    for w in section_words:
        if w["text"] in day_names and w["xMin"] < 610:
            day_anchors[w["text"]] = (w["xMin"] + w["xMax"]) / 2.0

    if len(day_anchors) < 3:
        return {}

    sorted_days = [d for d in day_names if d in day_anchors]
    day_bounds = []
    for i, d in enumerate(sorted_days):
        left = 95.0 if i == 0 else (day_anchors[sorted_days[i - 1]] + day_anchors[d]) / 2.0
        right = 612.0 if i == len(sorted_days) - 1 else (day_anchors[d] + day_anchors[sorted_days[i + 1]]) / 2.0
        day_bounds.append((d, left, right))

    # Buscar franjas horarias (ej. 8:30-9:30, 9:30-10:30, 15:30-16:30...)
    time_slots = []
    for w in section_words:
        if w["xMin"] < 105.0 and re.match(r"^\d{1,2}:\d{2}-\d{1,2}:\d{2}$", w["text"]):
            time_slots.append({
                "slot_text": w["text"],
                "yMin": w["yMin"],
                "yMax": w["yMax"]
            })
    time_slots.sort(key=lambda s: s["yMin"])

    if not time_slots:
        return {}

    clases_por_dia = {d: [] for d in day_names}

    for idx, slot in enumerate(time_slots):
        slot_y_min = slot["yMin"] - 3.0
        slot_y_max = time_slots[idx + 1]["yMin"] - 3.0 if idx + 1 < len(time_slots) else slot["yMax"] + 24.0

        slot_words = [w for w in section_words if slot_y_min <= w["yMin"] < slot_y_max and 95 <= w["xMin"] < 630]
        if not slot_words:
            continue

        # Palabras de aula: números con punto ('0.7', '1.5', '2.6', '3.8', '-1', '-2')
        room_words = [w for w in slot_words if re.match(r"^-?\d+\.\d+$|^-?[0-4]$", w["text"])]
        subject_words = [w for w in slot_words if w not in room_words]

        # Tokenizar asignaturas con el tokenizer robusto
        subject_tokens = tokenize_subject_line(subject_words)

        for tok in subject_tokens:
            acronym = tok["acronym"]
            subgroup = tok["subgroup"]
            is_practice = subgroup is not None

            # Buscar aula más cercana horizontalmente (tanto arriba como abajo)
            matched_room = "0.1"
            closest_dist = 999.0
            for rw in room_words:
                rw_center = (rw["xMin"] + rw["xMax"]) / 2.0
                dist = abs(rw_center - tok["x_center"])
                if dist < 25.0 and dist < closest_dist:
                    closest_dist = dist
                    matched_room = rw["text"]

            # Determinar día según x_center
            matched_day = "Lunes"
            for d_name, left, right in day_bounds:
                if left <= tok["x_center"] < right:
                    matched_day = d_name
                    break

            # Resolver nombre completo: primero diccionario maestro, luego leyenda local
            full_subject_name = DICCIONARIO_MAESTRO_ASIGNATURAS.get(
                acronym,
                page_legend.get(acronym, acronym)
            )

            clean_room_code = limpiar_codigo_aula(matched_room)
            room_label, planta, tipo_espacio = formatear_aula_y_planta(clean_room_code)

            slot_times = slot["slot_text"].split("-")
            hora_ini = slot_times[0].strip().zfill(5)
            hora_fin = slot_times[1].strip().zfill(5)

            clases_por_dia[matched_day].append({
                "time": f"{hora_ini} - {hora_fin}",
                "hora_inicio": hora_ini,
                "hora_fin": hora_fin,
                "subject": full_subject_name,
                "acronym": acronym,
                "subgroup": subgroup,
                "room": room_label,
                "room_code": clean_room_code,
                "planta": planta,
                "tipo_espacio": tipo_espacio,
                "professor": "",
                "isPractice": is_practice,
                "isCurrent": False
            })

    # Fusionar horas consecutivas idénticas
    clases_consolidadas = {}
    for d, lista in clases_por_dia.items():
        if not lista:
            clases_consolidadas[d] = []
            continue
        lista.sort(key=lambda c: c["hora_inicio"])
        fusionadas = []
        for c in lista:
            if not fusionadas:
                fusionadas.append(c.copy())
            else:
                prev = fusionadas[-1]
                if (prev["acronym"] == c["acronym"] and
                    prev["subgroup"] == c["subgroup"] and
                    prev["room_code"] == c["room_code"] and
                    prev["hora_fin"] == c["hora_inicio"]):
                    prev["hora_fin"] = c["hora_fin"]
                    prev["time"] = f"{prev['hora_inicio']} - {prev['hora_fin']}"
                else:
                    fusionadas.append(c.copy())
        clases_consolidadas[d] = fusionadas

    return {
        "curso": header_info.get("curso", 1),
        "grupo": header_info.get("grupo", "A"),
        "cuatrimestre": header_info.get("cuatrimestre", 1),
        "mencion": header_info.get("mencion", ""),
        "horario": clases_consolidadas
    }


def parse_horarios_pdf(pdf_path: str, grado_id: str, grado_nombre: str) -> dict:
    """Parsea completamente un PDF de horarios detectando dinámicamente tablas en cualquier posición."""
    print(f"\n[*] Procesando {grado_nombre} ({os.path.basename(pdf_path)})...")
    xml_str = pdf_to_bbox_xml(pdf_path)
    root = ET.fromstring(xml_str)
    
    pages = list(root.findall(".//{http://www.w3.org/1999/xhtml}page"))
    print(f"    [+] {len(pages)} páginas encontradas.")

    cursos_parsed = []

    for page_idx, page in enumerate(pages):
        words = parse_page_words(page)
        if not words:
            continue

        legend = parse_legend_items(words)

        # Detectar dinámicamente cada tabla en la página localizando la cabecera 'Lunes'
        lunes_words = [w for w in words if w["text"] == "Lunes" and w["xMin"] < 200.0]
        lunes_words.sort(key=lambda w: w["yMin"])

        for idx, lw in enumerate(lunes_words):
            y_lunes = lw["yMin"]
            
            # Palabras de cabecera inmediatamente superiores al día Lunes
            above_words = [w for w in words if y_lunes - 45.0 <= w["yMin"] < y_lunes]
            above_words.sort(key=lambda w: (w["yMin"], w["xMin"]))
            header_text = " ".join(w["text"] for w in above_words)

            m_curso = re.search(r"([1-5])º", header_text)
            curso = int(m_curso.group(1)) if m_curso else 1

            cuatrimestre = 1
            if "2" in header_text and "cuatrimestre" in header_text.lower():
                cuatrimestre = 2

            m_mencion = re.search(r"\(([^)]+)\)", header_text)
            mencion = m_mencion.group(1).replace("–", "-").strip() if m_mencion else ""

            grupo = "A"
            m_grupo_letra = re.search(r"[1-5]º\s*([A-Fa-f])\b", header_text)
            if m_grupo_letra:
                grupo = m_grupo_letra.group(1).upper()
            elif mencion:
                if "-" in mencion:
                    grupo = mencion.split("-")[-1].strip().upper()
                else:
                    grupo = mencion

            info = {
                "curso": curso,
                "grupo": grupo,
                "cuatrimestre": cuatrimestre,
                "mencion": mencion
            }

            next_y = lunes_words[idx + 1]["yMin"] - 45.0 if idx + 1 < len(lunes_words) else 600.0

            sec = parse_table_section(words, y_lunes - 5.0, next_y, info, legend)
            if sec and any(sec.get("horario", {}).values()):
                cursos_parsed.append(sec)

    return {
        "id": grado_id,
        "nombre": grado_nombre,
        "cursos": cursos_parsed
    }


def main():
    parser = argparse.ArgumentParser(description="Parser de Horarios Oficiales de la ETSIIT - UGR")
    parser.add_argument("--offline", action="store_true", help="Usar PDFs previamente descargados")
    parser.add_argument("--force-download", action="store_true", help="Forzar re-descarga de PDFs")
    parser.add_argument("--output", default=DEFAULT_OUTPUT_FILE, help="Ruta del archivo JSON de salida")
    parser.add_argument("--copy-assets", action="store_true", default=True, help="Copiar JSON a app/src/main/assets/")
    parser.add_argument("--grados", nargs="*", default=["gii", "gitt", "giim", "gcdia"],
                        help="Lista de identificadores de grado a parsear (gii, gitt, giim, gcdia, giiade)")
    args = parser.parse_args()

    os.makedirs(DEFAULT_DOWNLOAD_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(args.output), exist_ok=True)

    print("=" * 70)
    print("ETSIIT - Herramienta de Extracción y Parseo de Horarios (v2 Refined)")
    print("=" * 70)

    if not args.offline:
        year_title, pdf_links = scrape_horarios_links(BASE_URL)
    else:
        year_title = "Curso Académico Local"
        pdf_links = []
        for fname in os.listdir(DEFAULT_DOWNLOAD_DIR):
            if fname.endswith(".pdf"):
                fn = fname.lower()
                if "giim" in fn:
                    gid = "giim"
                elif "giiade" in fn:
                    gid = "giiade"
                elif "gcdia" in fn:
                    gid = "gcdia"
                elif "gitt" in fn:
                    gid = "gitt"
                elif "gii" in fn:
                    gid = "gii"
                else:
                    gid = "otro"
                pdf_links.append({
                    "title": fname,
                    "url": "",
                    "grado_id": gid,
                    "filename": fname
                })

    pdf_links = [l for l in pdf_links if l["grado_id"] in args.grados]

    nombres_grados = {
        "gii": "Grado en Ingeniería Informática",
        "gitt": "Grado en Ingeniería de Tecnologías de Telecomunicación",
        "giim": "Doble Grado en Ingeniería Informática y Matemáticas",
        "giiade": "Doble Grado en Ingeniería Informática y ADE",
        "gcdia": "Grado en Ciencia de Datos e Inteligencia Artificial"
    }

    grados_resultado = []
    total_clases_global = 0

    for item in pdf_links:
        gid = item["grado_id"]
        gname = nombres_grados.get(gid, item["title"])
        dest_pdf = os.path.join(DEFAULT_DOWNLOAD_DIR, item["filename"])

        if not args.offline:
            download_pdf(item["url"], dest_pdf, force=args.force_download)

        if not os.path.exists(dest_pdf):
            print(f"    [!] Error: No se encontró el archivo {dest_pdf}")
            continue

        try:
            parsed = parse_horarios_pdf(dest_pdf, gid, gname)
            clases_en_grado = sum(
                len(clases)
                for curso in parsed["cursos"]
                for clases in curso.get("horario", {}).values()
            )
            total_clases_global += clases_en_grado
            print(f"    [OK] Extraídos {len(parsed['cursos'])} grupos/cuatrimestres ({clases_en_grado} clases totales).")
            existing = next((g for g in grados_resultado if g["id"] == gid), None)
            if existing:
                existing_keys = set(
                    (c.get("curso"), c.get("grupo"), c.get("cuatrimestre"), c.get("mencion"))
                    for c in existing["cursos"]
                )
                for c in parsed["cursos"]:
                    k = (c.get("curso"), c.get("grupo"), c.get("cuatrimestre"), c.get("mencion"))
                    if k not in existing_keys:
                        existing["cursos"].append(c)
                        existing_keys.add(k)
            else:
                grados_resultado.append(parsed)
        except Exception as e:
            print(f"    [!] Error parseando {item['filename']}: {e}")

    payload = {
        "curso_academico": year_title,
        "fecha_extraccion": datetime.now().isoformat(),
        "total_grados": len(grados_resultado),
        "total_clases": total_clases_global,
        "grados": grados_resultado
    }

    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print(f"\n[+] Archivo JSON guardado en: {args.output}")

    if args.copy_assets:
        assets_dir = os.path.dirname(DEFAULT_ASSETS_FILE)
        os.makedirs(assets_dir, exist_ok=True)
        shutil.copyfile(args.output, DEFAULT_ASSETS_FILE)
        print(f"[+] Archivo JSON copiado para Android en: {DEFAULT_ASSETS_FILE}")

    print("\n" + "=" * 70)
    print(f"Resumen de extracción:")
    print(f"  - Curso académico: {year_title}")
    print(f"  - Grados procesados: {len(grados_resultado)}")
    print(f"  - Total de clases parseadas: {total_clases_global}")
    print("=" * 70)


if __name__ == "__main__":
    main()
