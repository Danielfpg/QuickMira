package com.quickmira.Controller;

import com.mongodb.client.MongoDatabase;
import com.quickmira.Database.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.bson.Document;

import java.io.IOException;
import java.sql.*;

public class ControladorInventoryView {

    @FXML private Button inventory, sell, Clouse_sesion, stadistics;
    @FXML private Button btnBackup;
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
        configurarNavegacionSidemenu();

        CargarProductos.cargarDesdeVenta();
        CargarProductos.crearTablaProducto();
        cargarDatos();
        actualizarFooter();
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

        if (btnSQLite != null) btnSQLite.setToggleGroup(grupo);
        if (btnMySQL != null) btnMySQL.setToggleGroup(grupo);
        if (btnMongoDB != null) btnMongoDB.setToggleGroup(grupo);

        int modoActual = Conexion.getModoActivo();
        if (modoActual == 0 && btnSQLite != null) {
            btnSQLite.setSelected(true);
            actualizarEstiloBotonesBD(btnSQLite);
            if (labelBDActiva != null) labelBDActiva.setText("SQLite Activo");
        } else if (modoActual == 1 && btnMySQL != null) {
            btnMySQL.setSelected(true);
            actualizarEstiloBotonesBD(btnMySQL);
            if (labelBDActiva != null) labelBDActiva.setText("MySQL Activa");
        } else if (modoActual == 2 && btnMongoDB != null) {
            btnMongoDB.setSelected(true);
            actualizarEstiloBotonesBD(btnMongoDB);
            if (labelBDActiva != null) labelBDActiva.setText("MongoDB Activa");
        }

