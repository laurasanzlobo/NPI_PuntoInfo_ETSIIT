# Trabajo con ramas y Pull Requests

En este proyecto no se trabaja directamente sobre `main` ni sobre `develop`.
Cada persona realiza sus cambios en una rama propia y los incorpora al proyecto
mediante una Pull Request (PR). Una PR es una solicitud para revisar y unir los
cambios de una rama con otra.
## Ramas del proyecto

| Rama | Para qué sirve | Cómo se modifica |
|---|---|---|
| `main` | Contiene la versión estable y lista para entregar. | Solo mediante una PR desde `develop`. |
| `develop` | Contiene la versión conjunta en desarrollo. | Mediante PRs aprobadas desde ramas personales. |
| `<tipo>/<nombre>` | Contiene el trabajo de una tarea concreta. | Cada persona crea y modifica su propia rama. |

`main` y `develop` están protegidas. Por eso, no se deben subir cambios
directamente a ellas.

## Nombres de las ramas
El nombre debe escribirse en minúsculas, usando guiones para separar palabras:

- `feat/nombre-tarea`: nueva funcionalidad.
- `fix/nombre-error`: corrección de un error.
- `docs/nombre-tarea`: documentación.
- `refactor/nombre-componente`: reorganización del código sin cambiar su comportamiento.
- `chore/nombre-tarea`: mantenimiento, configuración o dependencias.
Por ejemplo: `feat/filtro-busqueda`.

## Flujo de trabajo
### 1. Actualiza tu repo local desde `develop`

Antes de empezar una tarea, descarga la última versión disponible:
```bash
git switch develop
git pull origin develop
```

### 2. Crear una rama personal
Crea la rama desde `develop`. Sustituye el nombre del ejemplo por el de tu
tarea:

```bash
git switch -c feat/nombre-de-la-tarea
```
Desde este momento, todos tus cambios deben hacerse en esa rama.

### 3. Guardar y subir los cambios
Cuando hayas terminado una parte de trabajo, guarda los archivos en un commit y
sube la rama al repositorio:

```bash
git add <archivos-modificados>
git commit -m "descripcion breve del cambio"
git push -u origin feat/nombre-de-la-tarea
```
Comprueba antes que el proyecto funciona correctamente.

### 4. Actualizar la rama antes de abrir la PR
Mientras trabajabas, otra persona puede haber añadido cambios a `develop`.
Incorpóralos a tu rama antes de solicitar la revisión:

```bash
git switch develop
git pull origin develop
git switch feat/nombre-de-la-tarea
git merge develop
```
Si Git informa de conflictos, abre los archivos indicados, decide qué contenido
debe conservarse y elimina las marcas de conflicto (`<<<<<<<`, `=======` y
`>>>>>>>`). Después, guarda y ejecuta:
```bash
git add <archivos-resueltos>
git commit -m "resuelve conflictos con develop"
```
Comprueba de nuevo que el proyecto funciona y sube la rama actualizada:

```bash
git push origin feat/nombre-de-la-tarea
```
### 5. Crear la Pull Request

En GitHub, abre una nueva Pull Request y selecciona:

- **Base:** `develop`.
- **Comparar:** `feat/nombre-de-la-tarea`.
Añade un título claro y explica qué has cambiado. Comprueba que los archivos modificados son los esperados y solicita la revisión de al menos una persona del equipo.
### 6. Revisar y completar la PR

La persona revisora puede aprobar la PR o pedir cambios. Si pide cambios, realízalos en tu misma rama, crea otro commit y vuelve a subirla:
```bash
git add <archivos-modificados>
git commit -m "corrige los cambios solicitados"
git push origin feat/nombre-de-la-tarea
```
La Pull Request se actualizará automáticamente. Cuando esté aprobada y las comprobaciones sean correctas, se integra en `develop`. Después de integrarla, la rama de trabajo puede eliminarse.
## Publicar una versión estable

Cuando `develop` contenga una versión completa y probada, se crea una PR desde `develop` hacia `main`. Tras su revisión y aprobación, los cambios se integran en `main`, que pasa a contener la versión estable del proyecto.
