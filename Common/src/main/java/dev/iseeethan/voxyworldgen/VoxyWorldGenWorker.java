package dev.iseeethan.voxyworldgen;

import dev.iseeethan.voxyworldgen.levelpos.ILevelPos;
import dev.iseeethan.voxyworldgen.levelpos.StaticLevelPos;
import dev.iseeethan.voxyworldgen.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class VoxyWorldGenWorker {

    private Map<ServerLevel, Set<ChunkPos>> generated = new HashMap<>();
    private Map<ILevelPos, List<ChunkPos>> positionQueues = new HashMap<>();
    private Map<ILevelPos, Integer> queueIndices = new HashMap<>();
    private Map<ILevelPos, ChunkPos> lastKnownPositions = new HashMap<>();
    private String lastGenerationStyle = null;
    public AtomicInteger doneGenerating = new AtomicInteger();

    // Hook for external chunk processing (e.g., Voxy LOD ingestion)
    private static Consumer<ChunkAccess> chunkGeneratedCallback = null;

    public static void setChunkGeneratedCallback(Consumer<ChunkAccess> callback) {
        chunkGeneratedCallback = callback;
    }

    public void doWork() {
        // Check if generation is enabled
        if (!Services.PLATFORM.isChunkGenerationEnabled()) {
            return;
        }
        
        // Check if generation style changed
        String currentStyle = Services.PLATFORM.getGenerationStyle();
        if (!currentStyle.equals(lastGenerationStyle)) {
            clearCache();
            lastGenerationStyle = currentStyle;
        }
        
        var levelPositions = new ArrayList<>(VoxyWorldGenCommon.getPlayerPos());
        levelPositions.add(VoxyWorldGenCommon.getSpawnPoint());
        
        if (Services.PLATFORM.shouldPrioritizeNearPlayer()) {
            // Process player positions first, then spawn
            for (ILevelPos levelPos : levelPositions) {
                checkPos(levelPos);
            }
        } else {
            // Random order
            Collections.shuffle(levelPositions);
            for (ILevelPos levelPos : levelPositions) {
                checkPos(levelPos);
            }
        }
    }

    /**
     * Check if the player has moved significantly and needs a new queue.
     * Returns true if the queue needs to be regenerated.
     */
    private boolean hasPositionChanged(ILevelPos levelPos) {
        ChunkPos currentPos = levelPos.getPos();
        ChunkPos lastPos = lastKnownPositions.get(levelPos);
        
        if (lastPos == null) {
            return true; // No previous position, needs queue
        }
        
        // Check if moved more than 8 chunks (half a typical radius)
        int dx = Math.abs(currentPos.x - lastPos.x);
        int dz = Math.abs(currentPos.z - lastPos.z);
        
        return dx > 8 || dz > 8;
    }

    /**
     * Get or generate the chunk position queue for a level position based on the current generation style.
     */
    private List<ChunkPos> getPositionQueue(ILevelPos levelPos) {
        // Check if player has moved significantly - regenerate queue if so
        if (hasPositionChanged(levelPos)) {
            positionQueues.remove(levelPos);
            queueIndices.remove(levelPos);
        }
        
        // Check if we need to regenerate the queue
        if (!positionQueues.containsKey(levelPos)) {
            ChunkPos center = levelPos.getPos();
            int radius = levelPos.loadDistance();
            String style = Services.PLATFORM.getGenerationStyle();
            
            List<ChunkPos> queue = switch (style) {
                case "SPIRAL_OUT" -> ChunkPatternGenerator.generateSpiralOut(center, radius);
                case "SPIRAL_IN" -> ChunkPatternGenerator.generateSpiralIn(center, radius);
                case "CONCENTRIC" -> ChunkPatternGenerator.generateConcentric(center, radius);
                case "ORIGINAL" -> ChunkPatternGenerator.generateOriginal(center, radius);
                case "RANDOM" -> ChunkPatternGenerator.generateRandom(center, radius);
                default -> ChunkPatternGenerator.generateSpiralOut(center, radius);
            };
            
            positionQueues.put(levelPos, queue);
            queueIndices.put(levelPos, 0);
            lastKnownPositions.put(levelPos, center);
        }
        
        return positionQueues.get(levelPos);
    }

    private void checkPos(ILevelPos levelPos) {
        if (levelPos == null || levelPos.isCompleted())
            return;
            
        ServerLevel level = levelPos.getServerLevel();
        if (level == null || Services.PLATFORM.isChunkExecutorWorking(level))
            return;
        
        List<ChunkPos> queue = getPositionQueue(levelPos);
        int currentIndex = queueIndices.getOrDefault(levelPos, 0);
        Set<ChunkPos> levelGenerated = generated.computeIfAbsent(level, l -> new HashSet<>());
        
        // Process chunks from the queue
        while (currentIndex < queue.size()) {
            ChunkPos pos = queue.get(currentIndex);
            currentIndex++;
            queueIndices.put(levelPos, currentIndex);
            
            // Skip if already generated
            if (levelGenerated.contains(pos)) {
                continue;
            }
            
            // Check if chunk needs generation
            if (!level.hasChunk(pos.x, pos.z)) {
                ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.EMPTY, true);
                if (!chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL)) {
                    levelGenerated.add(pos);
                    CompletableFuture.supplyAsync(() -> {
                        ChunkAccess generatedChunk = level.getChunkSource().getChunk(pos.x, pos.z,
                                ChunkStatus.FULL, true);
                        // Call the chunk generated callback if set
                        if (chunkGeneratedCallback != null) {
                            chunkGeneratedCallback.accept(generatedChunk);
                        }
                        doneGenerating.getAndIncrement();
                        return null;
                    }, Services.PLATFORM.getChunkGenExecutor(level));
                    return; // One chunk at a time per level position
                }
            }
            levelGenerated.add(pos);
        }
        
        // Queue exhausted - mark as completed (only for static positions like spawn)
        if (currentIndex >= queue.size()) {
            if (levelPos instanceof StaticLevelPos staticLevelPos) {
                staticLevelPos.setCompleted(true);
            }
            // For dynamic player positions, clear the queue so it can regenerate
            // This allows continued generation as the player moves
            positionQueues.remove(levelPos);
            queueIndices.remove(levelPos);
        }
    }

    /**
     * Clear caches when config changes or world changes.
     */
    public void clearCache() {
        positionQueues.clear();
        queueIndices.clear();
        lastKnownPositions.clear();
    }
}
