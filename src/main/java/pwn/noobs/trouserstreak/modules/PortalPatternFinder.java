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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import pwn.noobs.trouserstreak.Trouser;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PortalPatternFinder extends Module {
    // Settings Groups
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General Settings
    private final Setting<Boolean> displayCoords = sgGeneral.add(new BoolSetting.Builder()
            .name("DisplayCoords")
            .description("Displays coords of portal patterns in chat.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> ignoreCorners = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-corner-blocks")
            .description("Also matches portal patterns that are missing the corner blocks.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> falsePositivesRemoval = sgGeneral.add(new BoolSetting.Builder()
            .name("False Positive Removal")
            .description("Removes false positives in relation to the air above and below the portal pattern.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> nonAirPercent = sgGeneral.add(new IntSetting.Builder()
            .name("Non-Air Percent")
            .description("What percentage of the blocks in the portal shape can be non-air.")
            .defaultValue(20)
            .min(0)
            .sliderRange(0, 100)
            .build()
    );

    private final Setting<Integer> adjacentAirPercent = sgGeneral.add(new IntSetting.Builder()
            .name("Adjacent Air Percent")
            .description("What percentage of the blocks in the portal shape that is allowed to have air blocks adjacent to it.")
            .defaultValue(15)
            .min(0)
            .sliderRange(0, 100)
            .build()
    );

    private final Setting<Integer> portalWidth = sgGeneral.add(new IntSetting.Builder()
            .name("Portal Width")
            .description("finds portals that are up to this large")
            .defaultValue(5)
            .min(4)
            .sliderRange(4, 8)
            .build()
    );

    private final Setting<Integer> portalHeight = sgGeneral.add(new IntSetting.Builder()
            .name("Portal Height")
            .description("finds portals that are up to this large")
            .defaultValue(5)
            .min(5)
            .sliderRange(5, 8)
            .build()
    );

    // Render Settings
    private final Setting<Boolean> removeOutsideRenderDist = sgRender.add(new BoolSetting.Builder()
            .name("RemoveOutsideRenderDistance")
            .description("Removes the cached portal patterns when they leave the defined render distance.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
            .name("Render-Distance(Chunks)")
            .description("How many chunks from the character to render the portal patterns.")
            .defaultValue(32)
            .min(6)
            .sliderRange(6, 1024)
            .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
            .name("Tracers")
            .description("Show tracers to the portal patterns.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> nearestTracer = sgRender.add(new BoolSetting.Builder()
            .name("Tracer to nearest Portal Only")
            .description("Show only one tracer to the nearest portal pattern.")
            .defaultValue(false)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> portalSideColor = sgRender.add(new ColorSetting.Builder()
            .name("possible-portal-side-color")
            .description("Color of possible portal locations.")
            .defaultValue(new SettingColor(170, 0, 255, 55))
            .visible(() -> shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> portalLineColor = sgRender.add(new ColorSetting.Builder()
            .name("possible-portal-line-color")
            .description("Color of possible portal locations.")
            .defaultValue(new SettingColor(170, 0, 255, 200))
            .visible(() -> shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both || tracers.get())
            .build()
    );

    // State Variables
    private static final ExecutorService taskExecutor = Executors.newCachedThreadPool();
    private final Set<ChunkPos> scannedChunks = Collections.synchronizedSet(new HashSet<>());
    private final Set<Box> possiblePortalLocations = Collections.synchronizedSet(new HashSet<>());
    private Vec3i closestPortal = new Vec3i(2000000000, 2000000000, 2000000000);
    private double portalDistance = 2000000000;

    public PortalPatternFinder() {
        super(Trouser.Main, "PortalPatternFinder", "Scans for the shapes of broken/removed Nether Portals within the cave air blocks found in caves and underground structures in 1.13+ chunks. **May be useful for finding portal skips in the Nether**");
    }

    @Override
    public void onActivate() {
        clearChunkData();
        scanAir();
    }

    @Override
    public void onDeactivate() {
        clearChunkData();
    }

    private void clearChunkData() {
        scannedChunks.clear();
        possiblePortalLocations.clear();
        closestPortal = new Vec3i(2000000000, 2000000000, 2000000000);
        portalDistance = 2000000000;
    }

    private void scanAir() {
        if (mc.world == null) return;

        ChunkPos playerChunkPos = new ChunkPos(mc.player.getBlockPos());
        int distance = renderDistance.get();

        List<ChunkPos> chunksToProcess = new ArrayList<>();
        for (int x = playerChunkPos.x - distance; x <= playerChunkPos.x + distance; x++) {
            for (int z = playerChunkPos.z - distance; z <= playerChunkPos.z + distance; z++) {
                chunksToProcess.add(new ChunkPos(x, z));
            }
        }

        chunksToProcess.parallelStream().forEach(this::processChunkIfUnscanned);
    }

    private void processChunkIfUnscanned(ChunkPos chunkPos) {
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
                    BlockPos pos = new BlockPos(
                            chunk.getPos().getStartX() + x,
                            y,
                            chunk.getPos().getStartZ() + z
                    );
                    if (chunk.getBlockState(pos).getBlock() == Blocks.CAVE_AIR) {
                        checkSurroundingAir(pos);
                    }
                }
            }
        }
    }

    private void checkSurroundingAir(BlockPos center) {
        BlockPos[] directions = {
                center.north(), center.south(),
                center.west(), center.east()
        };
        BlockPos[] adjacentBlocks = {
                center.north().north(),
                center.south().south(),
                center.west().west(),
                center.east().east()
        };

        for (int i = 0; i < directions.length; i++) {
            if (isAirBlock(directions[i]) && !isAirBlock(adjacentBlocks[i])) {
                findAirShape(directions[i]);
            }
        }
    }

    private boolean isAirBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock() == Blocks.AIR;
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
        updateClosestPortal();
        if (removeOutsideRenderDist.get()) {
            removeChunksOutsideRenderDistance();
        }
    }

    private void updateClosestPortal() {
        if (!nearestTracer.get() || possiblePortalLocations.isEmpty()) return;

        portalDistance = 2000000000;
        for (Box box : possiblePortalLocations) {
            double distance = Math.sqrt(
                    Math.pow(box.getCenter().x - 1 - mc.player.getBlockX(), 2) +
                            Math.pow(box.getCenter().z - 1 - mc.player.getBlockZ(), 2)
            );

            if (distance < portalDistance) {
                closestPortal = new Vec3i(
                        (int) (box.getCenter().x - 1),
                        (int) (box.getCenter().y - 1),
                        (int) (box.getCenter().z - 1)
                );
                portalDistance = distance;
            }
        }
    }

    @EventHandler
    private void onReadPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ChunkDataS2CPacket packet) || mc.world == null) return;

        processNewChunkPacket(packet);
    }

    private void processNewChunkPacket(ChunkDataS2CPacket packet) {
        ChunkPos chunkPos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
        if (mc.world.getChunkManager().getChunk(packet.getChunkX(), packet.getChunkZ()) != null) return;

        WorldChunk chunk = new WorldChunk(mc.world, chunkPos);
        try {
            CompletableFuture.runAsync(() -> {
                chunk.loadFromPacket(
                        packet.getChunkData().getSectionsDataBuf(),
                        new NbtCompound(),
                        packet.getChunkData().getBlockEntities(packet.getChunkX(), packet.getChunkZ())
                );
            }, taskExecutor).join();
        } catch (CompletionException e) {
            return;
        }

        if (!scannedChunks.contains(chunk.getPos())) {
            processChunk(chunk);
            scannedChunks.add(chunk.getPos());
        }
    }

    private void findAirShape(BlockPos pos) {
        findEastWestPortals(pos);
        findNorthSouthPortals(pos);
    }

    private void findEastWestPortals(BlockPos pos) {
        List<BlockPos> airBlocks = new ArrayList<>();
        int rejects = 0;
        int nonAirRejects = 0;

        collectEastWestBlocks(pos, airBlocks, rejects, nonAirRejects);
        processPortalShape(airBlocks, rejects, nonAirRejects, true);
    }

    private void findNorthSouthPortals(BlockPos pos) {
        List<BlockPos> airBlocks = new ArrayList<>();
        int rejects = 0;
        int nonAirRejects = 0;

        collectNorthSouthBlocks(pos, airBlocks, rejects, nonAirRejects);
        processPortalShape(airBlocks, rejects, nonAirRejects, false);
    }

    private void collectEastWestBlocks(BlockPos pos, List<BlockPos> airBlocks, int rejects, int nonAirRejects) {
        int areaWidth = (portalWidth.get() / 2) + 1;
        int areaHeight = (portalHeight.get() / 2) + 1;

        for (int x = -areaWidth; x <= areaWidth; x++) {
            for (int y = -areaHeight; y <= areaHeight; y++) {
                BlockPos checkPos = pos.add(x, y, 0);
                BlockState state = mc.world.getBlockState(checkPos);
                if (state.getBlock() == Blocks.AIR) {
                    int nonAirSides = countNonAirSides(checkPos, true);
                    if (nonAirSides >= 2) {
                        airBlocks.add(checkPos);
                    } else {
                        rejects++;
                        airBlocks.add(checkPos);
                    }
                } else if (state.getBlock() != Blocks.CAVE_AIR) {
                    nonAirRejects++;
                    airBlocks.add(checkPos);
                }
            }
        }
    }

    private void collectNorthSouthBlocks(BlockPos pos, List<BlockPos> airBlocks, int rejects, int nonAirRejects) {
        int areaWidth = (portalWidth.get() / 2) + 1;
        int areaHeight = (portalHeight.get() / 2) + 1;

        for (int z = -areaWidth; z <= areaWidth; z++) {
            for (int y = -areaHeight; y <= areaHeight; y++) {
                BlockPos checkPos = pos.add(0, y, z);
                BlockState state = mc.world.getBlockState(checkPos);
                if (state.getBlock() == Blocks.AIR) {
                    int nonAirSides = countNonAirSides(checkPos, false);
                    if (nonAirSides >= 2) {
                        airBlocks.add(checkPos);
                    } else {
                        rejects++;
                        airBlocks.add(checkPos);
                    }
                } else if (state.getBlock() != Blocks.CAVE_AIR) {
                    nonAirRejects++;
                    airBlocks.add(checkPos);
                }
            }
        }
    }

    private int countNonAirSides(BlockPos pos, boolean eastWest) {
        int count = 0;
        BlockPos[] sides = eastWest ?
                new BlockPos[]{pos.north(), pos.south()} :
                new BlockPos[]{pos.west(), pos.east()};

        for (BlockPos side : sides) {
            if (mc.world.getBlockState(side).getBlock() != Blocks.AIR) {
                count++;
            }
        }
        return count;
    }

    private void processPortalShape(List<BlockPos> airBlocks, int rejects, int nonAirRejects, boolean eastWest) {
        double nonAirRatio = ((double) nonAirRejects / (airBlocks.size() - rejects)) * 100;
        double rejectRatio = ((double) rejects / airBlocks.size()) * 100;

        if (nonAirRatio <= nonAirPercent.get() && rejectRatio <= adjacentAirPercent.get()) {
            for (BlockPos block : airBlocks) {
                checkPortalDimensions(block, airBlocks, eastWest);
            }
        }
    }

    private void checkPortalDimensions(BlockPos startBlock, List<BlockPos> airBlocks, boolean eastWest) {
        for (int width = 4; width <= portalWidth.get(); width++) {
            for (int height = 5; height <= portalHeight.get(); height++) {
                if (isValidPortalShape(airBlocks, startBlock, width, height, eastWest)) {
                    addPortalBox(startBlock, width, height, eastWest);
                }
            }
        }
    }

    private void addPortalBox(BlockPos start, int width, int height, boolean eastWest) {
        BlockPos end = eastWest ?
                new BlockPos(start.getX() + width - 1, start.getY() + height - 1, start.getZ()) :
                new BlockPos(start.getX(), start.getY() + height - 1, start.getZ() + width - 1);

        if (falsePositivesRemoval.get() && hasAdjacentAir(start, width, height, eastWest)) {
            return;
        }

        Box portalBox = new Box(
                new Vec3d(start.getX(), start.getY(), start.getZ()),
                new Vec3d(end.getX() + 1, end.getY() + 1, end.getZ() + 1)
        );

        if (!intersectsExistingPortals(portalBox)) {
            possiblePortalLocations.add(portalBox);
            notifyPortalFound(portalBox);
        }
    }

    private boolean hasAdjacentAir(BlockPos start, int width, int height, boolean eastWest) {
        for (int i = 0; i < width; i++) {
            BlockPos lower = eastWest ?
                    start.add(i, -1, 0) :
                    start.add(0, -1, i);
            BlockPos upper = eastWest ?
                    start.add(i, height + 1, 0) :
                    start.add(0, height + 1, i);

            if (isAirBlock(lower) || isAirBlock(upper)) {
                return true;
            }
        }
        return false;
    }

    private boolean intersectsExistingPortals(Box newBox) {
        for (Box existingBox : possiblePortalLocations) {
            if (newBox.intersects(existingBox)) {
                return true;
            }
        }
        return false;
    }

    private void notifyPortalFound(Box portalBox) {
        if (displayCoords.get()) {
            ChatUtils.sendMsg(Text.of("Possible portal found: " + portalBox.getCenter()));
        } else {
            ChatUtils.sendMsg(Text.of("Possible portal found!"));
        }
    }

    private boolean isValidPortalShape(List<BlockPos> portalBlocks, BlockPos startBlock, int width, int height, boolean eastWest) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos checkPos = eastWest ?
                        startBlock.add(x, y, 0) :
                        startBlock.add(0, y, x);

                if (ignoreCorners.get() && isCornerBlock(x, y, width, height)) {
                    continue;
                }

                if (!portalBlocks.contains(checkPos)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isCornerBlock(int x, int y, int width, int height) {
        return (x == 0 && y == 0) ||
                (x == width - 1 && y == 0) ||
                (x == 0 && y == height - 1) ||
                (x == width - 1 && y == height - 1);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (portalSideColor.get().a <= 5 && portalLineColor.get().a <= 5) return;

        synchronized (possiblePortalLocations) {
            renderPortals(event);
            if (nearestTracer.get()) {
                renderClosestPortal(event);
            }
        }
    }

    private void renderPortals(Render3DEvent event) {
        for (Box box : possiblePortalLocations) {
            if (isWithinRenderDistance(box)) {
                renderPortalBox(event, box);
            }
        }
    }

    private void renderPortalBox(Render3DEvent event, Box box) {
        if (tracers.get() && isWithinTracerRange(box)) {
            if (!nearestTracer.get()) {
                renderTracer(event, box);
            }
        }
        event.renderer.box(
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                portalSideColor.get(),
                new Color(0, 0, 0, 0),
                shapeMode.get(),
                0
        );
    }

    private void renderClosestPortal(Render3DEvent event) {
        Box box = new Box(
                new Vec3d(closestPortal.getX(), closestPortal.getY(), closestPortal.getZ()),
                new Vec3d(closestPortal.getX(), closestPortal.getY(), closestPortal.getZ())
        );
        renderPortalBox(event, box);
    }

    private void renderTracer(Render3DEvent event, Box box) {
        event.renderer.line(
                RenderUtils.center.x,
                RenderUtils.center.y,
                RenderUtils.center.z,
                box.minX + 0.5,
                box.minY + ((box.maxY - box.minY) / 2),
                box.minZ + 0.5,
                portalLineColor.get()
        );
    }

    private boolean isWithinRenderDistance(Box box) {
        BlockPos playerPos = new BlockPos(
                mc.player.getBlockX(),
                Math.round((float) box.getCenter().getY()),
                mc.player.getBlockZ()
        );
        return playerPos.isWithinDistance(box.getCenter(), renderDistance.get() * 16);
    }

    private boolean isWithinTracerRange(Box box) {
        return Math.abs(box.minX - RenderUtils.center.x) <= renderDistance.get() * 16 &&
                Math.abs(box.minZ - RenderUtils.center.z) <= renderDistance.get() * 16;
    }

    private void removeChunksOutsideRenderDistance() {
        double renderDistanceBlocks = renderDistance.get() * 16;
        BlockPos playerPos = mc.player.getBlockPos();

        removeDistantChunks(playerPos, renderDistanceBlocks);
        removeDistantPortals(renderDistanceBlocks);
    }

    private void removeDistantChunks(BlockPos playerPos, double renderDistanceBlocks) {
        scannedChunks.removeIf(chunkPos ->
                !playerPos.isWithinDistance(
                        new BlockPos(chunkPos.getCenterX(), mc.player.getBlockY(), chunkPos.getCenterZ()),
                        renderDistanceBlocks
                )
        );
    }

    private void removeDistantPortals(double renderDistanceBlocks) {
        possiblePortalLocations.removeIf(box -> {
            BlockPos playerPos = new BlockPos(
                    mc.player.getBlockX(),
                    Math.round((float) box.getCenter().getY()),
                    mc.player.getBlockZ()
            );
            return !playerPos.isWithinDistance(box.getCenter(), renderDistanceBlocks);
        });
    }
}