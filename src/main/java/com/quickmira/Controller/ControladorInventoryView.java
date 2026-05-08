package com.quickmira.Controller;

import com.quickmira.Database.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.regex.*;

public class ControladorInventoryView {

    @FXML private Button inventory, sell, Clouse_sesion, stadistics;
    @FXML private ToggleButton btnSQLite, btnMySQL;
    @FXML private Label labelBDActiva, labelTotalProductos, labelStockBajo, labelValorTotal;
    @FXML private TableView<FilaProducto> tablaProductos;
    @FXML private TableColumn<FilaProducto, String> colCodigo, colNombre, colPrecio, colImagen;
    @FXML private TableColumn<FilaProducto, Integer> colCantidad;

    private final ObservableList<FilaProducto> datos = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        configurarColumnas();
        configurarSelectorBD();

        // Carga inicial
        CargarProductos.cargarDesdeVenta();
        cargarDatos();
        actualizarFooter();

        inventory.setStyle("-fx-background-color: #15c0a9;");
        Clouse_sesion.setOnAction(e -> ((javafx.stage.Stage)Clouse_sesion.getScene().getWindow()).close());
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

        if (Conexion.isUsarMySQL()) btnMySQL.setSelected(true); else btnSQLite.setSelected(true);
        actualizarUISelector();

        grupo.selectedToggleProperty().addListener((obs, ant, nuevo) -> {
            if (nuevo == null) { ant.setSelected(true); return; }
            boolean elegirMySQL = (nuevo == btnMySQL);

            Conexion.setUsarMySQL(elegirMySQL);
            CargarProductos.cargarDesdeVenta();
            cargarDatos();
            actualizarFooter();
            actualizarUISelector();
        });
    }

    private void cargarDatos() {
        datos.clear();
        try (Connection con = Conexion.getConexion();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM producto")) {
            int i = 1;
            while (rs.next()) {
                datos.add(new FilaProducto("PRD-"+(i++), rs.getString("nombre"), rs.getDouble("precio"), rs.getInt("total"), rs.getString("imagen")));
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
        btnMySQL.setStyle(Conexion.isUsarMySQL() ? "-fx-background-color: #15c0a9;" : "");
        btnSQLite.setStyle(!Conexion.isUsarMySQL() ? "-fx-background-color: #15c0a9;" : "");
        if(labelBDActiva != null) labelBDActiva.setText("BD: " + (Conexion.isUsarMySQL()?"MySQL":"SQLite"));
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
        CargarProductos.cargarDesdeVenta(); // Sincroniza el TXT con la BD
        cargarDatos(); // Refresca la tabla
        actualizarFooter(); // Refresca los totales
    }
    // --- Clase interna FilaProducto ---
    public static class FilaProducto {
        private String codigo, nombre, rutaImagen;
        private double precio;
        private int cantidad;
        public FilaProducto(String c, String n, double p, int can, String img) {
            this.codigo=c; this.nombre=n; this.precio=p; this.cantidad=can; this.rutaImagen=img;
        }
        public String getCodigo() { return codigo; }
        public String getNombre() { return nombre; }
        public double getPrecio() { return precio; }
        public int getCantidad() { return cantidad; }
        public String getRutaImagen() { return rutaImagen; }
        public String getPrecioFormateado() { return String.format("$%,.2f", precio); }
    }
    private void cerrarSesion() {
        ((Stage) Clouse_sesion.getScene().getWindow()).close();
    }
}