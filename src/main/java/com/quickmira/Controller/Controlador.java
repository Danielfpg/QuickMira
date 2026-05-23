package com.quickmira.Controller;

import com.quickmira.Database.CargarProductos;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Controlador {

    @FXML private ImageView image;
    @FXML private Button Capture, ad, Clouse_sesion, inventory, sell, stadistics;
    @FXML private BorderPane rootPane;
    @FXML private TextField name, cost, whole;

    @FXML private Label labelTotalProductos, labelStockBajo, labelValorTotal;

    private static final String ARCHIVO_VENTAS = "venta/ventas.txt";
    private static final int UMBRAL_STOCK = 5;
    // Usamos una ruta relativa para que funcione en cualquier PC
    private static final String CARPETA_IMAGENES = "src/main/resources/com/quickmira/images/";

    private final List<Producto> listaProductos = new ArrayList<>();
    private File imagenSeleccionada = null;

    @FXML
    public void initialize() {
        // 1. Asegurar que la tabla exista al arrancar
        CargarProductos.crearTablaProducto();

        // 2. Cargar datos previos
        cargarProductosDesdeArchivo();

        sell.setStyle("-fx-background-color: #15c0a9;");

        inventory.setOnAction(e -> navegarA("ui/inventory-view.fxml", inventory));
        stadistics.setOnAction(e -> navegarA("ui/estadisticas-view.fxml", stadistics));
        Clouse_sesion.setOnAction(e -> cerrarSesion());

        // ── Validación en tiempo real del campo nombre ──
        name.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]*$")) {
                alerta("El nombre solo puede contener letras y espacios.");
                // Elimina cualquier carácter inválido automáticamente
                name.setText(newValue.replaceAll("[^a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]", ""));
            }
        });

        actualizarFooter();
    }


    private void cargarProductosDesdeArchivo() {
        listaProductos.clear();
        Path archivo = Paths.get(ARCHIVO_VENTAS);
        if (!Files.exists(archivo)) return;

        try {
            String contenido = Files.readString(archivo);
            String[] bloques = contenido.split("\\{");
            for (String bloque : bloques) {
                if (!bloque.contains("\"nombre\"")) continue;

                String nombre = extraerValor(bloque, "nombre");
                String precioS = extraerValor(bloque, "precio");
                String totalS = extraerValor(bloque, "total");
                String imagen = extraerValor(bloque, "imagen");

                if (nombre != null && precioS != null && totalS != null) {
                    try {
                        double precio = Double.parseDouble(precioS.trim().replace(",", "."));
                        int total = Integer.parseInt(totalS.trim());
                        listaProductos.add(new Producto(nombre, precio, total, imagen != null ? imagen : ""));
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException e) {
            System.err.println("Error al cargar archivo: " + e.getMessage());
        }
    }

    @FXML
    private void handleCapture(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar imagen");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg"));

        File archivo = fc.showOpenDialog((Stage) Capture.getScene().getWindow());
        if (archivo != null) {
            imagenSeleccionada = archivo;
            image.setImage(new Image(archivo.toURI().toString()));
        }
    }

    @FXML
    private void handleAgregar(ActionEvent event) {
        // Validaciones de UI
        if (name.getText().isBlank() || cost.getText().isBlank() || whole.getText().isBlank()) {
            alerta("Todos los campos son obligatorios.");
            return;
        }
        if (imagenSeleccionada == null) {
            alerta("Debes seleccionar una imagen.");
            return;
        }

        try {
            String nombre = name.getText().trim();
            // Reemplazamos comas por puntos antes de parsear para evitar excepciones de formato regional
            double precio = Double.parseDouble(cost.getText().trim().replace(",", "."));
            int total = Integer.parseInt(whole.getText().trim());

            if (precio < 0 || total < 0) {
                alerta("El precio y la cantidad deben ser valores positivos.");
                return;
            }

            // 1. Guardar imagen y obtener solo el nombre del archivo final
            String nombreImagen = copiarImagen(imagenSeleccionada);
            if (nombreImagen == null) {
                alerta("Error al procesar y almacenar la imagen seleccionada.");
                return;
            }

            // 2. GUARDAR EN BASE DE DATOS (Acoplado al motor híbrido usando formato estructurado)
            // Usamos una estructura de bloque simulada en base al cargador masivo autoincremental
            String formatoCarga = "====\nNombre: " + nombre + "\nPrecio: " + precio + "\nTotal: " + total + "\nRuta: " + nombreImagen;
            CargarProductos.guardarProductosBD(formatoCarga);

            // 3. GUARDAR EN ARCHIVO TXT Y LISTA LOCAL
            Producto p = new Producto(nombre, precio, total, nombreImagen);
            listaProductos.add(p);
            guardarEnTxt(p);

            actualizarFooter();
            limpiarFormulario();
            info("Producto \"" + nombre + "\" agregado y sincronizado de forma exitosa.");

        } catch (NumberFormatException e) {
            alerta("Precio o Cantidad no válidos. Asegúrate de ingresar números válidos.");
        }
    }

    private String copiarImagen(File origen) {
        try {
            Path carpeta = Paths.get(CARPETA_IMAGENES);
            if (!Files.exists(carpeta)) Files.createDirectories(carpeta);

            String ext = origen.getName().substring(origen.getName().lastIndexOf('.'));
            String nuevoNombre = System.currentTimeMillis() + ext;
            Path destino = carpeta.resolve(nuevoNombre);

            Files.copy(origen.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
            return nuevoNombre; // Retornamos solo el nombre limpio para la base de datos
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void guardarEnTxt(Producto p) {
        try {
            Path carpeta = Paths.get("venta/");
            if (!Files.exists(carpeta)) Files.createDirectories(carpeta);
            Path archivo = carpeta.resolve("ventas.txt");

            String fechaISO = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            String bloque = String.format(
                    "  {\n    \"nombre\" : \"%s\",\n    \"precio\" : %.2f,\n    \"total\"  : %d,\n    \"imagen\" : \"%s\",\n    \"fecha\"  : { \"$date\": \"%s\" }\n  }",
                    p.getNombre(), p.getPrecio(), p.getTotal(), p.getRutaImagen(), fechaISO
            );

            if (!Files.exists(archivo) || Files.size(archivo) == 0) {
                Files.writeString(archivo, "[\n" + bloque + "\n]", StandardOpenOption.CREATE);
            } else {
                String contenido = Files.readString(archivo).trim();
                if (contenido.endsWith("]")) contenido = contenido.substring(0, contenido.length() - 1).trim();
                if (!contenido.endsWith("[")) contenido += ",";
                Files.writeString(archivo, contenido + "\n" + bloque + "\n]", StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            alerta("Error al actualizar el archivo de texto en la carpeta de ventas.");
        }
    }

    private void actualizarFooter() {
        if (labelTotalProductos == null) return;
        int total = listaProductos.size();
        long stockBajo = listaProductos.stream().filter(p -> p.getTotal() <= UMBRAL_STOCK).count();
        double valorTotal = listaProductos.stream().mapToDouble(p -> p.getPrecio() * p.getTotal()).sum();

        labelTotalProductos.setText("Total: " + total);
        labelStockBajo.setText("Stock Bajo: " + stockBajo);
        labelValorTotal.setText(String.format("Valor: $%,.2f", valorTotal));
    }

    private void navegarA(String fxml, Button origen) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/quickmira/" + fxml));
            Parent root = loader.load();
            Stage stage = (Stage) origen.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            alerta("Error crítico al intentar cargar la vista: " + fxml);
            e.printStackTrace();
        }
    }

    private void limpiarFormulario() {
        name.clear(); cost.clear(); whole.clear();
        image.setImage(null); imagenSeleccionada = null;
    }

    private void alerta(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void info(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void cerrarSesion() {
        ((Stage) Clouse_sesion.getScene().getWindow()).close();
    }

    private String extraerValor(String bloque, String clave) {
        String patron = "\"" + clave + "\"";
        int idx = bloque.indexOf(patron);
        if (idx < 0) return null;
        int dosPuntos = bloque.indexOf(":", idx + patron.length());
        String resto = bloque.substring(dosPuntos + 1).trim();
        if (resto.startsWith("\"")) {
            return resto.substring(1, resto.indexOf("\"", 1));
        } else {
            int fin = 0;
            while (fin < resto.length() && (Character.isDigit(resto.charAt(fin)) || resto.charAt(fin) == '.' || resto.charAt(fin) == ',')) fin++;
            return resto.substring(0, fin);
        }
    }

    // --- Clase Anidada Producto ---
    public static class Producto {
        private final String nombre, rutaImagen;
        private final double precio;
        private final int total;

        public Producto(String n, double p, int t, String img) {
            this.nombre = n; this.precio = p; this.total = t; this.rutaImagen = img;
        }
        public String getNombre() { return nombre; }
        public double getPrecio() { return precio; }
        public int getTotal() { return total; }
        public String getRutaImagen() { return rutaImagen; }
    }
}