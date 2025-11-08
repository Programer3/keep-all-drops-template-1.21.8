package io.github.programer3.keepalldrops.mixin;

import io.github.programer3.keepalldrops.KeepAllDrops;
import io.github.programer3.keepalldrops.config.ConfigManager;
import io.github.programer3.keepalldrops.config.KeepExplosionsConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ExplosionImplMixin (fixed)
 *
 * - SnapshotItem moved to its own top-level class to satisfy mixin rules.
 * - @Shadow fields that are final in ExplosionImpl are annotated with @Final.
 * - Removed any empty 'else' statements.
 */
@Mixin(net.minecraft.world.explosion.ExplosionImpl.class)
public class ExplosionImplMixin {

    // --- Shadowed private final fields from ExplosionImpl (annotate with @Final) ---
    @Shadow @Final private ServerWorld world;
    @Shadow @Final private Vec3d pos;
    @Shadow @Final private float power;
    @Shadow @Final private Entity entity;

    // --- One-time fallback warning flag (unique to avoid name collisions) ---
    @Unique private static final AtomicBoolean FALLBACK_WARNED = new AtomicBoolean(false);

    // --- Per-instance snapshot storage (top-level SnapshotItem class used) ---
    @Unique private List<SnapshotItem> preExplodeItems;

    // -----------------------------
    // 1) Intercept addDroppedItem (block drops)
    // -----------------------------
    @Inject(method = "addDroppedItem(Ljava/util/List;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/BlockPos;)V",
            at = @At("HEAD"), cancellable = true)
    private static void onAddDroppedItem(List<?> droppedItemsOut, ItemStack item, BlockPos pos, CallbackInfo ci) {
        try {
            KeepExplosionsConfig cfg = ConfigManager.getConfig();
            if (cfg == null || !cfg.enabled) {
                return; // disabled: let vanilla run
            }

            ItemStack copy = item.copy();

            Class<?> droppedClass = Class.forName("net.minecraft.world.explosion.ExplosionImpl$DroppedItem");
            Constructor<?> ctor = droppedClass.getDeclaredConstructor(BlockPos.class, ItemStack.class);
            ctor.setAccessible(true);
            Object dropped = ctor.newInstance(pos, copy);

            @SuppressWarnings("unchecked")
            List<Object> rawList = (List<Object>) droppedItemsOut;
            rawList.add(dropped);

            ci.cancel();
            KeepAllDrops.LOGGER.debug("keepalldrops: preserved block drop {} at {}", copy, pos);
        } catch (Throwable t) {
            KeepAllDrops.LOGGER.warn("keepalldrops: failed to preserve block drop via reflection; falling back to vanilla", t);
            maybeWarnOnceClient("[KeepAllDrops] Could not preserve some block drops (fallback). See log.");
        }
    }

    // -----------------------------
    // 2) Snapshot ItemEntity before explosion (HEAD of explode)
    // -----------------------------
    @Inject(method = "explode", at = @At("HEAD"))
    private void beforeExplode(CallbackInfo ci) {
        try {
            KeepExplosionsConfig cfg = ConfigManager.getConfig();
            if (cfg == null || !cfg.enabled) return;

            preExplodeItems = new ArrayList<>();

            float f = this.power * 2.0F;
            int minX = MathHelper.floor(this.pos.x - f - 1.0);
            int maxX = MathHelper.floor(this.pos.x + f + 1.0);
            int minY = MathHelper.floor(this.pos.y - f - 1.0);
            int maxY = MathHelper.floor(this.pos.y + f + 1.0);
            int minZ = MathHelper.floor(this.pos.z - f - 1.0);
            int maxZ = MathHelper.floor(this.pos.z + f + 1.0);

            Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);

            List<Entity> entities = this.world.getOtherEntities(this.entity, box);
            for (Entity e : entities) {
                if (e instanceof ItemEntity ie && !ie.isRemoved()) {
                    ItemStack stackCopy = ie.getStack().copy();
                    Vec3d epos = new Vec3d(ie.getX(), ie.getY(), ie.getZ());
                    preExplodeItems.add(new SnapshotItem(ie, stackCopy, epos));
                }
            }
            KeepAllDrops.LOGGER.debug("keepalldrops: snapshot {} item entities before explosion", preExplodeItems.size());
        } catch (Throwable t) {
            KeepAllDrops.LOGGER.warn("keepalldrops: failed to snapshot item entities before explosion; continuing", t);
            preExplodeItems = null;
            maybeWarnOnceClient("[KeepAllDrops] Could not snapshot item entities (fallback). See log.");
        }
    }

    // -----------------------------
    // 3) After explode — respawn any ItemEntity that was removed
    // -----------------------------
    @Inject(method = "explode", at = @At("TAIL"))
    private void afterExplode(CallbackInfo ci) {
        try {
            if (preExplodeItems == null || preExplodeItems.isEmpty()) return;

            int respawned = 0;
            for (SnapshotItem snap : preExplodeItems) {
                try {
                    if (snap.entityRef.isRemoved()) {
                        ItemEntity spawned = new ItemEntity(this.world, snap.pos.x, snap.pos.y, snap.pos.z, snap.stackCopy.copy());
                        spawned.setPickupDelay(10);
                        this.world.spawnEntity(spawned);
                        respawned++;
                    }
                } catch (Throwable inner) {
                    KeepAllDrops.LOGGER.warn("keepalldrops: failed to respawn a pre-explode item; continuing", inner);
                }
            }
            if (respawned > 0) {
                KeepAllDrops.LOGGER.debug("keepalldrops: respawned {} removed item entities after explosion", respawned);
            }
        } catch (Throwable t) {
            KeepAllDrops.LOGGER.warn("keepalldrops: failed in afterExplode handling; continuing", t);
            maybeWarnOnceClient("[KeepAllDrops] Could not respawn items after explosion (fallback). See log.");
        } finally {
            preExplodeItems = null;
        }
    }

    // -----------------------------
    // Helper: warn once in client chat (reflective, safe)
    // -----------------------------
    @Unique
    private static void maybeWarnOnceClient(String message) {
        try {
            if (!FALLBACK_WARNED.getAndSet(true) && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                Class<?> mcClass = Class.forName("net.minecraft.client.MinecraftClient");
                Method getInstance = mcClass.getMethod("getInstance");
                Object mc = getInstance.invoke(null);
                if (mc != null) {
                    Method execute = mcClass.getMethod("execute", Runnable.class);
                    Text msg = Text.literal(message).formatted(Formatting.YELLOW).styled(s -> s.withItalic(true));
                    Runnable r = () -> {
                        try {
                            Method getPlayer = mcClass.getMethod("getPlayer");
                            Object player = getPlayer.invoke(mc);
                            if (player != null) {
                                Method sendMessage = player.getClass().getMethod("sendMessage", Text.class, boolean.class);
                                sendMessage.invoke(player, msg, false);
                            }
                        } catch (Throwable ignored) {}
                    };
                    execute.invoke(mc, r);
                }
            }
        } catch (Throwable ignored) {
            // never crash on warning
        }
    }
}
