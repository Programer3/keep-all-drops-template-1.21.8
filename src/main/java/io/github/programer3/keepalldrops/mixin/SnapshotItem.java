package io.github.programer3.keepalldrops.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/**
 * Simple top-level snapshot holder used by the ExplosionImplMixin.
 * This must be a top-level class (not an inner class) because mixin classes
 * are not allowed to have ordinary inner classes.
 */
public final class SnapshotItem {
    public final ItemEntity entityRef;
    public final ItemStack stackCopy;
    public final Vec3d pos;

    public SnapshotItem(ItemEntity e, ItemStack s, Vec3d p) {
        this.entityRef = e;
        this.stackCopy = s;
        this.pos = p;
    }
}
