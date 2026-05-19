package com.quickmira.Database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.regex.*;

/**
 * Clase encargada de la persistencia y carga masiva de productos.
 * Integra soporte para SQLite, MySQL y MongoDB con sistema de backups.
 */
public class CargarProductos {

    // ═════════════════════════════════════════════════════════════════════════
    //  CREAR TABLA / COLECCIÓN
    // ═════════════════════════════════════════════════════════════════════════
    public static void crearTablaProducto() {
        if (Conexion.isUsarMongo()) {
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db != null) {
                boolean existe = false;
                for (String nombre : db.listCollectionNames()) {
                    if (nombre.equals("producto")) { existe = true; break; }
                }
                if (!existe) db.createCollection("producto");
                System.out.println("✅ Colección 'producto' lista en MongoDB.");
            }
            return;
        }

        String sqlSQLite = "CREATE TABLE IF NOT EXISTS producto (" +
                "id     INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nombre TEXT    NOT NULL UNIQUE," +
                "precio REAL," +
                "total  INTEGER," +
                "imagen TEXT" +
                ");";

        String sqlMySQL = "CREATE TABLE IF NOT EXISTS producto (" +
                "id     INT AUTO_INCREMENT PRIMARY KEY," +
                "nombre VARCHAR(255) NOT NULL UNIQUE," +
                "precio DOUBLE," +
                "total  INT," +
                "imagen VARCHAR(255)" +
                ");";

        String sql = Conexion.isUsarMySQL() ? sqlMySQL : sqlSQLite;

