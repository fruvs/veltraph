package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.game.mc.McGameContextService;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Recovered current-plot USE_SPRAYONATOR branch, using existing inventory resources. */
public final class McPestSprayModule extends AbstractModule {
    public static final String ID="pest_spray_current_plot";
    private final Minecraft client;
    private final FarmServices services;
    private final ConfigService config;
    private final PestSprayRuntime runtime;
    private final McGameContextService location;
    private ClientLevel level;
    private LocalPlayer owner;
    private int savedSlot=-1;
    private ItemStack material=ItemStack.EMPTY;
    private String originalMaterial="",originalLore="";
    public McPestSprayModule(Minecraft client,FarmServices services,ConfigService config){
        super(ID,"Spray current Garden plot",ModuleCategory.INTERMEDIARY);
        this.client=client;this.services=services;this.config=config;location=new McGameContextService(client);
        runtime=new PestSprayRuntime(new PestSprayRuntime.Actions(){
            public boolean select(int slot){services.inventory().selectSlot(slot);return services.inventory().selectedSlot()==slot;}
            public boolean use(PestSprayRuntime.Snapshot expected){
                if(client.gameMode==null||!expected.equals(snapshot()))return false;
                client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);return true;
            }
            public void release(){cleanup();}
        });
    }
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    private PestSprayRuntime.Settings settings(){return new PestSprayRuntime.Settings(config.get(PestConfig.PESTS_USE_SPRAYONATOR),
            config.get(PestConfig.PEST_SPRAY_PLOT),config.get(PestConfig.PEST_SPRAY_UNITS),config.get(PestConfig.PEST_SPRAY_UNIT_VALUE),
            config.get(PestConfig.PEST_SPRAY_MAX_VALUE),config.get(PestConfig.PEST_SPRAY_CONTEST_ONLY),config.get(PestConfig.PEST_SPRAY_ACK_TIMEOUT_MS));}
    @Override public Optional<String> preStartCheck(){return PestSprayRuntime.validate(read(false),settings());}
    @Override public void onEnable(){
        releaseInput();level=client.level;owner=client.player;savedSlot=services.inventory().selectedSlot();material=ItemStack.EMPTY;
        originalMaterial=config.get(PestConfig.PEST_SPRAY_MATERIAL).trim();originalLore=config.get(PestConfig.PEST_SPRAY_SELECTION_LORE).trim();
        var s=read(true);if(!runtime.start(s,settings(),services.clock().nowMillis())){setEnabled(false);return;}
    }
    @Override public void onTick(){
        runtime.tick(snapshot(),settings(),services.clock().nowMillis());if(!runtime.active())setEnabled(false);
    }
    @Override public void onServerMessage(String line){runtime.message(LocationText.stripColorCodes(line));if(!runtime.active()&&isEnabled())setEnabled(false);}
    @Override public void onDisable(){try{runtime.cancel();}finally{cleanup();level=null;owner=null;material=ItemStack.EMPTY;originalMaterial="";originalLore="";}}
    private void cleanup(){
        try{releaseInput();}finally{
            if(savedSlot>=0&&client.player==owner&&client.level==level&&owner!=null){services.inventory().selectSlot(savedSlot);}
            savedSlot=-1;
        }
    }
    private void releaseInput(){
        try{services.rotation().cancelRotation();}finally{try{services.input().releaseAllMovement();services.breaking().setBreaking(false);}finally{
            if(client.options!=null){dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyAttack,false);dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,false);client.options.keyUp.setDown(false);
                client.options.keyDown.setDown(false);client.options.keyLeft.setDown(false);client.options.keyRight.setDown(false);
                client.options.keyJump.setDown(false);dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyShift,false);dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keySprint,false);}
        }}
    }
    public PestSprayRuntime.State sprayState(){return runtime.state();}
    @Override public String diagnostics(){return runtime.status();}
    private PestSprayRuntime.Snapshot snapshot(){return read(false);}
    private static String name(ItemStack s){return LocationText.stripColorCodes(s.getHoverName().getString()).trim();}
    private static String identity(ItemStack s){return s.isEmpty()?"":BuiltInRegistries.ITEM.getKey(s.getItem())+"|"+s.getComponents().toString();}
    private PestSprayRuntime.Snapshot read(boolean remember){
        if(client.player==null||client.level==null||!client.player.isAlive())return null;
        if(isEnabled()&&(client.player!=owner||client.level!=level))return null;
        String desired=config.get(PestConfig.PEST_SPRAY_MATERIAL).trim(),line=config.get(PestConfig.PEST_SPRAY_SELECTION_LORE).trim();
        if(desired.equals("Sprayonator"))return null;
        if(isEnabled()&&(!desired.equals(originalMaterial)||!line.equals(originalLore)))return null;
        var inv=client.player.getInventory();int tool=-1;ItemStack toolStack=ItemStack.EMPTY;
        for(int i=0;i<9;i++)if(!inv.getItem(i).isEmpty()&&name(inv.getItem(i)).equals("Sprayonator")){
            if(tool>=0)return null;tool=i;toolStack=inv.getItem(i);
        }
        ItemStack template=material;int count=0;
        if(template.isEmpty()){
            for(int i=0;i<36;i++){var s=inv.getItem(i);if(!s.isEmpty()&&!desired.isBlank()&&name(s).equals(desired)){
                if(template.isEmpty())template=s.copy();else if(!ItemStack.isSameItemSameComponents(template,s))return null;
            }}
        }
        for(int i=0;i<36;i++){var s=inv.getItem(i);if(!template.isEmpty()&&ItemStack.isSameItemSameComponents(template,s))count=Math.addExact(count,s.getCount());}
        if(remember)material=template.copy();
        var lore=toolStack.get(DataComponents.LORE);boolean selected=!line.isBlank()&&lore!=null&&lore.lines().stream()
                .anyMatch(c->line.equals(LocationText.stripColorCodes(c.getString()).trim()));
        location.resolution();boolean contest=GardenContestSignal.active(location.signals().sidebarRowsRaw());
        boolean screenFree=client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu
                &&client.player.inventoryMenu.getCarried().isEmpty();
        return new PestSprayRuntime.Snapshot(true,screenFree,GardenPlotBounds.contains(config.get(PestConfig.PEST_SPRAY_PLOT),client.player.getX(),client.player.getZ())
                &&GardenPlotBounds.sourceSprayContains(config.get(PestConfig.PEST_SPRAY_PLOT),client.player.getX(),client.player.getY(),client.player.getZ()),
                contest,tool,identity(toolStack),identity(template),count,selected,services.inventory().selectedSlot());
    }
}