        grupo.selectedToggleProperty().addListener((obs, viejo, nuevo) -> {
            if (nuevo == null) {
                if (viejo != null) viejo.setSelected(true);
                return;
            }

            ToggleButton botonSeleccionado = (ToggleButton) nuevo;
            actualizarEstiloBotonesBD(botonSeleccionado);

            if (nuevo.equals(btnSQLite)) {
                Conexion.setModoActivo(0);
                if (labelBDActiva != null) labelBDActiva.setText("SQLite Activo");
            }
            else if (nuevo.equals(btnMySQL)) {
                Conexion.setModoActivo(1);
                if (labelBDActiva != null) labelBDActiva.setText("MySQL Activa");
            }
            else if (nuevo.equals(btnMongoDB)) {
                Conexion.setModoActivo(2);
                if (labelBDActiva != null) labelBDActiva.setText("MongoDB Activa");
            }

            CargarProductos.crearTablaProducto();
            cargarDatos();
            actualizarFooter();
        });
    }

    private void actualizarEstiloBotonesBD(ToggleButton seleccionado) {
        if (btnSQLite != null) btnSQLite.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: black;");
        if (btnMySQL != null) btnMySQL.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: black;");
        if (btnMongoDB != null) btnMongoDB.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: black;");

        if (seleccionado.equals(btnSQLite)) {
            seleccionado.setStyle("-fx-background-color: #003b57; -fx-text-fill: white; -fx-font-weight: bold;");
        } else if (seleccionado.equals(btnMySQL)) {
            seleccionado.setStyle("-fx-background-color: #e49316; -fx-text-fill: white; -fx-font-weight: bold;");
        } else if (seleccionado.equals(btnMongoDB)) {
            seleccionado.setStyle("-fx-background-color: #13aa52; -fx-text-fill: white; -fx-font-weight: bold;");
        }
    }

    private void configurarNavegacionSidemenu() {
        if (inventory != null) inventory.setStyle("-fx-background-color: #15c0a9;");

        if (sell != null) sell.setOnAction(e -> navegarA("ui/vista.fxml", sell));
        if (stadistics != null) stadistics.setOnAction(e -> navegarA("ui/estadisticas-view.fxml", stadistics));
        if (Clouse_sesion != null) Clouse_sesion.setOnAction(e -> {
            Stage stage = (Stage) Clouse_sesion.getScene().getWindow();
            stage.close();
        });
    }

    private void cargarDatos() {
        datos.clear();

        // ═════════════════════════════════════════════════════════════════════════
        // CASO 1: MONGODB ACTIVO
        // ═════════════════════════════════════════════════════════════════════════
        if (Conexion.isUsarMongo()) {
            MongoDatabase db = Conexion.getMongoDatabase();
            if (db == null) return;

            try {
                for (Document doc : db.getCollection("producto").find()) {
                    // 1. Extraer ID seguro sin importar el nombre de la clave
                    String idDetectado = "N/A";
                    if (doc.containsKey("codigo") && doc.get("codigo") != null) {
                        idDetectado = String.valueOf(doc.get("codigo"));
                    } else if (doc.containsKey("id") && doc.get("id") != null) {
                        idDetectado = String.valueOf(doc.get("id"));
                    } else if (doc.get("_id") != null) {
                        idDetectado = doc.get("_id").toString();
                    }

                    // 2. Extraer Precio de forma ultra-segura (Evita que falle si es entero o double)
                    double precioDetectado = 0.0;
                    if (doc.get("precio") != null) {
                        if (doc.get("precio") instanceof Number) {
                            precioDetectado = ((Number) doc.get("precio")).doubleValue();
                        }
                    }

                    // 3. Extraer cantidad de forma segura
                    int cantidadDetectada = 0;
                    if (doc.get("total") != null && doc.get("total") instanceof Number) {
                        cantidadDetectada = ((Number) doc.get("total")).intValue();
                    } else if (doc.get("cantidad") != null && doc.get("cantidad") instanceof Number) {
                        cantidadDetectada = ((Number) doc.get("cantidad")).intValue();
                    }

                    datos.add(new FilaProducto(
                            idDetectado,
                            doc.getString("nombre") != null ? doc.getString("nombre") : "Sin Nombre",
                            precioDetectado,
                            cantidadDetectada,
                            doc.getString("imagen") != null ? doc.getString("imagen") : "default.png"
                    ));
                }
            } catch (Exception e) {
                System.err.println("❌ Error procesando documentos de MongoDB: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        // ═════════════════════════════════════════════════════════════════════════
        // CASO 2: RELACIONAL (MySQL / SQLite)
        // ═════════════════════════════════════════════════════════════════════════
        try (Connection con = Conexion.getConexion();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM producto")) {

            while (rs.next()) {
                String idDetectado = "N/A";
                try {
                    idDetectado = String.valueOf(rs.getInt("id"));
                } catch (SQLException e) {
                    try {
                        idDetectado = rs.getString("codigo");
                    } catch (SQLException ex) {
                        idDetectado = "N/A";
                    }
                }

                datos.add(new FilaProducto(
                        idDetectado,
                        rs.getString("nombre"),
                        rs.getDouble("precio"),
                        rs.getInt("total"),
                        rs.getString("imagen")
                ));
            }
        } catch (Exception e) {
            System.err.println("❌ Error en lectura SQL: " + e.getMessage());
        }
    }

    @FXML
    private void handleEditar() {
        FilaProducto sel = tablaProductos.getSelectionModel().getSelectedItem();
        if (sel == null) {
            alerta("Por favor, selecciona un producto para editar.");
            return;
        }

        Dialog<FilaProducto> dialog = new Dialog<>();
        dialog.setTitle("Editar Producto");
        dialog.setHeaderText("Modificando producto con Identificador ID: " + sel.getCodigo());

        ButtonType btnGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // ── Campos con placeholders y tooltips ──
        TextField tfNombre = new TextField(sel.getNombre());
        tfNombre.setPromptText("Solo letras y espacios");
        tfNombre.setTooltip(new Tooltip("Ingrese únicamente texto (sin números)."));

        TextField tfPrecio = new TextField(String.valueOf(sel.getPrecio()));
        tfPrecio.setPromptText("Ejemplo: 19.99");
        tfPrecio.setTooltip(new Tooltip("Ingrese un número decimal válido."));

        TextField tfCantidad = new TextField(String.valueOf(sel.getCantidad()));
        tfCantidad.setPromptText("Ejemplo: 10");
        tfCantidad.setTooltip(new Tooltip("Ingrese un número entero."));

        grid.add(new Label("Nombre:"), 0, 0);   grid.add(tfNombre, 1, 0);
        grid.add(new Label("Precio:"), 0, 1);   grid.add(tfPrecio, 1, 1);
        grid.add(new Label("Stock:"), 0, 2);    grid.add(tfCantidad, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnGuardar) {
                try {
                    String nuevoNombre = tfNombre.getText().trim();
                    String precioTexto = tfPrecio.getText().trim();
                    String cantidadTexto = tfCantidad.getText().trim();

                    // Validación: nombre solo texto
                    if (!nuevoNombre.matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$")) {
                        alerta("El nombre solo puede contener letras y espacios.");
                        return null;
                    }

                    // Validación: precio numérico con decimales
                    if (!precioTexto.matches("^\\d+(\\.\\d+)?$")) {
                        alerta("El precio debe ser un número válido.");
                        return null;
                    }

                    // Validación: cantidad numérica entera
                    if (!cantidadTexto.matches("^\\d+$")) {
                        alerta("La cantidad debe ser un número entero.");
                        return null;
                    }

                    double nuevoPrecio = Double.parseDouble(precioTexto);
                    int nuevaCantidad = Integer.parseInt(cantidadTexto);

                    return new FilaProducto(sel.getCodigo(), nuevoNombre, nuevoPrecio, nuevaCantidad, sel.getRutaImagen());
                } catch (NumberFormatException e) {
                    alerta("Formato inválido en precio o cantidad.");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(nuevoProd -> {
            CargarProductos.editarProducto(sel.getCodigo(), nuevoProd.getNombre(), nuevoProd.getPrecio(), nuevoProd.getCantidad());
            cargarDatos();
            actualizarFooter();
        });
    }


    @FXML
    private void handleVerModificados() {
        System.out.println("🔍 Abriendo el panel de logs e historial de modificaciones...");
        abrirVentanaAuditoria("modificados", "Historial de Productos Modificados (Auditoría)");
    }

    @FXML
    private void handleVerBackup() {
        System.out.println("🗂 Abriendo ventana visual de Backups...");
        // Redirigimos a leer la colección real donde se están insertando tus backups históricos
        abrirVentanaAuditoria("modificados", "Cajas de Respaldo e Historial de Cambios (Backup)");
    }

    // Método modular para renderizar el visor de logs/backups directamente desde MongoDB sin importar qué BD esté activa
    private void abrirVentanaAuditoria(String coleccionMongo, String tituloVentana) {
        MongoDatabase db = Conexion.getMongoDatabase();
        if (db == null) {
            alerta("No se pudo conectar a MongoDB remoto para extraer el historial.");
            return;
        }

        Stage stageLogs = new Stage();
        stageLogs.setTitle(tituloVentana);

        TableView<FilaModificado> tablaModificados = new TableView<>();
        ObservableList<FilaModificado> listaModificados = FXCollections.observableArrayList();

        TableColumn<FilaModificado, String> colIdMySql = new TableColumn<>("ID / Código Origen");
        colIdMySql.setCellValueFactory(new PropertyValueFactory<>("codigoMysql"));

        TableColumn<FilaModificado, String> colNomOrig = new TableColumn<>("Nombre Original");
        colNomOrig.setCellValueFactory(new PropertyValueFactory<>("nombreOriginal"));

        TableColumn<FilaModificado, String> colPreOrig = new TableColumn<>("Precio Guardado");
        colPreOrig.setCellValueFactory(new PropertyValueFactory<>("precioOriginal"));

        TableColumn<FilaModificado, String> colTotOrig = new TableColumn<>("Stock Histórico");
        colTotOrig.setCellValueFactory(new PropertyValueFactory<>("totalOriginal"));

        TableColumn<FilaModificado, String> colFechaMod = new TableColumn<>("Fecha Operación");
        colFechaMod.setCellValueFactory(new PropertyValueFactory<>("fecha"));

        TableColumn<FilaModificado, String> colAccion = new TableColumn<>("Acción / Evento");
        colAccion.setCellValueFactory(new PropertyValueFactory<>("accion"));

        tablaModificados.getColumns().addAll(colIdMySql, colNomOrig, colPreOrig, colTotOrig, colFechaMod, colAccion);

        for (Document doc : db.getCollection(coleccionMongo).find()) {
            Object fechaObj = doc.get("fecha_modificacion");
            if (fechaObj == null) fechaObj = doc.get("fecha");
            String fechaStr = (fechaObj != null) ? fechaObj.toString() : "N/A";

            String codSql = "N/A";
            if (doc.containsKey("codigo_mysql") && doc.get("codigo_mysql") != null) {
                codSql = String.valueOf(doc.get("codigo_mysql"));
            } else if (doc.containsKey("id") && doc.get("id") != null) {
                codSql = String.valueOf(doc.get("id"));
            }

            listaModificados.add(new FilaModificado(
                    codSql,
                    doc.getString("nombre_original"),
                    doc.get("precio_original") != null ? String.valueOf(doc.get("precio_original")) : "0.0",
                    doc.get("total_original") != null ? String.valueOf(doc.get("total_original")) : "0",
                    fechaStr,
                    doc.getString("accion") != null ? doc.getString("accion") : "Respaldo Nube"
            ));
        }

        tablaModificados.setItems(listaModificados);
        tablaModificados.setPrefWidth(750);

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));
        layout.getChildren().addAll(new Label("Registros encontrados en MongoDB remota (Colección: '" + coleccionMongo + "'):"), tablaModificados);

        stageLogs.setScene(new Scene(layout));
        stageLogs.show();
    }

    @FXML
    private void handleEliminar() {
        System.out.println("Eliminar presionado.");
        FilaProducto sel = tablaProductos.getSelectionModel().getSelectedItem();
        if (sel == null) {
            alerta("Selecciona un producto para eliminar de la base de datos.");
            return;
        }
        // Llamado al método de eliminación de la base de datos persistente
        CargarProductos.eliminarProducto(sel.getCodigo());
        cargarDatos();
        actualizarFooter();
    }

    @FXML private void handleRecargar() { cargarDatos(); actualizarFooter(); }

    private void actualizarFooter() {
        if (labelTotalProductos == null || labelStockBajo == null || labelValorTotal == null) return;

        int totalProd = datos.size();
        int stockBajo = 0;
        double valorTotal = 0.0;

        for (FilaProducto f : datos) {
            valorTotal += (f.getPrecio() * f.getCantidad());
            if (f.getCantidad() < 5) stockBajo++;
        }

        labelTotalProductos.setText(String.valueOf(totalProd));
        labelStockBajo.setText(String.valueOf(stockBajo));
        labelValorTotal.setText(String.format("$%,.2f", valorTotal));
    }

    private void navegarA(String fxml, Button origen) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/quickmira/" + fxml));
            Parent root = loader.load();
            Stage stage = (Stage) origen.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void alerta(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Notificación");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // ── Clases de Mapeo Interno JavaFX ─────────────────────────────────────────
    public static class FilaProducto {
        private final String codigo, nombre, rutaImagen;
        private final double precio;
        private final int cantidad;

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

    public static class FilaModificado {
        private final String codigoMysql, nombreOriginal, precioOriginal, totalOriginal, fecha, accion;

        public FilaModificado(String c, String n, String p, String t, String f, String a) {
            this.codigoMysql = c; this.nombreOriginal = n; this.precioOriginal = p; this.totalOriginal = t; this.fecha = f; this.accion = a;
        }
        public String getCodigoMysql()    { return codigoMysql; }
        public String getNombreOriginal() { return nombreOriginal; }
        public String getPrecioOriginal() { return precioOriginal; }
        public String getTotalOriginal()  { return totalOriginal; }
        public String getFecha()          { return fecha; }
        public String getAccion()         { return accion; }
    }
}