module org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;

    opens org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition to javafx.fxml;
    exports org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition;
}