
## Credits to the people I skidded from:
In no particular order
- [Meteor Client](https://github.com/meteordevelopment/meteor-client)
- [etianl](https://github.com/etianl/)

 <div align="left">
    <p>This modpack would not have been possible without you
 </p>

## Features:
- **ActivatedSpawnerDetector** Detects if a player was ever near a spawner or trial spawner block. The intended use for this module would be on anarchy servers where people try to hide their items within chests in dungeons, mineshafts, and trial chambers. (Credits to etianl :D)
- **BaseFinder:** Automatically detects if a Base or Build could be in a chunk by checking every block and entity in each chunk to see if there are "Un-natural" things within them. (Credits to etianl :D, and to Meteor-Rejects for some code from newchunks.)
- *BaseFinder Notes:*
- The Blocks Lists have been tuned to reduce any false positives while throwing the maximum amount of "good" results for builds. Adjust if you feel you need to, or add/remove things as needed.
- The Number of Blocks to Find options is the total amount any of the blocks from one of the lists to find before throwing a base coord.
- Do not do the same block in more than one list, it will be a waste of CPU time. The torches and signs in by default are fine because they are actually two different blocks, "WALL_TORCH" and just "TORCH".
- The "Unnatural Spawner Finder" option locates spawners and if they do not have one of the blocks that spawners have around them in nature (Mossy Cobblestone, Stone Brick Stairs, Cobweb, Nether Brick Fence, and Chain), then flag the spawner as unnatural.
- .base command returns the nearest base to you
- .base add or rmv will add or remove the location you are in as a base coord, or you can input X,Y after add/rmv (ex: .base add 69 420)
- .base rmv last will remove the last single base coordinate found. (Good for removing false positives)
- There are buttons in the options menu to do the same things as the commands listed above.
- Base location data will be stored in the "BaseChunks" folder, in your Minecraft folder.
- **Hole/Tunnel/StairsESP:** Detects 1x1 holes going straight down, horizontal tunnels of any height, and staircase tunnels. It by default ignores passable blocks such as torches or water but there is an option to have it only detect Air for holes and tunnels. (Thank you to Meteor Client for some code from TunnelESP, and credits to etianl for this version of it)
- **NbtEditor:** Requires Creative mode. Generates custom entities in the form of a custom spawn egg, generate items with custom enchantments (Only in Minecraft 1.20.4 and below), and potions with custom effects all based on the settings you configure. It can also copy the Nbt data from one item to another.  (Credits to etianl :D)
- **NewerNewChunks:** NewChunks module with new newchunk estimation exploits, and the ability to save chunk data for later! Comes with several new homebrewed newchunks methods made by yours truly. (Credits to Meteor Rejects, and BleachHack from where it was ported, and etianl for updating :D.)

-------------------------------------------------------------------------------------
***NewerNewChunks Notes:***
- NEAR 100% accurate chunk detection in all dimensions!
- NewerNewChunks stores your NewChunks data as text files seperately per server and per dimension in the TrouserStreak/NewChunks folder in your Minecraft folder. This enables you to chunk trace multiple different servers and dimensions without mixing NewChunks data.
- If the game crashes, chunk data is saved! No loss in tracing progress.
- Save and Load ChunkData options are for the stored files.
- There is also an option for deleting chunk data in that particular dimension on the server.
- You can even send chunk data to your friends! Just copy the TrouserStreak/NewChunks folder and send it.

***l33t new 3xpl0its:***

**Palette Exploit:**
- The **PaletteExploit** option enabled by default detects new chunks by scanning the order of chunk section palettes.
- The **PaletteExploit** highlights chunks that are being updated from an old version of minecraft as their own color.
- The **PaletteExploit** does not work in Minecraft servers where their version is less than 1.18. For those servers, disable **PaletteExploit** and enable Liquid flow and BlockExploit.
- The **PaletteExploit** does not work in flat worlds that are entirely void.
- Chunks appear to be defined as new until the person who generated them has unrendered them.
- The chunks that stay loaded due to the spawn chunk region always show up as new for some reason.
- In the Overworld dimension there are very rare false positives.

**Detection for Old Generation:**
- the **Pre 1.17 Overworld OldChunk Detector** detects chunks in the Overworld that do not contain new 1.17 blocks above Y level 0. This should be used when the .world command returns "This chunk is pre 1.17 generation!" when run at spawn.
- the **Pre 1.16 Nether OldChunk Detector** detects if Nether chunks are missing blocks found within the 1.16 Nether update.
- the **Pre 1.13 End OldChunk Detector** marks chunks as generated in an old version if they have the biome of minecraft:the_end.
- With the **Pre 1.13 End OldChunk Detector**  chunks that are old in the End just around the central end island are always marked as old because that biome is minecraft:the_end.

**Default Color Descriptions:**\
**Red:** New chunk, never loaded before.\
**Green:** Old chunk, only loaded in 1.18 or after.\
**Yellow-Green:** Old Generation chunk, only loaded in 1.17 or before for OVERWORLD, 1.13 or before in END, or 1.15 or before in NETHER (defined by static means, the state does not change).\
**Orange-Yellow:** Old chunk (1.17 or before) being currently updated to 1.18 or after (defined by dynamic means, the state does change if someone visits and leaves).\

**More Detection Methods:**
- The **LiquidExploit** option estimates possible newchunks based on liquid being just starting to flow for the first time.
- The **BlockUpdateExploit** option estimates possible newchunks based on block update packets. SOME OF THESE CHUNKS MAY BE OLD. Advanced Mode is needed to filter any false positives out. See Special Options notes for usage.
- The **BlockUpdateExploit** option can produce false positives if you are hanging around in the same location for a while. It's best to keep moving for it to work best.
  *Modes:*
- The **"BlockExploitMode"** will render BlockExploit chunks as their own color instead of a newchunk (Normal mode rendering).
- When using BlockExploitMode mode if the BlockUpdateExploit chunks appear infrequently and are combined with Old Chunks, then the chunks you are in are OLD. If there is alot of BlockUpdateExploit chunks appearing and/or they are mixed with NewChunks then the chunks are NEW.
- The **"IgnoreBlockExploit"** will render BlockExploit chunks as an oldchunk instead of a newchunk.

-------------------------------------------------------------------------------------
- **OnlinePlayerActivityDetector:** Detects if an online player is nearby if there are blocks missing from a BlockState palette and your render distances are overlapping. It can detect players that are outside of render distance. (Credits to etianl :D)
- **PotESP:** Detects Decorated Pots with un-natural contents, and also tells you what item it is and the location of the pot. (Credits to etianl :D)
- **StorageLooter:** Automatically steals the best stuff from storage containers according to itemlists and a list of values that are set for amounts of those items to take, and also puts junk items in there too. It either automatically opens the containers within reach and steals the stuff automatically, or steals the stuff automatically when you manually open the container. (Credits to etianl :D)
- **TrouserBuild:** It can build either horizontally or vertically according to a 5x5 grid centered on the block you are aiming at. Right click to build at the targeted location. (Credits to etianl, and to Banana for the checkboxes and idea. :D)
- **ViewNbtCommand:** Returns the nbt data for the item in your hand in the chat box. There is also a Save option for the command that saves the data to a text file in your .minecraft folder in the "SavedNBT" folder.
- **WorldInfoCommand** Type .world in chat to tell you some info about the server like world border coordinates and other things, and sometimes the players that have played there (players does not work on all servers). (Credits to etianl :D)

## Known Bugs:
- NewerNewChunks can rarely boot you from the server when going back and forth through a nether portal. For example, it sometimes may boot you if you just came out of a portal then you re-enter it immediately after exiting.
- .newchunkcount command shows exactly the chunks that are saved in chunk data, so when you are in normal mode or flowbelowY0 mode the returned values are not exactly in correlation to what is rendered on screen.
- NewerNewChunks has to be turned on atleast once prior to running .newchunkcount for the counter to work even if you already have data in that world.
- Joining a server with HandOfGod or Voider already on causes the module to be turned off due to "Not being OP" even if you are an operator

## Requirements:
- If you are using Minecraft version **1.21.3**, then use the latest **MeteorClient Dev Build of v0.5.9**
- If you are using Minecraft version **1.21.1**, then use **MeteorClient "Full Release" v0.5.8**
- Please try [ViaFabricPlus](https://github.com/FlorianMichael/ViaFabricPlus), which will let you connect to almost any version from a new version client.
- Don't forget to try updating any other mods you are using if your game is crashing.

plz give me star on githoob kthx
