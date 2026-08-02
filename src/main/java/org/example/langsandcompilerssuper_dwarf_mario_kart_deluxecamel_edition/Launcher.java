package org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition;

import com.almasb.fxgl.app.GameApplication;

/**
 * Entry point kept separate from {@link GameApp} so the jar can be launched without
 * the JavaFX runtime being on the module path as the main class.
 */
public class Launcher {
    public static void main(String[] args) {
        GameApplication.launch(GameApp.class, args);
    }
}