        try (Connection con = Conexion.getConexion();
             Statement stmt = con.createStatement()) {
            stmt.execute(sql);
            System.out.println("✅ Tabla 'producto' lista.");
        } catch (SQLException e) {
            System.out.println("❌ Error al crear tabla: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ELIMINAR PRODUCTO (CON BACKUP Y LIMPIEZA DE TXT)
    // ═════════════════════════════════════════════════════════════════════════
    public static void eliminarProducto(String nombre) {
        // 1. Eliminar del archivo TXT primero
        eliminarDeArchivo(nombre);

        // 2. Si es MySQL, respaldamos en la colección 'backups' de MongoDB
        if (Conexion.isUsarMySQL()) {
            respaldarEnMongo(nombre);
        }

        // 3. Eliminar de la base de datos activa
        if (Conexion.isUsarMongo()) {
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db != null) {
                db.getCollection("producto").deleteOne(Filters.eq("nombre", nombre));
                System.out.println("✅ Producto eliminado de MongoDB.");
            }
        } else {
            String sql = "DELETE FROM producto WHERE nombre = ?";
            try (Connection con = Conexion.getConexion();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                int filas = ps.executeUpdate();
                if (filas > 0) System.out.println("✅ Producto '" + nombre + "' eliminado de la BD.");
            } catch (SQLException e) {
                System.out.println("❌ Error SQL al eliminar: " + e.getMessage());
            }
        }
    }

    /**
     * Limpia el archivo ventas.txt eliminando el bloque del producto.
     */
    private static void eliminarDeArchivo(String nombreProducto) {
        String rutaArchivo = "venta/ventas.txt";
        try {
            Path path = Paths.get(rutaArchivo);
            if (!Files.exists(path)) return;

            String contenido = Files.readString(path);

            // Regex ultra-flexible para capturar el objeto JSON completo que contenga el nombre
            // Explicación: Busca '{', luego cualquier cosa que no sea '}' que incluya "nombre" : "valor"
            String regex = "\\s*\\{[^{}]*?\"nombre\"\\s*:\\s*\"" + Pattern.quote(nombreProducto) + "\"[^{}]*?\\}(,)?";

            Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(contenido);

            if (matcher.find()) {
                String nuevoContenido = matcher.replaceFirst("");

                // --- LIMPIEZA DE ESTRUCTURA JSON ---
                // 1. Si quedó una coma justo antes del cierre del array: [, {..}, ] -> [, {..}]
                nuevoContenido = nuevoContenido.replaceAll(",\\s*]", "\n]");
                // 2. Si el array quedó vacío con una coma: [ , ] -> [ ]
                nuevoContenido = nuevoContenido.replaceAll("\\[\\s*,", "[");
                // 3. Eliminar posibles comas dobles si estaban en el medio
                nuevoContenido = nuevoContenido.replaceAll(",\\s*,", ",");

                Files.writeString(path, nuevoContenido);
                System.out.println("🗑️ '" + nombreProducto + "' borrado físicamente de ventas.txt");
            } else {
                System.out.println("⚠️ No se encontró '" + nombreProducto + "' en el archivo de texto.");
            }
        } catch (IOException e) {
            System.err.println("❌ Error al procesar ventas.txt: " + e.getMessage());
        }
    }

    private static void respaldarEnMongo(String nombre) {
        String sql = "SELECT * FROM producto WHERE nombre = ?";
        try {
            Connection con = Conexion.getConexion();
            if (con == null) return;
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nombre);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        MongoDatabase db = Conexion.getMongoDatabase();
                        if (db != null) {
                            Document backup = new Document("nombre", rs.getString("nombre"))
                                    .append("precio", rs.getDouble("precio"))
                                    .append("total", rs.getInt("total"))
                                    .append("imagen", rs.getString("imagen"))
                                    .append("fecha_eliminacion", new java.util.Date())
                                    .append("origen_sistema", "MySQL_Backup");
                            db.getCollection("backups").insertOne(backup);
                            System.out.println("📥 Backup guardado en MongoDB.");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error creando respaldo: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ACTUALIZAR STOCK e INSERTAR INDIVIDUAL
    // ═════════════════════════════════════════════════════════════════════════
    public static void actualizarStock(String nombre, int nuevoTotal) {
        if (Conexion.isUsarMongo()) {
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db != null) {
                db.getCollection("producto").updateOne(
                        Filters.eq("nombre", nombre),
                        Updates.set("total", nuevoTotal)
                );
            }
            return;
        }

        String sql = "UPDATE producto SET total = ? WHERE nombre = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, nuevoTotal);
            ps.setString(2, nombre);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("❌ Error al actualizar stock: " + e.getMessage());
        }
    }

    public static void insertarProducto(String nombre, double precio, int total, String imagen) {
        if (Conexion.isUsarMongo()) {
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db == null) return;
            db.getCollection("producto").updateOne(
                    Filters.eq("nombre", nombre),
                    Updates.combine(
                            Updates.setOnInsert("nombre", nombre),
                            Updates.setOnInsert("precio", precio),
                            Updates.setOnInsert("total",  total),
                            Updates.setOnInsert("imagen", imagen)
                    ),
                    new UpdateOptions().upsert(true)
            );
            return;
        }

        String sql = Conexion.isUsarMySQL()
                ? "INSERT IGNORE INTO producto (nombre, precio, total, imagen) VALUES (?, ?, ?, ?)"
                : "INSERT OR IGNORE INTO producto (nombre, precio, total, imagen) VALUES (?, ?, ?, ?)";

        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setDouble(2, precio);
            ps.setInt(3, total);
            ps.setString(4, imagen);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("❌ Error SQL al insertar: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CARGA MASIVA DESDE TXT
    // ═════════════════════════════════════════════════════════════════════════
    public static void cargarDesdeVenta() {
        crearTablaProducto();
        String rutaArchivo = "venta/ventas.txt";
        String contenido;

        try {
            contenido = Files.readString(Paths.get(rutaArchivo));
            if (contenido.trim().isEmpty()) return;
        } catch (IOException e) {
            System.out.println("❌ Error de lectura: " + e.getMessage());
            return;
        }

        contenido = contenido.replaceAll("(\\d+),(\\d{2})", "$1.$2");
        Pattern pNombre = Pattern.compile("^\\s*:\\s*\"([^\"]+)\"");
        Pattern pPrecio = Pattern.compile("\"precio\"\\s*:\\s*([\\d.]+)");
        Pattern pTotal  = Pattern.compile("\"total\"\\s*:\\s*(\\d+)");
        Pattern pImagen = Pattern.compile("\"imagen\"\\s*:\\s*\"([^\"]+)\"");

        String[] fragmentos = contenido.split("\"nombre\"");
        int cargados = 0;

        if (Conexion.isUsarMongo()) {
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db == null) return;
            MongoCollection<Document> col = db.getCollection("producto");

            for (int i = 1; i < fragmentos.length; i++) {
                String f = fragmentos[i];
                Matcher mNombre = pNombre.matcher(f);
                Matcher mPrecio = pPrecio.matcher(f);
                Matcher mTotal  = pTotal.matcher(f);
                Matcher mImagen = pImagen.matcher(f);

                if (mNombre.find() && mPrecio.find() && mTotal.find()) {
                    String imagen = "default.png";
                    if (mImagen.find()) {
                        String rutaFull = mImagen.group(1).replace("\\\\", "/");
                        imagen = Paths.get(rutaFull).getFileName().toString();
                    }
                    col.updateOne(
                            Filters.eq("nombre", mNombre.group(1)),
                            Updates.combine(
                                    Updates.setOnInsert("nombre", mNombre.group(1)),
                                    Updates.setOnInsert("precio", Double.parseDouble(mPrecio.group(1))),
                                    Updates.setOnInsert("total",  Integer.parseInt(mTotal.group(1))),
                                    Updates.setOnInsert("imagen", imagen)
                            ),
                            new UpdateOptions().upsert(true)
                    );
                    cargados++;
                }
            }
            return;
        }

        String sql = Conexion.isUsarMySQL()
                ? "INSERT IGNORE INTO producto (nombre, precio, total, imagen) VALUES (?, ?, ?, ?)"
                : "INSERT OR IGNORE INTO producto (nombre, precio, total, imagen) VALUES (?, ?, ?, ?)";

        try (Connection con = Conexion.getConexion()) {
            if (!Conexion.isUsarMySQL()) con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 1; i < fragmentos.length; i++) {
                    String f = fragmentos[i];
                    Matcher mNombre = pNombre.matcher(f);
                    Matcher mPrecio = pPrecio.matcher(f);
                    Matcher mTotal  = pTotal.matcher(f);
                    Matcher mImagen = pImagen.matcher(f);

                    if (mNombre.find() && mPrecio.find() && mTotal.find()) {
                        String imagen = "default.png";
                        if (mImagen.find()) {
                            String rutaFull = mImagen.group(1).replace("\\\\", "/");
                            imagen = Paths.get(rutaFull).getFileName().toString();
                        }
                        ps.setString(1, mNombre.group(1));
                        ps.setDouble(2, Double.parseDouble(mPrecio.group(1)));
                        ps.setInt(3, Integer.parseInt(mTotal.group(1)));
                        ps.setString(4, imagen);
                        ps.executeUpdate();
                        cargados++;
                    }
                }
            }
            if (!Conexion.isUsarMySQL()) con.commit();
        } catch (SQLException e) {
            System.out.println("❌ Error crítico en carga: " + e.getMessage());
        }
    }
}