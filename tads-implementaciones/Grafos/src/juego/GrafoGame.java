package juego;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  GRAFO GAME — AyED 2 UADE                                      ║
 * ║  Juego competitivo multijugador de grafos con BFS/DFS          ║
 * ║  Arquitectura idéntica al Prode: HTTP server + HTML embebido   ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * CONCEPTOS PEDAGÓGICOS que cubre este juego:
 *  - Representación de grafos con MATRIZ DE ADYACENCIA (int[][])
 *  - BFS (Búsqueda en Anchura) — camino más corto
 *  - DFS (Búsqueda en Profundidad) — exploración exhaustiva
 *  - Comparación de algoritmos en tiempo real
 *  - Grafos ponderados y no ponderados
 *
 * MECÁNICA: El profe crea un grafo. Los alumnos eligen el camino
 * que creen que BFS/DFS recorrería. Gana quien acierta más rápido.
 * Ranking en vivo visible para todos (como el Prode).
 */
public class GrafoGame {

    // ╔══════════════════════════════════════════════════════════╗
    // ║  GRAFO CON MATRIZ DE ADYACENCIA                         ║
    // ╚══════════════════════════════════════════════════════════╝
    static class Grafo {
        int n;          // número de vértices
        int[][] mat;    // matriz de adyacencia (0 = sin arista, >0 = peso)
        String[] nombres; // etiquetas de nodos (ciudades del Mundial)

        Grafo(int n, String[] nombres) {
            this.n = n;
            this.mat = new int[n][n];
            this.nombres = nombres;
        }

        void agregarArista(int u, int v, int peso) {
            mat[u][v] = peso;
            mat[v][u] = peso; // no dirigido
        }

        void quitarArista(int u, int v) {
            mat[u][v] = 0;
            mat[v][u] = 0;
        }

        // BFS desde origen — devuelve orden de visita
        List<Integer> bfs(int origen) {
            List<Integer> orden = new ArrayList<>();
            boolean[] vis = new boolean[n];
            Queue<Integer> cola = new LinkedList<>();
            cola.add(origen);
            vis[origen] = true;
            while (!cola.isEmpty()) {
                int u = cola.poll();
                orden.add(u);
                for (int v = 0; v < n; v++) {
                    if (mat[u][v] != 0 && !vis[v]) {
                        vis[v] = true;
                        cola.add(v);
                    }
                }
            }
            return orden;
        }

        // BFS camino más corto entre origen y destino
        List<Integer> bfsCamino(int origen, int destino) {
            int[] padre = new int[n];
            Arrays.fill(padre, -1);
            boolean[] vis = new boolean[n];
            Queue<Integer> cola = new LinkedList<>();
            cola.add(origen);
            vis[origen] = true;
            while (!cola.isEmpty()) {
                int u = cola.poll();
                if (u == destino) break;
                for (int v = 0; v < n; v++) {
                    if (mat[u][v] != 0 && !vis[v]) {
                        vis[v] = true;
                        padre[v] = u;
                        cola.add(v);
                    }
                }
            }
            // reconstruir camino
            List<Integer> camino = new ArrayList<>();
            if (padre[destino] == -1 && destino != origen) return camino;
            for (int x = destino; x != -1; x = padre[x]) camino.add(0, x);
            return camino;
        }

        // DFS desde origen — devuelve orden de visita
        List<Integer> dfs(int origen) {
            List<Integer> orden = new ArrayList<>();
            boolean[] vis = new boolean[n];
            dfsRec(origen, vis, orden);
            return orden;
        }
        private void dfsRec(int u, boolean[] vis, List<Integer> orden) {
            vis[u] = true;
            orden.add(u);
            for (int v = 0; v < n; v++) {
                if (mat[u][v] != 0 && !vis[v]) dfsRec(v, vis, orden);
            }
        }

