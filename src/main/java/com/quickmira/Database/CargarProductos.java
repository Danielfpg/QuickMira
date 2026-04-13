package com.quickmira.Database;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.regex.*;

public class CargarProductos {

    public static void cargarDesdeVenta() {
        String rutaArchivo = "venta/ventas.txt";
        String contenido;

        try {
            contenido = Files.readString(Paths.get(rutaArchivo));
            // Si el archivo está vacío o no tiene datos
            if (contenido.trim().isEmpty()) {
                System.out.println("⚠️ El archivo ventas.txt está vacío.");
                return;
            }
        } catch (IOException e) {
            System.out.println("❌ Error de lectura: " + e.getMessage());
            return;
        }

        // Normalizar precios: 200,00 -> 200.00
        contenido = contenido.replaceAll("(\\d+),(\\d{2})", "$1.$2");

        Connection con = Conexion.getConexion();
        String sql = "INSERT INTO producto (nombre, precio, total, imagen) VALUES (?, ?, ?, ?)";

        // NUEVA ESTRATEGIA: Dividimos el texto por la palabra "nombre",
        // que es el inicio real de cada producto en tu JSON.
        String[] fragmentos = contenido.split("\"nombre\"");
        int cargados = 0;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            // El primer fragmento suele ser basura antes del primer producto
            for (int i = 1; i < fragmentos.length; i++) {
                String f = fragmentos[i];

                // Regex ultra-simples para extraer datos de cada fragmento
                Pattern pNombre = Pattern.compile("^\\s*:\\s*\"([^\"]+)\"");
                Pattern pPrecio = Pattern.compile("\"precio\"\\s*:\\s*([\\d.]+)");
                Pattern pTotal  = Pattern.compile("\"total\"\\s*:\\s*(\\d+)");
                Pattern pImagen = Pattern.compile("\"imagen\"\\s*:\\s*\"([^\"]+)\"");

                Matcher mNombre = pNombre.matcher(f);
                Matcher mPrecio = pPrecio.matcher(f);
                Matcher mTotal  = pTotal.matcher(f);
                Matcher mImagen = pImagen.matcher(f);

                if (mNombre.find() && mPrecio.find() && mTotal.find()) {
                    String nombre = mNombre.group(1);
                    double precio = Double.parseDouble(mPrecio.group(1));
                    int total     = Integer.parseInt(mTotal.group(1));

                    String imagen = "default.png";
                    if (mImagen.find()) {
                        String rutaFull = mImagen.group(1).replace("\\\\", "/");
                        imagen = Paths.get(rutaFull).getFileName().toString();
                    }

                    ps.setString(1, nombre);
                    ps.setDouble(2, precio);
                    ps.setInt(3, total);
                    ps.setString(4, imagen);
                    ps.executeUpdate();
                    cargados++;

                    System.out.println("✅ Insertado en BD: " + nombre);
                } else {
                    System.out.println("⚠️ Fragmento " + i + " ignorado (faltan campos)");
                }
            }
            System.out.println("🚀 Carga finalizada. Total: " + cargados);

        } catch (SQLException e) {
            System.out.println("❌ Error SQL: " + e.getMessage());
        }
    }
}