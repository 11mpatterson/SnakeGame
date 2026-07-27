module com.example.snakegame {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.jetbrains.annotations;
    requires java.desktop;
    requires java.prefs;
    requires javafx.media;


    opens com.example.snakegame to javafx.fxml;
    exports com.example.snakegame;
}