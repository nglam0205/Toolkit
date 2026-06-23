package toolkit;
import mindustry.world.blocks.power.PowerBlock;

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
    
    private boolean isPower(Block block){
        return block instanceof PowerBlock;
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
                table.checkPref("Production: Ignore Power (Restart Required)", true);
                table.checkPref("Production: Auto-fill Items", true);
                table.checkPref("Production: Auto-fill Liquids", true);

                table.checkPref("Crafting: Ignore Power (Restart Required)", true);
                table.checkPref("Crafting: Auto-fill Items", true);
                table.checkPref("Crafting: Auto-fill Liquids", true);

                table.checkPref("Turrets: Ignore Power (Restart Required)", true);
                table.checkPref("Turrets: Auto-fill Items", true);
                table.checkPref("Turrets: Auto-fill Liquids", true);
                table.checkPref("Power: Ignore Power (Restart Required)", true);
                table.checkPref("Power: Auto-fill Items", true);
                table.checkPref("Power: Auto-fill Liquids", true);
            });

            boolean prodPower = Core.settings.getBool("Production: Ignore Power (Restart Required)", true);
            boolean craftPower = Core.settings.getBool("Crafting: Ignore Power (Restart Required)", true);
            boolean turretPower = Core.settings.getBool("Turrets: Ignore Power (Restart Required)", true);
            boolean powerPower = Core.settings.getBool("Power: Ignore Power (Restart Required)", true);
            for (Block block : Vars.content.blocks()) {
                boolean remove = false;
                if (isProduction(block) && prodPower) remove = true;
                if (isCrafting(block) && craftPower) remove = true;
                if (isTurret(block) && turretPower) remove = true;
                if(isPower(block) && powerPower) remove = true;
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
            
            boolean prodItems = Core.settings.getBool("Production: Auto-fill Items", true);
            boolean prodLiquids = Core.settings.getBool("Production: Auto-fill Liquids", true);
            
            boolean craftItems = Core.settings.getBool("Crafting: Auto-fill Items", true);
            boolean craftLiquids = Core.settings.getBool("Crafting: Auto-fill Liquids", true);
            
            boolean turretItems = Core.settings.getBool("Turrets: Auto-fill Items", true);
            boolean turretLiquids = Core.settings.getBool("Turrets: Auto-fill Liquids", true);
            boolean powerItems = Core.settings.getBool("Power: Auto-fill Items", true);
            boolean powerLiquids = Core.settings.getBool("Power: Auto-fill Liquids", true);
            Groups.build.each((build) -> {
                Block block = build.block;
                
                boolean doItems = false;
                boolean doLiquids = false;
                
                if(isProduction(block)){
                    doItems = prodItems;
                    doLiquids = prodLiquids;
                }else if(isCrafting(block)){
                    doItems = craftItems;
                    doLiquids = craftLiquids;
                }else if(isTurret(block)){
                    doItems = turretItems;
                    doLiquids = turretLiquids;
                }else if(isPower(block)){
                    doItems = powerItems;
                    doLiquids = powerLiquids;
                }else{
                    return;
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

