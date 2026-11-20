package toolkit;

import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.mod.Mod;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.power.PowerBlock;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.Pump;
import mindustry.world.blocks.production.SolidPump;
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.consumers.ConsumePower;
import mindustry.world.modules.LiquidModule;
import arc.util.Timer;

public class ToolkitMod extends Mod {

    /** The "speed-up" blocks that get item and liquid inputs instead of power. */
    private static final String[] acceleratorBlocks = {"overdrive-projector", "overdrive-dome"};

    private boolean configured;

    /** Power consumer that never requests any power, so the block always runs without a power grid. */
    private static class NoPower extends ConsumePower {
        NoPower() {
            super(0f, 0f, false);
        }

        @Override
        public float efficiency(Building build) {
            return 1f;
        }

        @Override
        public float requestedPower(Building build) {
            return 0f;
        }
    }

    /** Cryofluid consumer that tolerates machines placed before they had a liquid tank. */
    private static class AcceleratorLiquid extends ConsumeLiquid {
        AcceleratorLiquid() {
            super(Liquids.cryofluid, 0.02f);
        }

        @Override
        public float efficiency(Building build) {
            return build.liquids == null ? 0f : super.efficiency(build);
        }

        @Override
        public void update(Building build) {
            if (build.liquids != null) super.update(build);
        }
    }

    private boolean isAccelerator(Block block) {
        for (String name : acceleratorBlocks) {
            if (block.name.equals(name)) return true;
        }
        return false;
    }

    private boolean isRTG(Block block) {
        return block.name.equals("rtg-generator");
    }

    private boolean target(Block block) {
        return block instanceof Drill
            || block instanceof Pump
            || block instanceof SolidPump
            || block instanceof GenericCrafter
            || block instanceof Turret
            || isRTG(block);
    }

    /** Replaces the power consumer of one block with a free one. Buffered consumers (batteries, cores) keep working. */
    private void removePowerConsumption(Block block) {
        ConsumePower power = block.consPower;
        if (power == null || power.buffered) return;

        NoPower free = new NoPower();
        replacePower(block.consumers, free);
        replacePower(block.optionalConsumers, free);
        replacePower(block.nonOptionalConsumers, free);
        replacePower(block.updateConsumers, free);
        block.consPower = free;
    }

    private void replacePower(Consume[] list, NoPower replacement) {
        for (int i = 0; i < list.length; i++) {
            if (list[i] instanceof ConsumePower) {
                list[i] = replacement;
            }
        }
    }

    /** Registers a consumer on a block that is already initialized. */
    private void addConsumer(Block block, Consume cons) {
        cons.apply(block);

        block.consumers = append(block.consumers, cons);
        if (cons.optional) {
            block.optionalConsumers = append(block.optionalConsumers, cons);
        } else {
            block.nonOptionalConsumers = append(block.nonOptionalConsumers, cons);
        }
        if (cons.update && !cons.ignore()) {
            block.updateConsumers = append(block.updateConsumers, cons);
        }
        block.hasConsumers = block.consumers.length > 0;
    }

    private Consume[] append(Consume[] list, Consume cons) {
        Consume[] result = new Consume[list.length + 1];
        System.arraycopy(list, 0, result, 0, list.length);
        result[list.length] = cons;
        return result;
    }

    private void configureBlocks() {
        for (Block block : Vars.content.blocks()) {
            removePowerConsumption(block);
        }

        for (String name : acceleratorBlocks) {
            Block block = Vars.content.block(name);
            if (block == null) continue;

            block.hasItems = true;
            block.acceptsItems = true;
            block.itemCapacity = Math.max(block.itemCapacity, 20);
            block.hasLiquids = true;
            block.liquidCapacity = Math.max(block.liquidCapacity, 20f);

            if (!block.consumesItem(Items.silicon)) {
                addConsumer(block, new ConsumeItems(new ItemStack[]{new ItemStack(Items.silicon, 1)}));
            }
            if (!block.consumesLiquid(Liquids.cryofluid)) {
                addConsumer(block, new AcceleratorLiquid());
            }
        }
    }

    public ToolkitMod() {
        Timer.schedule(() -> {
            if (!configured && Vars.content.blocks().size > 0) {
                configured = true;
                configureBlocks();
            }

            if (!Vars.state.isPlaying()) return;

            Groups.build.each(build -> {
                Block block = build.block;

                //machines already placed from older saves have no liquid tank yet
                if (isAccelerator(block) && build.liquids == null) {
                    build.liquids = new LiquidModule();
                }

                if (!target(block)) return;

                if (!(block instanceof Turret) && build.items != null) {
                    for (Item item : Vars.content.items()) {
                        if (block.consumesItem(item)) {
                            build.items.set(
                                item,
                                block.itemCapacity + 1000
                            );
                        }
                    }
                }

                if (!(block instanceof PowerBlock)
                    && build.liquids != null) {
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
