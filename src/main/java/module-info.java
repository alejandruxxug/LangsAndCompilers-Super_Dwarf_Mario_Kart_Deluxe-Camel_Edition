module org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition {
    requires com.almasb.fxgl.all;

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;

    // FXGL discovers GameApplication and @Spawns factory methods reflectively.
    opens org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition to com.almasb.fxgl.core;
    opens org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition.game to com.almasb.fxgl.core;

    // FXMLLoader instantiates controllers reflectively.
    opens org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition.ui to javafx.fxml;

    exports org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition;
    exports org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition.game;
    exports org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition.ui;
}
