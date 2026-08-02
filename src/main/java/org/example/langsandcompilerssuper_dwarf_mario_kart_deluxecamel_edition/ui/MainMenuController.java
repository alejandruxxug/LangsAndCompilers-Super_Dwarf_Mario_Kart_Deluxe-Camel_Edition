package org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class MainMenuController {
    @FXML
    private Label welcomeText;

    @FXML
    protected void onStartButtonClick() {
        welcomeText.setText("Start your engines!");
    }
}
