# AE2 Storage Locator

GTNH (Minecraft 1.7.10) add-on for Applied Energistics 2: hover an item or fluid in any AE terminal and the
tooltip lists where the network keeps it — which drive cells, ME chests and storage-bus targets (drawers,
Super Chests, tanks, subnets...) hold it, and how many each. The six biggest holders are listed, the rest summed.

- Server: answers from the network of the terminal the player has open (max 20 lookups/s per player).
- Client: asks once per stack and caches the answer for 5 s; shows "Locating..." until it arrives.
- Press **Insert** (rebindable: Options > Controls > AE2 Storage Locator) on an item in an ME terminal: the
  terminal closes and the blocks holding it are outlined in the world, drive cell slots included (AE2's own
  find-in-storage highlight). `config/ae2storagelocator.cfg` `highlightSeconds` (default 30) sets how long.
- Drives are listed by cell, drive and slot; storage buses facing another AE network list that network's drives
  ("(subnet)").
- GT Super/Quantum Chests and Tanks get a WAILA (and portable scanner) line saying whether they're in AE: which
  storage bus sees them and how many steps away, or why not (no bus, several buses on one group, past the group
  limit). Understands gtstorageclusters groups when that mod is installed.
- Install on both sides for the tooltip. Either side alone loads fine and does nothing.

Built against appliedenergistics2 rv3-beta-1095-GTNH. `./gradlew assemble` (needs a git tag for the version).
