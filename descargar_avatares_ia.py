import os
import urllib.request
import ssl

def descargar_avatares_formales():
    out_dir = os.path.join("app", "src", "main", "assets", "avatars")
    os.makedirs(out_dir, exist_ok=True)

    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE

    headers = {'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64)'}

    # Asignación de retratos formales tipo carnet (DNI / foto universitaria)
    # según el profesor y género correspondiente
    retratos = [
        # (número de avatar, género, id de foto tipo carnet)
        (4,  "men",   32),  # José Enrique Cano Ocaña
        (9,  "men",   45),  # Francisco Javier Abad Ortega
        (10, "women", 44),  # Silvia Acid Carrillo
        (11, "men",   52),  # Óscar Ramón Adamuz Hinojosa
        (12, "men",   60),  # Ángel Luis Agote Martín
        (13, "women", 65),  # Nuria Rico Castro
        (14, "women", 28),  # Begoña del Pino Prieto
        (15, "men",   68),  # Alberto Fernández Hilario
        (16, "men",   75),  # Juan Julián Merelo Guervós
        (17, "women", 63),  # María José Martín Bautista
        (18, "men",   83),  # Gabriel Maciá Fernández
        (19, "women", 79),  # Rocío Celeste Romero Zaliz
        (20, "men",   86),  # Pedro Ángel Castillo Valdivieso
        (21, "men",   91),  # Carlos García Martínez
        (22, "women", 85),  # María Carmen Pegalajar Jiménez
        (23, "men",   22),  # Fernando Berzal Galiano
        (24, "men",   36),  # Ignacio Rojas Ruiz
        (25, "men",   57),  # Juan Gómez Romero
        (26, "men",   64),  # Manuel Capel Tuñón
        (27, "women", 56),  # Cristina Urdiales García
        (28, "men",   71),  # Javier Martínez Baena
        (29, "men",   62),  # Antonio Gómez Torrente
        (30, "women", 48),  # Laura Martínez Jiménez
        (31, "men",   79),  # Carlos Mendoza Vega
        (32, "women", 68),  # Elena Navarro Castillo
        (33, "men",   81),  # Javier Ramos Roldán
        (34, "women", 90),  # Marta Romero Morales
        (35, "men",   94),  # David Sánchez Ruiz
    ]

    print(f"Descargando {len(retratos)} retratos formales tipo DNI en '{out_dir}'...")

    for num_avatar, genero, foto_id in retratos:
        file_name = f"avatar_{num_avatar:02d}.jpg"
        target_path = os.path.join(out_dir, file_name)
        # URLs de fotos de estudio tipo carnet / pasaporte
        url = f"https://randomuser.me/api/portraits/{genero}/{foto_id}.jpg"
        
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, context=ctx, timeout=10) as response, open(target_path, 'wb') as out_file:
                out_file.write(response.read())
            print(f" ✓ Descargado tipo carnet: {file_name} ({genero} #{foto_id})")
        except Exception as e:
            print(f" ✗ Error en {file_name}: {e}")

    print("\n¡Listo! Retratos tipo DNI descargados correctamente.")

if __name__ == "__main__":
    descargar_avatares_formales()
