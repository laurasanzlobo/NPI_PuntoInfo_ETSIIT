# NPI Aplicación Android

Este documento establece las normas de organización, flujo de ramas y buenas prácticas para coordinar el trabajo durante las 8 semanas del proyecto (aproximadamente 2 horas semanales por persona).

---

## 1. Modelo de Ramas

Se utiliza una variante ligera de GitHub Flow basada en integración continua sobre la rama `develop`:

* `main`: Código estable y entregable. Rama protegida; nadie sube cambios directamente aquí.
* `develop`: Rama base de trabajo semanal. Todo el desarrollo parte de aquí y se integra aquí mediante Pull Request.
* `feature/<nombre-tarea>`: Nuevas funcionalidades (ejemplo: `feature/filtro-busqueda`).
* `fix/<nombre-error>`: Corrección de fallos (ejemplo: `fix/error-auth`).
* `docs/<nombre-tarea>`: Cambios exclusivos de documentación (ejemplo: `docs/actualizar-readme`).

---

## 2. Flujo de Trabajo Paso a Paso

### Paso 1: Actualizar el entorno local
Antes de comenzar la sesión de trabajo SIEMPRE, descarga siempre la última versión de `develop`:

```bash
git checkout develop
git pull origin develop
```

### Paso 2: Crear la rama de trabajo
Genera la rama propia asignándole un nombre descriptivo según la convención:

```bash
git checkout -b feature/<nombre-del-feature>
```


### Paso 3: Realizar commits ordenados (en la medida de lo posible)
Registra cambios pequeños y funcionales siguiendo este estándar:

```bash
git add <archivos-modificados>
git commit -m "tipo: breve descripcion"
```
```
```
```
- `feat``: para nuevas características.
- `fix``: para corrección de errores.
- `docs``: para cambios en la documentación.
- `refactor``: para mejoras internas del código sin cambiar su funcionalidad.
```

### Paso 4: Sincronizar antes de subir (Para evitar conflictos)
Antes de subir a la rama de develop actualiza tu repo local para evitar conflictos por si alguien ha publicado algo mientras trabajabas.

```bash
git checkout develop
git pull origin develop
git checkout feature/nombre-de-tu-tarea
git merge develop
# Si surgen conflictos, resuélvelos en el editor, añade los archivos y haz commit
```

### Paso 5: Subir al repo y abrir Pull Request (si es que trabajamos con PRs)
Publica tu versión de la rama en el remoto:
```bash
git push -u origin feature/nombre-de-tu-tarea
```
