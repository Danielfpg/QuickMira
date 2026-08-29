# QuickMira

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue?logo=java&logoColor=white)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white)

Aplicación de escritorio para gestión de inventario y ventas, construida en JavaFX con soporte para tres motores de base de datos intercambiables: SQLite, MySQL y MongoDB.

## Descripción

QuickMira es un sistema de inventario y ventas pensado para un negocio pequeño. Permite iniciar sesión como operador, registrar y editar productos, controlar el stock y el valor total del inventario, revisar estadísticas de los productos más relevantes y mantener un historial de auditoría de los cambios, sin importar qué motor de base de datos esté activo en cada momento.

## Características principales

- **Inicio de sesión de operadores**, validado contra una tabla `operadores` en SQLite (se crea automáticamente un usuario `admin` / `1234` en el primer arranque).
- **Selector de base de datos en caliente**: cambia entre SQLite, MySQL y MongoDB desde la vista de Inventario, sin reiniciar la aplicación.
- **CRUD de productos**: alta, edición y eliminación de productos, con nombre, precio, cantidad e imagen.
- **Panel de estadísticas**: gráfico de barras y tabla Top 5 de productos, combinando los datos de la base activa con el respaldo local en texto.
- **Auditoría automática**: cada edición o eliminación de un producto en SQL/MySQL guarda una copia del estado anterior en la colección `modificados` de MongoDB, visible desde un visor de historial dedicado.
- **Respaldo de ventas en texto plano**, registradas también en `venta/ventas.txt` con un formato similar a JSON/BSON.
- **Importación de productos desde una API externa** *(módulo en construcción — ver [Estado del proyecto](#estado-del-proyecto))*.

## Tecnologías

| Categoría | Detalle |
|---|---|
| Lenguaje | Java 21 |
| Interfaz | JavaFX 21 (Controls + FXML) |
| Build | Maven, con `javafx-maven-plugin` |
| Bases de datos | SQLite (`sqlite-jdbc`), MySQL (`mysql-connector-j`), MongoDB (`mongodb-driver-sync`) |
| Logging | SLF4J |
| Pruebas | JUnit 5 (configurado en `pom.xml`; aún sin pruebas escritas) |

> El driver de PostgreSQL también está declarado en `pom.xml`, pero todavía no está conectado a ninguna funcionalidad.

## Estructura del proyecto

```
QuickMira/
├── pom.xml
├── src/main/java/com/quickmira/
│   ├── Main.java                          # Punto de entrada
│   ├── Controller/
│   │   ├── LoginController.java           # Autenticación de operadores
│   │   ├── Controlador.java               # Vista principal / alta de productos
│   │   ├── ControladorInventoryView.java  # CRUD de inventario y selector de BD
│   │   └── ControladorEstadisticas.java   # Panel de estadísticas
│   └── Database/
│       ├── Conexion.java                  # Conexión a SQLite / MySQL / MongoDB
│       └── CargarProductos.java           # Persistencia, carga masiva y auditoría
├── src/main/resources/com/quickmira/
│   ├── ui/       # Vistas FXML: login, vista principal, inventario, estadísticas
│   └── images/   # Imágenes de productos e íconos
└── venta/
    └── ventas.txt   # Respaldo local de ventas
```

## Requisitos previos

- JDK 21 o superior
- Maven 3.9+ (o el wrapper `./mvnw` incluido en el repositorio)
- Opcional, según el motor que quieras usar: un servidor MySQL o MongoDB accesible (SQLite no requiere nada adicional)

## Instalación y ejecución

```bash
git clone https://github.com/Danielfpg/QuickMira.git
cd QuickMira

# Ejecutar en modo desarrollo
./mvnw clean javafx:run

# (Opcional) generar una imagen de aplicación autocontenida con jlink
./mvnw clean javafx:jlink
```

## Configuración de bases de datos

Por defecto la aplicación usa **SQLite**: el login se valida contra `quicmira.db` y el resto de la app (productos) usa `quicmira2.db`, ambos en la raíz del proyecto.

Para conectarte a tu propio servidor de MySQL o MongoDB:

1. Abre `src/main/java/com/quickmira/Database/Conexion.java`.
2. Actualiza `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASS` y/o `MONGO_HOST`, `MONGO_PORT`, `MONGO_DB` con los datos de tu servidor.
3. Cambia de motor en caliente desde los botones **SQLite / MySQL / MongoDB** en la vista de Inventario.

> ⚠️ Estas credenciales quedan escritas directamente en el código fuente. Para un uso más allá de lo local/académico, conviene moverlas a variables de entorno o a un archivo de configuración excluido del control de versiones.

## Uso

1. Inicia sesión (usuario `admin`, contraseña `1234` por defecto, o cualquier operador registrado).
2. Desde el menú principal, **agrega** productos con nombre, precio, cantidad e imagen.
3. En **Inventario**, edita o elimina productos y cambia el motor de base de datos activo.
4. En **Estadísticas**, consulta el ranking de productos y el valor total del inventario.
5. Desde Inventario, abre el visor de **respaldo/auditoría** para ver el historial de cambios guardado en MongoDB.

## Estado del proyecto

- El botón **Importar API** depende de las clases `com.quickmira.Service.ProductApiService` y `com.quickmira.Model.ProductoApi`, y de la vista `modulo-api.fxml`. Ninguna de las tres está incluida todavía en el repositorio, así que el proyecto no compila hasta que se agreguen.
- Las contraseñas de los operadores se guardan en texto plano en la base de datos; se recomienda aplicar hash (por ejemplo, con bcrypt) antes de cualquier uso fuera del entorno académico.
- No hay pruebas automatizadas escritas aún, aunque JUnit 5 ya está configurado.

## Autor

**Daniel Gonzalez** — [@Danielfpg](https://github.com/Danielfpg)

## Licencia

Proyecto académico/personal. Licencia por definir.
