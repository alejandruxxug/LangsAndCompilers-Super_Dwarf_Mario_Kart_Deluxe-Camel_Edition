package org.example.langsandcompilerssuper_dwarf_mario_kart_deluxecamel_edition.game;

/**
 * Every entity in the world carries one of these. Collision handlers are registered
 * per type pair, so this enum is what drives {@code initPhysics()}.
 */
public enum EntityType {
    KART,
    ITEM_BOX,
    PROJECTILE,
    OBSTACLE,
    WALL,
    CHECKPOINT
}
