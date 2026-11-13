# AE2 Storage Locator for Minecraft Forge 1.20.1

Hover an item or fluid in any AE2 terminal and the tooltip lists where the network keeps it: which cell in which
ME Drive or ME Chest slot, and which block each storage bus faces (chests, drawers, tanks...), with how much each.
The six biggest holders are listed and the rest summed. Storage buses facing another AE network (through an ME
Interface) list that network's drives, marked "(subnet)".

Press **Insert** (rebindable: Options > Controls > AE2 Storage Locator) on an entry in an ME terminal: the terminal
closes and the blocks holding it are outlined in the world, through walls too. `config/ae2storagelocator-client.toml`
`highlightSeconds` (default 30) sets how long.

- Server: answers from the network of the terminal the player has open (max 20 lookups a second per player).
- Client: asks once per entry and caches the answer for 5 seconds; shows "Locating..." until it arrives.
- Install on both sides for the tooltip. Either side alone loads fine and does nothing.

The GregTech parts of the 1.7.10 version (Super/Quantum Chest status, gtstorageclusters, WAILA) have no
counterpart here.

Built against Applied Energistics 2 15.4.9 for Forge (works with any AE2 15.x). Run `./gradlew build` in this
folder (Java 17); the jar lands in `build/libs` as `ae2storagelocator-<mod version>-1.20.1-forge.jar`.
