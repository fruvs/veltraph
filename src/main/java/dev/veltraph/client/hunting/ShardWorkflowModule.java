package dev.veltraph.client.hunting;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.service.mc.McInventoryService;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.inventory.NativeStackIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Actual vanilla menu/slot/use requests. Source-unrecovered command/title/lore fields must be configured. */
public final class ShardWorkflowModule extends AbstractModule {
    public static final String ID="shards";
    private final Minecraft client;private final ConfigService config;private final McMenuAccess menus;
    private final McInventoryService inventory;private final ShardWorkflow runtime;
    private final java.util.function.LongSupplier clock;
    private int originalSlot=-1,consumptionSlot=-1,swapRevision=-1,swapRequestedTick=-1,sneakReadyTick=-1;
    private boolean heldSneak,awaitingUseAck;private String movingName;
    private int movingQuantity,stackBeforeTotal,usedStackCount;
    private Map<String,Integer> consumptionBaseline=Map.of();
    private List<String> openingCommands=List.of();
    private ShardWorkflow.Settings runSettings;private Object runPlayer,runWorld,runConnection;
    private int starts,rebinds;
    private int ownedSelectedSlot=-1;
    private final ShardOriginalStackCustody<NativeStackIdentity.Snapshot> originalStack=new ShardOriginalStackCustody<>();
    private net.minecraft.client.KeyMapping heldSneakKey;
    private record RestCapture(UUID uuid,String dimension,net.minecraft.world.phys.Vec3 position,float yaw,float pitch,Pose posture,
        int slot,List<ItemStack> inventory,Map<EquipmentSlot,ItemStack> gear,List<NativeStackIdentity.Snapshot> encodedInventory,
        Map<EquipmentSlot,NativeStackIdentity.Snapshot> encodedGear,ShardWorkflow.Settings settings,List<String> commands){}
    private RestCapture restCapture;private boolean restRebound;
    private static final List<EquipmentSlot> REST_GEAR=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND);
    public ShardWorkflowModule(Minecraft client,ConfigService config){this(client,config,System::currentTimeMillis);}
    public ShardWorkflowModule(Minecraft client,ConfigService config,java.util.function.LongSupplier clock){
        super(ID,"Shard leveling / mass syphon",ModuleCategory.HUNTING);this.client=client;this.config=config;this.clock=clock;
        menus=new McMenuAccess(client);inventory=new McInventoryService(client);
        runtime=new ShardWorkflow(new ShardWorkflow.Actions(){
            public boolean openShards(){return command(config.get(ShardConfig.SHARD_OPEN_COMMAND));}
            public boolean openAttributes(){return command(config.get(ShardConfig.SHARD_ATTRIBUTE_COMMAND));}
            public boolean searchShard(String name){
                String template=config.get(ShardConfig.SHARD_SEARCH_COMMAND),product=name+" Shard";
                if(!template.contains("{shard}")&&!template.contains("{query}"))return false;
                String query=ShardSearch.query(product,java.util.concurrent.ThreadLocalRandom.current().nextInt(12,17));
                return command(template.replace("{shard}",product).replace("{query}",query));
            }
            public boolean click(MenuSnapshot menu,MenuSnapshot.Slot slot){return menus.click(menu,slot.id());}
            public boolean close(MenuSnapshot menu){return menus.close(menu.id());}
            public boolean enterAmount(int quantity){
                if(!(client.gui.screen() instanceof AbstractSignEditScreen screen)||quantity<1)return false;
                // Vanilla editing dispatch updates its real sign text, and removed() sends vanilla sign packet.
                // 26.3 shortcuts use SDL keycode in KeyEvent's second field; GLFW values are ignored.
                if(!screen.keyPressed(new KeyEvent(0,1073741898,0))
                    ||!screen.keyPressed(new KeyEvent(0,1073741901,1))
                    ||!screen.keyPressed(new KeyEvent(0,8,0)))return false;
                for(char digit:Integer.toString(quantity).toCharArray())if(!screen.charTyped(new CharacterEvent(digit)))return false;
                screen.onClose();return true;
            }
            public boolean consume(String name,int quantity){return beginConsumption(name,quantity);}
            public void cleanup(){cleanupInputs();}
        });
    }
    public ShardWorkflow.Settings settings(){
        var catalog=ShardCatalog.parse(config.get(ShardConfig.SHARD_CATALOG));
        List<String> wanted=Arrays.stream(config.get(ShardConfig.SHARD_WANTED).split(";")).map(String::strip).filter(s->!s.isEmpty()).toList();
        return new ShardWorkflow.Settings(config.get(ShardConfig.SHARD_WORKFLOW_ENABLED),config.get(ShardConfig.SHARD_ALLOW_PURCHASES),config.get(ShardConfig.SHARD_ALLOW_CONSUMPTION),config.get(ShardConfig.SHARD_OPERATION)==ShardConfig.Operation.MASS_SYPHON,
            config.get(ShardConfig.SHARD_LEVEL_WANTED),config.get(ShardConfig.SHARD_MAX_PPA),config.get(ShardConfig.SHARD_MAX_SESSION_COST),config.get(ShardConfig.SHARD_MAX_QUANTITY),config.get(ShardConfig.SHARD_ACK_TIMEOUT),catalog,wanted,
            config.get(ShardConfig.SHARD_MENU_TITLE),config.get(ShardConfig.SHARD_CONFIRM_TITLE),config.get(ShardConfig.SHARD_MASS_BUTTON),config.get(ShardConfig.SHARD_CONFIRM_BUTTON),
            config.get(ShardConfig.SHARD_ATTRIBUTE_TITLE),config.get(ShardConfig.SHARD_ADVANCED_BUTTON),config.get(ShardConfig.SHARD_ADVANCED_MARKER),config.get(ShardConfig.SHARD_NEXT_BUTTON),config.get(ShardConfig.SHARD_PROGRESS_REGEX),
            config.get(ShardConfig.SHARD_BUY_TITLE),config.get(ShardConfig.SHARD_BUY_BUTTON),config.get(ShardConfig.SHARD_QUANTITY_TITLE),config.get(ShardConfig.SHARD_BUY_ONE_BUTTON),config.get(ShardConfig.SHARD_CUSTOM_BUTTON),
            config.get(ShardConfig.SHARD_PURCHASE_CONFIRM_TITLE),config.get(ShardConfig.SHARD_PURCHASE_CONFIRM_BUTTON),config.get(ShardConfig.SHARD_PRICE_REGEX));
    }
    public ShardWorkflow.Observation observation(){
        Map<String,Integer> counts=new LinkedHashMap<>();var catalog=settings().catalog();
        for(String name:catalog.names())counts.put(name,0);
        if(client.player!=null)for(int slot=0;slot<36;slot++){
            var item=client.player.getInventory().getItem(slot);if(item.isEmpty())continue;
            String label=ShardCatalog.clean(item.getHoverName().getString());
            if(!label.toLowerCase(Locale.ROOT).endsWith(" shard"))continue;
            var identity=catalog.identity(label.substring(0,label.length()-6));
            if(identity.isEmpty()&&config.get(ShardConfig.SHARD_OPERATION)==ShardConfig.Operation.MASS_SYPHON)
                throw new IllegalArgumentException("Unknown shard in inventory; mass syphon could consume an unlisted identity: "+label);
            if(identity.isPresent())counts.merge(identity.get(),item.getCount(),Integer::sum);
        }
        return new ShardWorkflow.Observation(menus.read(),counts,client.player!=null&&client.player.containerMenu.getCarried().isEmpty(),client.gui.screen() instanceof AbstractSignEditScreen);
    }
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public boolean ownsGameplayInput(){return !runtime.isScheduledPaused()&&(runtime.state()==ShardWorkflow.State.CONSUME_SHARD||runtime.state()==ShardWorkflow.State.WAIT_CONSUME_ACK);}
    @Override public boolean allowsScreen(String title){
        if(runtime.isScheduledPaused())return false;
        try{var cfg=settings();return List.of(cfg.shardTitle(),cfg.confirmTitle(),cfg.attributeTitle(),cfg.buyTitle(),cfg.quantityTitle(),cfg.purchaseConfirmTitle()).stream().anyMatch(t->ShardWorkflow.matchesTitle(title,t))
                ||runtime.state()==ShardWorkflow.State.ENTER_AMOUNT&&client.gui.screen() instanceof AbstractSignEditScreen;}
        catch(RuntimeException bad){return false;}
    }
    @Override public Optional<String> preStartCheck(){
        if(client.player==null||client.gameMode==null)return Optional.of("Player/world unavailable");
        try{var invalid=runtime.validate(settings());if(invalid.isPresent())return invalid;
            if(!observation().cursorEmpty())return Optional.of("Empty cursor required for shard workflow");return Optional.empty();
        }catch(RuntimeException bad){return Optional.of("Invalid shard configuration: "+bad.getMessage());}
    }
    @Override public void onEnable(){
        starts++;restCapture=null;restRebound=false;originalSlot=-1;ownedSelectedSlot=-1;originalStack.cleanup();
        try{if(!runtime.start(settings(),observation(),clock.getAsLong()))setEnabled(false);
            else {originalSlot=client.player==null?-1:inventory.selectedSlot();openingCommands=commands();runSettings=settings();runPlayer=client.player;runWorld=client.level;runConnection=client.getConnection();originalStack.reset(encodedStack(client.player.getInventory().getItem(originalSlot)));}}
        catch(RuntimeException bad){runtime.fail("Invalid shard setup: "+bad.getMessage());setEnabled(false);}
    }
    @Override public void onDisable(){runtime.cancel();cleanupInputs();menus.clear();restCapture=null;restRebound=false;}
    @Override public void onTick(){
        if(runtime.isScheduledPaused()){
            try{if(!Objects.equals(runSettings,settings())||!openingCommands.equals(commands())||client.player!=null&&!client.player.isAlive()
                ||restRebound&&(client.player!=runPlayer||client.level!=runWorld||client.getConnection()!=runConnection)){
                runtime.fail("Scheduled owner config, life or rebound native context changed");setEnabled(false);
            }}catch(RuntimeException invalid){runtime.fail("Scheduled owner settings became invalid");setEnabled(false);}
            return;
        }
        try{
            var current=settings();boolean valid=client.player!=null&&client.player.isAlive()&&client.level!=null&&client.player==runPlayer&&client.level==runWorld&&client.getConnection()==runConnection&&openingCommands.equals(commands())&&Objects.equals(runSettings,current);
            if(valid&&!selectionCustody())throw new IllegalStateException("Protected selected tool or manual slot changed");
            if(valid&&runtime.state()==ShardWorkflow.State.WAIT_CONSUME_ACK&&movingName!=null&&!advanceConsumption())throw new IllegalStateException("Inventory swap guard refused");
            runtime.tick(valid,current,observation(),clock.getAsLong());
        }catch(RuntimeException bad){runtime.fail("Shard adapter stopped: "+bad.getMessage());}
        if(!runtime.active())setEnabled(false);
    }
    @Override public void onServerMessage(String line){if(isEnabled()&&!runtime.isScheduledPaused())runtime.message(line);}
    @Override public String diagnostics(){return (runtime.isScheduledPaused()?"Scheduled pause; ":"")+runtime.status()+"; pending="+movingName+" quantity="+movingQuantity+" slot="+consumptionSlot
        +" inventory revision="+swapRevision+"→"+(client.player==null?-1:client.player.inventoryMenu.getStateId())+" swapTick="+swapRequestedTick+" useAck="+awaitingUseAck;}
    public ShardWorkflow runtime(){return runtime;}
    public int activationCount(){return starts;}
    public int scheduledRebindCount(){return rebinds;}
    public boolean scheduledPauseOwnedBy(Object ticket){return isEnabled()&&runtime.scheduledOwnedBy(ticket);}
    /** Root must independently own the matched departure/rejoin and area/pose restoration. */
    public Optional<String> scheduledPauseReadiness(){
        try{
            if(!isEnabled()||!runtime.active())return Optional.of("No active shard engine");
            if(!runtime.isScheduledPaused()&&!runtime.scheduledBoundary())return Optional.of("Shard currency/menu/sign/consumption request is pending");
            if(movingName!=null||awaitingUseAck||consumptionSlot>=0||heldSneak)return Optional.of("Owned inventory batch or input is pending");
            if(!nativeClosedLoaded())return Optional.of("Closed loaded native pose and empty cursor required");
            if(!runtime.isScheduledPaused()&&originalSlot>=0&&inventory.selectedSlot()!=originalSlot)return Optional.of("Run's protected selected tool changed before pause");
            if(!Objects.equals(runSettings,settings())||!openingCommands.equals(commands()))return Optional.of("Shard config/commands changed");
            if(runtime.isScheduledPaused()&&restCapture!=null&&!restoredPhysical())return Optional.of("Saved shard pose/tool/inventory has not been restored");
            return Optional.empty();
        }catch(RuntimeException invalid){return Optional.of("Invalid shard scheduling configuration");}
    }
    public boolean suspendForScheduledRest(Object ticket){
        thread();if(ticket==null||runtime.isScheduledPaused()||scheduledPauseReadiness().isPresent()
            ||client.player!=runPlayer||client.level!=runWorld||client.getConnection()!=runConnection)return false;
        RestCapture candidate;
        try{
            var p=client.player;var copied=new ArrayList<ItemStack>();for(int i=0;i<36;i++)copied.add(p.getInventory().getItem(i).copy());
            var gear=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);REST_GEAR.forEach(slot->gear.put(slot,p.getItemBySlot(slot).copy()));
            var encodedGear=new EnumMap<EquipmentSlot,NativeStackIdentity.Snapshot>(EquipmentSlot.class);gear.forEach((slot,item)->encodedGear.put(slot,encodedStack(item)));
            candidate=new RestCapture(p.getUUID(),client.level.dimension().identifier().toString(),p.position(),p.getYRot(),p.getXRot(),p.getPose(),inventory.selectedSlot(),List.copyOf(copied),Map.copyOf(gear),copied.stream().map(this::encodedStack).toList(),Map.copyOf(encodedGear),runSettings,List.copyOf(openingCommands));
        }catch(RuntimeException invalid){return false;}
        if(!runtime.pauseForScheduledRest(ticket,observation(),clock.getAsLong()))return false;
        restCapture=candidate;restRebound=false;menus.clear();return true;
    }
    /** Called only by Root after matched native join and physical restoration, while token remains owned. */
    public boolean rebindAfterScheduledArrival(Object ticket){
        thread();if(!scheduledPauseOwnedBy(ticket)||restCapture==null||restRebound||!restoredPhysical()
            ||client.player==runPlayer||client.level==runWorld||client.getConnection()==runConnection)return false;
        menus.clear();
        if(!runtime.rebindForScheduledRest(ticket,observation()))return false;
        runPlayer=client.player;runWorld=client.level;runConnection=client.getConnection();restRebound=true;rebinds++;return true;
    }
    public boolean resumeAfterScheduledRest(Object ticket){
        thread();if(!scheduledPauseOwnedBy(ticket)||!restRebound||!restoredPhysical()
            ||client.player!=runPlayer||client.level!=runWorld||client.getConnection()!=runConnection)return false;
        if(!runtime.resumeAfterScheduledRest(ticket,clock.getAsLong())){if(!runtime.active())setEnabled(false);return false;}
        restCapture=null;restRebound=false;return true;
    }
    private boolean nativeClosedLoaded(){
        if(client.player==null||client.level==null||client.gameMode==null||client.getConnection()==null||!client.player.isAlive()||!client.player.onGround()
            ||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return false;
        var pos=client.player.blockPosition();return client.level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false)!=null;
    }
    private boolean restoredPhysical(){
        if(restCapture==null||!nativeClosedLoaded())return false;var p=client.player;var saved=restCapture;
        if(!p.getUUID().equals(saved.uuid())||!client.level.dimension().identifier().toString().equals(saved.dimension())||p.position().distanceTo(saved.position())>.25
            ||Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(p.getYRot()-saved.yaw()))>1.5||Math.abs(p.getXRot()-saved.pitch())>1.5
            ||p.getPose()!=saved.posture()||inventory.selectedSlot()!=saved.slot()||!Objects.equals(saved.settings(),settings())||!saved.commands().equals(commands()))return false;
        for(int i=0;i<36;i++)if(!saved.encodedInventory().get(i).matches(p.getInventory().getItem(i),client.level.registryAccess()))return false;
        return REST_GEAR.stream().allMatch(slot->saved.encodedGear().get(slot).matches(p.getItemBySlot(slot),client.level.registryAccess()));
    }
    /** Native registry-aware encoding preserves exact ID/count/stored components across new holder owners. */
    private NativeStackIdentity.Snapshot encodedStack(ItemStack item){return NativeStackIdentity.capture(item,client.level.registryAccess());}
    public String scheduledRestorationDiagnostics(){
        if(restCapture==null||client.player==null||client.level==null)return "No captured/current native restoration";
        var p=client.player;var saved=restCapture;String different="";
        for(int i=0;i<36;i++)if(!saved.encodedInventory().get(i).equals(encodedStack(p.getInventory().getItem(i)))){different=" inventory slot "+i+" saved="+saved.encodedInventory().get(i)+" current="+encodedStack(p.getInventory().getItem(i));break;}
        for(var slot:REST_GEAR)if(!saved.encodedGear().get(slot).equals(encodedStack(p.getItemBySlot(slot))))different+=" gear="+slot;
        return "closedLoaded="+nativeClosedLoaded()+" uuid="+p.getUUID().equals(saved.uuid())+" distance="+p.position().distanceTo(saved.position())
            +" view="+p.getYRot()+","+p.getXRot()+" saved="+saved.yaw()+","+saved.pitch()+" pose="+p.getPose()+" saved="+saved.posture()
            +" selected="+inventory.selectedSlot()+" saved="+saved.slot()+" config="+Objects.equals(saved.settings(),settings())+" commands="+saved.commands().equals(commands())+different;
    }
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Shard scheduling requires client thread");}
    private List<String> commands(){return List.of(config.get(ShardConfig.SHARD_OPEN_COMMAND),config.get(ShardConfig.SHARD_ATTRIBUTE_COMMAND),config.get(ShardConfig.SHARD_SEARCH_COMMAND));}
    private boolean command(String text){
        if(client.player==null||text==null||text.isBlank()||text.contains("\n")||text.contains("\r"))return false;
        client.player.connection.sendCommand(text.startsWith("/")?text.substring(1):text);return true;
    }
    private boolean beginConsumption(String name,int quantity){
        if(client.player==null||client.gameMode==null||client.gui.screen()!=null||!config.get(ShardConfig.SHARD_ALLOW_CONSUMPTION))return false;
        if(originalSlot<0||!selectionCustody())return false;
        // Multiple acquired stacks are valid; excess preexisting shards must never be batch-consumed.
        if(count(name)!=quantity)return false;
        movingName=name;movingQuantity=quantity;consumptionBaseline=observation().inventory();
        return advanceConsumption();
    }
    private boolean advanceConsumption(){
        if(client.player==null||client.gameMode==null||movingName==null)return false;
        var observed=observation().inventory();
        for(var entry:consumptionBaseline.entrySet())if(!entry.getKey().equals(movingName)&&!Objects.equals(entry.getValue(),observed.get(entry.getKey())))return false;
        if(awaitingUseAck){
            int current=count(movingName);
            if(current==stackBeforeTotal)return true;
            if(current!=stackBeforeTotal-usedStackCount)return false;
            if(!originalStack.acknowledgeUse(value->value.matches(client.player.getInventory().getItem(originalSlot),client.level.registryAccess())))return false;
            movingQuantity-=usedStackCount;awaitingUseAck=false;consumptionSlot=-1;swapRevision=-1;swapRequestedTick=-1;sneakReadyTick=-1;
            if(movingQuantity==0){
                movingName=null;return true;
            }
        }
        if(consumptionSlot<0){
            int slot=-1;for(int index=0;index<36;index++)if(exactShard(index,movingName)){slot=index;break;}
            if(slot<0||count(movingName)!=movingQuantity)return false;
            if(slot>8){
                int empty=-1;for(int index=0;index<9;index++)if(client.player.getInventory().getItem(index).isEmpty()){empty=index;break;}
                if(empty<0)return false;
                // InventoryMenu indexes main inventory 9..35 and hotbar 36..44.
                swapRevision=client.player.inventoryMenu.getStateId();
                swapRequestedTick=client.player.tickCount;
                if(empty==originalSlot)originalStack.issuedSwap(encodedStack(client.player.getInventory().getItem(slot)));
                client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId,slot,empty,ContainerInput.SWAP,client.player);consumptionSlot=empty;return true;
            }
            consumptionSlot=slot;
        }
        // Correctly predicted vanilla SWAP emits no guaranteed server correction: changed-slot hashes
        // already synchronize the peer. Allow a vanilla tick for corrections, then send one ordered
        // held-slot/use request. Prediction is never success: exact server consumption must follow.
        if(swapRequestedTick>=0&&client.player.tickCount<=swapRequestedTick)return true;
        if(!exactShard(consumptionSlot,movingName))return false;
        if(!originalStack.acknowledgeSwap(value->value.matches(client.player.getInventory().getItem(originalSlot),client.level.registryAccess())))return false;
        int stackCount=client.player.getInventory().getItem(consumptionSlot).getCount();
        if(stackCount<1||stackCount>movingQuantity)return false;
        inventory.selectSlot(consumptionSlot);ownedSelectedSlot=consumptionSlot;
        if(stackCount>1){
            if(sneakReadyTick<0){McHeldKeys.setHeld(client.options.keyShift,true);heldSneak=true;heldSneakKey=client.options.keyShift;sneakReadyTick=client.player.tickCount;return true;}
            // Allow the ordinary client tick to send its changed PlayerInput before the use-item packet.
            if(client.player.tickCount<=sneakReadyTick)return true;
        }
        awaitingUseAck=true;stackBeforeTotal=count(movingName);usedStackCount=stackCount;
        if(consumptionSlot==originalSlot){var expected=client.player.getInventory().getItem(originalSlot).copy();expected.shrink(stackCount);originalStack.issuedUse(encodedStack(expected));}
        client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
        return true;
    }
    private int count(String name){int quantity=0;for(int index=0;index<36;index++)if(exactShard(index,name))quantity+=client.player.getInventory().getItem(index).getCount();return quantity;}
    private boolean exactShard(int slot,String name){
        var item=client.player.getInventory().getItem(slot);return !item.isEmpty()&&ShardCatalog.clean(item.getHoverName().getString()).equalsIgnoreCase(name+" Shard");
    }
    private boolean originalToolExact(){
        var item=client.player.getInventory().getItem(originalSlot);var registries=client.level.registryAccess();
        return originalStack.matches(value->value.matches(item,registries));
    }
    private boolean selectionCustody(){
        return client.player!=null&&client.level!=null&&originalStack.present()
            &&originalToolExact()
            &&inventory.selectedSlot()==(ownedSelectedSlot>=0?ownedSelectedSlot:originalSlot);
    }
    private void cleanupInputs(){
        boolean sameContext=client.player==runPlayer&&client.level==runWorld&&client.getConnection()==runConnection;
        boolean toolExact=client.player!=null&&client.level!=null&&originalSlot>=0&&originalStack.present()
            &&originalToolExact();
        int selected=client.player==null?-1:inventory.selectedSlot();
        boolean restore=ShardSelectionCustody.restore(sameContext,toolExact,originalSlot,ownedSelectedSlot,selected);
        movingName=null;consumptionSlot=-1;swapRevision=-1;swapRequestedTick=-1;sneakReadyTick=-1;awaitingUseAck=false;consumptionBaseline=Map.of();
        if(heldSneak){
            // Release only the exact key mapping captured by this run; never a new mapping.
            if(heldSneakKey!=null&&heldSneakKey==client.options.keyShift)McHeldKeys.setHeld(heldSneakKey,false);
            heldSneak=false;heldSneakKey=null;
        }
        if(restore)inventory.selectSlot(originalSlot);
        ownedSelectedSlot=-1;originalStack.cleanup();
        // Keep the original run tool across completed jobs. Activation replaces it explicitly.
    }
}
