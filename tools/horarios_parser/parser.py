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

# Normalización de plantas y aulas para coincidir con GrafoETSIIT y ScheduleRepository
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
    ejemplo: ('Aula 0.7 · Planta Baja', 'PB', 'aula')
             ('Laboratorio 2.6 · Planta 2', 'P2', 'laboratorio')
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
    """
    Descarga la página principal de horarios de la ETSIIT y extrae los enlaces
    a los PDFs del último curso académico disponible.
    """
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
                    
                    # Identificar grado
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


def parse_legend_items(words: list[dict], min_x: float = 610.0) -> dict[str, str]:
    """
    Extrae la leyenda de asignaturas situada en la columna derecha (x > 610).
    Mapea acrónimos como 'ALEM' -> 'Álgebra Lineal y Estructuras Matemáticas'.
    """
    legend_words = [w for w in words if w["xMin"] >= min_x]
    legend_words.sort(key=lambda w: (w["yMin"], w["xMin"]))

    # Agrupar palabras por líneas de altura similar (tolerancia y < 4)
    lines = []
    current_line = []
    last_y = -999.0
    for w in legend_words:
        if abs(w["yMin"] - last_y) > 4.5:
            if current_line:
                lines.append(" ".join(cw["text"] for cw in current_line))
            current_line = [w]
            last_y = w["yMin"]
        else:
            current_line.append(w)
    if current_line:
        lines.append(" ".join(cw["text"] for cw in current_line))

    # Parsear cada línea: 'ACRONYM[. ] Full Name'
    mapping = {}
    for line in lines:
        m = re.match(r"^([A-ZÁÉÍÓÚ0-9/]{2,10})\.?\s+(.+)$", line)
        if m:
            acronym = m.group(1).strip()
            name = m.group(2).strip()
            # Limpiar posible basura final
            name = re.sub(r"\s+\d+$", "", name)
            mapping[acronym] = name

    return mapping


def parse_table_section(words: list[dict], y_start: float, y_end: float,
                        header_info: dict, legend: dict) -> dict:
    """
    Parsea una sección de tabla (un cuatrimestre) comprendida entre y_start e y_end.
    """
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
        # No es una tabla válida
        return {}

    # Definir límites horizontales de cada día
    sorted_days = [d for d in day_names if d in day_anchors]
    day_bounds = []
    for i, d in enumerate(sorted_days):
        left = 95.0 if i == 0 else (day_anchors[sorted_days[i - 1]] + day_anchors[d]) / 2.0
        right = 612.0 if i == len(sorted_days) - 1 else (day_anchors[d] + day_anchors[sorted_days[i + 1]]) / 2.0
        day_bounds.append((d, left, right))

    # Buscar franjas horarias (ej. 8:30-9:30, 9:30-10:30, etc.) en x < 95
    time_slots = []
    for w in section_words:
        if w["xMin"] < 95.0 and re.match(r"^\d{1,2}:\d{2}-\d{1,2}:\d{2}$", w["text"]):
            time_slots.append({
                "slot_text": w["text"],
                "yMin": w["yMin"],
                "yMax": w["yMax"]
            })
    time_slots.sort(key=lambda s: s["yMin"])

    if not time_slots:
        return {}

    # Para cada franja horaria, extraer las clases correspondientes
    clases_por_dia = {d: [] for d in day_names}

    for idx, slot in enumerate(time_slots):
        slot_y_min = slot["yMin"] - 2.0
        slot_y_max = time_slots[idx + 1]["yMin"] - 2.0 if idx + 1 < len(time_slots) else slot["yMax"] + 20.0

        # Palabras de esta franja en el área de la tabla (x entre 95 y 610)
        slot_words = [w for w in section_words if slot_y_min <= w["yMin"] < slot_y_max and 95 <= w["xMin"] < 610]
        if not slot_words:
            continue

        # Distinguir palabras de aula (números con punto ej. '0.7', '1.5', '2.6', '-1.1')
        # de palabras de asignaturas y grupos
        room_words = [w for w in slot_words if re.match(r"^-?\d+\.\d+$|^-?[0-4]$", w["text"])]
        subject_words = [w for w in slot_words if w not in room_words]

        # Agrupar subject_words contiguos (ej. 'ALEM' + '(A1)')
        subject_tokens = []
        subject_words.sort(key=lambda w: (w["yMin"], w["xMin"]))
        
        curr_token = []
        for sw in subject_words:
            if not curr_token:
                curr_token.append(sw)
            else:
                last_w = curr_token[-1]
                # Si están en la misma línea y muy cerca horizontalmente (espacio < 15)
                if abs(sw["yMin"] - last_w["yMin"]) < 4.0 and (sw["xMin"] - last_w["xMax"]) < 12.0:
                    curr_token.append(sw)
                else:
                    tok_text = "".join(w["text"] for w in curr_token)
                    tok_x_center = (curr_token[0]["xMin"] + curr_token[-1]["xMax"]) / 2.0
                    subject_tokens.append({
                        "text": tok_text,
                        "x_center": tok_x_center,
                        "xMin": curr_token[0]["xMin"],
                        "xMax": curr_token[-1]["xMax"],
                        "yMin": curr_token[0]["yMin"]
                    })
                    curr_token = [sw]
        if curr_token:
            tok_text = "".join(w["text"] for w in curr_token)
            tok_x_center = (curr_token[0]["xMin"] + curr_token[-1]["xMax"]) / 2.0
            subject_tokens.append({
                "text": tok_text,
                "x_center": tok_x_center,
                "xMin": curr_token[0]["xMin"],
                "xMax": curr_token[-1]["xMax"],
                "yMin": curr_token[0]["yMin"]
            })

        # Para cada token de asignatura, buscar el aula más cercana en xMin/xMax
        for tok in subject_tokens:
            tok_raw = tok["text"]
            # Extraer acrónimo y subgrupo: ej 'ALEM(A1)' -> ('ALEM', 'A1')
            m_sub = re.match(r"^([A-ZÁÉÍÓÚ0-9/]+)(?:\(([A-Za-z0-9]+)\))?$", tok_raw)
            if not m_sub:
                continue
            
            acronym = m_sub.group(1)
            subgroup = m_sub.group(2)
            is_practice = subgroup is not None

            # Buscar aula verticalmente debajo y centrada
            matched_room = "0.1" # fallback por defecto
            closest_dist = 999.0
            for rw in room_words:
                rw_center = (rw["xMin"] + rw["xMax"]) / 2.0
                dist = abs(rw_center - tok["x_center"])
                # Debe estar en un rango razonable de alineación horizontal
                if dist < 22.0 and dist < closest_dist:
                    closest_dist = dist
                    matched_room = rw["text"]

            # Determinar día según x_center
            matched_day = "Lunes"
            for d_name, left, right in day_bounds:
                if left <= tok["x_center"] < right:
                    matched_day = d_name
                    break

            # Resolver nombre completo desde la leyenda
            full_subject_name = legend.get(acronym, acronym)
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

    # Opcional: fusionar horas consecutivas idénticas (ej. 8:30-9:30 + 9:30-10:30 de la misma asignatura y aula)
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
                # Si es la misma asignatura, mismo subgrupo, misma aula y la hora de fin coincide con la de inicio
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
        "horario": clases_consolidadas
    }


