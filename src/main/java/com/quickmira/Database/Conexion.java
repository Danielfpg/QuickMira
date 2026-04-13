package com.quickmira.Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Conexion {

    // Cambia estos datos por los del servidor de clase
    private static final String URL = "jdbc:mysql://172.30.16.76:3306/quickmira";
    private static final String USER = "dfparedes11";
    private static final String PASSWORD = "67001411";

    private static Connection connection = null;

    public static Connection getConexion() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("✅ Conexión exitosa");
            }
        } catch (SQLException e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
        return connection;
    }
}