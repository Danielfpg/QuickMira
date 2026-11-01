package com.quickmira.Database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Gestiona las conexiones a MySQL, SQLite y MongoDB.
 * Las credenciales se cargan de forma segura desde db.properties.
 *
 * Modo activo:
 * 0 = SQLite  (por defecto)
 * 1 = MySQL
 * 2 = MongoDB
 */
public class Conexion {

    // ── Credenciales (Cargadas dinámicamente) ────────────────────────────────
    private static String MYSQL_URL;
    private static String MYSQL_USER;
    private static String MYSQL_PASS;
    
    private static String MONGO_HOST;
    private static int MONGO_PORT;
    private static String MONGO_DB;

    private static final String SQLITE_URL = "jdbc:sqlite:quicmira2.db";

    // ── Carga de configuración ───────────────────────────────────────────────
    static {
        Properties props = new Properties();
        try (InputStream input = new FileInputStream("db.properties")) {
            props.load(input);
            
            MYSQL_URL  = props.getProperty("mysql.url");
            MYSQL_USER = props.getProperty("mysql.user");
            MYSQL_PASS = props.getProperty("mysql.pass");
            
            MONGO_HOST = props.getProperty("mongo.host");
            MONGO_PORT = Integer.parseInt(props.getProperty("mongo.port", "27017"));
            MONGO_DB   = props.getProperty("mongo.database");
            
        } catch (IOException e) {
            System.err.println("⚠️ Advertencia: No se encontró el archivo db.properties en la raíz del proyecto. Las conexiones a MySQL/MongoDB fallarán.");
        } catch (NumberFormatException e) {
            System.err.println("⚠️ Advertencia: El puerto de MongoDB en db.properties no es un número válido.");
        }
    }

    // ── Estado ───────────────────────────────────────────────────────────────
    public static final int MODO_SQLITE  = 0;
    public static final int MODO_MYSQL   = 1;
    public static final int MODO_MONGODB = 2;

    private static int modoActivo = MODO_SQLITE;

    // Conexiones JDBC (MySQL / SQLite)
    private static Connection jdbcConnection = null;

    // Conexión MongoDB
    private static MongoClient    mongoClient   = null;
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

    /** Vincula la llamada de 'setModoActivo' directamente con el flujo de 'setModo'. */
    public static void setModoActivo(int nuevoModo) {
        setModo(nuevoModo);
    }

    /** Compatibilidad hacia atrás: establece MySQL o SQLite. */
    public static void setUsarMySQL(boolean valor) {
        setModo(valor ? MODO_MYSQL : MODO_SQLITE);
    }

    // ── JDBC (MySQL / SQLite) ────────────────────────────────────────────────
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
    public static MongoDatabase getMongoDatabase() {
        try {
            if (mongoClient == null) {
                String uri = "mongodb://" + MONGO_HOST + ":" + MONGO_PORT;
                mongoClient   = MongoClients.create(uri);
                mongoDatabase = mongoClient.getDatabase(MONGO_DB);
                System.out.println("✅ Conexión establecida con MongoDB");
            }
        } catch (Exception e) {
            System.err.println("❌ Error al conectar con MongoDB: " + e.getMessage());
        }
        return mongoDatabase;
    }

    // ── Cierre ───────────────────────────────────────────────────────────────
    public static void cerrarConexion() {
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
