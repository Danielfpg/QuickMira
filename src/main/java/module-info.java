module com.quickmira {
    requires javafx.controls;
    requires javafx.fxml;
    requires jdk.jdi;
    requires java.sql;


    opens com.quickmira to javafx.fxml;
    exports com.quickmira;
    exports com.quickmira.Controller;
    opens com.quickmira.Controller to javafx.fxml;
}