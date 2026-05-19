package com.quickmira.Database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Gestiona las conexiones a MySQL, SQLite y MongoDB.
 *
 * Modo activo:
 *   0 = SQLite  (por defecto)
 *   1 = MySQL
 *   2 = MongoDB
 */
public class Conexion {

    // ── MySQL ────────────────────────────────────────────────────────────────
    private static final String MYSQL_URL  = "jdbc:mysql://172.30.16.104:3306/quickmira";
    private static final String MYSQL_USER = "dfparedes11";
    private static final String MYSQL_PASS = "67001411";

    // ── SQLite ───────────────────────────────────────────────────────────────
    private static final String SQLITE_URL = "jdbc:sqlite:quicmira2.db";

    // ── MongoDB ──────────────────────────────────────────────────────────────
    // ⚠️  Cambia estos valores por la IP/puerto real del servidor de la universidad
    private static final String MONGO_HOST = "172.30.16.104";   // ← IP del servidor
    private static final int    MONGO_PORT = 27017;            // ← Puerto (default 27017)
    private static final String MONGO_DB   = "quickmira";      // ← Nombre de la base de datos

    // ── Estado ───────────────────────────────────────────────────────────────
    public static final int MODO_SQLITE  = 0;
    public static final int MODO_MYSQL   = 1;
    public static final int MODO_MONGODB = 2;

    private static int modoActivo = MODO_SQLITE;

    // Conexiones JDBC (MySQL / SQLite)
    private static Connection jdbcConnection = null;

    // Conexión MongoDB
    private static MongoClient   mongoClient   = null;
    private static MongoDatabase mongoDatabase = null;

    // ── Getters de modo ──────────────────────────────────────────────────────
    public static int getModoActivo()   { return modoActivo; }
    public static boolean isUsarMySQL() { return modoActivo == MODO_MYSQL; }
    public static boolean isUsarMongo() { return modoActivo == MODO_MONGODB; }

    /** Cambia el motor activo y cierra la conexión previa. */
    public static void setModo(int nuevoModo) {
        if (nuevoModo != modoActivo) {
            cerrarConexion();
            modoActivo = nuevoModo;
        }
    }

    /** Compatibilidad hacia atrás: establece MySQL o SQLite. */
    public static void setUsarMySQL(boolean valor) {
        setModo(valor ? MODO_MYSQL : MODO_SQLITE);
    }

    // ── JDBC (MySQL / SQLite) ────────────────────────────────────────────────
    /**
     * Devuelve una Connection JDBC activa (MySQL o SQLite).
     * Retorna null si el modo activo es MongoDB.
     */
    public static Connection getConexion() {
        if (modoActivo == MODO_MONGODB) return null;

        try {
            if (jdbcConnection == null || jdbcConnection.isClosed()) {
                if (modoActivo == MODO_MYSQL) {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    jdbcConnection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASS);
                    System.out.println("✅ Conexión establecida con MySQL");
                } else {
                    Class.forName("org.sqlite.JDBC");
                    jdbcConnection = DriverManager.getConnection(SQLITE_URL);
                    System.out.println("✅ Conexión establecida con SQLite");
                }
            }
        } catch (ClassNotFoundException e) {
            System.err.println("❌ Driver no encontrado: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("❌ Error SQL al conectar: " + e.getMessage());
            jdbcConnection = null;
        }
        return jdbcConnection;
    }

    // ── MongoDB ──────────────────────────────────────────────────────────────
    /**
     * Devuelve el MongoDatabase activo.
     * Retorna null si el modo activo no es MongoDB.
     */
    public static MongoDatabase getMongoDatabase() {
        // Eliminamos la línea: if (modoActivo != MODO_MONGODB) return null;
        try {
            if (mongoClient == null) {
                String uri = "mongodb://" + MONGO_HOST + ":" + MONGO_PORT;
                mongoClient   = MongoClients.create(uri);
                mongoDatabase = mongoClient.getDatabase(MONGO_DB);
                System.out.println("✅ Conexión establecida con MongoDB para operaciones internas");
            }
        } catch (Exception e) {
            System.err.println("❌ Error al conectar con MongoDB: " + e.getMessage());
        }
        return mongoDatabase;
    }

    // ── Cierre ───────────────────────────────────────────────────────────────
    public static void cerrarConexion() {
        // Cerrar JDBC
        try {
            if (jdbcConnection != null && !jdbcConnection.isClosed()) {
                jdbcConnection.close();
                System.out.println("🔌 Conexión JDBC cerrada.");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al cerrar JDBC: " + e.getMessage());
        } finally {
            jdbcConnection = null;
        }

        // Cerrar MongoDB
        try {
            if (mongoClient != null) {
                mongoClient.close();
                System.out.println("🔌 Conexión MongoDB cerrada.");
            }
        } catch (Exception e) {
            System.err.println("❌ Error al cerrar MongoDB: " + e.getMessage());
        } finally {
            mongoClient   = null;
            mongoDatabase = null;
        }
    }
}