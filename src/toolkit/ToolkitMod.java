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
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.consumers.ConsumePower;
import mindustry.world.modules.ItemModule;
import mindustry.world.modules.LiquidModule;
import arc.util.Timer;

public class ToolkitMod extends Mod {

    /** The "speed-up" blocks that get item and liquid inputs instead of power. */
    private static final String[] acceleratorBlocks = {"overdrive-projector", "overdrive-dome"};

    /** Every block runs this much faster than its default speed. */
    private static final float speedMultiplier = 3f;
    /** Boost duration, refreshed every tick of the loop below. */
    private static final float speedDuration = 0.5f;

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

        /** Keeps the power bar full, since some machines read the power status directly instead of their efficiency. */
        @Override
        public void update(Building build) {
            if (build.power != null) {
                build.power.status = 1f;
            }
        }
    }

    /** Silicon consumer that tolerates machines placed before they had an item storage. */
    private static class AcceleratorItems extends ConsumeItems {
        AcceleratorItems() {
            super(new ItemStack[]{new ItemStack(Items.silicon, 1)});
        }

        @Override
        public float efficiency(Building build) {
            return build.items == null ? 0f : super.efficiency(build);
        }

        @Override
        public void trigger(Building build) {
            if (build.items != null) super.trigger(build);
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

    /** Item/liquid filter arrays only exist once the block has been initialized. */
    private boolean consumesItem(Block block, Item item) {
        return block.itemFilter != null && item.id < block.itemFilter.length && block.itemFilter[item.id];
    }

    private boolean consumesLiquid(Block block, Liquid liquid) {
        return block.liquidFilter != null && liquid.id < block.liquidFilter.length && block.liquidFilter[liquid.id];
    }

    private boolean isAccelerator(Block block) {
        for (String name : acceleratorBlocks) {
            if (block.name.equals(name)) return true;
        }
        return false;
    }

    /** Fills every item the machine eats, so drills, factories and generators never run dry. */
    private void fillItems(Building build, Block block) {
        if (build.items == null) return;

        //turrets keep their ammunition in their own ammo counter, not in the item storage
        if (build instanceof ItemTurret.ItemTurretBuild) {
            fillTurret(build);
            return;
        }

        for (Item item : Vars.content.items()) {
            if (consumesItem(block, item)) {
                build.items.set(item, block.itemCapacity + 1000);
            }
        }
    }

    /** Keeps every turret loaded with its first ammunition type. */
    private void fillTurret(Building build) {
        ItemTurret.ItemTurretBuild turretBuild = (ItemTurret.ItemTurretBuild) build;
        ItemTurret turret = (ItemTurret) build.block;
        if (turret.ammoTypes.size == 0 || turretBuild.totalAmmo >= turret.maxAmmo) return;

        Item ammo = turret.ammoTypes.keys().next();
        //bounded, in case an ammo type does not add anything
        for (int i = 0; i <= turret.maxAmmo && turretBuild.totalAmmo < turret.maxAmmo; i++) {
            turretBuild.handleItem(build, ammo);
        }
    }

    /** Fills every liquid the machine eats: coolant for turrets, cryofluid for reactors, water for pumps. */
    private void fillLiquids(Building build, Block block) {
        if (build.liquids == null) return;

        for (Liquid liquid : Vars.content.liquids()) {
            if (consumesLiquid(block, liquid)) {
                build.liquids.set(liquid, block.liquidCapacity + 1000);
            }
        }
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
            //blocks that refuse overdrive would otherwise drop the speed boost every frame
            block.canOverdrive = true;
        }

        for (String name : acceleratorBlocks) {
            Block block = Vars.content.block(name);
            if (block == null) continue;

            block.hasItems = true;
            block.acceptsItems = true;
            block.itemCapacity = Math.max(block.itemCapacity, 20);
            block.hasLiquids = true;
            block.liquidCapacity = Math.max(block.liquidCapacity, 20f);

            if (!consumesItem(block, Items.silicon)) {
                addConsumer(block, new AcceleratorItems());
            }
            if (!consumesLiquid(block, Liquids.cryofluid)) {
                addConsumer(block, new AcceleratorLiquid());
            }
        }
    }

    /** True once every block has been initialized, which is when their item/liquid filters exist. */
    private boolean contentReady() {
        if (Vars.content.blocks().size == 0 || Vars.content.items().size == 0 || Vars.content.liquids().size == 0) {
            return false;
        }

        for (Block block : Vars.content.blocks()) {
            if (block.itemFilter == null || block.itemFilter.length < Vars.content.items().size) {
                return false;
            }
        }

        return true;
    }

    public ToolkitMod() {
        Timer.schedule(() -> {
            if (!configured && contentReady()) {
                configured = true;
                configureBlocks();
            }

            if (!Vars.state.isPlaying()) return;

            Groups.build.each(build -> {
                Block block = build.block;

                //machines already placed from older saves have no item/liquid storage yet
                if (isAccelerator(block)) {
                    if (build.items == null) build.items = new ItemModule();
                    if (build.liquids == null) build.liquids = new LiquidModule();
                }

                //overdrive-style boost, so every machine runs faster than default
                build.applyBoost(speedMultiplier, speedDuration);

                fillItems(build, block);
                fillLiquids(build, block);
            });
        }, 0f, 0.1f);
    }
}
