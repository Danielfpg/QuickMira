package com.quickmira;

import com.quickmira.Database.CargarProductos;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.scene.layout.VBox;

public class Main extends Application {
    public Main() {
    }

    public void start(Stage primaryStage) throws Exception {
        CargarProductos.cargarDesdeVenta();
        FXMLLoader loader = new FXMLLoader(this.getClass().getResource("/com/quickmira/ui/login.fxml"));
        VBox raiz = (VBox) loader.load();
        Scene escena = new Scene(raiz, (double)350.0F, (double)300.0F);
        primaryStage.setTitle("QuicMira");
        primaryStage.setScene(escena);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
