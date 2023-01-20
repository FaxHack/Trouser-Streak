package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.Vec3i;
import pwn.noobs.trouserstreak.Trouser;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import pwn.noobs.trouserstreak.utils.BEntityUtils;
import pwn.noobs.trouserstreak.utils.BPlayerUtils;
import pwn.noobs.trouserstreak.utils.BWorldUtils;


/**
 * @Author majorsopa
 * https://github.com/majorsopa
 * @Author evaan
 * https://github.com/evaan
 * @Author etianll
 * https://github.com/etianl
 */
public class AutoStaircase extends Module {
    public enum CenterMode {
        Center,
        Snap,
        None
    }
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<CenterMode> centerMode = sgGeneral.add(new EnumSetting.Builder<CenterMode>()
        .name("center")
        .description("How AutoStaircase should center you.")
        .defaultValue(CenterMode.Center)
        .build()
    );

    private final Setting<Double> view = sgGeneral.add(new DoubleSetting.Builder()
        .name("ViewAngle")
        .description("Angle of your view")
        .defaultValue(1)
        .min(0.1)
        .sliderMax(30)
        .build());
    private final Setting<Double> jump = sgGeneral.add(new DoubleSetting.Builder()
            .name("JumpVelocity")
            .description("Your velocity when jumping, for fine tuning.")
            .defaultValue(0.4)
            .min(0.39)
            .sliderMax(0.57)
            .build()
    );
    private final Setting<Integer> limit = sgGeneral.add(new IntSetting.Builder()
            .name("Build Limit")
            .description("sets the height at which the stairs stop")
            .sliderRange(-64, 319)
            .defaultValue(319)
            .build()
    );
    public final Setting<Boolean> timer = sgGeneral.add(new BoolSetting.Builder()
            .name("Timer")
            .description("Timer on/off")
            .defaultValue(true)
            .build()
    );

    public final Setting<Double> StairTimer = sgGeneral.add(new DoubleSetting.Builder()
            .name("TimerMultiplier")
            .description("The multiplier value for Timer.")
            .defaultValue(10)
            .min(1)
            .sliderMax(30)
            .visible(() -> timer.get())
            .build()
    );

    private boolean resetTimer;

    public AutoStaircase() {
        super(Trouser.Main, "AutoStaircase", "Make stairs!");
    }

    // Fields
    private BlockPos playerPos;
    private int ticksPassed;
    private int blocksPlaced;
    private boolean centered;

    Direction dir;
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();

        // North
        WButton north = table.add(theme.button("North")).expandX().minWidth(100).widget();
        north.action = () ->
                mc.player.setYaw(180);
        mc.options.jumpKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.player.setMovementSpeed(0);
        centered = false;
        playerPos = BEntityUtils.playerPos(mc.player);

        if (centerMode.get() != CenterMode.None) {
            if (centerMode.get() == CenterMode.Snap) BWorldUtils.snapPlayer(playerPos);
            else PlayerUtils.centerPlayer();
        }

        dir = BPlayerUtils.direction(mc.gameRenderer.getCamera().getYaw());

        table.row();

        // East
        WButton east = table.add(theme.button("East")).expandX().minWidth(100).widget();
        east.action = () ->
                mc.player.setYaw(270);
        mc.options.jumpKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.player.setMovementSpeed(0);
        centered = false;
        playerPos = BEntityUtils.playerPos(mc.player);

        if (centerMode.get() != CenterMode.None) {
            if (centerMode.get() == CenterMode.Snap) BWorldUtils.snapPlayer(playerPos);
            else PlayerUtils.centerPlayer();
        }

        dir = BPlayerUtils.direction(mc.gameRenderer.getCamera().getYaw());

        table.row();

        // South
        WButton south = table.add(theme.button("South")).expandX().minWidth(100).widget();
        south.action = () ->
                mc.player.setYaw(360);
        mc.options.jumpKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.player.setMovementSpeed(0);
        centered = false;
        playerPos = BEntityUtils.playerPos(mc.player);

        if (centerMode.get() != CenterMode.None) {
            if (centerMode.get() == CenterMode.Snap) BWorldUtils.snapPlayer(playerPos);
            else PlayerUtils.centerPlayer();
        }

        dir = BPlayerUtils.direction(mc.gameRenderer.getCamera().getYaw());

        table.row();

        // West
        WButton west = table.add(theme.button("West")).expandX().minWidth(100).widget();
        west.action = () ->
                mc.player.setYaw(90);
        mc.options.jumpKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.player.setMovementSpeed(0);
        centered = false;
        playerPos = BEntityUtils.playerPos(mc.player);

        if (centerMode.get() != CenterMode.None) {
            if (centerMode.get() == CenterMode.Snap) BWorldUtils.snapPlayer(playerPos);
            else PlayerUtils.centerPlayer();
        }

