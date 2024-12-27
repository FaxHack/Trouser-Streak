package pwn.noobs.trouserstreak;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pwn.noobs.trouserstreak.commands.*;
import pwn.noobs.trouserstreak.modules.*;


public class Trouser extends MeteorAddon {
        public static final Logger LOG = LoggerFactory.getLogger(Trouser.class);
        public static final Category Main = new Category("TrouserStreak");

        @Override
        public void onInitialize() {
                LOG.info("Initializing PantsMod!");

                Modules.get().add(new ActivatedSpawnerDetector());
                Modules.get().add(new HoleAndTunnelAndStairsESP());
                Modules.get().add(new NewerNewChunks());
                Modules.get().add(new BaseFinder());
                Modules.get().add(new StorageLooter());
                Modules.get().add(new PotESP());
                Modules.get().add(new NbtEditor());
                Commands.add(new ViewNbtCommand());
        }

        @Override
        public void onRegisterCategories() {
                Modules.registerCategory(Main);
        }

        public String getPackage() {
                return "pwn.noobs.trouserstreak";
        }

}