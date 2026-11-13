# AE2 Storage Locator

GTNH (Minecraft 1.7.10) add-on for Applied Energistics 2: hover an item or fluid in any AE terminal and the
tooltip lists where the network keeps it — which drive cells, ME chests and storage-bus targets (drawers,
Super Chests, tanks, subnets...) hold it, and how many each. The six biggest holders are listed, the rest summed.

- Server: answers from the network of the terminal the player has open (max 20 lookups/s per player).
- Client: asks once per stack and caches the answer for 5 s; shows "Locating..." until it arrives.
- Install on both sides for the tooltip. Either side alone loads fine and does nothing.

Built against appliedenergistics2 rv3-beta-1095-GTNH. `./gradlew assemble` (needs a git tag for the version).
