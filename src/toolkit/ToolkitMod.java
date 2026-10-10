package toolkit;

import arc.util.Timer;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.mod.Mod;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.blocks.power.PowerBlock;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.Pump;
import mindustry.world.blocks.production.SolidPump;

public class ToolkitMod extends Mod {

    private boolean target(Block block) {
        return block instanceof Drill
            || block instanceof Pump
            || block instanceof SolidPump
            || block instanceof GenericCrafter
            || block instanceof Turret;
    }

    public ToolkitMod() {
        Timer.schedule(() -> {
            if (!Vars.state.isPlaying()) return;

            Groups.build.each(build -> {
                Block block = build.block;
                if (!target(block)) return;

                if (!(block instanceof Turret) && build.items != null) {
                    for (Item item : Vars.content.items()) {
                        if (block.consumesItem(item)) {
                            build.items.set(item, block.itemCapacity + 1000);
                        }
                    }
                }

                if (build.liquids != null) {
                    for (Liquid liquid : Vars.content.liquids()) {
                        if (block.consumesLiquid(liquid)) {
                            build.liquids.set(
                                liquid,
                                block.liquidCapacity + 1000
                            );
                        }
                    }
                }
            });
        }, 0f, 0.1f);
    }
}