def parse_horarios_pdf(pdf_path: str, grado_id: str, grado_nombre: str) -> dict:
    """Parsea completamente un PDF de horarios y retorna la estructura del grado."""
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

        # Leyenda de asignaturas de la página
        legend = parse_legend_items(words)

        # Buscar todas las tablas en la página identificando la posición 'Lunes'
        lunes_words = [w for w in words if w["text"] == "Lunes" and w["xMin"] < 200.0]
        lunes_words.sort(key=lambda w: w["yMin"])

        for idx, lw in enumerate(lunes_words):
            y_lunes = lw["yMin"]
            
            # Las palabras de cabecera están en los 45 puntos inmediatamente superiores a Lunes
            above_words = [w for w in words if y_lunes - 45.0 <= w["yMin"] < y_lunes]
            above_words.sort(key=lambda w: (w["yMin"], w["xMin"]))
            header_text = " ".join(w["text"] for w in above_words)

            # Extraer Curso
            m_curso = re.search(r"([1-5])º", header_text)
            curso = int(m_curso.group(1)) if m_curso else 1

            # Extraer Cuatrimestre
            cuatrimestre = 1
            if "2" in header_text and "cuatrimestre" in header_text.lower():
                cuatrimestre = 2

            # Extraer Mención o Especialidad si viene entre paréntesis
            m_mencion = re.search(r"\(([^)]+)\)", header_text)
            mencion = m_mencion.group(1).replace("–", "-").strip() if m_mencion else ""

            # Extraer Grupo
            grupo = "A"
            m_grupo_letra = re.search(r"[1-5]º\s*([A-Fa-f])\b", header_text)
            if m_grupo_letra:
                grupo = m_grupo_letra.group(1).upper()
            elif mencion:
                # Si es por especialidad, ej. 'Ingeniería del Software - A' o 'CSI-A'
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

            # Límite inferior de la tabla: siguiente Lunes o final de página
            next_y = lunes_words[idx + 1]["yMin"] - 45.0 if idx + 1 < len(lunes_words) else 600.0

            sec = parse_table_section(words, y_lunes - 5.0, next_y, info, legend)
            if sec and any(sec.get("horario", {}).values()):
                sec["mencion"] = mencion
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
    print("ETSIIT - Herramienta de Extracción y Parseo de Horarios")
    print("=" * 70)

    # 1. Obtener enlaces del último año académico
    if not args.offline:
        year_title, pdf_links = scrape_horarios_links(BASE_URL)
    else:
        year_title = "Curso Académico Local"
        # En modo offline buscar archivos en DEFAULT_DOWNLOAD_DIR
        pdf_links = []
        for fname in os.listdir(DEFAULT_DOWNLOAD_DIR):
            if fname.endswith(".pdf"):
                gid = "gii" if "gii" in fname.lower() else ("gitt" if "gitt" in fname.lower() else "otro")
                pdf_links.append({
                    "title": fname,
                    "url": "",
                    "grado_id": gid,
                    "filename": fname
                })

    # Filtrar por grados solicitados
    pdf_links = [l for l in pdf_links if l["grado_id"] in args.grados]

    # Nombres oficiales de grados
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
            # Contar total de clases
            clases_en_grado = sum(
                len(clases)
                for curso in parsed["cursos"]
                for clases in curso.get("horario", {}).values()
            )
            total_clases_global += clases_en_grado
            print(f"    [OK] Extraídos {len(parsed['cursos'])} grupos/cuatrimestres ({clases_en_grado} clases totales).")
            grados_resultado.append(parsed)
        except Exception as e:
            print(f"    [!] Error parseando {item['filename']}: {e}")

    # Estructura JSON final
    payload = {
        "curso_academico": year_title,
        "fecha_extraccion": datetime.now().isoformat(),
        "total_grados": len(grados_resultado),
        "total_clases": total_clases_global,
        "grados": grados_resultado
    }

    # Guardar archivo JSON
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print(f"\n[+] Archivo JSON guardado en: {args.output}")

    # Copiar a assets si está habilitado
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

