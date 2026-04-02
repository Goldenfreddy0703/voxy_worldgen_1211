package com.ethan.voxyworldgenv2.mixin;

import com.ethan.voxyworldgenv2.core.Config;
import com.ethan.voxyworldgenv2.core.ChunkGenerationManager;
import com.ethan.voxyworldgenv2.core.LodChunkTracker;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class ChunkSaveMixin {

    @Shadow @Final ServerLevel level;

    /**
     * Intercepts chunk saves. When saveNormalChunks is false, chunks that were only
     * loaded for LOD generation are suppressed unless a player is within view distance.
     */
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void voxyworldgen$onSave(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (Config.DATA.saveNormalChunks) return;

        ChunkPos pos = chunk.getPos();
        LodChunkTracker tracker = LodChunkTracker.getInstance();

        if (!tracker.isLodOnly(this.level.dimension(), pos.toLong())) return;

        if (ChunkGenerationManager.getInstance().isAnyPlayerNear(this.level.dimension(), pos)) {
            tracker.unmark(this.level.dimension(), pos.toLong());
            return;
        }

        tracker.unmark(this.level.dimension(), pos.toLong());
        ((ChunkAccessUnsavedMixin) chunk).voxyworldgen$setUnsaved(false);
        cir.setReturnValue(false);
    }
}
