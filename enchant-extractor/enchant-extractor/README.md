# Enchant Extractor (Fabric, Minecraft 26.3)

Hold an enchanted item and run:

- `/disenchant` lists its enchantments; click one to extract it.
- `/disenchant mending` removes Mending from the held item and gives you an enchanted book with Mending at the same level. Tab-completion suggests the enchantments on the held item.

The book can then be applied to another item at an anvil as usual. If your inventory is full, the book drops at your feet.

## Install
Put the built jar in the server's `mods` folder alongside Fabric API 0.163.0+26.3. It is server-side only, so players don't need it installed.

## Build
Requires JDK 25.

    ./gradlew build

The jar is `build/libs/enchant-extractor-1.0.0.jar` (not the `-sources` jar).
