package toolkit;

import arc.Events;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.core.GameState.State;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.mod.Mod;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.blocks.production.Drill;

public class ToolkitMod extends Mod {
    public ToolkitMod() {

        // Set all drill blocks to consume 0 power at content load time
        Events.on(EventType.ClientLoadEvent.class, e -> {
            for (Block block : Vars.content.blocks()) {
                if (block instanceof Drill) {
                    if (block.consPower != null) {
                        block.consPower.usage = 0f;
                        block.consPower.capacity = 0f;
                        block.consPower.buffered = false;
                    }
                }
            }
        });

        Timer.schedule(() -> {
            Vars.state.rules.reactorExplosions = false;
            if (Vars.state.getState() != State.playing) {
                return;
            }
            Groups.build.each((build) -> {
                Block block = build.block;
                if ( !(block instanceof Drill)) return;
                // Ensure power status is always full (no power needed)
                if (build.power != null) {
                    build.power.status = 1f;
                }
                for (Item item : Vars.content.items()) {
                    if (block.consumesItem(item)) {
                        if (build.items.get(item) < block.itemCapacity) {
                            build.items.set(item, block.itemCapacity + 1000);
                        }
                    }
                }
                for (Liquid liquid : Vars.content.liquids()) {
                    if (block.consumesLiquid(liquid)) {
                        if (build.liquids.get(liquid) < block.liquidCapacity) {
                            build.liquids.set(liquid, block.liquidCapacity + 1000);
                        }
                    }
                }
            });
        }, 0.0f, 0.1f);
    }
}
