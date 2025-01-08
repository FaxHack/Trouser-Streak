package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.AcknowledgeChunksC2SPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import pwn.noobs.trouserstreak.Trouser;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaveDisturbanceDetector extends Module {
    // Setting Groups
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General Settings
    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
            .name("Chat feedback")
            .description("Displays info for you.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> displayCoords = sgGeneral.add(new BoolSetting.Builder()
            .name("DisplayCoords")
            .description("Displays coords of air disturbances in chat.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> falsePositiveDistance = sgGeneral.add(new IntSetting.Builder()
            .name("False Positive Distance")
            .description("If extra normal air within this range of the cave air disturbance then ignore the disturbance")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 10)
            .build()
    );

    // Render Settings
    private final Setting<Boolean> removeRenderDist = sgRender.add(new BoolSetting.Builder()
            .name("RemoveOutsideRenderDistance")
            .description("Removes the cached disturbances when they leave the defined render distance.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
            .name("Render-Distance(Chunks)")
            .description("How many chunks from the character to render the detected disturbances.")
            .defaultValue(32)
            .min(6)
            .sliderRange(6, 1024)
            .build()
    );

    private final Setting<Boolean> trace = sgRender.add(new BoolSetting.Builder()
            .name("Tracers")
            .description("Show tracers to the air disturbances.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> nearestTracer = sgRender.add(new BoolSetting.Builder()
            .name("Tracer to nearest Disturbance Only")
            .description("Show only one tracer to the nearest air disturbance.")
            .defaultValue(false)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("air-disturbance-side-color")
            .description("Color of possible air disturbances.")
            .defaultValue(new SettingColor(255, 0, 130, 55))
            .visible(() -> shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("air-disturbance-line-color")
            .description("Color of possible air disturbances.")
            .defaultValue(new SettingColor(255, 0, 130, 200))
            .visible(() -> shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both || trace.get())
            .build()
    );

    // State Management
    private static final ExecutorService TASK_EXECUTOR = Executors.newCachedThreadPool();
    private final Set<ChunkPos> scannedChunks = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> scannedAir = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> disturbanceLocations = Collections.synchronizedSet(new HashSet<>());
    private BlockPos closestDisturbance = new BlockPos(2000000000, 2000000000, 2000000000);
    private double closestDistance = 2000000000;

    public CaveDisturbanceDetector() {
        super(Trouser.Main, "CaveDisturbanceDetector", "Scans for single air blocks within the cave air blocks found in caves and underground structures in 1.13+ chunks. There are several false positives.");
    }

    @Override
    public void onActivate() {
        clearChunkData();
        scanAirBlocks();
    }

    @Override
    public void onDeactivate() {
        clearChunkData();
    }

    private void clearChunkData() {
        scannedChunks.clear();
        scannedAir.clear();
        disturbanceLocations.clear();
        resetClosestDisturbance();
    }

    private void resetClosestDisturbance() {
        closestDisturbance = new BlockPos(2000000000, 2000000000, 2000000000);
        closestDistance = 2000000000;
    }

    private void scanAirBlocks() {
        if (mc.world == null) return;

        ChunkPos playerChunkPos = new ChunkPos(mc.player.getBlockPos());
        int radius = renderDistance.get();
        List<ChunkPos> chunksToProcess = new ArrayList<>();

        for (int chunkX = playerChunkPos.x - radius; chunkX <= playerChunkPos.x + radius; chunkX++) {
            for (int chunkZ = playerChunkPos.z - radius; chunkZ <= playerChunkPos.z + radius; chunkZ++) {
                chunksToProcess.add(new ChunkPos(chunkX, chunkZ));
            }
        }

        chunksToProcess.parallelStream().forEach(this::processUnscannedChunk);
    }

    private void processUnscannedChunk(ChunkPos chunkPos) {
        WorldChunk chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
        if (chunk != null && !scannedChunks.contains(chunk.getPos())) {
            processChunk(chunk);
            scannedChunks.add(chunk.getPos());
        }
    }

    private void processChunk(WorldChunk chunk) {
        int minY = mc.world.getBottomY();
        int maxY = mc.world.getRegistryKey() == World.NETHER ? 126 : 180;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos blockPos = new BlockPos(
                            chunk.getPos().getStartX() + x,
                            y,
                            chunk.getPos().getStartZ() + z
                    );
                    if (chunk.getBlockState(blockPos).getBlock() == Blocks.CAVE_AIR) {
                        checkSurroundingBlocks(blockPos);
                    }
                }
            }
        }
    }

    private void checkSurroundingBlocks(BlockPos centerPos) {
        BlockPos[] directions = {
                centerPos.north(), centerPos.south(),
                centerPos.west(), centerPos.east(),
                centerPos.up(), centerPos.down()
        };

        for (BlockPos airPos : directions) {
            if (scannedAir.contains(airPos)) continue;

            checkForDisturbance(airPos);
            scannedAir.add(airPos);
        }
    }

    private void checkForDisturbance(BlockPos airPos) {
        if (!isRegularAir(airPos)) return;
        if (!hasAdjacentSolidBlocks(airPos)) return;
        if (!isIsolatedAir(airPos)) return;

        disturbanceFound(airPos);
    }

    private boolean isRegularAir(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock() == Blocks.AIR;
    }

    private boolean hasAdjacentSolidBlocks(BlockPos pos) {
        return !isRegularAir(pos.north()) &&
                !isRegularAir(pos.south()) &&
                !isRegularAir(pos.east()) &&
                !isRegularAir(pos.west()) &&
                !isRegularAir(pos.up()) &&
                !isRegularAir(pos.down());
    }

    private boolean isIsolatedAir(BlockPos pos) {
        int range = falsePositiveDistance.get();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos checkPos = pos.add(x, y, z);
                    if (checkPos.equals(pos)) continue;
                    if (isRegularAir(checkPos)) return false;
                }
            }
        }
        return true;
    }

    private void disturbanceFound(BlockPos disturbance) {
        if (disturbanceLocations.contains(disturbance)) return;

        disturbanceLocations.add(disturbance);
        if (chatFeedback.get()) {
            String message = displayCoords.get()
                    ? "Disturbance in the Cave Air found: " + disturbance
                    : "Disturbance in the Cave Air found!";
            ChatUtils.sendMsg(Text.of(message));
        }
    }

    private void updateClosestDisturbance() {
        if (disturbanceLocations.isEmpty()) return;

        for (BlockPos pos : disturbanceLocations) {
            double distance = Math.sqrt(
                    Math.pow(pos.getX() - mc.player.getBlockX(), 2) +
                            Math.pow(pos.getZ() - mc.player.getBlockZ(), 2)
            );

            if (distance < closestDistance) {
                closestDisturbance = pos;
                closestDistance = distance;
            }
        }
        closestDistance = 2000000000;
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (event.screen instanceof DisconnectedScreen ||
                event.screen instanceof DownloadingTerrainScreen) {
            clearChunkData();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearChunkData();
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (nearestTracer.get()) {
            updateClosestDisturbance();
        }
        if (removeRenderDist.get()) {
            removeChunksOutsideRenderDistance();
        }
    }

    @EventHandler
    private void onReadPacket(PacketEvent.Receive event) {
        if (event.packet instanceof AcknowledgeChunksC2SPacket) return;

        if (event.packet instanceof ChunkDataS2CPacket packet && mc.world != null) {
            handleChunkDataPacket(packet);
        }
    }

    private void handleChunkDataPacket(ChunkDataS2CPacket packet) {
        ChunkPos packetChunkPos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());

        if (mc.world.getChunkManager().getChunk(packet.getChunkX(), packet.getChunkZ()) != null) {
            return;
        }

        WorldChunk chunk = new WorldChunk(mc.world, packetChunkPos);
        try {
            CompletableFuture.runAsync(() -> {
                chunk.loadFromPacket(
                        packet.getChunkData().getSectionsDataBuf(),
                        new NbtCompound(),
                        packet.getChunkData().getBlockEntities(packet.getChunkX(), packet.getChunkZ())
                );
            }, TASK_EXECUTOR).join();

            if (!scannedChunks.contains(chunk.getPos())) {
                processChunk(chunk);
                scannedChunks.add(chunk.getPos());
            }
        } catch (Exception ignored) {}
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!shouldRender()) return;

        synchronized (disturbanceLocations) {
            renderDisturbances(event);
        }
    }

    private boolean shouldRender() {
        return (sideColor.get().a > 5 || lineColor.get().a > 5) && mc.player != null;
    }

    private void renderDisturbances(Render3DEvent event) {
        for (BlockPos pos : disturbanceLocations) {
            if (!isWithinRenderDistance(pos)) continue;

            renderDisturbanceBox(pos, event);
        }

        if (nearestTracer.get()) {
            renderNearestDisturbance(event);
        }
    }

    private boolean isWithinRenderDistance(BlockPos pos) {
        BlockPos playerPos = new BlockPos(mc.player.getBlockX(), pos.getY(), mc.player.getBlockZ());
        return playerPos.isWithinDistance(pos, renderDistance.get() * 16);
    }
    private void renderDisturbanceBox(BlockPos pos, Render3DEvent event) {
        Box box = new Box(
                new Vec3d(pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1),
                new Vec3d(pos.getX(), pos.getY(), pos.getZ())
        );

        renderBox(box, sideColor.get(), lineColor.get(), shapeMode.get(), event);

        if (trace.get() && !nearestTracer.get()) {
            renderTracer(box, lineColor.get(), event);
        }
    }

    private void renderNearestDisturbance(Render3DEvent event) {
        Box box = new Box(
                new Vec3d(closestDisturbance.getX() + 1, closestDisturbance.getY() + 1, closestDisturbance.getZ() + 1),
                new Vec3d(closestDisturbance.getX(), closestDisturbance.getY(), closestDisturbance.getZ())
        );

        renderBox(box, sideColor.get(), lineColor.get(), ShapeMode.Sides, event);

        if (trace.get()) {
            renderTracer(box, lineColor.get(), event);
        }
    }

    private void renderBox(Box box, Color sides, Color lines, ShapeMode shapeMode, Render3DEvent event) {
        event.renderer.box(
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ,
                sides,
                new Color(0, 0, 0, 0),
                shapeMode,
                0
        );
    }

    private void renderTracer(Box box, Color lineColor, Render3DEvent event) {
        double distanceX = Math.abs(box.minX - RenderUtils.center.x);
        double distanceZ = Math.abs(box.minZ - RenderUtils.center.z);
        double renderDistanceBlocks = renderDistance.get() * 16;

        if (distanceX <= renderDistanceBlocks && distanceZ <= renderDistanceBlocks) {
            event.renderer.line(
                    RenderUtils.center.x,
                    RenderUtils.center.y,
                    RenderUtils.center.z,
                    box.minX + 0.5,
                    box.minY + ((box.maxY - box.minY) / 2),
                    box.minZ + 0.5,
                    lineColor
            );
        }
    }

    private void removeChunksOutsideRenderDistance() {
        double renderDistanceBlocks = renderDistance.get() * 16;
        BlockPos playerPos = mc.player.getBlockPos();

        removeOutOfRangeChunks(playerPos, renderDistanceBlocks);
        removeOutOfRangeBlockPos(disturbanceLocations, renderDistanceBlocks);
        removeOutOfRangeBlockPos(scannedAir, renderDistanceBlocks);
    }

    private void removeOutOfRangeChunks(BlockPos playerPos, double renderDistanceBlocks) {
        scannedChunks.removeIf(chunkPos ->
                !playerPos.isWithinDistance(
                        new BlockPos(
                                chunkPos.getCenterX(),
                                mc.player.getBlockY(),
                                chunkPos.getCenterZ()
                        ),
                        renderDistanceBlocks
                )
        );
    }

    private void removeOutOfRangeBlockPos(Set<BlockPos> positions, double renderDistanceBlocks) {
        positions.removeIf(pos -> {
            BlockPos playerPos = new BlockPos(
                    mc.player.getBlockX(),
                    pos.getY(),
                    mc.player.getBlockZ()
            );
            return !playerPos.isWithinDistance(pos, renderDistanceBlocks);
        });
    }
}