#!/usr/bin/env python3
import http.server
import json
import os
import shutil
import urllib.parse
import webbrowser

PORT = 8060
BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS_DIR = os.path.join(BASE_DIR, "app", "src", "main", "assets")
EDITOR_MAPA_DIR = os.path.join(BASE_DIR, "editor_mapa")
RUTAS_INT_DIR = os.path.join(ASSETS_DIR, "rutas", "comedor_Aula11")
BACKUP_ORIG_DIR = os.path.join(RUTAS_INT_DIR, "_originales")
JSON_PATH = os.path.join(ASSETS_DIR, "guidance_interior.json")
HTML_PATH = os.path.join(os.path.dirname(__file__), "mapeador_interior.html")


def ensure_backups():
    """Guarda copia de seguridad de las fotos originales la primera vez para no perderlas al renombrar."""
    if not os.path.exists(RUTAS_INT_DIR):
        os.makedirs(RUTAS_INT_DIR, exist_ok=True)
    if not os.path.exists(BACKUP_ORIG_DIR):
        os.makedirs(BACKUP_ORIG_DIR, exist_ok=True)
        for fname in os.listdir(RUTAS_INT_DIR):
            fpath = os.path.join(RUTAS_INT_DIR, fname)
            if os.path.isfile(fpath) and fname.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
                shutil.copy2(fpath, os.path.join(BACKUP_ORIG_DIR, fname))


class MapeadorInteriorHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = urllib.parse.unquote(parsed.path)

        if path in ("/", "/index.html"):
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            with open(HTML_PATH, "rb") as f:
                self.wfile.write(f.read())
            return

        if path == "/api/init":
            ensure_backups()
            # Listar fotos originales disponibles (desde _originales si existe, más las actuales)
            photos = []
            source_dir = BACKUP_ORIG_DIR if os.path.exists(BACKUP_ORIG_DIR) and os.listdir(BACKUP_ORIG_DIR) else RUTAS_INT_DIR
            if os.path.exists(source_dir):
                for fname in sorted(os.listdir(source_dir)):
                    if fname.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
                        photos.append(fname)

            guidance_data = None
            if os.path.exists(JSON_PATH):
                try:
                    with open(JSON_PATH, "r", encoding="utf-8") as f:
                        guidance_data = json.load(f)
                except Exception as e:
                    print("Error leyendo JSON:", e)

            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            resp = {
                "photos": photos,
                "guidance": guidance_data
            }
            self.wfile.write(json.dumps(resp, ensure_ascii=False).encode("utf-8"))
            return

        if path.startswith("/api/planta/"):
            planta = path.split("/")[-1].upper()
            json_file = os.path.join(ASSETS_DIR, f"{planta}.json")
            if not os.path.exists(json_file):
                json_file = os.path.join(EDITOR_MAPA_DIR, f"{planta}.json")
            if os.path.exists(json_file):
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.end_headers()
                with open(json_file, "rb") as f:
                    self.wfile.write(f.read())
                return
            self.send_error(404, "Planta JSON not found")
            return

        if path.startswith("/mapas/"):
            planta = path.split("/")[-1].replace(".jpeg", "").replace(".webp", "").upper()
            # Intentar cargar JPEG o WEBP de editor_mapa
            for ext, mime in [(".jpeg", "image/jpeg"), (".webp", "image/webp")]:
                candidate = os.path.join(EDITOR_MAPA_DIR, f"{planta}{ext}")
                if not os.path.exists(candidate):
                    candidate = os.path.join(EDITOR_MAPA_DIR, f"mapa_{planta.lower()}{ext}")
                if os.path.exists(candidate):
                    self.send_response(200)
                    self.send_header("Content-Type", mime)
                    self.end_headers()
                    with open(candidate, "rb") as f:
                        self.wfile.write(f.read())
                    return
            self.send_error(404, "Map image not found")
            return

        if path.startswith("/fotos_originales/"):
            fname = path[len("/fotos_originales/"):]
            candidate = os.path.join(BACKUP_ORIG_DIR, fname)
            if not os.path.exists(candidate):
                candidate = os.path.join(RUTAS_INT_DIR, fname)
            if os.path.exists(candidate) and os.path.isfile(candidate):
                self.send_response(200)
                self.send_header("Content-Type", "image/jpeg")
                self.end_headers()
                with open(candidate, "rb") as f:
                    self.wfile.write(f.read())
                return
            self.send_error(404, f"Photo {fname} not found")
            return

        if path.startswith("/rutas/"):
            rel = path.lstrip("/")
            full = os.path.join(ASSETS_DIR, rel)
            if os.path.exists(full) and os.path.isfile(full):
                self.send_response(200)
                self.send_header("Content-Type", "image/jpeg")
                self.end_headers()
                with open(full, "rb") as f:
                    self.wfile.write(f.read())
                return
            self.send_error(404, "Route image not found")
            return

        self.send_error(404)

    def do_POST(self):
        if self.path == "/api/save":
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8")
            payload = json.loads(body)

            guidance_json = payload.get("guidance")
            do_rename = payload.get("rename_files", False)
            rename_map = payload.get("rename_map", [])  # lista de {"original": "Comedor.jpg", "nuevo": "interior_1.jpg"}

            ensure_backups()

            renamed_list = []
            if do_rename and rename_map:
                for item in rename_map:
                    orig_name = item.get("original")
                    new_name = item.get("nuevo")
                    if not orig_name or not new_name:
                        continue
                    src = os.path.join(BACKUP_ORIG_DIR, orig_name)
                    if not os.path.exists(src):
                        src = os.path.join(RUTAS_INT_DIR, orig_name)
                    dst = os.path.join(RUTAS_INT_DIR, new_name)
                    if os.path.exists(src):
                        shutil.copy2(src, dst)
                        renamed_list.append(f"{orig_name} -> {new_name}")
                        # Si el archivo original estaba en la raíz con otro nombre, podemos eliminarlo para dejar limpia la carpeta
                        old_root = os.path.join(RUTAS_INT_DIR, orig_name)
                        if os.path.exists(old_root) and os.path.abspath(old_root) != os.path.abspath(dst):
                            try:
                                os.remove(old_root)
                            except Exception:
                                pass

            with open(JSON_PATH, "w", encoding="utf-8") as f:
                json.dump(guidance_json, f, indent=2, ensure_ascii=False)

            print(f"[OK] Guardado {JSON_PATH} ({len(renamed_list)} fotos renombradas)")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            self.wfile.write(json.dumps({
                "ok": True,
                "renamed": renamed_list,
                "json_path": JSON_PATH
            }, ensure_ascii=False).encode("utf-8"))
            return

        self.send_error(404)


def run():
    ensure_backups()
    server = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), MapeadorInteriorHandler)
    url = f"http://127.0.0.1:{PORT}"
    print("=" * 68)
    print("  MAPEADOR Y CALIBRADOR DE RUTAS INTERIORES - NPI ETSIIT")
    print(f"  Servidor activo en: {url}")
    print("  Presiona Ctrl+C para detener.")
    print("=" * 68)
    try:
        webbrowser.open(url)
    except Exception:
        pass
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nDeteniendo servidor...")
        server.server_close()


if __name__ == "__main__":
    run()
