package com.quickmira.Database;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.regex.*;

public class CargarProductos {

    // ═════════════════════════════════════════════════════════════════════════
    //  CREAR TABLA
    // ═════════════════════════════════════════════════════════════════════════
    public static void crearTablaProducto() {
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
    //  ELIMINAR PRODUCTO (Requerido por el Controlador)
    // ═════════════════════════════════════════════════════════════════════════
    public static void eliminarProducto(String nombre) {
        String sql = "DELETE FROM producto WHERE nombre = ?";
        try {
            Connection con = Conexion.getConexion();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, nombre);
            ps.executeUpdate();
            ps.close(); // Cerramos el statement, mantenemos la conexión
            System.out.println("✅ Producto eliminado de BD: " + nombre);
        } catch (SQLException e) {
            System.out.println("❌ Error al eliminar de BD: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ACTUALIZAR STOCK
    // ═════════════════════════════════════════════════════════════════════════
    public static void actualizarStock(String nombre, int nuevoTotal) {
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

        String sql = Conexion.isUsarMySQL()
                ? "INSERT IGNORE INTO producto (nombre, precio, total, imagen) VALUES (?, ?, ?, ?)"
                : "INSERT OR IGNORE INTO producto (nombre, precio, total, imagen) VALUES (?, ?, ?, ?)";

        String[] fragmentos = contenido.split("\"nombre\"");
        int cargados = 0;

        try {
            Connection con = Conexion.getConexion();
            // Optimización para SQLite
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