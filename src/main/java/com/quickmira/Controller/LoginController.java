package com.quickmira.Controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {
    private void inicializarBD() {
        String sql = "CREATE TABLE IF NOT EXISTS operadores (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nombre TEXT NOT NULL UNIQUE," +
                "telefono TEXT," +
                "email TEXT," +
                "contrasena TEXT NOT NULL" +
                ");";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.executeUpdate();

            // Insertar admin si no existe
            String insertAdmin = "INSERT OR IGNORE INTO operadores (nombre, telefono, email, contrasena) " +
                    "VALUES ('admin', '0000000000', 'admin@quickmira.com', '1234')";
            try (PreparedStatement pstmt2 = conn.prepareStatement(insertAdmin)) {
                pstmt2.executeUpdate();
            }

        } catch (SQLException e) {
            System.out.println("Error inicializando BD: " + e.getMessage());
        }

    }

    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblMensaje;

    // Método para conectarse a la base de datos SQLite
    private Connection connect() {
        String url = "jdbc:sqlite:quicmira.db"; // Cambia a quicmira2.db si es necesario
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return conn;
    }

    // Método para validar login contra la tabla operadores
    private boolean validarLogin(String user, String pass) {
        String sql = "SELECT * FROM operadores WHERE nombre = ? AND contrasena = ?";
        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user);
            pstmt.setString(2, pass);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return true; // Usuario válido
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return false; // Usuario inválido
    }

    @FXML
    private void login() throws Exception {
        inicializarBD();
        String user = txtUsuario.getText();
        String pass = txtPassword.getText();

        if (validarLogin(user, pass)) {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/quickmira/ui/vista.fxml"));

            Stage stage = new Stage();
            stage.setScene(new Scene(loader.load()));
            stage.setTitle("MetaSistema - Menú");
            stage.show();

            // Cerrar ventana actual
            Stage actual = (Stage) txtUsuario.getScene().getWindow();
            actual.close();

        } else {
            lblMensaje.setText("Usuario o contraseña incorrectos");
        }
    }
}
