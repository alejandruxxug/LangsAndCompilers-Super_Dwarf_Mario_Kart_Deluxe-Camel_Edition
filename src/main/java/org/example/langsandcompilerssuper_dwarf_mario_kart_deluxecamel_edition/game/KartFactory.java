package org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition.game;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;
import javafx.scene.paint.Color;

/**
 * The one place entities are built. Anything spawned with {@code spawn("name", ...)}
 * is declared here with {@code @Spawns}, so there is a single list of what can exist
 * in the world.
 *
 * <p>FXGL finds these methods reflectively, which is why the enclosing package is
 * opened to {@code com.almasb.fxgl.core} in {@code module-info.java}.
 */
public class KartFactory implements EntityFactory {

    @Spawns("kart")
    public Entity newKart(SpawnData data) {
        return FXGL.entityBuilder(data)
                .type(EntityType.KART)
                .viewWithBBox(new javafx.scene.shape.Rectangle(40, 24, Color.CRIMSON))
                .collidable()
                .build();
    }
}
