package dev.veltraph.client.inventory.equipment;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import dev.veltraph.client.service.mc.McHeldKeys;
import java.util.*;

/** Native local compactor/deletor toggle, pet equip and wardrobe selection with explicit contracts. */
public final class EquipmentWorkflowModule extends AbstractModule {
    public static final String ID="equipment_workflow";
    private final Minecraft client;private final ConfigService config;private final EquipmentWorkflow workflow;private long tick;
    public EquipmentWorkflowModule(Minecraft client,ConfigService config){
        super(ID,"Inventory & Equipment",ModuleCategory.MISC);this.client=client;this.config=config;workflow=new EquipmentWorkflow(new McEquipmentWorkflow(client));
    }
    /** Injectable request seam for future mining/farming interruption ownership. Caller ticks the workflow. */
    public EquipmentWorkflow workflow(){return workflow;}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public boolean ownsGameplayInput(){return true;}
    @Override public boolean allowsScreen(String title){var request=workflow.request();return request!=null&&request.titleAllowed(title);}
    @Override public Optional<String> preStartCheck(){
        if(workflow.active())return Optional.of("Owned equipment restoration is still pending");
        if(!config.get(EquipmentConfig.EQUIPMENT_WORKFLOW_ENABLED))return Optional.of("Enable Inventory & Equipment explicitly");
        if(client.player==null||!client.player.isAlive()||client.level==null||client.gameMode==null)return Optional.of("Living loaded player required");
        if(client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Close other menus and empty the cursor");
        try{EquipmentConfig.request(config);}catch(IllegalArgumentException e){return Optional.of(e.getMessage());}
        return Optional.empty();
    }
    private void release(){var o=client.options;for(KeyMapping key:new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse})McHeldKeys.setHeld(key,false);}
    @Override public void onEnable(){release();tick=0;var blocker=preStartCheck();if(blocker.isPresent()){setEnabled(false);return;}workflow.start(EquipmentConfig.request(config),tick);if(workflow.terminal())setEnabled(false);}
    @Override public void onTick(){release();workflow.tick(++tick);if(workflow.terminal())setEnabled(false);}
    @Override public void onServerMessage(String line){workflow.onServerMessage(line);}
    @Override public void onDisable(){release();if(workflow.active())workflow.cancel();}
    public boolean hasPendingCleanup(){return !isEnabled()&&workflow.active();}
    /** Root can drain owned restoration after a manual stop without enabling the module. */
    public void maintenanceTick(){if(!isEnabled()&&workflow.active())workflow.tick(++tick);}
    @Override public String diagnostics(){return workflow.state()+": "+workflow.reason();}
}
