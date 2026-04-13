package com.quickmira.Controller;


import com.quickmira.Database.Conexion;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Controlador {

    // ── Controles del FXML ──────────────────────────────────────────────────
    @FXML private ImageView image;
    @FXML private Button    Capture;
    @FXML private Button    ad;
    @FXML private Button    Clouse_sesion;
    @FXML private Button    inventory;
    @FXML private Button    sell;
    @FXML private Button    stadistics;
    @FXML private BorderPane rootPane;
    @FXML private TextField name;
    @FXML private TextField cost;
    @FXML private TextField whole;

    @FXML private Label labelTotalProductos;
    @FXML private Label labelStockBajo;
    @FXML private Label labelValorTotal;

    private static final String ARCHIVO_VENTAS   = "venta/ventas.txt";
    private static final int    UMBRAL_STOCK      = 5;
    private static final String CARPETA_IMAGENES  = "src/main/resources/com/quickmira/images/";

    // ── Estado interno ──────────────────────────────────────────────────────
    private final List<Producto> listaProductos = new ArrayList<>();
    private File imagenSeleccionada = null;

    // ═══════════════════════════════════════════════════════════════════════
    //  INICIALIZACIÓN
    // ═══════════════════════════════════════════════════════════════════════
    @FXML
    public void initialize() {
        cargarProductosDesdeArchivo(); // ← NUEVO: llena el ArrayList al arrancar

        sell.setStyle("-fx-background-color: #15c0a9;");
        inventory.setStyle("");

        inventory.setOnAction(e -> navegarA("ui/inventory-view.fxml", inventory));
        sell.setOnAction(e -> { });
        stadistics.setOnAction(e -> navegarA("ui/estadisticas-view.fxml",stadistics));
        Clouse_sesion.setOnAction(e -> cerrarSesion());


        actualizarFooter();
    }
    // ═══════════════════════════════════════════════════════════════════════
    //  VERIFICACIÓN DE CONEXIÓN A MYSQL
    // ══════════════════════════════════════════════════════════════════════
    // ═══════════════════════════════════════════════════════════════════════
    //  NUEVO: CARGAR PRODUCTOS DESDE ARCHIVO AL ARRAYLIST
    // ═══════════════════════════════════════════════════════════════════════
    private void cargarProductosDesdeArchivo() {
        listaProductos.clear();
        Path archivo = Paths.get(ARCHIVO_VENTAS);
        if (!Files.exists(archivo)) return;

        try {
            String contenido = Files.readString(archivo);
            for (String bloque : contenido.split("\\{")) {
                if (!bloque.contains("\"nombre\"")) continue;

                String nombre  = extraerValor(bloque, "nombre");
                String precioS = extraerValor(bloque, "precio");
                String totalS  = extraerValor(bloque, "total");
                String imagen  = extraerValor(bloque, "imagen");

                if (nombre == null || precioS == null || totalS == null) continue;

                try {
                    double precio = Double.parseDouble(precioS.trim());
                    int    total  = Integer.parseInt(totalS.trim());
                    listaProductos.add(new Producto(nombre, precio, total,
                            imagen != null ? imagen : ""));
                } catch (NumberFormatException ignored) {}
            }
            System.out.println("📦 Productos cargados al ArrayList: " + listaProductos.size());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CAPTURA DE IMAGEN
    // ═══════════════════════════════════════════════════════════════════════
    @FXML
    private void handleCapture(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar imagen del producto");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );

        Stage stage = (Stage) Capture.getScene().getWindow();
        File archivo = fc.showOpenDialog(stage);

        if (archivo != null) {
            imagenSeleccionada = archivo;
            image.setImage(new Image(archivo.toURI().toString()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  AGREGAR PRODUCTO
    // ═══════════════════════════════════════════════════════════════════════
    @FXML
    private void handleAgregar(ActionEvent event) {

        if (name.getText().isBlank()) {
            alerta("El nombre no puede estar vacío.");
            return;
        }
        if (imagenSeleccionada == null) {
            alerta("Debes seleccionar una imagen antes de agregar.");
            return;
        }

        double precio;
        try {
            precio = Double.parseDouble(cost.getText().trim());
        } catch (NumberFormatException e) {
            alerta("El precio debe ser un número válido (ej: 1500.50).");
            return;
        }

        int total;
        try {
            total = Integer.parseInt(whole.getText().trim());
        } catch (NumberFormatException e) {
            alerta("El total debe ser un número entero.");
            return;
        }

        String nombreImagen = copiarImagen(imagenSeleccionada);
        if (nombreImagen == null) {
            alerta("No se pudo guardar la imagen.");
            return;
        }

        Producto p = new Producto(name.getText().trim(), precio, total, nombreImagen);
        listaProductos.add(p);   // ← guarda en ArrayList
        guardarEnTxt(p);         // ← guarda en ventas.txt
        actualizarFooter();

        System.out.println("✅ Agregado: " + p);
        System.out.println("📦 Total en ArrayList: " + listaProductos.size());
        limpiarFormulario();
        info("Producto \"" + p.getNombre() + "\" agregado correctamente.");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN
    // ═══════════════════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════════════════
    //  UTILIDADES INTERNAS
    // ═══════════════════════════════════════════════════════════════════════
    private String copiarImagen(File origen) {
        try {
            Path carpeta = Paths.get(CARPETA_IMAGENES);
            Files.createDirectories(carpeta);

            String ext         = origen.getName().substring(origen.getName().lastIndexOf('.'));
            String nuevoNombre = System.currentTimeMillis() + ext;
            Path destino       = carpeta.resolve(nuevoNombre);
            Files.copy(origen.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
            return destino.toAbsolutePath().toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void guardarEnTxt(Producto p) {
        try {
            Path carpeta = Paths.get("venta/");
            Files.createDirectories(carpeta);

            Path   archivo      = carpeta.resolve("ventas.txt");
            int    indice       = listaProductos.size() - 1;
            String fechaISO     = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            String rutaEscapada = p.getRutaImagen().replace("\\", "\\\\");

            String bloque = "  {\n"
                    + "    \"_id\"    : { \"$oid\": \"" + generarObjectId(indice) + "\" },\n"
                    + "    \"nombre\" : \"" + p.getNombre() + "\",\n"
                    + "    \"precio\" : " + String.format("%.2f", p.getPrecio()) + ",\n"
                    + "    \"total\"  : " + p.getTotal() + ",\n"
                    + "    \"imagen\" : \"" + rutaEscapada + "\",\n"
                    + "    \"fecha\"  : { \"$date\": \"" + fechaISO + "\" }\n"
                    + "  }";

            if (!archivo.toFile().exists() || archivo.toFile().length() == 0) {
                Files.writeString(archivo, "[\n" + bloque + "\n]",
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                String contenido = Files.readString(archivo).stripTrailing();
                if (contenido.endsWith("]"))
                    contenido = contenido.substring(0, contenido.length() - 1).stripTrailing();
                if (!contenido.endsWith(","))
                    contenido = contenido + ",";
                Files.writeString(archivo, contenido + "\n" + bloque + "\n]",
                        StandardOpenOption.TRUNCATE_EXISTING);
            }

        } catch (IOException e) {
            e.printStackTrace();
            alerta("No se pudo guardar el archivo.");
        }
    }

    private String generarObjectId(int indice) {
        String timestamp = Long.toHexString(System.currentTimeMillis() / 1000L);
        String base      = String.format("%016x", (long)(indice + 1));
        return (timestamp + base).substring(0, 24);
    }

    private void limpiarFormulario() {
        name.clear();
        cost.clear();
        whole.clear();
        image.setImage(null);
        imagenSeleccionada = null;
    }

    public List<Producto> getListaProductos() { return listaProductos; }

    private void alerta(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Atención"); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }

    private void info(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Éxito"); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }

    private void actualizarFooter() {
        if (labelTotalProductos == null) return;

        List<double[]> items = new ArrayList<>();
        Path archivo = Paths.get(ARCHIVO_VENTAS);
        if (Files.exists(archivo)) {
            try {
                String contenido = Files.readString(archivo);
                for (String bloque : contenido.split("\\{")) {
                    if (!bloque.contains("\"nombre\"")) continue;
                    String precioS = extraerValor(bloque, "precio");
                    String totalS  = extraerValor(bloque, "total");
                    if (precioS == null || totalS == null) continue;
                    try {
                        items.add(new double[]{
                                Double.parseDouble(precioS.trim()),
                                Double.parseDouble(totalS.trim())
                        });
                    } catch (NumberFormatException ignored) {}
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        int    total     = items.size();
        long   stockBajo = items.stream().filter(i -> i[1] <= UMBRAL_STOCK).count();
        double valor     = items.stream().mapToDouble(i -> i[0] * i[1]).sum();

        labelTotalProductos.setText("Total de productos: " + total);
        labelStockBajo.setText("Productos en stock bajo: " + stockBajo);
        labelValorTotal.setText(String.format("Valor total inventario: $%,.0f", valor));
    }

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
            int fin = 0;
            while (fin < resto.length() &&
                    (Character.isDigit(resto.charAt(fin)) || resto.charAt(fin) == '.'))
                fin++;
            return fin == 0 ? null : resto.substring(0, fin);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CLASE PRODUCTO
    // ═══════════════════════════════════════════════════════════════════════
    public static class Producto {
        private final String nombre;
        private final double precio;
        private final int    total;
        private final String rutaImagen;

        public Producto(String nombre, double precio, int total, String rutaImagen) {
            this.nombre     = nombre;
            this.precio     = precio;
            this.total      = total;
            this.rutaImagen = rutaImagen;
        }

        public String getNombre()     { return nombre; }
        public double getPrecio()     { return precio; }
        public int    getTotal()      { return total; }
        public String getRutaImagen() { return rutaImagen; }

        @Override
        public String toString() {
            return String.format("Producto{ nombre='%s', precio=%.2f, total=%d, imagen='%s' }",
                    nombre, precio, total, rutaImagen);
        }
    }
}