import http.server
import socketserver
import json
import os
import sys

PORT = 8085
DIRECTORY = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(DIRECTORY, ".."))
ASSETS_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets")

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def do_POST(self):
        if self.path == '/save':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            payload = json.loads(post_data.decode('utf-8'))
            
            planta = payload.get('planta')
            data = payload.get('data')
            
            # Guardar en local del editor
            local_path = os.path.join(DIRECTORY, f"{planta}.json")
            with open(local_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                
            # Guardar directamente en los assets de Android de la app
            asset_path = os.path.join(ASSETS_DIR, f"{planta}.json")
            with open(asset_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)

            # Reconstruir grafo_etsiit_completo.json
            reconstruir_grafo_completo()

            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(b'{"status":"ok"}')
        else:
            self.send_response(404)
            self.end_headers()

def reconstruir_grafo_completo():
    todos_nodos = []
    todas_aristas = []
    
    plantas = ["PSS", "PB", "P1", "P2", "P3", "P4"]
    for p in plantas:
        path = os.path.join(ASSETS_DIR, f"{p}.json")
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                d = json.load(f)
                todos_nodos.extend(d.get("nodos", []))
                todas_aristas.extend(d.get("aristas", []))
                
    completo_path = os.path.join(ASSETS_DIR, "grafo_etsiit_completo.json")
    with open(completo_path, "w", encoding="utf-8") as f:
        json.dump({"nodos": todos_nodos, "aristas": todas_aristas}, f, indent=2, ensure_ascii=False)
    print("grafo_etsiit_completo.json actualizado con éxito.")

if __name__ == "__main__":
    with socketserver.TCPServer(("", PORT), Handler) as httpd:
        print(f"Servidor del Editor en ejecución en http://localhost:{PORT}")
        httpd.serve_forever()

