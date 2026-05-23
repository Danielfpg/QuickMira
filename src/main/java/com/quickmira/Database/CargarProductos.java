package com.quickmira.Database;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.types.ObjectId;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.regex.*;

/**
 * Clase encargada de la persistencia y carga masiva de productos.
 * Integra soporte para SQLite, MySQL y MongoDB con sistema de backups y auditoría por ID.
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

        // Estructura uniforme usando 'id' como Llave Primaria Autoincremental
        String sqlSQLite = "CREATE TABLE IF NOT EXISTS producto (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nombre TEXT, precio REAL, total INTEGER, imagen TEXT);";

        String sqlMySQL = "CREATE TABLE IF NOT EXISTS producto (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "nombre VARCHAR(255), precio DOUBLE, total INT, imagen VARCHAR(255));";

        String sql = Conexion.isUsarMySQL() ? sqlMySQL : sqlSQLite;

        try (Connection con = Conexion.getConexion();
             Statement stmt = con.createStatement()) {
            stmt.execute(sql);
            System.out.println("✅ Tabla 'producto' verificada/creada con columna 'id' en SQL.");
        } catch (SQLException e) {
            System.out.println("❌ Error al crear tabla SQL: " + e.getMessage());
        }
    }
    // ═════════════════════════════════════════════════════════════════════════
    //  METODO AUXILIAR: RESPALDO DE AUDITORÍA ANTES DE CAMBIOS SQL
    // ═════════════════════════════════════════════════════════════════════════
    private static void crearRespaldoAuditoria(String codigo) {
        String sql = "SELECT * FROM producto WHERE id = ?"; // O 'codigo' según tu esquema SQL principal
        try {
            Connection con = Conexion.getConexion();
            if (con == null) return;
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, Integer.parseInt(codigo));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        MongoDatabase db = Conexion.getMongoDatabase();
                        if (db != null) {
                            Document modificado = new Document("codigo_mysql", rs.getInt("id"))
                                    .append("nombre_original", rs.getString("nombre"))
                                    .append("precio_original", rs.getDouble("precio"))
                                    .append("total_original", rs.getInt("total"))
                                    .append("imagen", rs.getString("imagen"))
                                    .append("fecha_modificacion", new java.util.Date())
                                    .append("accion", "edicion_mysql");

                            db.getCollection("modificados").insertOne(modificado);
                            System.out.println("📥 Auditoría: Estado anterior guardado en MongoDB.");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error creando respaldo de auditoría: " + e.getMessage());
        }
    }
    // ═════════════════════════════════════════════════════════════════════════
    //  EDITAR PRODUCTO (Híbrido Relacional / NoSQL)
    // ═════════════════════════════════════════════════════════════════════════
    public static void editarProducto(String id, String nuevoNombre, double nuevoPrecio, int nuevoTotal) {
        if (Conexion.isUsarMongo()) {
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db != null) {
                try {
                    // Validamos si el ID de la tabla es un ObjectId de Mongo válido de 24 caracteres hex
                    org.bson.conversions.Bson filtro;
                    if (id != null && id.length() == 24) {
                        filtro = Filters.eq("_id", new org.bson.types.ObjectId(id));
                    } else {
                        filtro = Filters.eq("id", id);
                    }

                    // Respaldamos el estado previo en la colección 'modificados' antes del cambio
                    Document anterior = db.getCollection("producto").find(filtro).first();
                    if (anterior != null) {
                        Document auditoria = new Document("codigo_mysql", id)
                                .append("nombre_original", anterior.getString("nombre"))
                                .append("precio_original", anterior.get("precio"))
                                .append("total_original", anterior.get("total"))
                                .append("fecha_modificacion", new java.util.Date())
                                .append("accion", "edicion_mongo");
                        db.getCollection("modificados").insertOne(auditoria);
                    }

                    // Actualizamos usando tipos primitivos estándar
                    db.getCollection("producto").updateOne(
                            filtro,
                            Updates.combine(
                                    Updates.set("nombre", nuevoNombre),
                                    Updates.set("precio", nuevoPrecio),
                                    Updates.set("total", nuevoTotal)
                            )
                    );
                    System.out.println("✅ Producto actualizado con éxito en MongoDB.");
                } catch (Exception e) {
                    System.err.println("⚠️ Error al editar en MongoDB: " + e.getMessage());
                }
            }
            return;
        }

        // Bloque SQL para MySQL y SQLite
        crearRespaldoAuditoria(id);
        String sql = "UPDATE producto SET nombre = ?, precio = ?, total = ? WHERE id = ?";
        try (Connection con = Conexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nuevoNombre);
            ps.setDouble(2, nuevoPrecio);
            ps.setInt(3, nuevoTotal);
            ps.setInt(4, Integer.parseInt(id));
            ps.executeUpdate();
            System.out.println("✅ Producto actualizado con éxito en SQL.");
        } catch (Exception e) {
            System.err.println("⚠️ Error al editar producto en SQL: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  RESPALDO DE AUDITORÍA EN MONGO (BUSCA EN SQL POR LA COLUMNA 'id')
    // ═════════════════════════════════════════════════════════════════════════
    private static void respaldarModificadoEnMongo(String idString) {
        String sql = "SELECT * FROM producto WHERE id = ?";
        try {
            Connection con = Conexion.getConexion();
            if (con == null) return;
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, Integer.parseInt(idString));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        MongoDatabase db = Conexion.getMongoDatabase();
                        if (db != null) {
                            Document modificado = new Document("codigo_mysql", String.valueOf(rs.getInt("id")))
                                    .append("nombre_original", rs.getString("nombre"))
                                    .append("precio_original", rs.getDouble("precio"))
                                    .append("total_original", rs.getInt("total"))
                                    .append("imagen", rs.getString("imagen"))
                                    .append("fecha_modificacion", new java.util.Date())
                                    .append("accion", "edicion_sql");

                            db.getCollection("modificados").insertOne(modificado);
                            System.out.println("📥 Auditoría: Estado anterior respaldado en Mongo para el ID: " + idString);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error creando respaldo de auditoría: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CARGA MASIVA DESDE TXT (OMITE EL ID PARA QUE SEA AUTOINCREMENTAL)
    // ═════════════════════════════════════════════════════════════════════════
    public static void guardarProductosBD(String contenido) {
        String[] fragmentos = contenido.split("====");
        Pattern pNombre = Pattern.compile("Nombre:\\s*(.*)");
        Pattern pPrecio = Pattern.compile("Precio:\\s*([0-9.]+)");
        Pattern pTotal  = Pattern.compile("Total:\\s*([0-9]+)");
        Pattern pImagen = Pattern.compile("Ruta:\\s*(.*)");

        int cargados = 0;
        String sql = "INSERT INTO producto (nombre, precio, total, imagen) VALUES (?, ?, ?, ?)";

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
            System.out.println("📊 Masivo: Se insertaron " + cargados + " registros autoincrementales en SQL.");
        } catch (SQLException e) {
            System.out.println("❌ Error crítico en carga masiva SQL: " + e.getMessage());
        }
    }

    // Simulación del disparador desde vista
    public static void cargarDesdeVenta() {
        System.out.println("🔄 Sincronizador de ventas inicializado.");
    }
    // ═════════════════════════════════════════════════════════════════════════
    //  ELIMINAR PRODUCTO (Híbrido Relacional / NoSQL)
    // ═════════════════════════════════════════════════════════════════════════
    public static void eliminarProducto(String idProducto) {
        if (Conexion.isUsarMongo()) {
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db != null) {
                try {
                    org.bson.conversions.Bson filtro;
                    if (idProducto != null && idProducto.length() == 24) {
                        filtro = Filters.eq("_id", new org.bson.types.ObjectId(idProducto));
                    } else {
                        filtro = Filters.eq("id", idProducto);
                    }

                    Document remoto = db.getCollection("producto").find(filtro).first();
                    if (remoto != null) {
                        Document backupBorrados = new Document("codigo_mysql", idProducto)
                                .append("nombre_original", remoto.getString("nombre"))
                                .append("precio_original", remoto.get("precio"))
                                .append("total_original", remoto.get("total"))
                                .append("imagen", remoto.getString("imagen"))
                                .append("fecha_modificacion", new java.util.Date())
                                .append("accion", "ELIMINADO_MONGO");

                        db.getCollection("modificados").insertOne(backupBorrados);
                    }

                    db.getCollection("producto").deleteOne(filtro);
                    System.out.println("🗑 Documento eliminado físicamente de MongoDB.");
                } catch (Exception e) {
                    System.err.println("⚠️ Error al eliminar en MongoDB: " + e.getMessage());
                }
            }
            return;
        }

        // Bloque SQL original para MySQL y SQLite
        String querySeleccion = "SELECT * FROM producto WHERE id = ?";
        String queryEliminar  = "DELETE FROM producto WHERE id = ?";
        try (Connection con = Conexion.getConexion()) {
            if (con == null) return;
            try (PreparedStatement psSel = con.prepareStatement(querySeleccion)) {
                psSel.setInt(1, Integer.parseInt(idProducto));
                try (ResultSet rs = psSel.executeQuery()) {
                    if (rs.next()) {
                        MongoDatabase db = Conexion.getMongoDatabase();
                        if (db != null) {
                            Document backupBorrados = new Document("codigo_mysql", rs.getInt("id"))
                                    .append("nombre_original", rs.getString("nombre"))
                                    .append("precio_original", rs.getDouble("precio"))
                                    .append("total_original", rs.getInt("total"))
                                    .append("imagen", rs.getString("imagen"))
                                    .append("fecha_modificacion", new java.util.Date())
                                    .append("accion", "ELIMINADO_SQL");

                            db.getCollection("modificados").insertOne(backupBorrados);
                        }
                    }
                }
            }
            try (PreparedStatement psDel = con.prepareStatement(queryEliminar)) {
                psDel.setInt(1, Integer.parseInt(idProducto));
                psDel.executeUpdate();
                System.out.println("🗑 Producto extraído del stock relacional con éxito.");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error al eliminar/respaldar producto en SQL: " + e.getMessage());
        }
    }
}