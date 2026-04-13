package com.quickmira.Controller;

import com.quickmira.Database.Conexion;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class ControladorEstadisticas {

    // Componentes de la UI (IDs del FXML)
    @FXML private BarChart<String, Number> graficaVentas;
    @FXML private TableView<FilaProducto> tablaTop5;
    @FXML private TableColumn<FilaProducto, Integer> colRanking;
    @FXML private TableColumn<FilaProducto, String> colNombre;
    @FXML private TableColumn<FilaProducto, Integer> colCantidadVendida;
    @FXML private TableColumn<FilaProducto, String> colPrecio;
    @FXML private TableColumn<FilaProducto, String> colIngresoTotal;

    @FXML private Label lblTotalVentas;
    @FXML private Label lblProductosVendidos;
    @FXML private Label lblTicketPromedio;

    @FXML private Button inventory, sell, Clouse_sesion, stadistics;

    @FXML
    public void initialize() {
        configurarNavegacion();
        configurarColumnas();

        // 1. MAPA para consolidar: Clave = Nombre en minúsculas
        Map<String, FilaProducto> consolidado = new HashMap<>();

        // 2. Cargar datos de ambas fuentes
        List<FilaProducto> desdeMySQL = cargarDesdeMySQL();
        List<FilaProducto> desdeTxt = cargarDesdeTxt();

        // 3. Unificar datos sumando totales si el nombre se repite
        for (FilaProducto p : desdeMySQL) {
            agregarOConsolidar(consolidado, p);
        }
        for (FilaProducto p : desdeTxt) {
            agregarOConsolidar(consolidado, p);
        }

        // 4. Convertir a lista y ordenar por mayor cantidad
        List<FilaProducto> listaFinal = new ArrayList<>(consolidado.values());
        listaFinal.sort((a, b) -> Integer.compare(b.getTotal(), a.getTotal()));

        // 5. Poblar la interfaz
        if (!listaFinal.isEmpty()) {
            llenarGrafica(listaFinal);
            llenarTabla(listaFinal);
            actualizarFooter(listaFinal);
        }
    }

    private void agregarOConsolidar(Map<String, FilaProducto> mapa, FilaProducto nuevo) {
        String clave = nuevo.getNombre().toLowerCase().trim();
        if (mapa.containsKey(clave)) {
            FilaProducto existente = mapa.get(clave);
            int nuevoTotal = existente.getTotal() + nuevo.getTotal();
            // Actualizamos el objeto existente con la nueva suma
            mapa.put(clave, new FilaProducto(
                    existente.getNombre(),
                    existente.getPrecio(),
                    nuevoTotal,
                    "Mixto"
            ));
        } else {
            mapa.put(clave, nuevo);
        }
    }

    // CARGA DE DATOS
    private List<FilaProducto> cargarDesdeMySQL() {
        List<FilaProducto> lista = new ArrayList<>();
        Connection con = Conexion.getConexion();
        if (con == null) return lista;
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombre, precio, total FROM producto")) {
            while (rs.next()) {
                lista.add(new FilaProducto(rs.getString("nombre"), rs.getDouble("precio"), rs.getInt("total"), "MySQL"));
            }
        } catch (SQLException e) {
            System.out.println("❌ Error MySQL: " + e.getMessage());
        }
        return lista;
    }

    private List<FilaProducto> cargarDesdeTxt() {
        List<FilaProducto> lista = new ArrayList<>();
        // Problema 3: Ruta absoluta del proyecto
        Path archivo = Paths.get(System.getProperty("user.dir"), "venta", "ventas.txt");

        if (!Files.exists(archivo)) {
            System.out.println("⚠️ Archivo no encontrado en: " + archivo.toAbsolutePath());
            return lista;
        }

        try {
            String contenido = Files.readString(archivo);
            String[] bloques = contenido.split("\\}");

            for (String bloque : bloques) {
                if (!bloque.contains("\"nombre\"")) continue;

                String nombre = extraerValor(bloque, "nombre");
                String precioS = extraerValor(bloque, "precio");
                String totalS = extraerValor(bloque, "total");

                if (nombre != null && precioS != null && totalS != null) {
                    lista.add(new FilaProducto(
                            nombre.trim(),
                            Double.parseDouble(precioS.trim()),
                            Integer.parseInt(totalS.trim()),
                            "TXT"
                    ));
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error TXT: " + e.getMessage());
        }
        return lista;
    }

    private String extraerValor(String bloque, String clave) {
        String patron = "\"" + clave + "\"";
        int idx = bloque.indexOf(patron);
        if (idx < 0) return null;
        int dosPuntos = bloque.indexOf(":", idx + patron.length());
        String resto = bloque.substring(dosPuntos + 1).stripLeading();
        if (resto.startsWith("\"")) {
            return resto.substring(1, resto.indexOf("\"", 1));
        } else {
            int fin = 0;
            while (fin < resto.length() && (Character.isDigit(resto.charAt(fin)) || resto.charAt(fin) == '.')) fin++;
            return resto.substring(0, fin);
        }
    }

    // CONFIGURACIÓN DE UI
    private void configurarColumnas() {
        colRanking.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setText(null);
                else setText(String.valueOf(getIndex() + 1));
            }
        });

        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colCantidadVendida.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getTotal()).asObject());
        colPrecio.setCellValueFactory(c -> new SimpleStringProperty(String.format("$%,.0f", c.getValue().getPrecio())));
        colIngresoTotal.setCellValueFactory(c -> new SimpleStringProperty(String.format("$%,.0f", c.getValue().getPrecio() * c.getValue().getTotal())));

        tablaTop5.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(FilaProducto item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) setStyle("");
                else if (item.getFuente().equals("MySQL")) setStyle("-fx-background-color: #f0f7ff;");
                else if (item.getFuente().equals("TXT")) setStyle("-fx-background-color: #f0fffb;");
                else setStyle("-fx-background-color: #fffaf0;"); // Mixto
            }
        });
    }

    private void llenarGrafica(List<FilaProducto> datos) {
        graficaVentas.getData().clear();
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName("Stock Disponible");

        // Top 6 para que la gráfica se vea limpia
        datos.stream().limit(6).forEach(p ->
                serie.getData().add(new XYChart.Data<>(p.getNombre(), p.getTotal()))
        );

        graficaVentas.getData().add(serie);

        CategoryAxis xAxis = (CategoryAxis) graficaVentas.getXAxis();
        xAxis.setGapStartAndEnd(true);
        graficaVentas.setBarGap(10);
        graficaVentas.setCategoryGap(50);
    }

    private void llenarTabla(List<FilaProducto> datos) {
        tablaTop5.setItems(FXCollections.observableArrayList(datos));
    }

    private void actualizarFooter(List<FilaProducto> datos) {
        int totalProd = datos.size();
        int unidadesTotal = datos.stream().mapToInt(FilaProducto::getTotal).sum();
        double valorInventario = datos.stream().mapToDouble(p -> p.getPrecio() * p.getTotal()).sum();

        lblProductosVendidos.setText("Total productos: " + totalProd);
        lblTicketPromedio.setText("Unidades totales: " + unidadesTotal);
        lblTotalVentas.setText(String.format("Valor inventario: $%,.0f", valorInventario));
    }

    private void configurarNavegacion() {
        if (sell != null) sell.setOnAction(e -> navegarA("/com/quickmira/ui/vista.fxml", sell));
        if (inventory != null) inventory.setOnAction(e -> navegarA("/com/quickmira/ui/inventory-view.fxml", inventory));
        if (Clouse_sesion != null) Clouse_sesion.setOnAction(e -> {
            Stage stage = (Stage) Clouse_sesion.getScene().getWindow();
            stage.close();
        });
    }

    private void navegarA(String fxml, Button origen) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Stage stage = (Stage) origen.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static class FilaProducto {
        private final String nombre;
        private final double precio;
        private final int total;
        private final String fuente;

        public FilaProducto(String nombre, double precio, int total, String fuente) {
            this.nombre = nombre; this.precio = precio; this.total = total; this.fuente = fuente;
        }
        public String getNombre() { return nombre; }
        public double getPrecio() { return precio; }
        public int getTotal() { return total; }
        public String getFuente() { return fuente; }
    }
}
