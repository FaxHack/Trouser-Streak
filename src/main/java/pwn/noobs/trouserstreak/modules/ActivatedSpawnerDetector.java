package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.block.enums.TrialSpawnerState;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import pwn.noobs.trouserstreak.Trouser;

import java.util.*;

public class ActivatedSpawnerDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> extraMessage = sgGeneral.add(new BoolSetting.Builder()
            .name("stash-message")
            .description("Toggle the message reminding you about stashes.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> lessSpam = sgGeneral.add(new BoolSetting.Builder()
            .name("less-stash-spam")
            .description("Suppress stash messages if no chests are within 16 blocks of the spawner.")
            .defaultValue(true)
            .visible(extraMessage::get)
            .build());

    private final Setting<List<Block>> storageBlocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("storage-blocks")
            .description("Blocks considered as storage when evaluating messages and renders.")
            .defaultValue(Arrays.asList(Blocks.CHEST, Blocks.BARREL, Blocks.HOPPER, Blocks.DISPENSER))
            .build());

    private final Setting<Boolean> displayCoords = sgGeneral.add(new BoolSetting.Builder()
            .name("display-coords")
            .description("Displays the coordinates of activated spawners in chat.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> detectDeactivatedSpawner = sgGeneral.add(new BoolSetting.Builder()
            .name("deactivated-spawner-detector")
            .description("Detects spawners with torches on them.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> detectTrialSpawner = sgGeneral.add(new BoolSetting.Builder()
            .name("trial-spawner-detector")
            .description("Detects activated trial spawners.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> torchScanDistance = sgGeneral.add(new IntSetting.Builder()
            .name("torch-scan-distance")
            .description("Scan distance for light-producing blocks around spawners.")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 10)
            .visible(detectDeactivatedSpawner::get)
            .build());

    private final Setting<Boolean> lessRenderSpam = sgRender.add(new BoolSetting.Builder()
            .name("less-render-spam")
            .description("Suppress rendering if no chests are within 16 blocks of the spawner.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
            .name("render-distance")
            .description("Render distance in chunks from the player.")
            .defaultValue(32)
            .min(6)
            .sliderRange(6, 1024)
            .build());

    private final Setting<Boolean> removeOutsideRenderDist = sgRender.add(new BoolSetting.Builder()
            .name("remove-outside-render-distance")
            .description("Remove cached block positions outside the render distance.")
            .defaultValue(true)
            .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build());

    private final Setting<SettingColor> spawnerSideColor = sgRender.add(new ColorSetting.Builder()
            .name("spawner-side-color")
            .description("Color of the activated spawner sides.")
            .defaultValue(new SettingColor(251, 5, 5, 70))
            .visible(() -> shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both)
            .build());

    private final Setting<SettingColor> spawnerLineColor = sgRender.add(new ColorSetting.Builder()
            .name("spawner-line-color")
            .description("Color of the activated spawner lines.")
            .defaultValue(new SettingColor(251, 5, 5, 235))
            .visible(() -> shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both)
            .build());

    private final Set<BlockPos> spawnerPositions = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> trialSpawnerPositions = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> deactivatedSpawnerPositions = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> noRenderPositions = Collections.synchronizedSet(new HashSet<>());

    private static final List<Block> LIGHT_BLOCKS = Arrays.asList(
            Blocks.TORCH, Blocks.SOUL_TORCH, Blocks.REDSTONE_TORCH, Blocks.JACK_O_LANTERN,
            Blocks.GLOWSTONE, Blocks.SHROOMLIGHT, Blocks.OCHRE_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT,
            Blocks.SEA_LANTERN, Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE
    );

    public ActivatedSpawnerDetector() {
        super(Trouser.Main, "ActivatedSpawnerDetector",
                "Detects activated mob spawners and provides related information and visualization.");
    }

    @Override
    public void onActivate() {
        clearAllPositions();
    }

    @Override
    public void onDeactivate() {
        clearAllPositions();
    }

    private void clearAllPositions() {
        spawnerPositions.clear();
        deactivatedSpawnerPositions.clear();
        noRenderPositions.clear();
        trialSpawnerPositions.clear();
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (mc.world == null) return;

        scanForSpawners();
        if (removeOutsideRenderDist.get()) {
            removeChunksOutsideRenderDistance();
        }
    }

    private void scanForSpawners() {
        int viewDistance = mc.options.getViewDistance().getValue();
        assert mc.player != null;
        ChunkPos playerChunk = new ChunkPos(mc.player.getBlockPos());

        for (int chunkX = playerChunk.x - viewDistance; chunkX <= playerChunk.x + viewDistance; chunkX++) {
            for (int chunkZ = playerChunk.z - viewDistance; chunkZ <= playerChunk.z + viewDistance; chunkZ++) {
                assert mc.world != null;
                WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                processChunkBlockEntities(chunk);
            }
        }
    }

    private void processChunkBlockEntities(WorldChunk chunk) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof MobSpawnerBlockEntity spawner) {
                processMobSpawner(spawner);
            } else if (blockEntity instanceof TrialSpawnerBlockEntity spawner && detectTrialSpawner.get()) {
                processTrialSpawner(spawner);
            }
        }
    }

    private void processMobSpawner(MobSpawnerBlockEntity spawner) {
        BlockPos pos = spawner.getPos();
        if (shouldProcessSpawner(pos)) return;
        if (spawner.getLogic().spawnDelay == 20 || (Objects.requireNonNull(mc.world).getRegistryKey() == World.NETHER && spawner.getLogic().spawnDelay == 0)) return;

        handleSpawnerDetection(pos, spawner);
    }

    private void processTrialSpawner(TrialSpawnerBlockEntity spawner) {
        BlockPos pos = spawner.getPos();
        if (shouldProcessSpawner(pos)) return;
        if (spawner.getSpawnerState() == TrialSpawnerState.WAITING_FOR_PLAYERS) return;

        handleTrialSpawnerDetection(pos);
    }

    private boolean shouldProcessSpawner(BlockPos pos) {
        return isWithinRenderDistance(pos) || isAlreadyProcessed(pos);
    }

    private boolean isWithinRenderDistance(BlockPos pos) {
        assert mc.player != null;
        BlockPos playerPos = new BlockPos(mc.player.getBlockX(), pos.getY(), mc.player.getBlockZ());
        return !playerPos.isWithinDistance(pos, renderDistance.get() * 16);
    }

    private boolean isAlreadyProcessed(BlockPos pos) {
        return spawnerPositions.contains(pos) || trialSpawnerPositions.contains(pos) ||
                noRenderPositions.contains(pos) || deactivatedSpawnerPositions.contains(pos);
    }

    private void handleSpawnerDetection(BlockPos pos, MobSpawnerBlockEntity spawner) {
        String spawnerType = determineSpawnerType(spawner);
        sendSpawnerDetectionMessage(pos, spawnerType);
        spawnerPositions.add(pos);

        if (detectDeactivatedSpawner.get()) {
            checkForDeactivation(pos);
        }

        handleStorageDetection(pos);
    }

    private String determineSpawnerType(MobSpawnerBlockEntity spawner) {
        var spawnEntry = spawner.getLogic().spawnEntry;
        if (spawnEntry == null || spawnEntry.getNbt() == null || spawnEntry.getNbt().get("id") == null) return "Unknown";

        String monster = spawnEntry.getNbt().getString("id");
        if (monster.contains("zombie") || monster.contains("skeleton")) return "DUNGEON";
        if (monster.contains(":spider")) {
            assert mc.world != null;
            return mc.world.getBlockState(spawner.getPos().down()).getBlock() == Blocks.BIRCH_PLANKS ? "WOODLAND MANSION" : "DUNGEON";
        }
        if (monster.contains("cave_spider")) return "MINESHAFT";
        if (monster.contains("silverfish")) return "STRONGHOLD";
        if (monster.contains("blaze")) return "FORTRESS";
        if (monster.contains("magma")) return "BASTION";

        return "Unknown";
    }

    private void sendSpawnerDetectionMessage(BlockPos pos, String type) {
        String message = type.equals("Unknown") ? "Detected Activated Spawner!" : "Detected Activated §c" + type + "§r Spawner!";
        if (displayCoords.get()) {
            message += " Block Position: " + pos;
        }
        ChatUtils.sendMsg(Text.of(message));
    }

    private void checkForDeactivation(BlockPos pos) {
        int distance = torchScanDistance.get();
        for (int x = -distance; x <= distance; x++) {
            for (int y = -distance; y <= distance; y++) {
                for (int z = -distance; z <= distance; z++) {
                    BlockPos checkPos = pos.add(x, y, z);
                    assert mc.world != null;
                    if (LIGHT_BLOCKS.contains(mc.world.getBlockState(checkPos).getBlock())) {
                        deactivatedSpawnerPositions.add(pos);
                        ChatUtils.sendMsg(Text.of("The spawner has torches or other light blocks!"));
                        return;
                    }
                }
            }
        }
    }

    private void handleStorageDetection(BlockPos pos) {
        if (hasNearbyStorage(pos)) {
            if (extraMessage.get() && (!lessSpam.get() || hasNearbyStorage(pos))) {
                ChatUtils.error("There may be stashed items in the storage near the spawners!");
            }
        } else if (lessRenderSpam.get()) {
            spawnerPositions.remove(pos);
            noRenderPositions.add(pos);
        }
    }

    private boolean hasNearbyStorage(BlockPos pos) {
        for (int x = -16; x <= 16; x++) {
            for (int y = -16; y <= 16; y++) {
                for (int z = -16; z <= 16; z++) {
                    if (isStorageBlock(pos.add(x, y, z))) return true;
                }
            }
        }
        return false;
    }

    private boolean isStorageBlock(BlockPos pos) {
        assert mc.world != null;
        if (storageBlocks.get().contains(mc.world.getBlockState(pos).getBlock())) return true;
        Box box = new Box(pos);
        return !mc.world.getEntitiesByClass(ChestMinecartEntity.class, box, entity -> true).isEmpty();
    }

    private void handleTrialSpawnerDetection(BlockPos pos) {
        String message = "Detected Activated §cTRIAL§r Spawner!";
        if (displayCoords.get()) {
            message += " Block Position: " + pos;
        }
        ChatUtils.sendMsg(Text.of(message));
        trialSpawnerPositions.add(pos);
        handleStorageDetection(pos);
    }

    private void removeChunksOutsideRenderDistance() {
        double renderDistanceBlocks = renderDistance.get() * 16;
        removePositionsOutsideRenderDistance(spawnerPositions, renderDistanceBlocks);
        removePositionsOutsideRenderDistance(deactivatedSpawnerPositions, renderDistanceBlocks);
        removePositionsOutsideRenderDistance(trialSpawnerPositions, renderDistanceBlocks);
        removePositionsOutsideRenderDistance(noRenderPositions, renderDistanceBlocks);
    }

    private void removePositionsOutsideRenderDistance(Set<BlockPos> positions, double renderDistanceBlocks) {
        positions.removeIf(this::isWithinRenderDistance);
    }
}
