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

    def end_headers(self):
        self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate')
        self.send_header('Pragma', 'no-cache')
        self.send_header('Expires', '0')
        super().end_headers()

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

INTER_FLOOR_EDGES = [
    {"origen": "n_1791914711015_789", "destino": "n_1791914113485_119", "distancia_metros": 15},
    {"origen": "n_1791914995295_675", "destino": "Ascensor_PB_Despachos", "distancia_metros": 5},
    {"origen": "n_1791914724402_696", "destino": "n_1791913292457_895", "distancia_metros": 15},
    {"origen": "n_1791913884204_730", "destino": "n_1791915749714_13", "distancia_metros": 15},
    {"origen": "n_1791913923106_438", "destino": "n_1791915763675_148", "distancia_metros": 5},
    {"origen": "n_1791913405743_173", "destino": "n_1791915873928_698", "distancia_metros": 15},
    {"origen": "n_1791913415349_574", "destino": "n_1791915883684_272", "distancia_metros": 15},
    {"origen": "n_1791913154169_461", "destino": "n_1791916089556_395", "distancia_metros": 15},
    {"origen": "Ascensor_PB_Despachos", "destino": "Ascensor_Despachos_P1", "distancia_metros": 5},
    {"origen": "n_1791915749714_13", "destino": "n_1791998634407_239", "distancia_metros": 15},
    {"origen": "n_1791915763675_148", "destino": "n_1791998648053_223", "distancia_metros": 5},
    {"origen": "n_1791916089556_395", "destino": "n_1791998126052_317", "distancia_metros": 15},
    {"origen": "Ascensor_Despachos_P1", "destino": "n_1791998111075_764", "distancia_metros": 5},
    {"origen": "n_1791915883684_272", "destino": "n_1791998445992_24", "distancia_metros": 15},
    {"origen": "n_1791998634407_239", "destino": "n_1791999942151_398", "distancia_metros": 15},
    {"origen": "n_1791998648053_223", "destino": "n_1791999957262_535", "distancia_metros": 5},
    {"origen": "n_1791998126052_317", "destino": "n_1791999519729_602", "distancia_metros": 15},
    {"origen": "n_1791998111075_764", "destino": "n_1791999502955_53", "distancia_metros": 5},
    {"origen": "n_1791998445992_24", "destino": "n_1791999818689_18", "distancia_metros": 15},
    {"origen": "n_1791999942151_398", "destino": "n_1792001073139_930", "distancia_metros": 15},
    {"origen": "n_1791999957262_535", "destino": "n_1792001097241_373", "distancia_metros": 5},
    {"origen": "n_1791999519729_602", "destino": "n_1792000766391_978", "distancia_metros": 15},
    {"origen": "n_1791999502955_53", "destino": "n_1792000725673_794", "distancia_metros": 5}
]

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

    todas_aristas.extend(INTER_FLOOR_EDGES)
                
    for out_dir in [ASSETS_DIR, DIRECTORY]:
        completo_path = os.path.join(out_dir, "grafo_etsiit_completo.json")
        with open(completo_path, "w", encoding="utf-8") as f:
            json.dump({"nodos": todos_nodos, "aristas": todas_aristas}, f, indent=2, ensure_ascii=False)
    print("grafo_etsiit_completo.json actualizado con éxito.")

if __name__ == "__main__":
    with socketserver.TCPServer(("", PORT), Handler) as httpd:
        print(f"Servidor del Editor en ejecución en http://localhost:{PORT}")
        httpd.serve_forever()

