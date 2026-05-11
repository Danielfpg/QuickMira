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

public class CargarProductos {

    // ═════════════════════════════════════════════════════════════════════════
    //  CREAR TABLA / COLECCIÓN
    // ═════════════════════════════════════════════════════════════════════════
    public static void crearTablaProducto() {
        if (Conexion.isUsarMongo()) {
            // MongoDB crea la colección automáticamente al insertar; no hace falta nada
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db != null) {
                // Asegurar que la colección exista
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

        try {
            Connection con = Conexion.getConexion();
            Statement stmt = con.createStatement();
            stmt.execute(sql);
            stmt.close();
            System.out.println("✅ Tabla 'producto' lista.");
        } catch (SQLException e) {
            System.out.println("❌ Error al crear tabla: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ELIMINAR PRODUCTO
    // ═════════════════════════════════════════════════════════════════════════
    public static void eliminarProducto(String nombre) {
        if (Conexion.isUsarMongo()) {
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db == null) return;
            db.getCollection("producto").deleteOne(Filters.eq("nombre", nombre));
            System.out.println("✅ Producto eliminado de MongoDB: " + nombre);
            return;
        }

        String sql = "DELETE FROM producto WHERE nombre = ?";
        try {
            Connection con = Conexion.getConexion();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, nombre);
            ps.executeUpdate();
            ps.close();
            System.out.println("✅ Producto eliminado de BD: " + nombre);
        } catch (SQLException e) {
            System.out.println("❌ Error al eliminar de BD: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ACTUALIZAR STOCK
    // ═════════════════════════════════════════════════════════════════════════
    public static void actualizarStock(String nombre, int nuevoTotal) {
        if (Conexion.isUsarMongo()) {
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db == null) return;
            db.getCollection("producto").updateOne(
                    Filters.eq("nombre", nombre),
                    Updates.set("total", nuevoTotal)
            );
            System.out.println("✅ Stock actualizado en MongoDB: " + nombre);
            return;
        }

        String sql = "UPDATE producto SET total = ? WHERE nombre = ?";
        try {
            Connection con = Conexion.getConexion();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt(1, nuevoTotal);
            ps.setString(2, nombre);
            ps.executeUpdate();
            ps.close();
            System.out.println("✅ Stock actualizado en BD: " + nombre);
        } catch (SQLException e) {
            System.out.println("❌ Error al actualizar stock: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  INSERTAR INDIVIDUAL
    // ═════════════════════════════════════════════════════════════════════════
    public static void insertarProducto(String nombre, double precio, int total, String imagen) {
        if (Conexion.isUsarMongo()) {
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db == null) return;
            MongoCollection<Document> col = db.getCollection("producto");
            // Upsert: inserta si no existe, no modifica si ya existe
            col.updateOne(
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

        try {
            Connection con = Conexion.getConexion();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, nombre);
            ps.setDouble(2, precio);
            ps.setInt(3, total);
            ps.setString(4, imagen);
            ps.executeUpdate();
            ps.close();
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

        // ── MongoDB ──────────────────────────────────────────────────────────
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
                    String nombre = mNombre.group(1);
                    double precio = Double.parseDouble(mPrecio.group(1));
                    int    total  = Integer.parseInt(mTotal.group(1));

                    col.updateOne(
                            Filters.eq("nombre", nombre),
                            Updates.combine(
                                    Updates.setOnInsert("nombre", nombre),
                                    Updates.setOnInsert("precio", precio),
                                    Updates.setOnInsert("total",  total),
                                    Updates.setOnInsert("imagen", imagen)
                            ),
                            new UpdateOptions().upsert(true)
                    );
                    cargados++;
                }
            }
            System.out.println("🚀 Carga masiva MongoDB exitosa. Items: " + cargados);
            return;
        }

        // ── JDBC (MySQL / SQLite) ─────────────────────────────────────────────
        String sql = Conexion.isUsarMySQL()
                ? "INSERT IGNORE INTO producto (nombre, precio, total, imagen) VALUES (?, ?, ?, ?)"
                : "INSERT OR IGNORE INTO producto (nombre, precio, total, imagen) VALUES (?, ?, ?, ?)";

        try {
            Connection con = Conexion.getConexion();
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

            if (!Conexion.isUsarMySQL()) {
                con.commit();
                con.setAutoCommit(true);
            }
            System.out.println("🚀 Carga masiva exitosa. Items: " + cargados);

        } catch (SQLException e) {
            System.out.println("❌ Error crítico en carga: " + e.getMessage());
        }
    }
}