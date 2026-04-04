package toolkit;

import arc.Events;
import arc.struct.Seq;
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
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumePower;

public class ToolkitMod extends Mod {

    // Helper: filter out ConsumePower from a Consume array
    private static Consume[] removePower(Consume[] arr) {
        Seq<Consume> filtered = new Seq<>();
        for (Consume c : arr) {
            if (!(c instanceof ConsumePower)) {
                filtered.add(c);
            }
        }
        return filtered.toArray(Consume.class);
    }

    public ToolkitMod() {

        // Remove power consumption from all Drill blocks at load time
        Events.on(EventType.ClientLoadEvent.class, e -> {
            for (Block block : Vars.content.blocks()) {
                if (block instanceof Drill) {
                    // Remove ConsumePower from all consumer arrays
                    block.consumers = removePower(block.consumers);
                    block.nonOptionalConsumers = removePower(block.nonOptionalConsumers);
                    block.optionalConsumers = removePower(block.optionalConsumers);
                    block.updateConsumers = removePower(block.updateConsumers);
                    // Clear power consumer reference
                    block.consPower = null;
                    block.consumesPower = false;
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
                if (!(block instanceof Drill)) return;
                // Force power status full in case power module still exists
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

