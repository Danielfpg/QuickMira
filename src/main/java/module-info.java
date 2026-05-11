module com.quickmira {
    requires javafx.controls;
    requires javafx.fxml;
    requires jdk.jdi;
    requires java.sql;
    requires org.mongodb.driver.sync.client;
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;

    opens com.quickmira to javafx.fxml;
    exports com.quickmira;
    exports com.quickmira.Controller;
    opens com.quickmira.Controller to javafx.fxml;
}