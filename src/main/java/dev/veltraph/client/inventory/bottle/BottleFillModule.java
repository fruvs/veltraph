package dev.veltraph.client.inventory.bottle;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Prepared native glass/water filling and chest storage. Buying bottles is a separate owner. */
public final class BottleFillModule extends AbstractModule {
    public static final String ID="bottle_fill_chest";
    private final ConfigService config;private final BottleFillWorkflow workflow;private final McBottleFillPort port;private BottleFillWorkflow.Request authorized;private long tick;
    public BottleFillModule(Minecraft client,ConfigService config,FarmServices services){super(ID,"Water Bottle Chest Filler",ModuleCategory.MISC);this.config=config;port=new McBottleFillPort(client,config,services,()->this);workflow=new BottleFillWorkflow(port);}
    public BottleFillWorkflow workflow(){return workflow;}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){try{return isEnabled()&&authorized!=null&&config.get(BottleFillConfig.BOTTLE_FILL_ENABLED)&&authorized.equals(BottleFillConfig.request(config))&&port.consumeExpectedTeleport(position,now);}catch(IllegalArgumentException invalid){return false;}}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public boolean allowsScreen(String title){return workflow.request()!=null&&workflow.request().chestTitle().equals(title);}
    @Override public Optional<String> preStartCheck(){
        if(workflow.active()||workflow.state()==BottleFillWorkflow.State.RECOVERY_REQUIRED)return Optional.of("Bottle filling or manual restitution still owned");
        if(!config.get(BottleFillConfig.BOTTLE_FILL_ENABLED))return Optional.of("Enable Water Bottle Chest Filler explicitly");
        try{var request=BottleFillConfig.request(config);var o=port.read(request);if(!o.valid()||o.menu()!=null||!o.cursorEmpty()||!o.dimension().equals(request.dimension())||!o.waterSource()||!o.chestValid())return Optional.of("Living loaded player, source water, exact chest, closed menu and empty cursor required");
            var bottles=o.inventory().stream().filter(request.glass()::matches).toList();if(bottles.size()!=1||bottles.getFirst().tool()||bottles.getFirst().count()<request.quantity())return Optional.of("One exact prepared glass stack of sufficient quantity required");
            if(o.inventory().stream().anyMatch(request.water()::matches))return Optional.of("Start with no matching filled bottles");
        }catch(IllegalArgumentException e){return Optional.of(e.getMessage());}return Optional.empty();
    }
    @Override public void onEnable(){tick=0;port.release();var error=preStartCheck();if(error.isPresent()){setEnabled(false);return;}authorized=BottleFillConfig.request(config);port.beginOwner();workflow.start(authorized,tick);if(!workflow.active())setEnabled(false);}
    @Override public void onTick(){boolean unchanged=false;try{unchanged=config.get(BottleFillConfig.BOTTLE_FILL_ENABLED)&&authorized.equals(BottleFillConfig.request(config));}catch(IllegalArgumentException ignored){}
        if(!unchanged)workflow.cancel();workflow.tick(++tick);if(!workflow.active())setEnabled(false);}
    @Override public void onDisable(){port.release();if(workflow.active())workflow.cancel();}
    @Override public boolean hasPendingCleanup(){return !isEnabled()&&(workflow.active()||workflow.recoveryReservesCurrentContext());}
    @Override public void maintenanceTick(){if(!isEnabled()&&workflow.active())workflow.tick(++tick);}
    @Override public String diagnostics(){return workflow.state()+": "+workflow.reason()+" (stored "+workflow.completed()+")";}
}
