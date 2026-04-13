package com.quickmira.Controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ControladorInventoryView {
    // ── Botones de navegación (mismos fx:id que en el FXML) ─────────────────
    @FXML private Button inventory;   // ya estamos aquí
    @FXML private Button sell;        // ← navega a ventas
    @FXML private Button Clouse_sesion;
    @FXML private Button    stadistics;

    // ── Tabla ────────────────────────────────────────────────────────────────
    @FXML private TableView<FilaProducto>   tablaProductos;
    @FXML private TableColumn<FilaProducto, String> colCodigo;
    @FXML private TableColumn<FilaProducto, String> colNombre;
    @FXML private TableColumn<FilaProducto, String> colPrecio;
    @FXML private TableColumn<FilaProducto, Integer> colCantidad;
    @FXML private TableColumn<FilaProducto, String> colImagen;

    // ── Barra de búsqueda (opcional – agrégala al FXML si la quieres) ────────
    // @FXML private TextField campoBusqueda;

    // ── Labels del footer ────────────────────────────────────────────────────
    @FXML private Label labelTotalProductos;
    @FXML private Label labelStockBajo;
    @FXML private Label labelValorTotal;

    // ── Datos ─────────────────────────────────────────────────────────────────
    private final ObservableList<FilaProducto> datos = FXCollections.observableArrayList();
    private static final String ARCHIVO_VENTAS = "venta/ventas.txt";
    private static final int    UMBRAL_STOCK   = 5;   // cantidad mínima antes de alertar

    // ═════════════════════════════════════════════════════════════════════════
    //  INICIALIZACIÓN
    // ═════════════════════════════════════════════════════════════════════════
    @FXML
    public void initialize() {
        configurarColumnas();
        cargarDatos();
        actualizarFooter();

        // Resaltar botón activo
        inventory.setStyle("-fx-background-color: #15c0a9;");
        sell.setStyle("");

        // Navegación
        sell.setOnAction(e -> navegarA("ui/vista.fxml", sell));
        inventory.setOnAction(e -> { /* ya estamos aquí */ });
        stadistics.setOnAction(e -> navegarA("ui/estadisticas-view.fxml",stadistics));
        Clouse_sesion.setOnAction(e -> cerrarSesion());

        // Doble clic en fila → mostrar detalle / editar
        tablaProductos.setRowFactory(tv -> {
            TableRow<FilaProducto> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    mostrarDetalle(row.getItem());
                }
            });
            return row;
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CONFIGURAR COLUMNAS
    // ═════════════════════════════════════════════════════════════════════════
    private void configurarColumnas() {
        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigo"));
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colPrecio.setCellValueFactory(new PropertyValueFactory<>("precioFormateado"));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colImagen.setCellValueFactory(new PropertyValueFactory<>("rutaImagen"));

        // Colorear filas con stock bajo
        tablaProductos.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(FilaProducto item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.getCantidad() <= UMBRAL_STOCK) {
                    setStyle("-fx-background-color: #ffe0e0;"); // rojo suave
                } else {
                    setStyle("");
                }
            }
        });

        tablaProductos.setItems(datos);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CARGAR DATOS DESDE ventas.txt
    // ═════════════════════════════════════════════════════════════════════════
    private void cargarDatos() {
        datos.clear();
        Path archivo = Paths.get(ARCHIVO_VENTAS);
        if (!Files.exists(archivo)) return;

        try {
            String contenido = Files.readString(archivo);
            String[] bloques = contenido.split("\\{");
            int codigo = 1;

            for (String bloque : bloques) {
                if (!bloque.contains("\"nombre\"")) continue;

                String nombre   = extraerValor(bloque, "nombre");
                String precioS  = extraerValor(bloque, "precio");
                String totalS   = extraerValor(bloque, "total");
                String imagen   = extraerValor(bloque, "imagen");

                if (nombre == null || precioS == null || totalS == null) continue;

                try {
                    double precio   = Double.parseDouble(precioS.trim());
                    int    cantidad = Integer.parseInt(totalS.trim());
                    datos.add(new FilaProducto(
                            String.format("PRD-%03d", codigo++),
                            nombre, precio, cantidad, imagen != null ? imagen : ""
                    ));
                } catch (NumberFormatException ignored) {}
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Extrae el valor de una clave JSON-like:  "clave" : "valor"  o  "clave" : 123 */
    private String extraerValor(String bloque, String clave) {
        String patron = "\"" + clave + "\"";
        int idx = bloque.indexOf(patron);
        if (idx < 0) return null;

        int dosPuntos = bloque.indexOf(":", idx + patron.length());
        if (dosPuntos < 0) return null;

        String resto = bloque.substring(dosPuntos + 1).stripLeading();

        if (resto.startsWith("\"")) {
            int fin = resto.indexOf("\"", 1);
            return fin < 0 ? null : resto.substring(1, fin);
        } else {
            // número
            int fin = 0;
            while (fin < resto.length() && (Character.isDigit(resto.charAt(fin)) || resto.charAt(fin) == '.'))
                fin++;
            return fin == 0 ? null : resto.substring(0, fin);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  BOTÓN "Recargar"
    // ═════════════════════════════════════════════════════════════════════════
    @FXML
    private void handleRecargar() {
        cargarDatos();
        actualizarFooter();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  BOTÓN "Eliminar"
    // ═════════════════════════════════════════════════════════════════════════
    @FXML
    private void handleEliminar() {
        FilaProducto seleccionado = tablaProductos.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            alerta("Selecciona un producto para eliminar.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar \"" + seleccionado.getNombre() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar eliminación");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.YES) {
                datos.remove(seleccionado);
                reescribirArchivo();
                actualizarFooter();
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  REESCRIBIR EL ARCHIVO después de eliminar
    // ═════════════════════════════════════════════════════════════════════════
    private void reescribirArchivo() {
        try {
            Path archivo = Paths.get(ARCHIVO_VENTAS);
            StringBuilder sb = new StringBuilder("[\n");
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

            for (int i = 0; i < datos.size(); i++) {
                FilaProducto fp = datos.get(i);
                String rutaEscapada = fp.getRutaImagen().replace("\\", "\\\\");
                sb.append("  {\n")
                        .append("    \"_id\"    : { \"$oid\": \"").append(generarObjectId(i)).append("\" },\n")
                        .append("    \"nombre\" : \"").append(fp.getNombre()).append("\",\n")
                        .append("    \"precio\" : ").append(String.format("%.2f", fp.getPrecio())).append(",\n")
                        .append("    \"total\"  : ").append(fp.getCantidad()).append(",\n")
                        .append("    \"imagen\" : \"").append(rutaEscapada).append("\",\n")
                        .append("    \"fecha\"  : { \"$date\": \"").append(LocalDateTime.now().format(fmt)).append("\" }\n")
                        .append("  }");
                if (i < datos.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]");
            Files.writeString(archivo, sb.toString(), StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  FOOTER: totales
    // ═════════════════════════════════════════════════════════════════════════
    private void actualizarFooter() {
        if (labelTotalProductos == null) return; // evitar NPE si el FXML no los tiene

        int total      = datos.size();
        long stockBajo = datos.stream().filter(p -> p.getCantidad() <= UMBRAL_STOCK).count();
        double valor   = datos.stream().mapToDouble(p -> p.getPrecio() * p.getCantidad()).sum();

        labelTotalProductos.setText("Total de productos: " + total);
        labelStockBajo.setText("Productos en stock bajo: " + stockBajo);
        labelValorTotal.setText(String.format("Valor total inventario: $%,.0f", valor));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  DETALLE (doble clic en fila)
    // ═════════════════════════════════════════════════════════════════════════
    private void mostrarDetalle(FilaProducto fp) {
        String msg = "Código:   " + fp.getCodigo()   + "\n"
                + "Nombre:   " + fp.getNombre()   + "\n"
                + "Precio:   " + fp.getPrecioFormateado() + "\n"
                + "Cantidad: " + fp.getCantidad() + "\n"
                + "Imagen:   " + fp.getRutaImagen();

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Detalle del producto");
        a.setHeaderText(fp.getNombre());
        a.setContentText(msg);
        a.showAndWait();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN
    // ═════════════════════════════════════════════════════════════════════════
    private void navegarA(String fxml, Button origen) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/quickmira/" + fxml));
            Parent root = loader.load();
            Stage stage = (Stage) origen.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            alerta("No se pudo abrir la vista: " + fxml);
        }
    }

    private void cerrarSesion() {
        Stage stage = (Stage) Clouse_sesion.getScene().getWindow();
        stage.close();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════════════
    private String generarObjectId(int indice) {
        String timestamp = Long.toHexString(System.currentTimeMillis() / 1000L);
        String base      = String.format("%016x", (long)(indice + 1));
        return (timestamp + base).substring(0, 24);
    }

    private void alerta(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Atención"); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CLASE FilaProducto  (JavaFX Bean para la TableView)
    // ═════════════════════════════════════════════════════════════════════════
    public static class FilaProducto {
        private final String codigo;
        private final String nombre;
        private final double precio;
        private final int    cantidad;
        private final String rutaImagen;

        public FilaProducto(String codigo, String nombre, double precio,
                            int cantidad, String rutaImagen) {
            this.codigo     = codigo;
            this.nombre     = nombre;
            this.precio     = precio;
            this.cantidad   = cantidad;
            this.rutaImagen = rutaImagen;
        }

        // Getters (requeridos por PropertyValueFactory)
        public String getCodigo()           { return codigo; }
        public String getNombre()           { return nombre; }
        public double getPrecio()           { return precio; }
        public int    getCantidad()         { return cantidad; }
        public String getRutaImagen()       { return rutaImagen; }
        public String getPrecioFormateado() { return String.format("$%,.2f", precio); }
    }

}