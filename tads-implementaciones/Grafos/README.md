# GrafoGame — AyED 2 UADE

Juego competitivo multijugador de grafos. El profe crea preguntas sobre BFS, DFS, Dijkstra y matrices de adyacencia. Los alumnos responden en tiempo real desde el navegador. Ranking en vivo.

## Requisitos

- Java 11+
- Sin dependencias externas (usa `com.sun.net.httpserver` incluido en el JDK)

## Compilar y correr

```bash
# Compilar
javac -d bin src/juego/GrafoGame.java

# Correr (puerto por defecto: 8081)
java -cp bin juego.GrafoGame

# Puerto custom
PORT=9090 java -cp bin juego.GrafoGame
```

Abrir `http://localhost:8081` en el navegador.

## Estructura

```
src/juego/
├── GrafoGame.java   # servidor HTTP + lógica del juego
└── grafo.html       # frontend (Bootstrap 5 + D3.js, embebido)
```

## Tipos de pregunta

| Tipo | Descripción | Cómo responder |
|------|-------------|----------------|
| `BFS_CAMINO` | Camino más corto por saltos (BFS) | Clic en nodos en orden |
| `BFS_ORDEN` | Orden de visita completo de BFS | Clic en nodos en orden |
| `DFS_ORDEN` | Orden de visita completo de DFS | Clic en nodos en orden |
| `DIJKSTRA_CAMINO` | Camino de **menor costo** considerando pesos | Clic en nodos — se acepta cualquier camino con el mismo costo mínimo |
| `MATRIZ_QUERY` | ¿Están conectados dos nodos? ¿Cuál es el peso? | Radio Sí/No + input de peso |

## Panel Admin

Contraseña: `profe2026`

Desde el botón **Admin** (navbar) se puede:
- Activar/desactivar preguntas
- Crear nuevas preguntas (tipo, origen, destino)
- Editar aristas del grafo en vivo
- Mostrar solución al curso
- Reiniciar el juego

## Grafo por defecto

15 nodos — sedes del Mundial FIFA 2026 (EE.UU., México, Canadá). Grafo no dirigido con pesos.

| Nodo | Ciudad |
|------|--------|
| 0 | NYC |
| 1 | LA |
| 2 | Chicago |
| 3 | Dallas |
| 4 | Houston |
| 5 | Miami |
| 6 | Seattle |
| 7 | Boston |
| 8 | SF |
| 9 | Atlanta |
| 10 | CDMX |
| 11 | Guadalajara |
| 12 | Monterrey |
| 13 | Toronto |
| 14 | Vancouver |

## Algoritmos implementados

- **BFS** — búsqueda en anchura, camino mínimo por saltos
- **DFS** — búsqueda en profundidad, recursivo
- **Dijkstra** — camino de menor costo total (considera pesos de aristas)

## Mecánica de puntaje

- Respuesta correcta: hasta 100 pts (bonus velocidad: −2 pts/segundo)
- Racha de 3+ correctas seguidas: +20 pts bonus
- Respuesta incorrecta: puntaje parcial por nodos correctos en orden (3 pts c/u)
- Dijkstra/BFS_CAMINO: se acepta cualquier camino válido con longitud/costo óptimo

## Cambios recientes

### v2 — mejoras pedagógicas
- **Dijkstra**: nuevo algoritmo + tipo `DIJKSTRA_CAMINO`. 3 preguntas predefinidas donde BFS y Dijkstra dan caminos distintos (Vancouver→Houston, Seattle→Miami, Dallas→Vancouver)
- **Fix MATRIZ_QUERY**: ahora tiene UI propia (radio Sí/No + peso) — antes era imposible responder
- **Auto-expiración**: preguntas se desactivan solas al agotar el tiempo límite
- **Animación Dijkstra**: tab Grafo muestra exploración (naranja) y camino final (azul) con costo total
