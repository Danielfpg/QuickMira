package com.quickmira.Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Conexion {

    private static final String MYSQL_URL  = "jdbc:mysql://172.30.16.165:3306/quickmira";
    private static final String USER       = "dfparedes11";
    private static final String PASSWORD   = "67001411";
    private static final String SQLITE_URL = "jdbc:sqlite:quicmira2.db";

    private static Connection connection = null;
    private static boolean usarMySQL     = false;

    public static boolean isUsarMySQL() { return usarMySQL; }

    public static void setUsarMySQL(boolean valor) {
        if (valor != usarMySQL) {
            usarMySQL = valor;
            cerrarConexion();
        }
    }

    public static Connection getConexion() {
        try {
            // Verificamos si la conexión es nula o si ya no es válida
            if (connection == null || connection.isClosed()) {
                if (usarMySQL) {
                    // Carga explícita del driver de MySQL
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    connection = DriverManager.getConnection(MYSQL_URL, USER, PASSWORD);
                    System.out.println("✅ Conexión establecida con MySQL");
                } else {
                    // Carga explícita del driver de SQLite
                    Class.forName("org.sqlite.JDBC");
                    connection = DriverManager.getConnection(SQLITE_URL);
                    System.out.println("✅ Conexión establecida con SQLite");
                }
            }
        } catch (ClassNotFoundException e) {
            System.err.println("❌ Error: No se encontró el Driver de la BD: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("❌ Error de SQL al conectar: " + e.getMessage());
            connection = null;
        }
        return connection;
    }

    public static void cerrarConexion() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("🔌 Conexión cerrada.");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al cerrar conexión: " + e.getMessage());
        } finally {
            connection = null;
        }
    }
}