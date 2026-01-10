package dev.agnor.passivepregen;

import dev.agnor.passivepregen.config.PassiveConfig;
import dev.agnor.passivepregen.platform.FabricPlatformHelper;
import dev.agnor.passivepregen.platform.Services;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.level.chunk.LevelChunk;

public class PassivePregenFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        PassiveConfig.create();
        ServerLifecycleEvents.SERVER_STARTED.register((FabricPlatformHelper) Services.PLATFORM);
        ServerLifecycleEvents.SERVER_STOPPED.register((FabricPlatformHelper) Services.PLATFORM);
        ServerPlayConnectionEvents.JOIN.register(FabricEvents.getINSTANCE());
        ServerPlayConnectionEvents.DISCONNECT.register(FabricEvents.getINSTANCE());
        ServerTickEvents.END_SERVER_TICK.register(FabricEvents.getINSTANCE());

        // Register Voxy LOD integration callback
        PassivePregenWorker.setChunkGeneratedCallback(chunk -> {
            if (chunk instanceof LevelChunk levelChunk) {
                VoxelIngestService.tryAutoIngestChunk(levelChunk);
            }
        });
    }
}
