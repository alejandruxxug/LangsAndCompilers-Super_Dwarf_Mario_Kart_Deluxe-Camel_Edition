package org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition.game.KartFactory;

import static com.almasb.fxgl.dsl.FXGL.getGameWorld;
import static com.almasb.fxgl.dsl.FXGL.spawn;

public class GameApp extends GameApplication {

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("Super Dwarf Mario Kart Deluxe - Camel Edition");
        settings.setVersion("1.0");
        settings.setWidth(960);
        settings.setHeight(640);
    }

    @Override
    protected void initGame() {
        getGameWorld().addEntityFactory(new KartFactory());
        spawn("kart", 100, 100);
    }
}