        // Serializa la matriz a JSON para el frontend
        String matrizToJson() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(",");
                sb.append("[");
                for (int j = 0; j < n; j++) {
                    if (j > 0) sb.append(",");
                    sb.append(mat[i][j]);
                }
                sb.append("]");
            }
            return sb.append("]").toString();
        }

        String nombresJson() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(esc(nombres[i])).append("\"");
            }
            return sb.append("]").toString();
        }

        String listaToJson(List<Integer> lista) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < lista.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(lista.get(i));
            }
            return sb.append("]").toString();
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  DOMINIO DEL JUEGO                                      ║
    // ╚══════════════════════════════════════════════════════════╝
    static class Jugador {
        String nombre;
        int puntaje = 0;
        int racha = 0;       // respuestas correctas consecutivas
        int tiempoTotal = 0; // ms acumulados respondiendo
        Map<Integer, RespuestaJugador> respuestas = new HashMap<>();

        Jugador(String n) { nombre = n; }
    }

    static class RespuestaJugador {
        int preguntaId;
        List<Integer> camino; // camino elegido por el jugador
        long tiempoMs;        // tiempo en responder
        boolean correcta;
        int puntosObtenidos;
        RespuestaJugador(int pid, List<Integer> c, long t) {
            preguntaId = pid; camino = c; tiempoMs = t;
        }
    }

    static class Pregunta {
        int id;
        String tipo;          // "BFS_ORDEN", "BFS_CAMINO", "DFS_ORDEN", "MATRIZ_QUERY"
        String enunciado;
        int origen, destino;  // nodos relevantes
        List<Integer> respuestaCorrecta;
        boolean activa = false;
        long iniciadaEn = 0;
        int tiempoLimite = 45; // segundos

        Pregunta(int id, String tipo, String enunciado, int origen, int destino, List<Integer> resp) {
            this.id = id; this.tipo = tipo; this.enunciado = enunciado;
            this.origen = origen; this.destino = destino; this.respuestaCorrecta = resp;
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  ESTADO GLOBAL                                          ║
    // ╚══════════════════════════════════════════════════════════╝
    // Grafo del Mundial 2026: ciudades sede como nodos
    static final String[] CIUDADES = {
        "NYC","LA","Chicago","Dallas","Houston",
        "Miami","Seattle","Boston","SF","Atlanta",
        "CDMX","Guadalajara","Monterrey","Toronto","Vancouver"
    };

    static Grafo grafo = crearGrafoInicial();
    static final Map<String, Jugador> jugadores = new LinkedHashMap<>();
    static final List<Pregunta> preguntas = new ArrayList<>();
    static int preguntaActualId = -1;
    static int nextPregId = 1;
    static final String SAVE = "grafogame_data.txt";
    static final String PASS = "profe2026";

    static Grafo crearGrafoInicial() {
        Grafo g = new Grafo(15, CIUDADES);
        // Conexiones geográficas/logísticas entre sedes
        g.agregarArista(0, 1, 4); // NYC - LA
        g.agregarArista(0, 2, 2); // NYC - Chicago
        g.agregarArista(0, 4, 3); // NYC - Houston (via Delta)
        g.agregarArista(1, 8, 1); // LA - SF
        g.agregarArista(1, 9, 5); // LA - Atlanta
        g.agregarArista(2, 3, 2); // Chicago - Dallas
        g.agregarArista(2, 6, 3); // Chicago - Seattle
        g.agregarArista(3, 4, 1); // Dallas - Houston
        g.agregarArista(3, 10,3); // Dallas - CDMX
        g.agregarArista(4, 5, 3); // Houston - Miami
        g.agregarArista(5, 9, 2); // Miami - Atlanta
        g.agregarArista(6, 14,4); // Seattle - Vancouver
        g.agregarArista(7, 0, 1); // Boston - NYC
        g.agregarArista(7, 13,5); // Boston - Toronto
        g.agregarArista(8, 6, 2); // SF - Seattle
        g.agregarArista(9, 5, 2); // Atlanta - Miami
        g.agregarArista(10,11,1); // CDMX - Guadalajara
        g.agregarArista(10,12,2); // CDMX - Monterrey
        g.agregarArista(11,12,2); // Guadalajara - Monterrey
        g.agregarArista(12,3, 3); // Monterrey - Dallas
        g.agregarArista(13,2, 2); // Toronto - Chicago
        g.agregarArista(13,14,2); // Toronto - Vancouver
        // (Seattle-Vancouver ya fue agregada arriba con peso 4)
        return g;
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  MAIN                                                   ║
    // ╚══════════════════════════════════════════════════════════╝
    public static void main(String[] args) throws IOException {
        cargar();
        // Precalcular preguntas base
        if (preguntas.isEmpty()) generarPreguntas();

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.createContext("/",                   ex -> wrap(ex, "GET",  GrafoGame::root));
        server.createContext("/api/state",          ex -> wrap(ex, "GET",  GrafoGame::apiState));
        server.createContext("/api/unirse",         ex -> wrap(ex, "POST", GrafoGame::apiUnirse));
        server.createContext("/api/responder",      ex -> wrap(ex, "POST", GrafoGame::apiResponder));
        server.createContext("/api/activar",        ex -> wrap(ex, "POST", GrafoGame::apiActivar));
        server.createContext("/api/nueva_pregunta", ex -> wrap(ex, "POST", GrafoGame::apiNuevaPregunta));
        server.createContext("/api/editar_arista",  ex -> wrap(ex, "POST", GrafoGame::apiEditarArista));
        server.createContext("/api/reiniciar",      ex -> wrap(ex, "POST", GrafoGame::apiReiniciar));
        server.createContext("/api/resolver",       ex -> wrap(ex, "GET",  GrafoGame::apiResolver));

        server.start();
        System.out.println("🎮 GrafoGame iniciado en puerto " + port);
        System.out.println("   Abrí http://localhost:" + port + " en el navegador");
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  HANDLERS HTTP                                          ║
    // ╚══════════════════════════════════════════════════════════╝
    @FunctionalInterface interface H { void run(HttpExchange ex) throws IOException; }

    static void wrap(HttpExchange ex, String method, H h) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if ("OPTIONS".equals(ex.getRequestMethod())) { respond(ex, 200, ""); return; }
        if (!method.equals(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"Method not allowed\"}"); return; }
        try { h.run(ex); }
        catch (Exception e) {
            e.printStackTrace();
            try { respond(ex, 500, "{\"error\":\"" + esc(e.getMessage()) + "\"}"); } catch (Exception ignored) {}
        }
    }

    static void root(HttpExchange ex) throws IOException { respondHtml(ex, HTML); }

    static synchronized void apiState(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("{");
        // Grafo
        sb.append("\"nodos\":").append(grafo.nombresJson());
        sb.append(",\"matriz\":").append(grafo.matrizToJson());
        // Pregunta actual
        Pregunta pActual = preguntas.stream().filter(p -> p.id == preguntaActualId).findFirst().orElse(null);
        if (pActual != null) {
            sb.append(",\"pregunta\":{");
            sb.append("\"id\":").append(pActual.id);
            sb.append(",\"tipo\":\"").append(pActual.tipo).append("\"");
            sb.append(",\"enunciado\":\"").append(esc(pActual.enunciado)).append("\"");
            sb.append(",\"origen\":").append(pActual.origen);
            sb.append(",\"destino\":").append(pActual.destino);
            sb.append(",\"activa\":").append(pActual.activa);
            sb.append(",\"tiempoLimite\":").append(pActual.tiempoLimite);
            long transcurrido = pActual.activa ? (System.currentTimeMillis() - pActual.iniciadaEn) / 1000 : 0;
            sb.append(",\"transcurrido\":").append(transcurrido);
            sb.append("}");
        } else {
            sb.append(",\"pregunta\":null");
        }
        // Ranking
        sb.append(",\"ranking\":[");
        List<Jugador> ranked = new ArrayList<>(jugadores.values());
        ranked.sort((a, b) -> b.puntaje != a.puntaje ? b.puntaje - a.puntaje : a.tiempoTotal - b.tiempoTotal);
        for (int i = 0; i < ranked.size(); i++) {
            if (i > 0) sb.append(",");
            Jugador j = ranked.get(i);
            sb.append("{\"nombre\":\"").append(esc(j.nombre)).append("\"");
            sb.append(",\"puntaje\":").append(j.puntaje);
            sb.append(",\"racha\":").append(j.racha);
            // estado en pregunta actual
            if (pActual != null && j.respuestas.containsKey(pActual.id)) {
                RespuestaJugador r = j.respuestas.get(pActual.id);
                sb.append(",\"respondio\":true");
                sb.append(",\"correcto\":").append(r.correcta);
                sb.append(",\"puntos\":").append(r.puntosObtenidos);
            } else {
                sb.append(",\"respondio\":false");
            }
            sb.append("}");
        }
        sb.append("],\"preguntas\":[");
        for (int i = 0; i < preguntas.size(); i++) {
            if (i > 0) sb.append(",");
            Pregunta pr = preguntas.get(i);
            sb.append("{\"id\":").append(pr.id);
            sb.append(",\"tipo\":\"").append(pr.tipo).append("\"");
            sb.append(",\"origen\":").append(pr.origen);
            sb.append(",\"destino\":").append(pr.destino);
            sb.append(",\"activa\":").append(pr.activa);
            sb.append(",\"etiqueta\":\"").append(esc(etiquetaPregunta(pr))).append("\"");
            sb.append("}");
        }
        sb.append("],\"totalPreguntas\":").append(preguntas.size()).append("}");
        respondJson(ex, sb.toString());
    }

    static String etiquetaPregunta(Pregunta p) {
        return switch (p.tipo) {
            case "BFS_CAMINO" -> "#" + p.id + " · BFS camino: " + CIUDADES[p.origen] + " → " + CIUDADES[p.destino];
            case "BFS_ORDEN"  -> "#" + p.id + " · BFS orden desde " + CIUDADES[p.origen];
            case "DFS_ORDEN"  -> "#" + p.id + " · DFS orden desde " + CIUDADES[p.origen];
            case "MATRIZ_QUERY" -> "#" + p.id + " · Matriz: " + CIUDADES[p.origen] + " ↔ " + CIUDADES[p.destino];
            default -> "#" + p.id + " · " + p.tipo;
        };
    }

    static synchronized void apiUnirse(HttpExchange ex) throws IOException {
        Map<String, String> b = parseJson(body(ex));
        String nombre = b.get("nombre");
        if (nombre == null || nombre.isBlank()) { respondJson(ex, "{\"error\":\"Nombre requerido\"}"); return; }
        nombre = nombre.trim();
        jugadores.putIfAbsent(nombre, new Jugador(nombre));
        guardar();
        respondJson(ex, "{\"ok\":true,\"nombre\":\"" + esc(nombre) + "\"}");
    }

    static synchronized void apiResponder(HttpExchange ex) throws IOException {
        Map<String, String> b = parseJson(body(ex));
        String nombre = b.get("nombre");
        String caminoStr = b.get("camino"); // ej: "0,2,3,4"
        Jugador j = nombre != null ? jugadores.get(nombre.trim()) : null;
        if (j == null) { respondJson(ex, "{\"error\":\"Jugador no registrado\"}"); return; }

        Pregunta p = preguntas.stream().filter(pr -> pr.id == preguntaActualId).findFirst().orElse(null);
        if (p == null || !p.activa) { respondJson(ex, "{\"error\":\"No hay pregunta activa\"}"); return; }
        if (j.respuestas.containsKey(p.id)) { respondJson(ex, "{\"error\":\"Ya respondiste esta pregunta\"}"); return; }

        // parsear camino del alumno
        List<Integer> camino = new ArrayList<>();
        if (caminoStr != null && !caminoStr.isBlank()) {
            for (String s : caminoStr.split(",")) {
                try { camino.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }

        long tiempoMs = System.currentTimeMillis() - p.iniciadaEn;
        RespuestaJugador resp = new RespuestaJugador(p.id, camino, tiempoMs);

        // calcular puntaje
        // Para BFS_CAMINO aceptamos CUALQUIER camino más corto válido,
        // no solo el que devolvió la implementación de referencia.
        // Para el resto exigimos el orden exacto.
        boolean correcta;
        if ("BFS_CAMINO".equals(p.tipo)) {
            correcta = esCaminoOptimoValido(camino, p.origen, p.destino, p.respuestaCorrecta.size());
        } else {
            correcta = camino.equals(p.respuestaCorrecta);
        }
        resp.correcta = correcta;
        int pts = 0;
        if (correcta) {
            // bonus por velocidad: más puntos si responde antes
            long seg = tiempoMs / 1000;
            pts = Math.max(10, 100 - (int)(seg * 2));
            j.racha++;
            if (j.racha >= 3) pts += 20; // bonus racha
        } else {
            // puntaje parcial: cuántos nodos del camino coinciden en orden
            int coincide = 0;
            List<Integer> correcto = p.respuestaCorrecta;
            for (int i = 0; i < Math.min(camino.size(), correcto.size()); i++) {
                if (camino.get(i).equals(correcto.get(i))) coincide++;
                else break;
            }
            pts = coincide * 3;
            j.racha = 0;
        }
        resp.puntosObtenidos = pts;
        j.puntaje += pts;
        j.tiempoTotal += (int)tiempoMs;
        j.respuestas.put(p.id, resp);
        guardar();
        respondJson(ex, "{\"ok\":true,\"correcta\":" + correcta + ",\"puntos\":" + pts
                + ",\"tiempoMs\":" + tiempoMs + "}");
    }

    static synchronized void apiActivar(HttpExchange ex) throws IOException {
        Map<String, String> b = parseJson(body(ex));
        if (!PASS.equals(b.get("pass"))) { respondJson(ex, "{\"error\":\"Contraseña incorrecta\"}"); return; }
        String idStr = b.get("id");
        int id = idStr != null ? Integer.parseInt(idStr.trim()) : -1;
        Pregunta p = preguntas.stream().filter(pr -> pr.id == id).findFirst().orElse(null);
        if (p == null) { respondJson(ex, "{\"error\":\"Pregunta no encontrada\"}"); return; }
        // desactivar anterior
        preguntas.forEach(pr -> pr.activa = false);
        p.activa = true;
        p.iniciadaEn = System.currentTimeMillis();
        preguntaActualId = p.id;
        respondJson(ex, "{\"ok\":true}");
    }

    static synchronized void apiNuevaPregunta(HttpExchange ex) throws IOException {
        Map<String, String> b = parseJson(body(ex));
        if (!PASS.equals(b.get("pass"))) { respondJson(ex, "{\"error\":\"Contraseña incorrecta\"}"); return; }
        String tipo = b.getOrDefault("tipo", "BFS_CAMINO");
        int origen  = Integer.parseInt(b.getOrDefault("origen",  "0"));
        int destino = Integer.parseInt(b.getOrDefault("destino", "5"));
        generarPregunta(tipo, origen, destino);
        Pregunta ultima = preguntas.get(preguntas.size() - 1);
        respondJson(ex, "{\"ok\":true,\"id\":" + ultima.id + "}");
    }

    static synchronized void apiEditarArista(HttpExchange ex) throws IOException {
        Map<String, String> b = parseJson(body(ex));
        if (!PASS.equals(b.get("pass"))) { respondJson(ex, "{\"error\":\"Contraseña incorrecta\"}"); return; }
        int u    = Integer.parseInt(b.getOrDefault("u",    "0"));
        int v    = Integer.parseInt(b.getOrDefault("v",    "1"));
        int peso = Integer.parseInt(b.getOrDefault("peso", "0"));
        if (peso <= 0) grafo.quitarArista(u, v);
        else           grafo.agregarArista(u, v, peso);
        // recalcular preguntas afectadas (simplificado: regenerar todas)
        preguntas.clear();
        nextPregId = 1;
        generarPreguntas();
        respondJson(ex, "{\"ok\":true}");
    }

    static synchronized void apiResolver(HttpExchange ex) throws IOException {
        // Endpoint educativo: muestra la solución de la pregunta activa
        Pregunta p = preguntas.stream().filter(pr -> pr.id == preguntaActualId).findFirst().orElse(null);
        if (p == null) { respondJson(ex, "{\"error\":\"Sin pregunta activa\"}"); return; }
        String resp = grafo.listaToJson(p.respuestaCorrecta);
        // también mostrar paso a paso según tipo
        respondJson(ex, "{\"respuesta\":" + resp
                + ",\"tipo\":\"" + p.tipo + "\""
                + ",\"origen\":" + p.origen
                + ",\"destino\":" + p.destino + "}");
    }

    static synchronized void apiReiniciar(HttpExchange ex) throws IOException {
        Map<String, String> b = parseJson(body(ex));
        if (!PASS.equals(b.get("pass"))) { respondJson(ex, "{\"error\":\"Contraseña incorrecta\"}"); return; }
        jugadores.clear();
        preguntas.clear();
        nextPregId = 1;
        preguntaActualId = -1;
        grafo = crearGrafoInicial();
        generarPreguntas();
        guardar();
        respondJson(ex, "{\"ok\":true}");
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  VALIDACIONES                                           ║
    // ╚══════════════════════════════════════════════════════════╝
    /**
     * Acepta cualquier camino del alumno que:
     *  - empiece en origen y termine en destino,
     *  - use solo aristas existentes en la matriz,
     *  - tenga exactamente la misma cantidad de nodos que el camino
     *    óptimo encontrado por BFS (= longitud mínima).
     * BFS no garantiza unicidad del camino, así que esto es lo correcto
     * para no marcar como incorrectas respuestas válidas.
     */
    static boolean esCaminoOptimoValido(List<Integer> camino, int origen, int destino, int longitudOptima) {
        if (camino == null || camino.size() != longitudOptima) return false;
        if (camino.isEmpty()) return false;
        if (camino.get(0) != origen) return false;
        if (camino.get(camino.size() - 1) != destino) return false;
        for (int i = 0; i < camino.size() - 1; i++) {
            int u = camino.get(i), v = camino.get(i + 1);
            if (u < 0 || u >= grafo.n || v < 0 || v >= grafo.n) return false;
            if (grafo.mat[u][v] == 0) return false; // no hay arista
        }
        return true;
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  LÓGICA DEL JUEGO                                       ║
    // ╚══════════════════════════════════════════════════════════╝
    static void generarPreguntas() {
        // BFS camino más corto
        generarPregunta("BFS_CAMINO", 7, 10);   // Boston → CDMX
        generarPregunta("BFS_CAMINO", 14, 5);   // Vancouver → Miami
        generarPregunta("BFS_CAMINO", 0, 11);   // NYC → Guadalajara
        // BFS orden de visita
        generarPregunta("BFS_ORDEN", 0, -1);    // BFS desde NYC
        generarPregunta("BFS_ORDEN", 10, -1);   // BFS desde CDMX
        // DFS orden
        generarPregunta("DFS_ORDEN", 7, -1);    // DFS desde Boston
        // NOTA: MATRIZ_QUERY queda disponible desde el panel Admin
        // pero no se predefine porque la UI actual no permite responder
        // (los alumnos clickean nodos, no escriben "1,peso").
    }

    static void generarPregunta(String tipo, int origen, int destino) {
        List<Integer> respuesta;
        String enunciado;
        switch (tipo) {
            case "BFS_CAMINO" -> {
                respuesta = grafo.bfsCamino(origen, destino);
                enunciado = "🗺️ BFS: ¿Cuál es el camino más corto de "
                        + CIUDADES[origen] + " a " + CIUDADES[destino] + "?\n"
                        + "Ingresá los nodos en orden separados por coma (ej: 0,2,3)";
            }
            case "BFS_ORDEN" -> {
                respuesta = grafo.bfs(origen);
                enunciado = "🔍 BFS: ¿En qué orden visita BFS todos los nodos partiendo de "
                        + CIUDADES[origen] + " (nodo " + origen + ")?\n"
                        + "Ingresá el orden completo de visita";
            }
            case "DFS_ORDEN" -> {
                respuesta = grafo.dfs(origen);
                enunciado = "🌀 DFS: ¿En qué orden visita DFS todos los nodos partiendo de "
                        + CIUDADES[origen] + " (nodo " + origen + ")?\n"
                        + "Ingresá el orden completo de visita";
            }
            case "MATRIZ_QUERY" -> {
                int peso = grafo.mat[origen][destino];
                respuesta = peso > 0 ? List.of(1, peso) : List.of(0);
                enunciado = "📊 MATRIZ: ¿Están conectados " + CIUDADES[origen]
                        + " y " + CIUDADES[destino] + "? Si sí, ¿cuál es el peso?\n"
                        + "Respondé: '1,peso' si están conectados, '0' si no";
            }
            default -> { respuesta = new ArrayList<>(); enunciado = "Pregunta desconocida"; }
        }
        preguntas.add(new Pregunta(nextPregId++, tipo, enunciado, origen, destino, respuesta));
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  PERSISTENCIA                                           ║
    // ╚══════════════════════════════════════════════════════════╝
    static void guardar() {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Path.of(SAVE), StandardCharsets.UTF_8))) {
            for (Jugador j : jugadores.values()) {
                pw.println("JUG|" + j.nombre + "|" + j.puntaje + "|" + j.racha + "|" + j.tiempoTotal);
            }
            pw.println("PREGACTUAL|" + preguntaActualId);
        } catch (IOException e) {
            System.err.println("Error guardando: " + e.getMessage());
        }
    }

    static void cargar() {
        if (!Files.exists(Path.of(SAVE))) return;
        try {
            for (String line : Files.readAllLines(Path.of(SAVE), StandardCharsets.UTF_8)) {
                String[] p = line.split("\\|", -1);
                if (p.length == 0) continue;
                switch (p[0]) {
                    case "JUG" -> {
                        if (p.length >= 5) {
                            Jugador j = new Jugador(p[1]);
                            j.puntaje     = Integer.parseInt(p[2]);
                            j.racha       = Integer.parseInt(p[3]);
                            j.tiempoTotal = Integer.parseInt(p[4]);
                            jugadores.put(p[1], j);
                        }
                    }
                    case "PREGACTUAL" -> {
                        if (p.length >= 2) preguntaActualId = Integer.parseInt(p[1]);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error cargando: " + e.getMessage());
        }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  UTILS                                                  ║
    // ╚══════════════════════════════════════════════════════════╝
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
    static String body(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }
    static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isBlank()) return map;
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1, json.endsWith("}") ? json.length() - 1 : json.length());
        int i = 0;
        while (i < json.length()) {
            while (i < json.length() && json.charAt(i) != '"') i++;
            if (i >= json.length()) break;
            int ks = i + 1, ke = json.indexOf('"', ks);
            if (ke < 0) break;
            String key = json.substring(ks, ke);
            i = ke + 1;
            while (i < json.length() && json.charAt(i) != ':') i++;
            i++;
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            String val;
            if (i < json.length() && json.charAt(i) == '"') {
                int vs = i + 1, ve = vs;
                while (ve < json.length()) {
                    if (json.charAt(ve) == '\\') ve++;
                    else if (json.charAt(ve) == '"') break;
                    ve++;
                }
                val = json.substring(vs, ve).replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
                i = ve + 1;
            } else {
                int ve = i;
                while (ve < json.length() && json.charAt(ve) != ',' && json.charAt(ve) != '}') ve++;
                val = json.substring(i, ve).trim();
                i = ve;
            }
            map.put(key, val);
            while (i < json.length() && json.charAt(i) != '"' && json.charAt(i) != '}') i++;
        }
        return map;
    }
    static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
    static void respondJson(HttpExchange ex, String body) throws IOException { respond(ex, 200, body); }
    static void respondHtml(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ╔══════════════════════════════════════════════════════════╗
    // ║  HTML EMBEBIDO — mismo estilo que el Prode              ║
    // ╚══════════════════════════════════════════════════════════╝
    static final String HTML = """
<!DOCTYPE html>
<html lang="es" data-bs-theme="dark">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>🎮 GrafoGame — AyED 2 UADE</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
<script src="https://d3js.org/d3.v7.min.js"></script>
<style>
  :root {
    --bg: #0d1117; --card: #161b22; --border: #30363d;
    --accent: #58a6ff; --green: #3fb950; --red: #f85149;
    --orange: #e3b341; --purple: #bc8cff; --muted: #8b949e;
  }
  * { box-sizing: border-box; }
  body { background: var(--bg); color: #e6edf3; font-family: 'Segoe UI', sans-serif; }

  /* NAV */
  .navbar { background: #161b22 !important; border-bottom: 1px solid var(--border); }
  .navbar-brand { color: var(--accent) !important; font-weight: 700; letter-spacing: -0.5px; font-size: 1.1rem; }

  /* TABS */
  .nav-tabs .nav-link { color: var(--muted); border-radius: 8px 8px 0 0; }
  .nav-tabs .nav-link.active { background: var(--card); color: var(--accent); border-color: var(--border) var(--border) var(--card); }

  /* CARDS */
  .card { background: var(--card); border: 1px solid var(--border); border-radius: 10px; }
  .card-header { background: #1c2128; border-bottom: 1px solid var(--border); font-weight: 600; }

  /* GRAFO SVG */
  #grafoSvg, #grafoSvg2 { width: 100%; height: 480px; background: #0a1420; border-radius: 10px; border: 1px solid var(--border); }
  .mapa-fondo { pointer-events: none; }
  .mapa-pais { stroke: #3d5a80; stroke-width: 2; }
  .mapa-can { fill: #1a3348; opacity: 0.95; }
  .mapa-usa { fill: #1e3d63; opacity: 0.95; }
  .mapa-mex { fill: #3d3520; opacity: 0.95; }
  .mapa-label { fill: #5a7a9a; font-size: 22px; font-weight: 700; opacity: 0.55; font-family: 'Segoe UI', sans-serif; }
  .panel-unirse-compact .card-body { padding: 0.75rem 1rem; }
  .node circle { stroke-width: 2.5; cursor: grab; transition: r 0.15s; }
  .node.dragging circle { cursor: grabbing; }
  .node circle:hover { r: 28; }
  .node text { font-size: 11px; fill: #e6edf3; pointer-events: none; text-anchor: middle; dominant-baseline: central; font-weight: 600; }
  .link { stroke: #6e9ac7; stroke-width: 3.5; stroke-linecap: round; opacity: 0.85; }
  .link-label { font-size: 11px; fill: #e6edf3; font-weight: 700; paint-order: stroke; stroke: #0d1117; stroke-width: 3px; }

  /* CAMINO ANIMADO */
  .link-highlight { stroke: var(--accent); stroke-width: 4; }
  .link-bfs { stroke: var(--green); stroke-width: 3; }
  .link-dfs { stroke: var(--purple); stroke-width: 3; }
  .node-visited { fill: var(--orange) !important; }
  .node-path { fill: var(--accent) !important; }
  .node-start { fill: var(--green) !important; }
  .node-end { fill: var(--red) !important; }

  /* TOOLTIP SEDE */
  #nodoTooltip {
    position: fixed; z-index: 2000; display: none; max-width: 300px;
    padding: 12px 14px; background: #1c2128; border: 1px solid var(--accent);
    border-radius: 10px; box-shadow: 0 8px 24px rgba(0,0,0,.45);
    pointer-events: none; font-size: .85rem; line-height: 1.45;
  }
  #nodoTooltip .tt-titulo { font-weight: 700; color: var(--accent); margin-bottom: 4px; }
  #nodoTooltip .tt-estadio { color: #e6edf3; }
  #nodoTooltip .tt-nota { color: var(--muted); font-size: .8rem; margin-top: 6px; }

  /* RANKING */
  .rank-row { background: var(--card); border: 1px solid var(--border); border-radius: 8px;
              padding: 10px 16px; margin: 6px 0; display: flex; align-items: center; gap: 12px;
              transition: background 0.3s; }
  .rank-row.correcto  { border-color: var(--green); background: #1a2d1a; }
  .rank-row.incorrecto{ border-color: var(--red); }
  .rank-row.esperando { border-color: var(--orange); }
  .rank-num { font-size: 1.2rem; font-weight: 700; width: 36px; color: var(--muted); }
  .rank-num.gold   { color: #FFD700; }
  .rank-num.silver { color: #C0C0C0; }
  .rank-num.bronze { color: #CD7F32; }
  .rank-nombre { flex: 1; font-weight: 600; }
  .rank-pts { font-size: 1.1rem; font-weight: 700; color: var(--accent); min-width: 60px; text-align: right; }
  .rank-badge { font-size: .7rem; padding: 2px 8px; border-radius: 20px; }
  .badge-correcto  { background: #1a4a1a; color: var(--green); }
  .badge-incorrecto{ background: #4a1a1a; color: var(--red); }
  .badge-esperando { background: #3a2f00; color: var(--orange); }
  .badge-racha { background: #2d1a4a; color: var(--purple); }

  /* PREGUNTA */
  .pregunta-box { background: #1c2128; border: 1px solid var(--accent); border-radius: 10px;
                  padding: 20px; margin-bottom: 16px; }
  .pregunta-tipo { font-size: .75rem; font-weight: 700; letter-spacing: 1px; color: var(--orange); }
  .pregunta-texto { font-size: 1rem; margin: 8px 0; line-height: 1.5; }
  .timer-bar { height: 6px; background: var(--border); border-radius: 3px; overflow: hidden; margin-top: 10px; }
  .timer-fill { height: 100%; background: var(--green); border-radius: 3px; transition: width 1s linear, background 0.5s; }

  /* MATRIZ */
  .mat-table { font-size: .65rem; border-collapse: collapse; }
  .mat-table td, .mat-table th { border: 1px solid var(--border); padding: 4px 6px; text-align: center; min-width: 28px; }
  .mat-table th { background: #1c2128; color: var(--accent); font-weight: 600; }
  .mat-cell-on  { background: #1a4a1a; color: var(--green); font-weight: 700; }
  .mat-cell-off { color: var(--muted); }
  .mat-diag { background: #1c2128; color: var(--border); }

  /* ADMIN */
  .admin-badge { background: var(--red); color: white; font-size: .7rem; padding: 2px 8px; border-radius: 20px; font-weight: 700; }

  /* RESPUESTA INPUT */
  .camino-input { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
  .nodo-btn { background: #1c2128; border: 1px solid var(--border); color: #e6edf3; border-radius: 8px;
              padding: 6px 12px; cursor: pointer; font-size: .85rem; transition: all .15s; font-family: monospace; }
  .nodo-btn:hover { border-color: var(--accent); color: var(--accent); }
  .nodo-btn.selected { background: var(--accent); color: #0d1117; border-color: var(--accent); font-weight: 700; }
  .camino-preview { font-family: monospace; color: var(--accent); font-size: .9rem; margin: 8px 0;
                    background: #0d1117; padding: 6px 12px; border-radius: 6px; min-height: 32px; }

  /* ANIMACIONES */
  @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.5} }
  .pulsing { animation: pulse 1s infinite; }
  @keyframes slideIn { from{opacity:0;transform:translateY(-8px)} to{opacity:1;transform:translateY(0)} }
  .slide-in { animation: slideIn .3s ease; }
</style>
</head>
<body>

<nav class="navbar navbar-dark px-3 py-2">
  <span class="navbar-brand">🎮 GrafoGame</span>
  <span class="text-muted small">AyED 2 — UADE &nbsp;|&nbsp; BFS / DFS / Matriz de Adyacencia</span>
  <button class="btn btn-sm btn-outline-danger ms-2" onclick="openAdmin()">Admin</button>
</nav>

<div class="container-fluid px-3 pt-2 pb-3">

  <!-- TABS PRINCIPAL -->
  <ul class="nav nav-tabs mb-2" id="mainTabs">
    <li class="nav-item"><a class="nav-link active" href="#" onclick="showTab('juego');return false">🎯 Juego</a></li>
    <li class="nav-item"><a class="nav-link" href="#" onclick="showTab('grafo');return false">🗺️ Grafo</a></li>
    <li class="nav-item"><a class="nav-link" href="#" onclick="showTab('matriz');return false">📊 Matriz</a></li>
    <li class="nav-item"><a class="nav-link" href="#" onclick="showTab('ranking');return false">🏆 Ranking</a></li>
  </ul>

  <!-- TAB JUEGO -->
  <div id="tabJuego">
    <div class="row g-2 align-items-start">
      <div class="col-lg-8 order-1">
        <div class="card mb-2">
          <div class="card-header d-flex align-items-center gap-2 py-2">
            <span class="small">🌎 Mundial 2026 — EE.UU. · México · Canadá</span>
            <span class="text-muted small d-none d-md-inline">Clic = camino · Arrastrá = mover sede</span>
          </div>
          <div class="card-body p-1">
            <svg id="grafoSvg"></svg>
            <div class="px-3 pb-3">
              <div class="text-muted small">Camino seleccionado:</div>
              <div class="camino-preview" id="caminoPreview">— ninguno —</div>
              <div id="avisoUnirse" class="alert alert-warning py-2 px-3 mt-2 mb-0 small" role="alert">
                👤 Unite con tu nombre (panel derecho) para poder enviar tu respuesta.
              </div>
              <div class="d-flex flex-wrap gap-2 mt-2 align-items-center">
                <button type="button" class="btn btn-sm btn-outline-warning" onclick="deshacerUltimoNodo()" id="btnDeshacer" disabled>↩ Quitar último nodo</button>
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="limpiarCamino()">Limpiar todo</button>
                <button type="button" class="btn btn-sm btn-success ms-auto" onclick="enviarRespuesta()" id="btnEnviar" disabled title="Unite al juego primero">Enviar respuesta</button>
              </div>
            </div>
          </div>
        </div>
        <div id="preguntaBox">
          <div class="card p-3 text-center text-muted">
            <div style="font-size:1.5rem">⏳</div>
            <p class="mb-0 small">Esperando que el profe active una pregunta...</p>
          </div>
        </div>
      </div>
      <div class="col-lg-4 order-2">
        <div id="panelUnirse" class="card panel-unirse-compact mb-2">
          <div class="card-body">
            <h6 class="mb-1">👤 Ingresá al juego</h6>
            <p class="text-muted small mb-2">Tu nombre para el ranking en vivo</p>
            <div class="input-group input-group-sm">
              <input type="text" id="inputNombre" class="form-control" placeholder="Tu nombre..." maxlength="30" onkeydown="if(event.key==='Enter')unirse()">
              <button class="btn btn-primary" onclick="unirse()">Unirse</button>
            </div>
          </div>
        </div>
        <div class="card">
          <div class="card-header py-2">🏆 Ranking en vivo</div>
          <div class="card-body p-2" id="rankingJuego" style="max-height:520px;overflow-y:auto"></div>
        </div>
      </div>
    </div>
  </div>

  <!-- TAB GRAFO -->
  <div id="tabGrafo" style="display:none">
    <div class="card">
      <div class="card-header d-flex flex-wrap align-items-center gap-2">
        <span>🗺️ Grafo interactivo — Sedes del Mundial 2026</span>
        <span class="text-muted small">Arrastrá los nodos para reacomodar (las aristas siguen conectadas)</span>
        <button class="btn btn-sm btn-outline-secondary ms-auto" onclick="restaurarPosiciones()">↩ Restaurar mapa</button>
      </div>
      <div class="card-body p-1">
        <svg id="grafoSvg2"></svg>
        <div class="px-3 pb-3">
          <div class="row g-2 mt-1">
            <div class="col-md-6">
              <div class="card p-3">
                <div class="fw-bold mb-2 text-success">▶ BFS desde nodo:</div>
                <div class="d-flex gap-2">
                  <select id="bfsOrigen" class="form-select form-select-sm"></select>
                  <button class="btn btn-sm btn-success" onclick="animarBFS()">Animar BFS</button>
                </div>
              </div>
            </div>
            <div class="col-md-6">
              <div class="card p-3">
                <div class="fw-bold mb-2 text-purple" style="color:var(--purple)">▶ DFS desde nodo:</div>
                <div class="d-flex gap-2">
                  <select id="dfsOrigen" class="form-select form-select-sm"></select>
                  <button class="btn btn-sm btn-outline-light" style="border-color:var(--purple);color:var(--purple)" onclick="animarDFS()">Animar DFS</button>
                </div>
              </div>
            </div>
          </div>
          <div id="ordenVisita" class="mt-2 p-2 rounded" style="background:#0d1117;font-family:monospace;font-size:.85rem;color:var(--accent)"></div>
        </div>
      </div>
    </div>
  </div>

  <!-- TAB MATRIZ -->
  <div id="tabMatriz" style="display:none">
    <div class="card">
      <div class="card-header">📊 Matriz de Adyacencia</div>
      <div class="card-body">
        <p class="text-muted small">Cada celda [i][j] indica el <strong>peso</strong> de la arista entre el nodo i y el nodo j. 0 = sin conexión.</p>
        <div style="overflow-x:auto" id="matrizContainer"></div>
        <div class="mt-3 row g-2">
          <div class="col-md-4"><div class="card p-3">
            <div class="fw-bold mb-2 small">Consultar arista</div>
            <div class="d-flex gap-1 flex-wrap">
              <select id="qU" class="form-select form-select-sm"></select>
              <select id="qV" class="form-select form-select-sm"></select>
              <button class="btn btn-sm btn-outline-primary" onclick="consultarArista()">Consultar</button>
            </div>
            <div id="consultaResult" class="mt-2 small font-monospace"></div>
          </div></div>
        </div>
      </div>
    </div>
  </div>

  <!-- TAB RANKING -->
  <div id="tabRanking" style="display:none">
    <div class="card">
      <div class="card-header">🏆 Ranking global</div>
      <div class="card-body" id="rankingGlobal"></div>
    </div>
  </div>

</div>

<!-- MODAL ADMIN -->
<div class="modal fade" id="adminLoginModal" tabindex="-1">
  <div class="modal-dialog modal-sm">
    <div class="modal-content" style="background:#161b22;border:1px solid #30363d">
      <div class="modal-header border-secondary"><h6 class="modal-title">🔒 Admin</h6></div>
      <div class="modal-body">
        <input type="password" id="adminLoginPass" class="form-control" placeholder="Contraseña"
               onkeydown="if(event.key==='Enter')adminLogin()">
      </div>
      <div class="modal-footer border-secondary">
        <button class="btn btn-primary w-100" onclick="adminLogin()">Ingresar</button>
      </div>
    </div>
  </div>
</div>

<div class="modal fade" id="adminModal" tabindex="-1">
  <div class="modal-dialog modal-lg">
    <div class="modal-content" style="background:#161b22;border:1px solid #30363d">
      <div class="modal-header border-secondary">
        <h6 class="modal-title">⚙️ Panel Admin <span class="admin-badge">PROFE</span></h6>
        <button class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
      </div>
      <div class="modal-body">
        <div class="row g-3">
          <div class="col-md-6">
            <div class="card p-3">
              <div class="fw-bold mb-2">🎯 Activar pregunta</div>
              <div id="listaPreguntasAdmin"></div>
            </div>
          </div>
          <div class="col-md-6">
            <div class="card p-3">
              <div class="fw-bold mb-2">➕ Nueva pregunta</div>
              <select id="nuevoTipo" class="form-select form-select-sm mb-2">
                <option value="BFS_CAMINO">BFS — Camino más corto</option>
                <option value="BFS_ORDEN">BFS — Orden de visita</option>
                <option value="DFS_ORDEN">DFS — Orden de visita</option>
                <option value="MATRIZ_QUERY">Matriz — Consultar arista</option>
              </select>
              <div class="d-flex gap-2 mb-2">
                <div class="flex-fill">
                  <label class="text-muted" style="font-size:.7rem">Origen</label>
                  <select id="nuevoOrigen" class="form-select form-select-sm"></select>
                </div>
                <div class="flex-fill">
                  <label class="text-muted" style="font-size:.7rem">Destino</label>
                  <select id="nuevoDestino" class="form-select form-select-sm"></select>
                </div>
              </div>
              <button class="btn btn-success btn-sm w-100" onclick="crearPregunta()">Crear y activar</button>
            </div>
            <div class="card p-3 mt-2">
              <div class="fw-bold mb-2">🔗 Editar arista</div>
              <div class="d-flex gap-2 mb-2">
                <select id="aristU" class="form-select form-select-sm"></select>
                <select id="aristV" class="form-select form-select-sm"></select>
                <input type="number" id="aristPeso" class="form-control form-control-sm" placeholder="Peso (0=borrar)" min="0" max="99" value="1" style="max-width:80px">
              </div>
              <button class="btn btn-warning btn-sm w-100" onclick="editarArista()">Actualizar arista</button>
            </div>
          </div>
        </div>
        <div class="card p-3 mt-3">
          <div class="fw-bold mb-2"><span class="admin-badge">PROFE</span> Modo demo</div>
          <p class="text-muted small mb-2">Unite con tu nombre si querés probar el envío como alumno.</p>
          <div class="d-flex flex-wrap gap-2">
            <button class="btn btn-sm btn-outline-info" onclick="completarAutomatico(false); bootstrap.Modal.getInstance(document.getElementById('adminModal'))?.hide(); showTab('juego');">✨ Rellenar solución en el grafo</button>
            <button class="btn btn-sm btn-info" onclick="completarAutomatico(true)" id="btnCompletarEnviar">📤 Rellenar y enviar respuesta</button>
          </div>
        </div>
        <div class="mt-3 d-flex flex-wrap gap-2">
          <button class="btn btn-sm btn-outline-info" onclick="mostrarSolucion()">👁️ Mostrar solución al curso</button>
          <button class="btn btn-sm btn-outline-danger ms-auto" onclick="reiniciar()">🔄 Reiniciar juego</button>
        </div>
        <p class="text-muted small mt-2 mb-0">Tip: activá una pregunta con los botones de arriba. Los alumnos la ven en la pestaña Juego.</p>
      </div>
    </div>
  </div>
</div>

<div id="nodoTooltip" role="tooltip"></div>

<script>
// ─── ESTADO CLIENTE ──────────────────────────────────────────
let S = {}; // state del servidor
let me = localStorage.getItem('grafogame_nombre') || null;
let adminPass = null;
let caminoSeleccionado = []; // nodos clickeados por el alumno
let simulacionActiva = false;
let arrastrandoNodo = false;
let huboDragReciente = false;
let NODOS = [], MATRIZ = [];

// ─── POSICIONES DE NODOS (mapa aproximado de sedes, arrastrables) ───
// Posiciones calibradas sobre mapa Norteamérica (viewBox 1000×700)
const POS_DEFAULT = [
  {x:0.867,y:0.262}, {x:0.072,y:0.437}, {x:0.633,y:0.278}, {x:0.500,y:0.484}, {x:0.522,y:0.579},
  {x:0.844,y:0.627}, {x:0.083,y:0.175}, {x:0.911,y:0.222}, {x:0.050,y:0.341}, {x:0.722,y:0.516},
  {x:0.422,y:0.786}, {x:0.344,y:0.833}, {x:0.467,y:0.706}, {x:0.811,y:0.183}, {x:0.078,y:0.119}
];
let POS = POS_DEFAULT.map(p => ({x: p.x, y: p.y}));

function cargarPosiciones() {
  try {
    const raw = localStorage.getItem('grafogame_pos_v2') || localStorage.getItem('grafogame_pos');
    if (!raw) return;
    const arr = JSON.parse(raw);
    if (Array.isArray(arr) && arr.length === POS_DEFAULT.length) {
      POS = arr.map(p => ({ x: +p.x, y: +p.y }));
    }
  } catch (e) { /* ignorar */ }
}
function guardarPosiciones() {
  localStorage.setItem('grafogame_pos_v2', JSON.stringify(POS));
}
function restaurarPosiciones() {
  POS = POS_DEFAULT.map(p => ({ x: p.x, y: p.y }));
  localStorage.removeItem('grafogame_pos');
  localStorage.removeItem('grafogame_pos_v2');
  renderGrafoSvg('grafoSvg');
  renderGrafoSvg('grafoSvg2');
  actualizarCaminoVisual();
}
function posToPx(p, W, H) {
  return { x: p.x * W * 0.9 + W * 0.05, y: p.y * H * 0.9 + H * 0.05 };
}
function pxToPos(px, py, W, H) {
  return {
    x: Math.max(0, Math.min(1, (px - W * 0.05) / (W * 0.9))),
    y: Math.max(0, Math.min(1, (py - H * 0.05) / (H * 0.9)))
  };
}
function moverNodoEnPantalla(idx) {
  ['grafoSvg', 'grafoSvg2'].forEach(svgId => {
    const svgEl = document.getElementById(svgId);
    if (!svgEl) return;
    const W = svgEl.clientWidth || 600, H = svgEl.clientHeight || 480;
    const { x, y } = posToPx(POS[idx], W, H);
    d3.select('#' + svgId).select('#node-group-' + svgId + '-' + idx)
      .attr('transform', `translate(${x},${y})`);
    actualizarAristasNodo(svgId, idx, W, H);
  });
}
function actualizarAristasNodo(svgId, idx, W, H) {
  const svg = d3.select('#' + svgId);
  for (let j = 0; j < NODOS.length; j++) {
    if (j === idx) continue;
    const i = Math.min(idx, j), k = Math.max(idx, j);
    if (!MATRIZ[i] || MATRIZ[i][k] === 0) continue;
    const p1 = posToPx(POS[i], W, H), p2 = posToPx(POS[k], W, H);
    svg.select('#link-' + svgId + '-' + i + '-' + k)
      .attr('x1', p1.x).attr('y1', p1.y).attr('x2', p2.x).attr('y2', p2.y);
    svg.select('#link-label-' + svgId + '-' + i + '-' + k)
      .attr('x', (p1.x + p2.x) / 2).attr('y', (p1.y + p2.y) / 2 - 4);
  }
}

const COLORES_NODO = [
  '#58a6ff','#3fb950','#f85149','#e3b341','#bc8cff',
  '#79c0ff','#56d364','#ff7b72','#d2a8ff','#ffa657',
  '#58a6ff','#3fb950','#e3b341','#bc8cff','#79c0ff'
];

// Sedes del Mundial FIFA 2026 (dato pedagógico / referencia)
const SEDES = [
  { nombre: 'Nueva York / New Jersey', pais: 'EE.UU.', estadio: 'MetLife Stadium (East Rutherford)', nota: 'Gran área metropolitana del noreste.' },
  { nombre: 'Los Ángeles', pais: 'EE.UU.', estadio: 'SoFi Stadium (Inglewood)', nota: 'Costa oeste; hub de entretenimiento y deportes.' },
  { nombre: 'Chicago', pais: 'EE.UU.', estadio: 'Soldier Field', nota: 'Corazón del Medio Oeste estadounidense.' },
  { nombre: 'Dallas', pais: 'EE.UU.', estadio: 'AT&T Stadium (Arlington)', nota: 'Enlace clave hacia México y el sur.' },
  { nombre: 'Houston', pais: 'EE.UU.', estadio: 'NRG Stadium', nota: 'Puerto y ciudad multicultural del Golfo.' },
  { nombre: 'Miami', pais: 'EE.UU.', estadio: 'Hard Rock Stadium (Miami Gardens)', nota: 'Puerta de entrada al Caribe y Latinoamérica.' },
  { nombre: 'Seattle', pais: 'EE.UU.', estadio: 'Lumen Field', nota: 'Noroeste del Pacífico, cerca de Canadá.' },
  { nombre: 'Boston', pais: 'EE.UU.', estadio: 'Gillette Stadium (Foxborough)', nota: 'Región histórica de Nueva Inglaterra.' },
  { nombre: 'San Francisco / Bay Area', pais: 'EE.UU.', estadio: "Levi's Stadium (Santa Clara)", nota: 'Silicon Valley y costa californiana.' },
  { nombre: 'Atlanta', pais: 'EE.UU.', estadio: 'Mercedes-Benz Stadium', nota: 'Hub del sureste con gran conectividad aérea.' },
  { nombre: 'Ciudad de México', pais: 'México', estadio: 'Estadio Azteca', nota: 'Único estadio en tres Mundiales (1970, 1986, 2026).' },
  { nombre: 'Guadalajara', pais: 'México', estadio: 'Estadio Akron', nota: 'Cuna del fútbol mexicano en el occidente.' },
  { nombre: 'Monterrey', pais: 'México', estadio: 'Estadio BBVA', nota: 'Norte industrial y fronterizo de México.' },
  { nombre: 'Toronto', pais: 'Canadá', estadio: 'BMO Field', nota: 'Mayor ciudad de Canadá; sede co-anfitriona.' },
  { nombre: 'Vancouver', pais: 'Canadá', estadio: 'BC Place', nota: 'Costa del Pacífico canadiense.' },
];

// ─── INIT ─────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  cargarPosiciones();
  if (me) {
    document.getElementById('panelUnirse').style.display = 'none';
  }
  load();
  setInterval(load, 3000); // polling cada 3s para ranking en vivo
});

async function load() {
  const r = await api('/api/state');
  S = r;
  NODOS = S.nodos || [];
  MATRIZ = S.matriz || [];
  renderTodo();
}

function renderTodo() {
  renderPregunta();
  renderRanking();
  if (!arrastrandoNodo) {
    renderGrafoSvg('grafoSvg');
    renderGrafoSvg('grafoSvg2');
  }
  renderMatriz();
  poblarSelectores();
  actualizarCaminoUI();
}

// ─── PREGUNTA ─────────────────────────────────────────────────
function renderPregunta() {
  const box = document.getElementById('preguntaBox');
  const p = S.pregunta;
  if (!p || !p.activa) {
    box.innerHTML = `<div class="card p-4 text-center text-muted">
      <div style="font-size:2rem">⏳</div>
      <p>Esperando que el profe active una pregunta...</p>
    </div>`;
    const b1 = document.getElementById('btnEnviar');
    const b2 = document.getElementById('btnDeshacer');
    if (b1) b1.disabled = true;
    if (b2) b2.disabled = true;
    return;
  }

  // verificar si ya respondí
  const miDato = S.ranking?.find(r => r.nombre === me);
  const yaRespondi = miDato?.respondio;

  const pct = Math.max(0, Math.min(100, (1 - p.transcurrido / p.tiempoLimite) * 100));
  const color = pct > 50 ? 'var(--green)' : pct > 20 ? 'var(--orange)' : 'var(--red)';

  box.innerHTML = `<div class="pregunta-box slide-in">
    <div class="d-flex justify-content-between align-items-start">
      <span class="pregunta-tipo">${p.tipo.replace('_',' ')}</span>
      <span class="text-muted small">#${p.id}</span>
    </div>
    <div class="pregunta-texto">${p.enunciado.replace(/\\n/g,'<br>')}</div>
    <div class="timer-bar"><div class="timer-fill" id="timerFill" style="width:${pct}%;background:${color}"></div></div>
    <div class="d-flex justify-content-between mt-1">
      <span class="text-muted small">⏱️ ${p.transcurrido}s / ${p.tiempoLimite}s</span>
      ${yaRespondi ? `<span class="badge badge-correcto">✅ Respondido</span>` : `<span class="pulsing text-warning small">● Respondiendo...</span>`}
    </div>
  </div>`;

  const btnEnviar = document.getElementById('btnEnviar');
  const btnDeshacer = document.getElementById('btnDeshacer');
  if (btnEnviar) {
    btnEnviar.disabled = yaRespondi || !me || caminoSeleccionado.length === 0;
    btnEnviar.title = me ? 'Enviar tu camino' : 'Unite al juego primero';
  }
  if (btnDeshacer) btnDeshacer.disabled = yaRespondi || !me || caminoSeleccionado.length === 0;
  const aviso = document.getElementById('avisoUnirse');
  if (aviso) aviso.style.display = me ? 'none' : '';
}

// ─── GRAFO SVG ────────────────────────────────────────────────
const dragNodo = d3.drag()
  .clickDistance(10)
  .on('start', function(evt) {
    arrastrandoNodo = true;
    huboDragReciente = false;
    d3.select(this).classed('dragging', true).raise();
    ocultarTooltipSede();
  })
  .on('drag', function(evt) {
    huboDragReciente = true;
    const idx = +this.getAttribute('data-idx');
    const svgId = this.getAttribute('data-svg');
    const svgEl = document.getElementById(svgId);
    if (!svgEl || isNaN(idx)) return;
    const W = svgEl.clientWidth || 600, H = svgEl.clientHeight || 480;
    const [mx, my] = d3.pointer(evt, svgEl);
    POS[idx] = pxToPos(mx, my, W, H);
    moverNodoEnPantalla(idx);
  })
  .on('end', function() {
    d3.select(this).classed('dragging', false);
    arrastrandoNodo = false;
    if (huboDragReciente) {
      guardarPosiciones();
      setTimeout(() => { huboDragReciente = false; }, 80);
    }
  });

function dibujarMapaFondo(svg, W, H) {
  const ox = W * 0.05, oy = H * 0.05, sw = W * 0.9, sh = H * 0.9;
  const g = svg.append('g').attr('class', 'mapa-fondo')
    .attr('transform', 'translate(' + ox + ',' + oy + ') scale(' + (sw / 1000) + ',' + (sh / 700) + ')');
  g.append('rect').attr('width', 1000).attr('height', 700).attr('fill', '#0a1420');
  g.append('path').attr('class', 'mapa-pais mapa-can')
    .attr('d', 'M55,35 L945,42 L925,215 L70,198 Z');
  g.append('path').attr('class', 'mapa-pais mapa-usa')
    .attr('d', 'M70,198 L925,215 L895,485 L95,468 Z');
  g.append('path').attr('class', 'mapa-pais mapa-mex')
    .attr('d', 'M185,468 L855,492 L795,665 L255,648 L165,535 Z');
  [['CANADÁ', 500, 115], ['EE.UU.', 500, 340], ['MÉXICO', 490, 575]].forEach(function(t) {
    g.append('text').attr('class', 'mapa-label').attr('x', t[1]).attr('y', t[2])
      .attr('text-anchor', 'middle').text(t[0]);
  });
}

function renderGrafoSvg(svgId) {
  if (!NODOS.length || !MATRIZ.length) return;
  const svgEl = document.getElementById(svgId);
  if (!svgEl) return;
  const W = svgEl.clientWidth || 600, H = svgEl.clientHeight || 480;

  const svg = d3.select('#' + svgId);
  svg.selectAll('*').remove();

  dibujarMapaFondo(svg, W, H);

  // Aristas
  for (let i = 0; i < NODOS.length; i++) {
    for (let j = i + 1; j < NODOS.length; j++) {
      if (MATRIZ[i][j] === 0) continue;
      const p1 = posToPx(POS[i], W, H), p2 = posToPx(POS[j], W, H);
      svg.append('line').attr('class', 'link')
        .attr('x1', p1.x).attr('y1', p1.y).attr('x2', p2.x).attr('y2', p2.y)
        .attr('id', 'link-' + svgId + '-' + i + '-' + j);
      svg.append('text').attr('class', 'link-label')
        .attr('x', (p1.x + p2.x) / 2).attr('y', (p1.y + p2.y) / 2 - 4)
        .attr('id', 'link-label-' + svgId + '-' + i + '-' + j)
        .text(MATRIZ[i][j]);
    }
  }

  // Nodos (arrastrables; clic corto sigue armando el camino en Juego)
  NODOS.forEach((nombre, idx) => {
    const { x, y } = posToPx(POS[idx], W, H);
    const g = svg.append('g').attr('class', 'node')
      .attr('id', 'node-group-' + svgId + '-' + idx)
      .attr('data-idx', idx)
      .attr('data-svg', svgId)
      .attr('transform', `translate(${x},${y})`)
      .call(dragNodo);

    g.append('circle').attr('r', 24)
      .attr('fill', COLORES_NODO[idx % COLORES_NODO.length])
      .attr('stroke', '#0d1117').attr('id', 'node-' + svgId + '-' + idx)
      .on('click', () => clickNodo(idx));

    g.on('mouseenter', (evt) => mostrarTooltipSede(evt, idx))
      .on('mousemove', moverTooltipSede)
      .on('mouseleave', ocultarTooltipSede);

    g.append('text').attr('dy', '0.35em').attr('font-size', '10px')
      .text(nombre.slice(0, 3));
    g.append('text').attr('dy', '2.1em').attr('font-size', '9px').attr('fill', 'var(--muted)')
      .text(idx);
  });
}

// ─── TOOLTIP SEDE ─────────────────────────────────────────────
function mostrarTooltipSede(evt, idx) {
  const tt = document.getElementById('nodoTooltip');
  const sede = SEDES[idx];
  const codigo = NODOS[idx] || ('Nodo ' + idx);
  if (!tt || !sede) return;
  tt.innerHTML = `<div class="tt-titulo">${codigo} · Nodo ${idx}</div>`
    + `<div><strong>${sede.nombre}</strong> (${sede.pais})</div>`
    + `<div class="tt-estadio">🏟️ ${sede.estadio}</div>`
    + `<div class="tt-nota">${sede.nota}</div>`;
  tt.style.display = 'block';
  moverTooltipSede(evt);
}
function moverTooltipSede(evt) {
  const tt = document.getElementById('nodoTooltip');
  if (!tt || tt.style.display === 'none') return;
  const pad = 14;
  let x = evt.clientX + pad, y = evt.clientY + pad;
  const rect = tt.getBoundingClientRect();
  if (x + rect.width > window.innerWidth - 8) x = evt.clientX - rect.width - pad;
  if (y + rect.height > window.innerHeight - 8) y = evt.clientY - rect.height - pad;
  tt.style.left = x + 'px';
  tt.style.top = y + 'px';
}
function ocultarTooltipSede() {
  const tt = document.getElementById('nodoTooltip');
  if (tt) tt.style.display = 'none';
}

// ─── CLICK NODO (armar camino) ────────────────────────────────
function clickNodo(idx) {
  if (huboDragReciente) return;
  if (!S.pregunta?.activa) return;
  if (!me) {
    alert('Primero unite al juego con tu nombre (panel derecho).');
    return;
  }
  const miDato = S.ranking?.find(r => r.nombre === me);
  if (miDato?.respondio) return;

  // toggle: si ya está al final, quitar; si no, agregar
  if (caminoSeleccionado[caminoSeleccionado.length-1] === idx) {
    caminoSeleccionado.pop();
  } else {
    caminoSeleccionado.push(idx);
  }

  actualizarCaminoUI();
}

function actualizarCaminoUI() {
  actualizarCaminoVisual();
  const preview = document.getElementById('caminoPreview');
  if (preview) {
    preview.textContent = caminoSeleccionado.length
      ? caminoSeleccionado.map(i => `${i}(${NODOS[i]})`).join(' → ')
      : '— ninguno —';
  }
  const vacio = caminoSeleccionado.length === 0;
  const unido = !!me;
  const btnEnviar = document.getElementById('btnEnviar');
  const btnDeshacer = document.getElementById('btnDeshacer');
  const aviso = document.getElementById('avisoUnirse');
  if (btnEnviar) {
    btnEnviar.disabled = vacio || !unido;
    btnEnviar.title = unido ? 'Enviar tu camino al servidor' : 'Primero unite con tu nombre';
  }
  if (btnDeshacer) btnDeshacer.disabled = vacio || !unido;
  if (aviso) aviso.style.display = unido ? 'none' : '';
}

function deshacerUltimoNodo() {
  if (!caminoSeleccionado.length) return;
  caminoSeleccionado.pop();
  actualizarCaminoUI();
}

function actualizarCaminoVisual() {
  // Reset todos los nodos
  NODOS.forEach((_, i) => {
    const el = document.getElementById(`node-grafoSvg-${i}`);
    if (el) el.style.fill = COLORES_NODO[i % COLORES_NODO.length];
  });
  // Pintar camino
  caminoSeleccionado.forEach((idx, pos) => {
    const el = document.getElementById(`node-grafoSvg-${idx}`);
    if (!el) return;
    if (pos === 0) el.style.fill = 'var(--green)';
    else if (pos === caminoSeleccionado.length-1) el.style.fill = 'var(--red)';
    else el.style.fill = 'var(--accent)';
  });
}

function limpiarCamino() {
  caminoSeleccionado = [];
  actualizarCaminoUI();
}

// ─── ANIMACIONES BFS / DFS ────────────────────────────────────
async function animarBFS() {
  if (simulacionActiva) return;
  simulacionActiva = true;
  const origen = parseInt(document.getElementById('bfsOrigen').value);
  const orden = bfsLocal(origen);
  document.getElementById('ordenVisita').textContent = 'BFS: ' + orden.map(i => NODOS[i]).join(' → ');
  await animarOrden(orden, 'bfs', 'grafoSvg2');
  simulacionActiva = false;
}

async function animarDFS() {
  if (simulacionActiva) return;
  simulacionActiva = true;
  const origen = parseInt(document.getElementById('dfsOrigen').value);
  const orden = dfsLocal(origen);
  document.getElementById('ordenVisita').textContent = 'DFS: ' + orden.map(i => NODOS[i]).join(' → ');
  await animarOrden(orden, 'dfs', 'grafoSvg2');
  simulacionActiva = false;
}

async function animarOrden(orden, tipo, svgId) {
  // Reset colores
  NODOS.forEach((_, i) => {
    const el = document.getElementById(`node-${svgId}-${i}`);
    if (el) el.style.fill = COLORES_NODO[i % COLORES_NODO.length];
  });
  // Animar paso a paso
  for (let step = 0; step < orden.length; step++) {
    const nodo = orden[step];
    const el = document.getElementById(`node-${svgId}-${nodo}`);
    if (el) {
      el.style.fill = tipo === 'bfs' ? 'var(--green)' : 'var(--purple)';
      el.style.r = '30';
      await sleep(600);
      el.style.r = '24';
    }
  }
}

// BFS local en JS (refleja el Java)
function bfsLocal(origen) {
  const n = NODOS.length, vis = new Array(n).fill(false), orden = [], cola = [origen];
  vis[origen] = true;
  while (cola.length) {
    const u = cola.shift();
    orden.push(u);
    for (let v = 0; v < n; v++) {
      if (MATRIZ[u][v] !== 0 && !vis[v]) { vis[v] = true; cola.push(v); }
    }
  }
  return orden;
}

// DFS local en JS
function dfsLocal(origen) {
  const n = NODOS.length, vis = new Array(n).fill(false), orden = [];
  function rec(u) { vis[u] = true; orden.push(u); for (let v=0;v<n;v++) if(MATRIZ[u][v]&&!vis[v])rec(v); }
  rec(origen);
  return orden;
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

// ─── MATRIZ ───────────────────────────────────────────────────
function renderMatriz() {
  if (!NODOS.length || !MATRIZ.length) return;
  const cont = document.getElementById('matrizContainer');
  if (!cont) return;
  let html = '<table class="mat-table"><thead><tr><th>—</th>';
  NODOS.forEach((n,i) => html += `<th>${i}<br><span style="font-weight:400;color:var(--muted)">${n.slice(0,3)}</span></th>`);
  html += '</tr></thead><tbody>';
  MATRIZ.forEach((fila, i) => {
    html += `<tr><th>${i}<br><span style="font-weight:400;color:var(--muted)">${NODOS[i].slice(0,3)}</span></th>`;
    fila.forEach((v, j) => {
      if (i === j) html += `<td class="mat-diag">0</td>`;
      else if (v > 0) html += `<td class="mat-cell-on">${v}</td>`;
      else html += `<td class="mat-cell-off">0</td>`;
    });
    html += '</tr>';
  });
  html += '</tbody></table>';
  cont.innerHTML = html;
}

function consultarArista() {
  const u = parseInt(document.getElementById('qU').value);
  const v = parseInt(document.getElementById('qV').value);
  const peso = MATRIZ[u]?.[v] ?? 0;
  const res = document.getElementById('consultaResult');
  if (peso > 0)
    res.innerHTML = `<span class="text-success">✅ mat[${u}][${v}] = ${peso} → Están conectados con peso ${peso}</span>`;
  else
    res.innerHTML = `<span class="text-danger">❌ mat[${u}][${v}] = 0 → No hay arista</span>`;
}

// ─── RANKING ──────────────────────────────────────────────────
function renderRanking() {
  const ranking = S.ranking || [];
  const p = S.pregunta;

  function rowHtml(j, i) {
    const estado = p?.activa
      ? (j.respondio ? (j.correcto ? 'correcto' : 'incorrecto') : 'esperando')
      : '';
    const badgeEstado = j.respondio
      ? (j.correcto ? `<span class="rank-badge badge-correcto">✅ +${j.puntos}pts</span>`
                    : `<span class="rank-badge badge-incorrecto">❌</span>`)
      : (p?.activa ? `<span class="rank-badge badge-esperando pulsing">⏳</span>` : '');
    const badgeRacha = j.racha >= 3 ? `<span class="rank-badge badge-racha">🔥×${j.racha}</span>` : '';
    const numClass = i===0?'gold':i===1?'silver':i===2?'bronze':'';
    const medal = i===0?'🥇':i===1?'🥈':i===2?'🥉':i+1;
    const esMio = j.nombre === me ? 'border-color:var(--accent);' : '';
    return `<div class="rank-row ${estado}" style="${esMio}">
      <span class="rank-num ${numClass}">${medal}</span>
      <span class="rank-nombre">${j.nombre}</span>
      ${badgeRacha} ${badgeEstado}
      <span class="rank-pts">${j.puntaje}</span>
    </div>`;
  }

  const html = ranking.map((j,i) => rowHtml(j,i)).join('') || '<p class="text-muted text-center p-3">Nadie se unió aún</p>';
  const el1 = document.getElementById('rankingJuego');
  if (el1) el1.innerHTML = html;
  const el2 = document.getElementById('rankingGlobal');
  if (el2) el2.innerHTML = html;
}

// ─── ACCIONES JUGADOR ─────────────────────────────────────────
async function unirse() {
  const nombre = document.getElementById('inputNombre').value.trim();
  if (!nombre) return;
  const r = await api('/api/unirse', {nombre});
  if (r.error) { alert(r.error); return; }
  me = nombre;
  localStorage.setItem('grafogame_nombre', nombre);
  document.getElementById('panelUnirse').style.display = 'none';
  await load();
  actualizarCaminoUI();
}

async function enviarRespuesta() {
  if (!me) {
    alert('Tenés que unirte al juego con tu nombre antes de enviar la respuesta.');
    return;
  }
  if (caminoSeleccionado.length === 0) return;
  const r = await api('/api/responder', {nombre: me, camino: caminoSeleccionado.join(',')});
  if (r.error) { alert(r.error); return; }
  const msg = r.correcta
    ? `✅ ¡Correcto! +${r.puntos} puntos (${(r.tiempoMs/1000).toFixed(1)}s)`
    : `❌ Incorrecto. +${r.puntos} pts parciales`;
  // feedback visual
  const btn = document.getElementById('btnEnviar');
  btn.textContent = msg;
  btn.disabled = true;
  await load();
}

// ─── ADMIN ────────────────────────────────────────────────────
function openAdmin() {
  document.getElementById('adminLoginPass').value = '';
  bootstrap.Modal.getOrCreateInstance(document.getElementById('adminLoginModal')).show();
}
function adminLogin() {
  const p = document.getElementById('adminLoginPass').value;
  if (p !== 'profe2026') { alert('Contraseña incorrecta'); return; }
  adminPass = p;
  const loginEl = document.getElementById('adminLoginModal');
  bootstrap.Modal.getInstance(loginEl)?.hide() ?? bootstrap.Modal.getOrCreateInstance(loginEl).hide();
  renderAdminPreguntas();
  bootstrap.Modal.getOrCreateInstance(document.getElementById('adminModal')).show();
}

function renderAdminPreguntas() {
  const lista = S.preguntas || [];
  const activaId = S.pregunta?.activa ? S.pregunta.id : null;
  let html = '<div class="text-muted small mb-2">Clic en <strong>Activar</strong> para lanzar la pregunta al curso:</div>';
  if (!lista.length) {
    html += '<p class="text-muted small mb-0">No hay preguntas. Usá «Crear y activar».</p>';
  } else {
    lista.forEach(pr => {
      const esActiva = pr.id === activaId;
      html += `<div class="card p-2 mb-2 border-${esActiva ? 'success' : 'secondary'}">
        <div class="d-flex align-items-center gap-2 flex-wrap">
          <span class="badge bg-${esActiva ? 'success' : 'secondary'}">#${pr.id}</span>
          <span class="small flex-fill">${pr.etiqueta || pr.tipo}</span>
          ${esActiva
            ? '<span class="text-success small fw-bold">● EN VIVO</span>'
            : `<button class="btn btn-sm btn-success" onclick="activarPregunta(${pr.id})">Activar</button>`}
        </div>
      </div>`;
    });
  }
  document.getElementById('listaPreguntasAdmin').innerHTML = html;
  const btn = document.getElementById('btnCompletarEnviar');
  if (btn) btn.disabled = !me || !S.pregunta?.activa;
}

async function completarAutomatico(enviar) {
  if (!S.pregunta?.activa) {
    alert('Primero activá una pregunta desde Admin.');
    return;
  }
  const r = await api('/api/resolver');
  if (r.error) { alert(r.error); return; }
  const nums = Array.isArray(r.respuesta) ? r.respuesta : [];
  if (r.tipo === 'MATRIZ_QUERY') {
    const ok = nums[0] === 1;
    alert('Pregunta de matriz (no usa clic en nodos).\\nSolución: ' + (ok ? 'conectados, peso ' + nums[1] : 'no conectados (0)'));
    return;
  }
  if (!nums.length) {
    alert('No hay solución para esta pregunta (¿nodos desconectados?).');
    return;
  }
  caminoSeleccionado = nums.slice();
  actualizarCaminoUI();
  showTab('juego');
  if (enviar) {
    if (!me) {
      alert('Para enviar la respuesta, unite al juego con tu nombre (panel de arriba).\\nYa rellené el camino correcto en el grafo.');
      return;
    }
    await enviarRespuesta();
  }
}

async function activarPregunta(id) {
  const r = await api('/api/activar', {pass: adminPass, id});
  if (r.error) { alert(r.error); return; }
  await load();
  renderAdminPreguntas();
  caminoSeleccionado = [];
  limpiarCamino();
}

async function crearPregunta() {
  const tipo   = document.getElementById('nuevoTipo').value;
  const origen = parseInt(document.getElementById('nuevoOrigen').value);
  const destino= parseInt(document.getElementById('nuevoDestino').value);
  const r = await api('/api/nueva_pregunta', {pass: adminPass, tipo, origen: String(origen), destino: String(destino)});
  if (r.error) { alert(r.error); return; }
  await activarPregunta(r.id);
}

async function editarArista() {
  const u    = parseInt(document.getElementById('aristU').value);
  const v    = parseInt(document.getElementById('aristV').value);
  const peso = parseInt(document.getElementById('aristPeso').value);
  const r = await api('/api/editar_arista', {pass: adminPass, u: String(u), v: String(v), peso: String(peso)});
  if (r.error) { alert(r.error); return; }
  await load();
  renderAdminPreguntas();
  alert('Arista actualizada');
}

async function mostrarSolucion() {
  const r = await api('/api/resolver');
  if (r.error) { alert(r.error); return; }
  // r.respuesta ya es un array (el server lo serializa como JSON array,
  // no como string). No hace falta JSON.parse.
  const nums = Array.isArray(r.respuesta) ? r.respuesta : [];
  if (r.tipo === 'MATRIZ_QUERY') {
    // Para preguntas de matriz, la respuesta es [conectado(0/1), peso?]
    const conectado = nums[0] === 1;
    alert('Solución (MATRIZ_QUERY):\\n' + (conectado
      ? '✅ Conectados con peso ' + nums[1]
      : '❌ No están conectados'));
    return;
  }
  alert('Solución (' + r.tipo + '):\\n' + nums.map(i => i + '(' + NODOS[i] + ')').join(' → '));
  // animar en el grafo
  if (r.tipo === 'BFS_CAMINO' || r.tipo === 'BFS_ORDEN') {
    await animarOrden(nums, 'bfs', 'grafoSvg2');
  } else if (r.tipo === 'DFS_ORDEN') {
    await animarOrden(nums, 'dfs', 'grafoSvg2');
  }
}

async function reiniciar() {
  if (!confirm('¿Reiniciar el juego? Se borrarán todos los puntajes.')) return;
  const r = await api('/api/reiniciar', {pass: adminPass});
  if (r.error) { alert(r.error); return; }
  me = null;
  localStorage.removeItem('grafogame_nombre');
  document.getElementById('panelUnirse').style.display = '';
  await load();
  alert('Juego reiniciado');
}

// ─── SELECTORES ───────────────────────────────────────────────
function poblarSelectores() {
  if (!NODOS.length) return;
  const ids = ['bfsOrigen','dfsOrigen','nuevoOrigen','nuevoDestino','qU','qV','aristU','aristV'];
  ids.forEach(id => {
    const el = document.getElementById(id);
    if (!el || el.options.length === NODOS.length) return;
    el.innerHTML = NODOS.map((n,i) => `<option value="${i}">${i} — ${n}</option>`).join('');
  });
}

// ─── TABS ─────────────────────────────────────────────────────
function showTab(tab) {
  ['juego','grafo','matriz','ranking'].forEach(t => {
    const el = document.getElementById('tab' + t.charAt(0).toUpperCase() + t.slice(1));
    if (el) el.style.display = t === tab ? '' : 'none';
  });
  document.querySelectorAll('#mainTabs .nav-link').forEach((el, i) => {
    el.classList.toggle('active', ['juego','grafo','matriz','ranking'][i] === tab);
  });
  if (tab === 'grafo') setTimeout(() => { renderGrafoSvg('grafoSvg2'); }, 50);
}

// ─── UTILS ────────────────────────────────────────────────────
async function api(url, body) {
  const opts = body
    ? {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)}
    : {method:'GET'};
  const r = await fetch(url, opts);
  return r.json();
}
</script>
</body>
</html>
""";
}