        dir = BPlayerUtils.direction(mc.gameRenderer.getCamera().getYaw());

        table.row();

        return table;
    }

    @Override
    public void onActivate() {
        mc.player.setVelocity(0,0,0);
        resetTimer = false;
        ticksPassed = 0;
        blocksPlaced = 0;

        centered = false;
        playerPos = BEntityUtils.playerPos(mc.player);

        if (centerMode.get() != CenterMode.None) {
            if (centerMode.get() == CenterMode.Snap) BWorldUtils.snapPlayer(playerPos);
            else PlayerUtils.centerPlayer();
        }

        dir = BPlayerUtils.direction(mc.gameRenderer.getCamera().getYaw());
        if (!(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem)) return;
        BlockPos pos = playerPos.add(new Vec3i(0,-1.5,0));
        if (mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
            mc.options.forwardKey.setPressed(false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
            mc.player.swingHand(Hand.MAIN_HAND);}
        if (Modules.get().get(TrouserFlight.class).isActive()) {
            Modules.get().get(TrouserFlight.class).toggle();
        }
        if (Modules.get().get(TPFly.class).isActive()) {
            Modules.get().get(TPFly.class).toggle();
        }
    }

    @Override
    public void onDeactivate() {
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        Modules.get().get(Timer.class).setOverride(Timer.OFF);
        resetTimer = true;
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (timer.get()) {
            if (mc.world.getBlockState(mc.player.getBlockPos()).getBlock() == Blocks.AIR && !mc.player.isOnGround()) {
                resetTimer = false;
                Modules.get().get(Timer.class).setOverride(StairTimer.get());
            } else if (!resetTimer) {
                Modules.get().get(Timer.class).setOverride(Timer.OFF);
                resetTimer = true;
            }
        }
        if (mc.player.getMainHandStack().isEmpty()) {
            mc.options.forwardKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
        }
        if (mc.options.backKey.isPressed()){
            mc.options.jumpKey.setPressed(false);
            mc.player.setVelocity(0,-5,0);
            ticksPassed = 0;
            blocksPlaced = 0;

            centered = false;
            playerPos = BEntityUtils.playerPos(mc.player);
            PlayerUtils.centerPlayer();
        }
        if (mc.options.rightKey.isPressed())
            mc.options.rightKey.setPressed(false);
        if (mc.options.leftKey.isPressed())
            mc.options.leftKey.setPressed(false);
        if(mc.player.getY() >= limit.get()){
            mc.options.forwardKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
        }
    }

    @EventHandler
    private void onKey(KeyAction action) {
        if (mc.options.forwardKey.isPressed())
        {mc.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ() - view.get()));}
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent playerMoveEvent) {
        if (mc.player == null || mc.world == null) {toggle(); return;}
        if (!mc.player.isOnGround() || !(mc.player.getInventory().getMainHandStack().getItem() instanceof BlockItem)) return;
        BlockPos pos = mc.player.getBlockPos().offset(mc.player.getMovementDirection());
        switch (mc.player.getMovementDirection()) {
            case NORTH ->
                mc.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ() - view.get()));
            case EAST ->
                mc.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, new Vec3d(mc.player.getX() + view.get(), mc.player.getY(), mc.player.getZ()));
            case SOUTH ->
                mc.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ() + view.get()));
            case WEST ->
                mc.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, new Vec3d(mc.player.getX() - view.get(), mc.player.getY(), mc.player.getZ()));
            default -> {
            }
        }
        if (mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
            mc.options.forwardKey.setPressed(false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.of(pos), Direction.DOWN, pos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        if (!mc.world.getBlockState(pos).getMaterial().isReplaceable()) {
            mc.options.forwardKey.setPressed(true);
            mc.options.jumpKey.setPressed(true);
            mc.player.setVelocity(0, jump.get(), 0);

                ticksPassed = 0;
                blocksPlaced = 0;

                centered = false;
                playerPos = BEntityUtils.playerPos(mc.player);

                if (centerMode.get() != CenterMode.None) {
                    if (centerMode.get() == CenterMode.Snap) BWorldUtils.snapPlayer(playerPos);
                    else PlayerUtils.centerPlayer();
                }

                dir = BPlayerUtils.direction(mc.gameRenderer.getCamera().getYaw());

        }
        if (mc.player.getMainHandStack().isEmpty()) {
            mc.options.forwardKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
        }
    }
    private void unpress() {
        setPressed(mc.options.forwardKey, false);
        setPressed(mc.options.backKey, false);
        setPressed(mc.options.leftKey, false);
        setPressed(mc.options.rightKey, false);
    }
    private void setPressed(KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
        Input.setKeyState(key, pressed);
    }
}
