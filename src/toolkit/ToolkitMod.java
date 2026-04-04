package toolkit;

import arc.Core;
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
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.Pump;
import mindustry.world.blocks.production.SolidPump;
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
    
    private boolean isProduction(Block block) {
        return block instanceof Drill || block instanceof Pump || block instanceof SolidPump;
    }
    
    private boolean isCrafting(Block block) {
        return block instanceof GenericCrafter;
    }
    
    private boolean isTurret(Block block) {
        return block instanceof Turret;
    }

    public ToolkitMod() {

        Events.on(EventType.ClientLoadEvent.class, e -> {
            
            // Add settings UI category
            Vars.ui.settings.addCategory("Toolkit Options", table -> {
                table.checkPref("tk-prod-power", true, "Production: Ignore Power (Requires Restart)");
                table.checkPref("tk-prod-items", true, "Production: Auto-fill Items");
                table.checkPref("tk-prod-liquids", true, "Production: Auto-fill Liquids");

                table.checkPref("tk-craft-power", true, "Crafting: Ignore Power (Requires Restart)");
                table.checkPref("tk-craft-items", true, "Crafting: Auto-fill Items");
                table.checkPref("tk-craft-liquids", true, "Crafting: Auto-fill Liquids");

                table.checkPref("tk-turret-power", true, "Turrets: Ignore Power (Requires Restart)");
                table.checkPref("tk-turret-items", true, "Turrets: Auto-fill Items");
                table.checkPref("tk-turret-liquids", true, "Turrets: Auto-fill Liquids");
            });

            boolean prodPower = Core.settings.getBool("tk-prod-power", true);
            boolean craftPower = Core.settings.getBool("tk-craft-power", true);
            boolean turretPower = Core.settings.getBool("tk-turret-power", true);

            for (Block block : Vars.content.blocks()) {
                boolean remove = false;
                if (isProduction(block) && prodPower) remove = true;
                if (isCrafting(block) && craftPower) remove = true;
                if (isTurret(block) && turretPower) remove = true;

                if (remove) {
                    // Remove ConsumePower from all consumer arrays
                    block.consumers = removePower(block.consumers);
                    block.nonOptionalConsumers = removePower(block.nonOptionalConsumers);
                    block.optionalConsumers = removePower(block.optionalConsumers);
                    block.updateConsumers = removePower(block.updateConsumers);
                    // Remove power bar (its lambda references consPower)
                    block.removeBar("power");
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
            
            boolean prodItems = Core.settings.getBool("tk-prod-items", true);
            boolean prodLiquids = Core.settings.getBool("tk-prod-liquids", true);
            
            boolean craftItems = Core.settings.getBool("tk-craft-items", true);
            boolean craftLiquids = Core.settings.getBool("tk-craft-liquids", true);
            
            boolean turretItems = Core.settings.getBool("tk-turret-items", true);
            boolean turretLiquids = Core.settings.getBool("tk-turret-liquids", true);
            
            Groups.build.each((build) -> {
                Block block = build.block;
                
                boolean doItems = false;
                boolean doLiquids = false;
                
                if (isProduction(block)) {
                    doItems = prodItems;
                    doLiquids = prodLiquids;
                } else if (isCrafting(block)) {
                    doItems = craftItems;
                    doLiquids = craftLiquids;
                } else if (isTurret(block)) {
                    doItems = turretItems;
                    doLiquids = turretLiquids;
                } else {
                    return; // Not a block we're targeting here
                }

                // Force power status full in case power module still exists
                if (build.power != null) {
                    build.power.status = 1f;
                }
                
                if (doItems && build.items != null) {
                    for (Item item : Vars.content.items()) {
                        if (block.consumesItem(item)) {
                            if (build.items.get(item) < block.itemCapacity) {
                                build.items.set(item, block.itemCapacity + 1000);
                            }
                        }
                    }
                }
                
                if (doLiquids && build.liquids != null) {
                    for (Liquid liquid : Vars.content.liquids()) {
                        if (block.consumesLiquid(liquid)) {
                            if (build.liquids.get(liquid) < block.liquidCapacity) {
                                build.liquids.set(liquid, block.liquidCapacity + 1000);
                            }
                        }
                    }
                }
            });
        }, 0.0f, 0.1f);
    }
}

