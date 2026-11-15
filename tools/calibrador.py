#!/usr/bin/env python3
import http.server
import json
import os
import sys
import webbrowser

PORT = 8050
BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS_DIR = os.path.join(BASE_DIR, "app", "src", "main", "assets")
JSON_PATH = os.path.join(ASSETS_DIR, "guidance_exterior.json")
JSON_INT_PATH = os.path.join(ASSETS_DIR, "guidance_interior.json")
HTML_PATH = os.path.join(os.path.dirname(__file__), "calibrador.html")
CALIBRATION_HTML_PATH = os.path.join(os.path.dirname(__file__), "calibration.html")
CALIBRACION_INT_PATH = os.path.join(os.path.dirname(__file__), "calibracion_interior.html")

class CalibradorHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path in ("/calibracion_interior", "/calibracion_interior.html", "/interior"):
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            with open(CALIBRACION_INT_PATH, "rb") as f:
                self.wfile.write(f.read())
            return

        if self.path in ("/calibration", "/calibration.html"):
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            with open(CALIBRATION_HTML_PATH, "rb") as f:
                self.wfile.write(f.read())
            return

        if self.path in ("/", "/index.html"):
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            with open(HTML_PATH, "rb") as f:
                self.wfile.write(f.read())
            return

        if self.path == "/api/guidance":
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            with open(JSON_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
            if os.path.exists(JSON_INT_PATH):
                with open(JSON_INT_PATH, "r", encoding="utf-8") as f_int:
                    int_data = json.load(f_int)
                data["interior"] = int_data.get("escaleras", {})
                data["interior_escaleras"] = int_data.get("escaleras", {})
                data["interior_ascensor"] = int_data.get("ascensor", {})
            self.wfile.write(json.dumps(data, ensure_ascii=False).encode("utf-8"))
            return

        if self.path.startswith("/rutas/"):
            rel_path = self.path.lstrip("/")
            full_path = os.path.join(ASSETS_DIR, rel_path)
            if os.path.exists(full_path):
                self.send_response(200)
                self.send_header("Content-Type", "image/jpeg")
                self.end_headers()
                with open(full_path, "rb") as f:
                    self.wfile.write(f.read())
                return
            else:
                self.send_error(404, "File not found")
                return

        super().do_GET()

    def do_POST(self):
        if self.path == "/api/save":
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8")
            req = json.loads(body)

            tramo = req.get("tramo")
            nodo_id = req.get("nodoId")
            eje_x = req.get("eje_x_camino")

            if tramo == "interior":
                with open(JSON_INT_PATH, "r", encoding="utf-8") as f:
                    data = json.load(f)

                updated = False
                for key in ("escaleras", "ascensor"):
                    for nodo in data.get(key, {}).get("nodos", []):
                        if nodo.get("id") == nodo_id:
                            nodo["eje_x_camino"] = eje_x
                            updated = True

                if updated:
                    with open(JSON_INT_PATH, "w", encoding="utf-8") as f:
                        json.dump(data, f, indent=2, ensure_ascii=False)
            else:
                with open(JSON_PATH, "r", encoding="utf-8") as f:
                    data = json.load(f)

                updated = False
                for nodo in data.get(tramo, {}).get("nodos", []):
                    if nodo.get("id") == nodo_id:
                        nodo["eje_x_camino"] = eje_x
                        updated = True
                        break

                if updated:
                    with open(JSON_PATH, "w", encoding="utf-8") as f:
                        json.dump(data, f, indent=2, ensure_ascii=False)

            if updated:
                print(f"[OK] Calibrado {tramo} -> {nodo_id} = {eje_x * 100:.1f}%")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"ok": true}')
            else:
                self.send_response(400)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"ok": false, "error": "Nodo no encontrado"}')
            return

        self.send_error(404)

def run():
    os.chdir(BASE_DIR)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), CalibradorHandler)
    url = f"http://127.0.0.1:{PORT}/calibracion_interior.html"
    print("=" * 60)
    print("  CALIBRADOR VISUAL DE PANORÁMICAS - NPI ETSIIT")
    print(f"  Servidor iniciado en: {url}")
    print("  Presiona Ctrl+C en cualquier momento para detenerlo.")
    print("=" * 60)
    try:
        webbrowser.open(url)
    except Exception:
        pass
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServidor detenido.")

if __name__ == "__main__":
    run()

