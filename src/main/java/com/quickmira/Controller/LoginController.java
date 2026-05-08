package com.quickmira.Controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
public class LoginController {

    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblMensaje;

    @FXML
    private void login() throws Exception {

        String user = txtUsuario.getText();
        String pass = txtPassword.getText();

        if(user.equals("admin") && pass.equals("1234")){

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/quickmira/ui/vista.fxml"));

            Stage stage = new Stage();
            stage.setScene(new Scene(loader.load()));
            stage.setTitle("MetaSistema - Menú");
            stage.show();

            // Cerrar ventana actual
            Stage actual = (Stage) txtUsuario.getScene().getWindow();
            actual.close();

        }else{
            lblMensaje.setText("Usuario o contraseña incorrectos");
        }
    }
}