package com.quickmira.Controller;

import com.mongodb.client.MongoDatabase;
import com.quickmira.Database.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.bson.Document;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.regex.*;

public class ControladorInventoryView {

    @FXML private Button inventory, sell, Clouse_sesion, stadistics;
    @FXML private ToggleButton btnSQLite, btnMySQL, btnMongoDB;
    @FXML private Label labelBDActiva, labelTotalProductos, labelStockBajo, labelValorTotal;
    @FXML private TableView<FilaProducto> tablaProductos;
    @FXML private TableColumn<FilaProducto, String>  colCodigo, colNombre, colPrecio, colImagen;
    @FXML private TableColumn<FilaProducto, Integer> colCantidad;

    private final ObservableList<FilaProducto> datos = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurarColumnas();
        configurarSelectorBD();

        CargarProductos.cargarDesdeVenta();
        cargarDatos();
        actualizarFooter();

        inventory.setStyle("-fx-background-color: #15c0a9;");
        sell.setOnAction(e -> navegarA("ui/vista.fxml", sell));
        stadistics.setOnAction(e -> navegarA("ui/estadisticas-view.fxml", stadistics));
        Clouse_sesion.setOnAction(e -> cerrarSesion());
    }

    private void configurarColumnas() {
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colPrecio.setCellValueFactory(new PropertyValueFactory<>("precioFormateado"));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colImagen.setCellValueFactory(new PropertyValueFactory<>("rutaImagen"));
        tablaProductos.setItems(datos);
    }

    private void configurarSelectorBD() {
        ToggleGroup grupo = new ToggleGroup();
        btnSQLite.setToggleGroup(grupo);
        btnMySQL.setToggleGroup(grupo);
        btnMongoDB.setToggleGroup(grupo);

        // Selección inicial según modo activo
        switch (Conexion.getModoActivo()) {
            case Conexion.MODO_MYSQL   -> btnMySQL.setSelected(true);
            case Conexion.MODO_MONGODB -> btnMongoDB.setSelected(true);
            default                    -> btnSQLite.setSelected(true);
        }
        actualizarUISelector();

        grupo.selectedToggleProperty().addListener((obs, ant, nuevo) -> {
            if (nuevo == null) { ant.setSelected(true); return; }

            int modo;
            if      (nuevo == btnMySQL)   modo = Conexion.MODO_MYSQL;
            else if (nuevo == btnMongoDB) modo = Conexion.MODO_MONGODB;
            else                          modo = Conexion.MODO_SQLITE;

            Conexion.setModo(modo);
            CargarProductos.cargarDesdeVenta();
            cargarDatos();
            actualizarFooter();
            actualizarUISelector();
        });
    }

    private void cargarDatos() {
        datos.clear();

        // ── MongoDB ──────────────────────────────────────────────────────────
        if (Conexion.isUsarMongo()) {
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db == null) {
                System.out.println("⚠️ MongoDB no disponible.");
                return;
            }
            int i = 1;
            for (Document doc : db.getCollection("producto").find()) {
                datos.add(new FilaProducto(
                        "PRD-" + (i++),
                        doc.getString("nombre"),
                        doc.getDouble("precio") != null ? doc.getDouble("precio") : 0.0,
                        doc.getInteger("total")  != null ? doc.getInteger("total")  : 0,
                        doc.getString("imagen")
                ));
            }
            return;
        }

        // ── JDBC (MySQL / SQLite) ────────────────────────────────────────────
        try (Connection con = Conexion.getConexion();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM producto")) {
            int i = 1;
            while (rs.next()) {
                datos.add(new FilaProducto(
                        "PRD-" + (i++),
                        rs.getString("nombre"),
                        rs.getDouble("precio"),
                        rs.getInt("total"),
                        rs.getString("imagen")
                ));
            }
        } catch (Exception e) {
            System.out.println("⚠️ BD no disponible, revisa la conexión.");
        }
    }

    @FXML
    private void handleEliminar() {
        FilaProducto sel = tablaProductos.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        CargarProductos.eliminarProducto(sel.getNombre());
        datos.remove(sel);
        actualizarFooter();
    }

    private void actualizarUISelector() {
        // Resetear estilos
        String apagado  = "-fx-background-color: #cccccc; -fx-text-fill: #333333;";
        String encendido = "-fx-background-color: #15c0a9; -fx-text-fill: white; -fx-font-weight: bold;";

        btnSQLite.setStyle(apagado);
        btnMySQL.setStyle(apagado);
        btnMongoDB.setStyle(apagado);

        switch (Conexion.getModoActivo()) {
            case Conexion.MODO_MYSQL   -> { btnMySQL.setStyle(encendido);   if (labelBDActiva != null) labelBDActiva.setText("BD: MySQL"); }
            case Conexion.MODO_MONGODB -> { btnMongoDB.setStyle(encendido); if (labelBDActiva != null) labelBDActiva.setText("BD: MongoDB"); }
            default                    -> { btnSQLite.setStyle(encendido);  if (labelBDActiva != null) labelBDActiva.setText("BD: SQLite"); }
        }
    }

    private void actualizarFooter() {
        labelTotalProductos.setText("Total: " + datos.size());
        double valor = datos.stream().mapToDouble(p -> p.getPrecio() * p.getCantidad()).sum();
        labelValorTotal.setText(String.format("Valor: $%,.0f", valor));
    }

    private void navegarA(String fxml, Button origen) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/quickmira/" + fxml));
            Parent root = loader.load();
            Stage stage = (Stage) origen.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            alerta("Error al cargar vista: " + fxml);
        }
    }

    private void alerta(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg);
        a.showAndWait();
    }

    @FXML
    private void handleRecargar() {
        System.out.println("🔄 Recargando datos...");
        CargarProductos.cargarDesdeVenta();
        cargarDatos();
        actualizarFooter();
    }

    private void cerrarSesion() {
        ((Stage) Clouse_sesion.getScene().getWindow()).close();
    }

    // ── Clase interna FilaProducto ────────────────────────────────────────────
    public static class FilaProducto {
        private final String codigo, nombre, rutaImagen;
        private final double precio;
        private final int    cantidad;

        public FilaProducto(String c, String n, double p, int can, String img) {
            this.codigo = c; this.nombre = n; this.precio = p; this.cantidad = can; this.rutaImagen = img;
        }
        public String getCodigo()          { return codigo; }
        public String getNombre()          { return nombre; }
        public double getPrecio()          { return precio; }
        public int    getCantidad()        { return cantidad; }
        public String getRutaImagen()      { return rutaImagen; }
        public String getPrecioFormateado(){ return String.format("$%,.2f", precio); }
    }
}