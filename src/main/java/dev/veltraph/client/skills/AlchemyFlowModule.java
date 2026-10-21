package dev.veltraph.client.skills;

import dev.veltraph.client.config.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.regex.Pattern;

/** Finite, explicitly configured native stand/resource/recipe/replenishment orchestration. */
public final class AlchemyFlowModule extends GuardedSkillModule {
    public static final String ID="alchemy_brew_chain";
    private final AlchemyFlowMenus io;
    private final McNavigator navigator;private final McSharedMovementNavigator mobility;private final dev.veltraph.client.navigation.mc.McParentMotionCleanup motionCleanup;
    private final AlchemyApproachGate approachGate=new AlchemyApproachGate();
    private net.minecraft.world.phys.Vec3 approachPose;
    private final McAlchemySalePort salePort;
    private final AlchemySaleParent saleParent;
    private final List<dev.veltraph.client.inventory.selling.SellingObservation.Stack> collectedPotions=new ArrayList<>();
    private dev.veltraph.client.inventory.selling.SellingObservation collectionBefore;
    private Object connection;
    private int soldPotions;
    private AlchemyFlowProfile profile;
    private String rawProfile;
    private int batches,step,fillSlot,tick,openedTick;
    private long due,nextAction,coins,value;
    private boolean walking,opened,cleaning,pendingIngress,pendingIngressStand,transferPicked;
    private Object previousMenu;
    private String afterOpen,terminal="CANCELLED",failureReason="Stopped";
    private Transfer transfer;
    private Check check;
    private Purchase purchase;
    private final Map<Integer,AlchemyFlowProfile.Item> placed=new TreeMap<>();
    private final Set<BlockPos> visited=new HashSet<>();
    private final Map<String,Integer> requirements=new LinkedHashMap<>();
    private final Map<String,AlchemyFlowProfile.Resource> resources=new LinkedHashMap<>();
    private int fuelBefore,stepInventoryBefore;
    private boolean stageStarted;
    private record Transfer(AlchemyFlowProfile.Item item,int source,int target,int stack,int before,int beforeFuel,String after) {}
    private record Check(String kind,AlchemyFlowProfile.Item item,int slot,int before,int count,int oldFuel,String after) {}
    private static final class Purchase {
        final AlchemyFlowProfile.Resource resource;final int before;final long cost;final AlchemyFlowProfile.Item icon;final int iconCount;
        boolean receipt;
        Purchase(AlchemyFlowProfile.Resource resource,int before,long cost,AlchemyFlowProfile.Item icon,int count){this.resource=resource;this.before=before;this.cost=cost;this.icon=icon;iconCount=count;}
    }
    public AlchemyFlowModule(Minecraft client,ConfigService config,FarmServices services) {
        super(ID,"Native Alchemy Brew Chain",client,config,services);io=new AlchemyFlowMenus(client);navigator=new McNavigator(client,services.input(),config);mobility=new McSharedMovementNavigator(client,services,config);motionCleanup=new dev.veltraph.client.navigation.mc.McParentMotionCleanup(client,services,()->this);salePort=new McAlchemySalePort(client);saleParent=new AlchemySaleParent(salePort);
    }
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.ISLAND);}
    @Override public boolean allowsScreen(String title){return profile!=null&&(AlchemyFlowMenus.clean(title).equals(profile.standTitle())||profile.acquireMissing()&&AlchemyFlowMenus.clean(title).equals(profile.shopTitle())||saleParent.allowsScreen(AlchemyFlowMenus.clean(title)));}
    @Override public Optional<String> preStartCheck(){return invalid(()->{
        if(!config.get(AlchemyFlowConfig.ALCHEMY_FLOW_ENABLED)) throw new IllegalArgumentException("Enable native alchemy flow explicitly");
        var plan=AlchemyFlowProfile.parse(config.get(AlchemyFlowConfig.ALCHEMY_FLOW_PROFILE));
        if(client.gui.screen()!=null||!io.emptyCursor()) throw new IllegalArgumentException("Start native alchemy from gameplay with an empty cursor");
        if(plan.terminalSale()!=null){if(!salePort.read(plan.terminalSale()).cookieActive())throw new IllegalArgumentException("Configure and observe fresh native cookie readiness before Alchemy sale");var opener=client.player.getInventory().getItem(plan.terminalSale().openerSlot());if(opener.isEmpty()||!AlchemyFlowMenus.same(AlchemyFlowMenus.identity(opener),plan.terminalSale().opener()))throw new IllegalArgumentException("Exact configured Alchemy SkyBlock opener required");}
        for(var stand:plan.stands()) block(stand,"minecraft:brewing_stand");
        if(plan.acquireMissing())block(plan.shop(),plan.shopBlock());
        for(var resource:catalog(plan).values()){var key=net.minecraft.resources.Identifier.tryParse(resource.item().id());if(key==null||!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalArgumentException("Unknown resource registry ID");io.count(resource.item());}
        for(var s:plan.steps()){var key=net.minecraft.resources.Identifier.tryParse(s.output().id());if(key==null||!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalArgumentException("Unknown output registry ID");}
        if(io.free()<plan.bottles()+2)throw new IllegalArgumentException("Reserve native potion and resource inventory capacity");
        if(!plan.acquireMissing()){
            if(io.count(plan.input().item())<plan.bottles()||io.count(plan.fuel().item())<1)throw new IllegalArgumentException("Missing initial water/input or fuel resources");
            var needs=new HashMap<String,Integer>();for(var s:plan.steps())needs.merge(s.reagent().item().key(),1,Integer::sum);
            for(var r:catalog(plan).values())if(io.count(r.item())<needs.getOrDefault(r.item().key(),0))throw new IllegalArgumentException("Missing declared ingredient chain");
        }
    });}
    private static Map<String,AlchemyFlowProfile.Resource> catalog(AlchemyFlowProfile p){
        var result=new LinkedHashMap<String,AlchemyFlowProfile.Resource>();
        for(var r:java.util.stream.Stream.concat(java.util.stream.Stream.of(p.input(),p.fuel()),p.steps().stream().map(AlchemyFlowProfile.Step::reagent)).toList()) {
            var old=result.putIfAbsent(r.item().key(),r);if(old!=null&&!old.equals(r))throw new IllegalArgumentException("Conflicting resource/offer contracts");
        }return result;
    }
    private void block(BlockPos point,String id){
        if(client.level==null||!client.level.getChunkSource().hasChunk(point.x()>>4,point.z()>>4))throw new IllegalStateException("Alchemy target chunk unloaded");
        var actual=BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(mc(point)).getBlock()).toString();if(!actual.equals(id))throw new IllegalStateException("Configured alchemy block changed: "+actual);
    }
    private static net.minecraft.core.BlockPos mc(BlockPos p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    private BlockPos stand(){return profile.stands().get(batches%profile.stands().size());}
    private List<Integer> bottleSlots(){return profile.layout().bottleSlots().subList(0,profile.bottles());}
    private int ingredientSlot(){return profile.layout().ingredientSlot();}
    private int fuelSlot(){return profile.layout().fuelSlot();}
    private List<Integer> standSlots(){var list=new ArrayList<>(bottleSlots());list.add(ingredientSlot());list.add(fuelSlot());return list;}
    private void phase(String next){phase=next;due=now()+(next.equals("WAIT_BREW")?profile.brewTimeout():profile.actionTimeout());}
    @Override public void onEnable(){
        var invalid=preStartCheck();if(invalid.isPresent()){phase="FAILED";reason=invalid.get();setEnabled(false);return;}
        begin();rawProfile=config.get(AlchemyFlowConfig.ALCHEMY_FLOW_PROFILE);profile=AlchemyFlowProfile.parse(rawProfile);io.configure(profile);
        connection=client.getConnection();salePort.reset();soldPotions=0;collectedPotions.clear();collectionBefore=null;
        deadline=now()+profile.runTimeout();batches=step=fillSlot=tick=0;coins=value=0;transfer=null;check=null;purchase=null;placed.clear();visited.clear();resources.clear();resources.putAll(catalog(profile));cleaning=false;nextAction=now();
        open("STAND","INSPECT");
    }
    @Override protected void release(){motionCleanup.release(mobility);}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return isEnabled()&&context()&&client.getConnection()==connection&&now()<=due&&config.get(AlchemyFlowConfig.ALCHEMY_FLOW_ENABLED)&&rawProfile.equals(config.get(AlchemyFlowConfig.ALCHEMY_FLOW_PROFILE))&&walking&&mobility.consumeExpectedTeleport(position,now);}
    private void open(String target,String then){mobility.stop();approachGate.reset();approachPose=client.player.position();io.resetReceipt();walking=opened=false;previousMenu=client.player.containerMenu;ownedMenu=null;afterOpen=then;phase("OPEN_"+target);}
    private void closeOwned(){if(client.player!=null&&client.player.containerMenu==ownedMenu&&io.emptyCursor()){client.player.closeContainer();ownedMenu=null;io.resetReceipt();}}
    private void reopen(String target,String then){closeOwned();open(target,then);}
    @Override public void onTick(){try{
        tick++;
        if(!context()||client.getConnection()!=connection||!config.get(AlchemyFlowConfig.ALCHEMY_FLOW_ENABLED)||!rawProfile.equals(config.get(AlchemyFlowConfig.ALCHEMY_FLOW_PROFILE))){stop("Alchemy context/config changed or run timed out");return;}
        io.validateProtected();
        if(phase.equals("TERMINAL_SALE")){tickTerminalSale();return;}
        if(now()>due){stop("Alchemy phase has no authoritative acknowledgment: "+phase);return;}
        if(phase.startsWith("OPEN_")){approach(phase.endsWith("STAND"));return;}
        var view=io.read();if(view==null||view.menu()!=ownedMenu){stop("Alchemy menu replaced or closed");return;}
        block(io.stand(view)?stand():profile.shop(),io.stand(view)?"minecraft:brewing_stand":profile.shopBlock());
        if(io.awaitingReceipt(view)){reason="Awaiting native same-menu Alchemy slot receipt";return;}
        if(phase.equals("TRANSFER_PICK")){transferPick(view);return;}
        if(phase.equals("TRANSFER_PLACE")){transferPlace(view);return;}
        if(phase.equals("TRANSFER_RETURN")){transferReturn(view);return;}
        if(phase.equals("VERIFY_TRANSFER")){verify(view);return;}
        if(phase.equals("BUY_WAIT")){buyWait(view);return;}
        if(view.cursorCount()!=0){stop("Foreign alchemy cursor");return;}
        switch(phase){
            case "INSPECT"->inspect(view);
            case "STOCK"->stock(view);
            case "SHOP"->buy(view);
            case "FILL"->fill(view);
            case "REAGENT"->reagent(view);
            case "WAIT_BREW"->brew(view);
            case "COLLECT"->collect(view);
            default->stop("Invalid alchemy state");
        }
    }catch(RuntimeException error){stop("Alchemy guard: "+error.getMessage());}}
    private void approach(boolean brewer){
        BlockPos point=brewer?stand():profile.shop();block(point,brewer?"minecraft:brewing_stand":profile.shopBlock());
        var view=io.read();if(view!=null){
            String title=brewer?profile.standTitle():profile.shopTitle();
            if(!opened||view.menu()==previousMenu||!view.title().equals(title)||brewer&&!io.stand(view)||view.cursorCount()!=0){stop("Unexpected alchemy ingress menu");return;}
            if(ownedMenu!=view.menu()){ownedMenu=view.menu();openedTick=tick;}
            if(view.revision()>0&&tick-openedTick>=2){phase(afterOpen);}return;
        }
        if(client.gui.screen()!=null){stop("Unexpected alchemy ingress screen");return;}
        if(opened)return;
        var center=net.minecraft.world.phys.Vec3.atCenterOf(mc(point));
        var actualPose=client.player.position();
        double displacement=approachPose==null?0:actualPose.distanceTo(approachPose);approachPose=actualPose;
        var action=approachGate.decide(client.player.getEyePosition().distanceTo(center),displacement);
        if(action==AlchemyApproachGate.Decision.AIM&&!client.player.onGround())return;
        if(action==AlchemyApproachGate.Decision.START){
            var candidates=java.util.stream.Stream.of(point.add(1,0,0),point.add(-1,0,0),point.add(0,0,1),point.add(0,0,-1))
                    .sorted(Comparator.comparingDouble(p->client.player.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(mc(p))))).toList();
            for(var candidate:candidates)if(mobility.startPreferLevel(navigator.position(candidate),.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL)){walking=true;approachGate.started();break;}
            if(!walking){stop("No safe alchemy approach");return;}
            action=AlchemyApproachGate.Decision.WALK;
        }
        if(action==AlchemyApproachGate.Decision.WALK){
            var state=mobility.tick();
            if(state==McTravelNavigator.State.FAILED){stop("Alchemy approach blocked");return;}
            if(state==McTravelNavigator.State.ARRIVED){walking=false;approachGate.arrived();mobility.stop();}
            return;
        }
        if(action==AlchemyApproachGate.Decision.SETTLE)return;
        double dx=center.x-client.player.getX(),dz=center.z-client.player.getZ(),dy=center.y-client.player.getEyeY();
        float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        if(motionCleanup.beforePublication(2)){services.rotation().track(yaw,pitch);motionCleanup.remember();}
        if(Math.abs(WalkController.wrap(client.player.getYRot()-yaw))<1&&Math.abs(client.player.getXRot()-pitch)<1
                &&client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(mc(point))&&now()>=nextAction){
            client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,hit);opened=true;nextAction=now()+profile.clickDelay();reason="Awaiting native alchemy menu";
        }
    }
    private void inspect(AlchemyFlowMenus.View v){
        if(!io.stand(v))throw new IllegalStateException("Native BrewingStandMenu required");
        for(int id:standSlots())if(v.slot(id).count()!=0)throw new IllegalStateException("Stand must have no foreign prepared resources");
        if(v.brew()!=0||v.fuel()>0&&!visited.contains(stand()))throw new IllegalStateException("Stand already brewing or holds unowned fuel energy");
        requirements.clear();requirements.put(profile.input().item().key(),profile.bottles());
        for(var s:profile.steps())requirements.merge(s.reagent().item().key(),1,Integer::sum);
        if(v.fuel()<profile.steps().size())requirements.merge(profile.fuel().item().key(),1,Integer::sum);
        long batchValue=0;for(var e:requirements.entrySet())batchValue=Math.addExact(batchValue,Math.multiplyExact(resources.get(e.getKey()).item().value(),e.getValue()));
        if(Math.addExact(value,batchValue)>profile.valueBudget())throw new IllegalStateException("Repeat would exceed resource value budget");
        phase("STOCK");
    }
    private boolean stocked(){return requirements.entrySet().stream().allMatch(e->io.count(resources.get(e.getKey()).item())>=e.getValue());}
    private void stock(AlchemyFlowMenus.View v){
        if(stocked()){step=fillSlot=0;phase("FILL");return;}
        if(!profile.acquireMissing())throw new IllegalStateException("Missing repeat resources; acquisition is disabled");
        closeOwned();open("SHOP","SHOP");
    }
    private void buy(AlchemyFlowMenus.View v){
        if(stocked()){reopen("STAND","FILL");step=fillSlot=0;return;}
        var need=requirements.entrySet().stream().filter(e->io.count(resources.get(e.getKey()).item())<e.getValue()).findFirst().orElseThrow();
        var resource=resources.get(need.getKey());var offer=resource.offer();
        var buttons=v.slots().stream().filter(s->!s.player()&&s.count()>0&&s.identity().name().equals(offer.label())).toList();
        if(buttons.size()!=1)throw new IllegalStateException("Ambiguous or missing resource offer");
        var button=buttons.getFirst();var quote=SkillContracts.quotedCost(button.lore(),offer.amount());
        if(quote.isEmpty()||quote.getAsLong()>offer.maxCost()||Math.addExact(coins,quote.getAsLong())>profile.coinBudget())throw new IllegalStateException("Resource quote unreadable or exceeds purchase/session cap");
        if(io.free()<offer.amount())throw new IllegalStateException("Reserve capacity for each quoted resource item");
        if(now()<nextAction)return;
        purchase=new Purchase(resource,io.count(resource.item()),quote.getAsLong(),button.identity(),button.count());
        if(!io.click(v,button.id(),0,ContainerInput.PICKUP))throw new IllegalStateException("Stale acquisition quote");
        coins=Math.addExact(coins,purchase.cost);nextAction=now()+profile.clickDelay();phase("BUY_WAIT");
    }
    @Override public void onServerMessage(String text){
        saleParent.onMessage(text);
        if(purchase==null)return;
        String line=AlchemyFlowMenus.clean(text);if(line.length()>2048)return;
        var match=Pattern.compile(purchase.resource.offer().receipt()).matcher(line);
        if(match.matches())try{
            long quantity=Long.parseLong(match.group("quantity").replace(",","")),cost=Long.parseLong(match.group("cost").replace(",",""));
            if(quantity==purchase.resource.offer().amount()&&cost==purchase.cost)purchase.receipt=true;
            else if(isEnabled())stop("Resource receipt disagrees with owned quote");
        }catch(RuntimeException invalid){if(isEnabled())stop("Invalid resource receipt");}
    }
    private void buyWait(AlchemyFlowMenus.View v){
        if(v.cursorCount()!=0&&!(v.cursorCount()==purchase.iconCount&&AlchemyFlowMenus.same(v.cursor(),purchase.icon)))throw new IllegalStateException("Acquisition cursor changed");
        int count=io.count(purchase.resource.item());if(count!=purchase.before&&count!=purchase.before+purchase.resource.offer().amount())throw new IllegalStateException("Acquired resource quantity differs from quote");
        if(purchase.receipt&&count==purchase.before+purchase.resource.offer().amount()&&v.cursorCount()==0){
            check=new Check("BUY",purchase.resource.item(),-1,purchase.before,purchase.resource.offer().amount(),-1,"SHOP");purchase=null;phase("VERIFY_TRANSFER");
        }
    }
    private void fill(AlchemyFlowMenus.View v){
        for(int id:bottleSlots())if(!placed.containsKey(id)){startTransfer(v,profile.input().item(),id,"FILL");return;}
        if(v.fuel()<profile.steps().size()){startTransfer(v,profile.fuel().item(),fuelSlot(),"REAGENT");return;}
        visited.add(stand());phase("REAGENT");
    }
    private void reagent(AlchemyFlowMenus.View v){
        if(step>=profile.steps().size()){phase("COLLECT");return;}
        var expected=step==0?profile.input().item():profile.steps().get(step-1).output();
        for(int id:bottleSlots())if(v.slot(id).count()!=1||!AlchemyFlowMenus.same(v.slot(id).identity(),expected))throw new IllegalStateException("Native potion input differs from declared stage");
        if(v.slot(ingredientSlot()).count()!=0||v.brew()!=0||v.fuel()<1)throw new IllegalStateException("Native stand not ready for next ingredient");
        var ingredient=profile.steps().get(step).reagent().item();fuelBefore=v.fuel();stepInventoryBefore=io.count(ingredient);stageStarted=false;
        startTransfer(v,ingredient,ingredientSlot(),"WAIT_BREW");
    }
    private void startTransfer(AlchemyFlowMenus.View v,AlchemyFlowProfile.Item item,int target,String after){
        if(v.cursorCount()!=0||v.slot(target)==null||v.slot(target).player()||v.slot(target).count()!=0)throw new IllegalStateException("Resource destination/cursor changed");
        var sources=v.slots().stream().filter(s->s.player()&&s.count()>0&&AlchemyFlowMenus.same(s.identity(),item)).toList();
        if(sources.isEmpty())throw new IllegalStateException("Resource disappeared before pickup");
        var source=sources.getFirst();transferPicked=false;transfer=new Transfer(item,source.id(),target,source.count(),io.count(item),v.fuel(),after);phase("TRANSFER_PICK");
        if(!io.mayPlace(v,target,source.id()))throw new IllegalStateException("Configured resource is not accepted by native stand slot");
    }
    private void request(AlchemyFlowMenus.View v,int slot,int button,ContainerInput input){
        if(!io.click(v,slot,button,input))throw new IllegalStateException("Resource slot/cursor/revision changed at dispatch");nextAction=now()+profile.clickDelay();
    }
    private void transferPick(AlchemyFlowMenus.View v){
        if(now()<nextAction)return;
        var source=v.slot(transfer.source());if(v.cursorCount()!=0||source.count()!=transfer.stack()||!AlchemyFlowMenus.same(source.identity(),transfer.item()))throw new IllegalStateException("Ingredient source/cursor changed");
        request(v,transfer.source(),0,ContainerInput.PICKUP);transferPicked=true;phase("TRANSFER_PLACE");
    }
    private void transferPlace(AlchemyFlowMenus.View v){
        if(now()<nextAction)return;
        if(v.cursorCount()!=transfer.stack()||!AlchemyFlowMenus.same(v.cursor(),transfer.item())||v.slot(transfer.target()).count()!=0)throw new IllegalStateException("Owned resource pickup not observed or placement changed");
        request(v,transfer.target(),1,ContainerInput.PICKUP);phase("TRANSFER_RETURN");
    }
    private void transferReturn(AlchemyFlowMenus.View v){
        if(now()<nextAction)return;
        int remain=transfer.stack()-1;
        if(remain>0){if(v.cursorCount()!=remain||!AlchemyFlowMenus.same(v.cursor(),transfer.item())||v.slot(transfer.source()).count()!=0)throw new IllegalStateException("Owned remaining stack/source changed");request(v,transfer.source(),0,ContainerInput.PICKUP);}
        else if(v.cursorCount()!=0)throw new IllegalStateException("Placed resource remains on cursor");
        check=new Check("PLACE",transfer.item(),transfer.target(),transfer.before(),1,transfer.beforeFuel(),transfer.after());transfer=null;transferPicked=false;
        phase("VERIFY_TRANSFER");
    }
    private void verify(AlchemyFlowMenus.View v){
        if(v.cursorCount()!=0)throw new IllegalStateException("Foreign verification cursor");
        if(check.kind().equals("BUY")){if(io.count(check.item())!=check.before()+check.count())throw new IllegalStateException("Native same-menu receipt disagrees with acquisition");phase(check.after());check=null;return;}
        if(check.kind().equals("COLLECT")){if(v.slot(check.slot()).count()!=0||io.count(check.item())!=check.before()+check.count())throw new IllegalStateException("Native same-menu result collection differs");recordCollectedPotion();placed.remove(check.slot());phase(check.after());check=null;return;}
        if(io.count(check.item())!=check.before()-1)throw new IllegalStateException("Native same-menu inventory does not acknowledge placement");
        var slot=v.slot(check.slot());
        boolean fuel=check.slot()==fuelSlot()&&slot.count()==0&&v.fuel()>check.oldFuel();
        boolean begun=check.slot()==ingredientSlot()&&slot.count()==0&&v.brew()==0&&v.fuel()<check.oldFuel();
        if(!fuel&&!begun&&(slot.count()!=1||!AlchemyFlowMenus.same(slot.identity(),check.item())))throw new IllegalStateException("Native same-menu stand does not acknowledge resource");
        if(!fuel&&!begun)placed.put(check.slot(),check.item());
        if(fuel)visited.add(stand());
        value=Math.addExact(value,check.item().value());if(value>profile.valueBudget())throw new IllegalStateException("Committed resource value cap exceeded");
        phase(check.after());check=null;
    }
    private void brew(AlchemyFlowMenus.View v){
        if(v.brew()>0){stageStarted=true;return;}
        if(v.slot(ingredientSlot()).count()!=0)return;
        if(v.fuel()>=fuelBefore||io.count(profile.steps().get(step).reagent().item())!=stepInventoryBefore-1)throw new IllegalStateException("Brew has no native fuel/ingredient consumption");
        var expected=profile.steps().get(step).output();
        for(int id:bottleSlots())if(v.slot(id).count()!=1||!AlchemyFlowMenus.same(v.slot(id).identity(),expected))throw new IllegalStateException("Native stand produced an unexpected potion outcome");
        for(int id:bottleSlots())placed.put(id,expected);placed.remove(ingredientSlot());step++;phase("REAGENT");
    }
    private void recordCollectedPotion(){
        if(profile.terminalSale()==null||collectionBefore==null)return;
        var after=salePort.read(profile.terminalSale()).sale();
        collectedPotions.add(AlchemySaleParent.collected(collectionBefore,after));
        collectionBefore=null;
    }
    private void collect(AlchemyFlowMenus.View view){
        var result=profile.steps().getLast().output();
        for(int id:bottleSlots())if(placed.containsKey(id)){
            var slot=view.slot(id);
            if(slot.count()!=1||!AlchemyFlowMenus.same(slot.identity(),result))
                throw new IllegalStateException("Final native result changed before collection");
            if(now()<nextAction)return;
            if(profile.terminalSale()!=null)collectionBefore=salePort.read(profile.terminalSale()).sale();
            check=new Check("COLLECT",result,id,io.count(result),1,-1,"COLLECT");
            request(view,id,0,ContainerInput.QUICK_MOVE);
            phase("VERIFY_TRANSFER");
            return;
        }
        closeOwned();
        if(profile.terminalSale()!=null){
            saleParent.start(profile.terminalSale(),List.copyOf(collectedPotions),tick);
            phase("TERMINAL_SALE");
            return;
        }
        finishBatch();
    }
    private void tickTerminalSale(){
        saleParent.tick(tick);
        if(saleParent.state()==AlchemySaleParent.State.COMPLETE){
            soldPotions+=saleParent.acknowledgedCount();
            collectedPotions.clear();
            finishBatch();
        }else if(!saleParent.active())stop("Alchemy terminal sale: "+saleParent.reason());
    }
    private void finishBatch(){
        batches++;
        if(batches>=profile.batches()){
            phase="COMPLETE";
            reason=profile.terminalSale()==null
                    ?"Collection-only: exact native brew batches collected; terminal sale bindings absent"
                    :"Exact native brew batches collected and potion FIFO sales acknowledged";
            setEnabled(false);
        }else open("STAND","INSPECT");
    }
    private void stop(String why){reason=why;phase="FAILED";setEnabled(false);}
    @Override public void onDisable(){
        release();saleParent.cancel();
        if(profile==null)return;
        terminal=phase.equals("COMPLETE")?"COMPLETE":phase.equals("FAILED")?"FAILED":"CANCELLED";failureReason=terminal.equals("CANCELLED")?"Stopped by player or guard":reason;
        pendingIngress=opened&&ownedMenu==null&&phase.startsWith("OPEN_");pendingIngressStand=phase.equals("OPEN_STAND");
        if(transferPicked&&transfer!=null&&check==null)check=new Check(phase.equals("TRANSFER_RETURN")?"PLACE":"RETURN_CURSOR",transfer.item(),transfer.target(),transfer.before(),1,transfer.beforeFuel(),"");
        AlchemyFlowMenus.View view;try{view=io.read();}catch(RuntimeException unknown){view=null;}boolean ownedCursor=view!=null&&transfer!=null&&AlchemyFlowMenus.same(view.cursor(),transfer.item())&&view.cursorCount()>0&&view.cursorCount()<=transfer.stack();
        if(client.level==world&&client.player==player&&client.getConnection()==connection&&view!=null&&view.menu()==ownedMenu&&(view.cursorCount()==0||ownedCursor)){client.player.closeContainer();ownedMenu=null;}
        boolean foreignMenu=view!=null&&ownedMenu!=null&&view.menu()!=ownedMenu;
        cleaning=!foreignMenu&&!terminal.equals("COMPLETE")&&(!placed.isEmpty()||check!=null||purchase!=null||pendingIngress)&&client.level==world&&client.player==player&&client.getConnection()==connection;
        if(cleaning){due=now()+5000;opened=walking=false;if(!pendingIngress)previousMenu=client.player.containerMenu;phase="CLEANUP";}else{phase=terminal;reason=failureReason;}
        transfer=null;io.resetReceipt();approachGate.reset();
    }
    @Override public boolean hasPendingCleanup(){return cleaning;}
    @Override public void maintenanceTick(){if(!cleaning)return;try{
        release();
        if(client.level!=world||client.player!=player||client.getConnection()!=connection||client.player==null||!client.player.isAlive()||now()>due){cleanupEnd("Known resources remain in stand for manual inspection");return;}
        block(stand(),"minecraft:brewing_stand");
        if(pendingIngress){
            var incoming=io.read();String title=pendingIngressStand?profile.standTitle():profile.shopTitle();
            if(incoming==null||incoming.menu()==previousMenu||!incoming.title().equals(title)||pendingIngressStand&&!io.stand(incoming)||incoming.cursorCount()!=0)return;
            ownedMenu=incoming.menu();closeOwned();pendingIngress=false;previousMenu=client.player.containerMenu;
            if(placed.isEmpty()&&check==null&&purchase==null){cleanupEnd("Issued menu ingress drained");return;}
        }
        if(purchase!=null){
            int count=io.count(purchase.resource.item());
            if(purchase.receipt&&count==purchase.before+purchase.resource.offer().amount()&&io.emptyCursor()){
                closeOwned();purchase=null;check=null;if(placed.isEmpty()){cleanupEnd("Issued acquisition acknowledged; purchased stock retained");return;}
            }else return;
        }
        var view=io.read();
        if(view==null){
            if(client.gui.screen()!=null||!io.emptyCursor())return;
            if(opened)return;
            if(client.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(mc(stand())))>3.4){cleanupEnd("Stopped outside restitution reach");return;}
            if(motionCleanup.beforePublication(1)){services.rotation().lookAt(new dev.veltraph.client.world.Vec3(stand().x()+.5,stand().y()+.5,stand().z()+.5));motionCleanup.remember();}
            if(client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(mc(stand()))&&client.player.getDeltaMovement().length()<.1){client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,hit);opened=true;}return;
        }
        if(!io.stand(view)||!view.title().equals(profile.standTitle())||view.menu()==previousMenu||view.cursorCount()!=0)return;
        if(ownedMenu!=view.menu()){ownedMenu=view.menu();openedTick=tick++;return;}if(view.revision()<1||tick++-openedTick<2)return;
        if(io.awaitingReceipt(view))return;
        if(check!=null){
            if(check.kind().equals("RETURN_CURSOR")){
                if(io.count(check.item())!=check.before()){cleanupEnd("Returned cursor inventory differs");return;}
            }else if(check.kind().equals("PLACE")){
                var slot=view.slot(check.slot());
                if(io.count(check.item())!=check.before()-1){cleanupEnd("Unconfirmed placement preserved for inspection");return;}
                if(check.slot()==fuelSlot()&&slot.count()==0&&view.fuel()>check.oldFuel()){}
                else if(slot.count()==1&&AlchemyFlowMenus.same(slot.identity(),check.item()))placed.put(check.slot(),check.item());
                else{cleanupEnd("Changed/consumed placement retained for inspection");return;}
            }else if(check.kind().equals("BUY")){
                if(io.count(check.item())!=check.before()+check.count()){cleanupEnd("Resource acquisition verification differs");return;}
            }else{
                if(view.slot(check.slot()).count()!=0||io.count(check.item())!=check.before()+check.count()){cleanupEnd("Restitution acknowledgment differs");return;}placed.remove(check.slot());
            }
            check=null;
        }
        if(placed.isEmpty()){closeOwned();cleanupEnd("Owned unchanged resources returned");return;}
        var first=placed.entrySet().iterator().next();var slot=view.slot(first.getKey());
        if(slot.count()!=1||!AlchemyFlowMenus.same(slot.identity(),first.getValue())){cleanupEnd("Changed/consumed resource remains for manual inspection");return;}
        check=new Check("COLLECT",first.getValue(),first.getKey(),io.count(first.getValue()),1,-1,"");request(view,first.getKey(),0,ContainerInput.QUICK_MOVE);
        // Native receipt drains this restitution click in the same owned stand window.
        // A missing receipt ends at the existing cleanup deadline; never repeat the move.
    }catch(RuntimeException error){cleanupEnd("Restitution guard: "+error.getMessage());}}
    private void cleanupEnd(String why){cleaning=false;pendingIngress=false;purchase=null;check=null;transfer=null;phase=terminal;reason=failureReason+"; "+why;}
    public int completedBatches(){return batches;}
    public int soldPotions(){return soldPotions;}
    public AlchemySaleParent.State saleState(){return saleParent.state();}
    public int recipeStep(){return step;}
    public long reservedCoins(){return coins;}
    public long committedValue(){return value;}
    @Override public String diagnostics(){return super.diagnostics()+"; batches="+batches+" coins="+coins+" committed="+value+" sold="+soldPotions;}
}
