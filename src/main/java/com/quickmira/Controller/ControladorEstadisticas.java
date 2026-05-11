package com.quickmira.Controller;

import com.mongodb.client.MongoDatabase;
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
import org.bson.Document;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class ControladorEstadisticas {

    @FXML private BarChart<String, Number> graficaVentas;
    @FXML private TableView<FilaProducto> tablaTop5;
    @FXML private TableColumn<FilaProducto, Integer> colRanking;
    @FXML private TableColumn<FilaProducto, String>  colNombre;
    @FXML private TableColumn<FilaProducto, Integer> colCantidadVendida;
    @FXML private TableColumn<FilaProducto, String>  colPrecio;
    @FXML private TableColumn<FilaProducto, String>  colIngresoTotal;

    @FXML private Label lblTotalVentas, lblProductosVendidos, lblTicketPromedio;
    @FXML private Button inventory, sell, Clouse_sesion, stadistics;

    @FXML
    public void initialize() {
        configurarNavegacion();
        configurarColumnas();

        Map<String, FilaProducto> consolidado = new HashMap<>();

        // Cargar según el modo activo
        List<FilaProducto> desdeBD  = cargarDesdeBD();
        List<FilaProducto> desdeTxt = cargarDesdeTxt();

        for (FilaProducto p : desdeBD)  agregarOConsolidar(consolidado, p);
        for (FilaProducto p : desdeTxt) agregarOConsolidar(consolidado, p);

        List<FilaProducto> listaFinal = new ArrayList<>(consolidado.values());
        listaFinal.sort((a, b) -> Integer.compare(b.getTotal(), a.getTotal()));

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
            mapa.put(clave, new FilaProducto(
                    existente.getNombre(),
                    existente.getPrecio(),
                    existente.getTotal() + nuevo.getTotal(),
                    "Mixto"
            ));
        } else {
            mapa.put(clave, nuevo);
        }
    }

    // ── Carga desde la BD activa ───────────────────────────────────────────
    private List<FilaProducto> cargarDesdeBD() {
        if (Conexion.isUsarMongo()) return cargarDesdeMongo();
        return cargarDesdeJDBC();
    }

    private List<FilaProducto> cargarDesdeJDBC() {
        List<FilaProducto> lista = new ArrayList<>();
        String fuente = Conexion.isUsarMySQL() ? "MySQL" : "SQLite";
        Connection con = Conexion.getConexion();
        if (con == null) return lista;
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombre, precio, total FROM producto")) {
            while (rs.next()) {
                lista.add(new FilaProducto(rs.getString("nombre"), rs.getDouble("precio"), rs.getInt("total"), fuente));
            }
        } catch (SQLException e) {
            System.out.println("❌ Error " + fuente + ": " + e.getMessage());
        }
        return lista;
    }

    private List<FilaProducto> cargarDesdeMongo() {
        List<FilaProducto> lista = new ArrayList<>();
        MongoDatabase db = Conexion.getMongoDatabase();
        if (db == null) return lista;
        try {
            for (Document doc : db.getCollection("producto").find()) {
                lista.add(new FilaProducto(
                        doc.getString("nombre"),
                        doc.getDouble("precio") != null ? doc.getDouble("precio") : 0.0,
                        doc.getInteger("total")  != null ? doc.getInteger("total")  : 0,
                        "MongoDB"
                ));
            }
        } catch (Exception e) {
            System.out.println("❌ Error MongoDB: " + e.getMessage());
        }
        return lista;
    }

    private List<FilaProducto> cargarDesdeTxt() {
        List<FilaProducto> lista = new ArrayList<>();
        Path archivo = Paths.get(System.getProperty("user.dir"), "venta", "ventas.txt");
        if (!Files.exists(archivo)) return lista;

        try {
            String contenido = Files.readString(archivo);
            String[] bloques = contenido.split("\\}");
            for (String bloque : bloques) {
                if (!bloque.contains("\"nombre\"")) continue;
                String nombre  = extraerValor(bloque, "nombre");
                String precioS = extraerValor(bloque, "precio");
                String totalS  = extraerValor(bloque, "total");
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

    // ── Configuración de UI ────────────────────────────────────────────────
    private void configurarColumnas() {
        colRanking.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });

        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colCantidadVendida.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getTotal()).asObject());
        colPrecio.setCellValueFactory(c -> new SimpleStringProperty(String.format("$%,.0f", c.getValue().getPrecio())));
        colIngresoTotal.setCellValueFactory(c -> new SimpleStringProperty(String.format("$%,.0f", c.getValue().getPrecio() * c.getValue().getTotal())));

        tablaTop5.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(FilaProducto item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) { setStyle(""); return; }
                setStyle(switch (item.getFuente()) {
                    case "MySQL"   -> "-fx-background-color: #f0f7ff;";
                    case "SQLite"  -> "-fx-background-color: #f0fffb;";
                    case "MongoDB" -> "-fx-background-color: #fff5f0;";
                    default        -> "-fx-background-color: #fffaf0;";
                });
            }
        });
    }

    private void llenarGrafica(List<FilaProducto> datos) {
        graficaVentas.getData().clear();
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName("Stock Disponible");
        datos.stream().limit(6).forEach(p -> serie.getData().add(new XYChart.Data<>(p.getNombre(), p.getTotal())));
        graficaVentas.getData().add(serie);
        ((CategoryAxis) graficaVentas.getXAxis()).setGapStartAndEnd(true);
        graficaVentas.setBarGap(10);
        graficaVentas.setCategoryGap(50);
    }

    private void llenarTabla(List<FilaProducto> datos) {
        tablaTop5.setItems(FXCollections.observableArrayList(datos));
    }

    private void actualizarFooter(List<FilaProducto> datos) {
        int totalProd    = datos.size();
        int unidades     = datos.stream().mapToInt(FilaProducto::getTotal).sum();
        double valor     = datos.stream().mapToDouble(p -> p.getPrecio() * p.getTotal()).sum();
        lblProductosVendidos.setText("Total productos: " + totalProd);
        lblTicketPromedio.setText("Unidades totales: " + unidades);
        lblTotalVentas.setText(String.format("Valor inventario: $%,.0f", valor));
    }

    private void configurarNavegacion() {
        if (sell      != null) sell.setOnAction(e -> navegarA("/com/quickmira/ui/vista.fxml", sell));
        if (inventory != null) inventory.setOnAction(e -> navegarA("/com/quickmira/ui/inventory-view.fxml", inventory));
        if (Clouse_sesion != null) Clouse_sesion.setOnAction(e -> ((Stage) Clouse_sesion.getScene().getWindow()).close());
    }

    private void navegarA(String fxml, Button origen) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            ((Stage) origen.getScene().getWindow()).setScene(new Scene(root));
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ── FilaProducto ──────────────────────────────────────────────────────
    public static class FilaProducto {
        private final String nombre, fuente;
        private final double precio;
        private final int    total;

        public FilaProducto(String nombre, double precio, int total, String fuente) {
            this.nombre = nombre; this.precio = precio; this.total = total; this.fuente = fuente;
        }
        public String getNombre() { return nombre; }
        public double getPrecio() { return precio; }
        public int    getTotal()  { return total; }
        public String getFuente() { return fuente; }
    }
}