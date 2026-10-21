package dev.veltraph.client.garden.visitor.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.garden.visitor.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McServerCommandService;
import dev.veltraph.client.service.mc.McHeldKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.input.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import java.util.*;

/** One recoverable visitor transaction: approach, quote, optional sacks/buy, return, guarded serve. */
public final class McVisitorServeModule extends AbstractModule {
    public static final String ID="visitor_serve_ready";
    public enum Outcome { IDLE, RUNNING, COMPLETE, SKIPPED, FAILED, CANCELLED }
    private enum Phase { OPEN, QUOTE, SACKS, BUY, RETURN, SERVE }
    private static final String ACCEPT="Accept Offer",REFUSE="Refuse Offer";
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final VisitorServeRuntime runtime;private final McMenuAccess menus;private final McNavigator navigator;private final McSharedMovementNavigator travel;
    private final VisitorBazaarFlow bazaar=new VisitorBazaarFlow();
    private ClientLevel world;private LocalPlayer owner;private Entity npc;
    private String title,status="Idle";private Map<String,ItemStack> templates=Map.of();
    private Candidate original;private Phase phase;private int index,ownedMenu=-1,sackBefore,sackQuantity;
    private long deadline,spent,nextActionAt;private boolean walking,interacted,sacksRejected;private final Map<String,Long> prices=new HashMap<>();
    private Boolean pendingBazaar;
    private Entity targetOverride;private long spendingLimit=Long.MAX_VALUE;private Outcome outcome=Outcome.IDLE;
    private Object connection;
    private final dev.veltraph.client.garden.visitor.history.VisitorHistoryResolver history;
    private final dev.veltraph.client.garden.visitor.history.VisitorHistoryPresentationState historyPresentation=
            new dev.veltraph.client.garden.visitor.history.VisitorHistoryPresentationState();
    private record Candidate(VisitorServeRuntime.Snapshot snapshot,Map<String,ItemStack> templates){}
    public McVisitorServeModule(Minecraft client,FarmServices services,ConfigService config){
        this(client,services,config,dev.veltraph.client.garden.visitor.history.VisitorHistoryResolver.UNKNOWN);
    }
    public McVisitorServeModule(Minecraft client,FarmServices services,ConfigService config,
            dev.veltraph.client.garden.visitor.history.VisitorHistoryResolver history){
        super(ID,"Garden visitor transaction",ModuleCategory.INTERMEDIARY);
        this.history=Objects.requireNonNull(history);
        this.client=client;this.services=services;this.config=config;menus=new McMenuAccess(client);navigator=new McNavigator(client,services.input(),config);travel=new McSharedMovementNavigator(client,services,config);
        runtime=new VisitorServeRuntime(expected->{
            var current=read();
            if(current==null||client.gameMode==null||!current.snapshot().equals(expected)||original==null||!sameTemplates(current.templates(),original.templates())||!client.player.containerMenu.getCarried().isEmpty())return false;
            client.gameMode.handleContainerInput(expected.menuId(),expected.acceptSlot(),0,ContainerInput.PICKUP,client.player);return true;
        });
    }
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return isEnabled()&&walking&&now()<deadline&&(phase==Phase.OPEN||phase==Phase.RETURN)&&travel.consumeExpectedTeleport(position,now);}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    @Override public boolean allowsScreen(String current){
        String clean=LocationText.stripColorCodes(current).trim();
        if(title!=null&&title.equals(clean))return true;
        if(phase==Phase.QUOTE||phase==Phase.BUY){
            if(client.gui.screen() instanceof AbstractSignEditScreen)return bazaar.state()==VisitorBazaarFlow.State.SIGN||bazaar.state()==VisitorBazaarFlow.State.CONFIRM;
            if(original!=null&&index<original.snapshot().offer().requiredItems().size()){
                String item=original.snapshot().offer().requiredItems().get(index).name();
                return clean.toLowerCase(Locale.ROOT).contains("bazaar")||clean.equalsIgnoreCase(item)||clean.toLowerCase(Locale.ROOT).contains("instant buy")||clean.equals("How many do you want?");
            }
        }
        return false;
    }
    private VisitorServeRuntime.Settings settings(){return new VisitorServeRuntime.Settings(config.get(VisitorConfig.VISITOR_AUTO_SERVE),config.get(VisitorConfig.VISITOR_MAX_SERVE_QUANTITY),config.get(VisitorConfig.VISITOR_MAX_COST),VisitorSettings.fromConfig(config),8000);}
    /** Coordinator target is transient, never a persistent config mutation or label-only retarget. */
    public void target(Entity entity,long maximumSpending){if(isEnabled())throw new IllegalStateException("Stop visitor transaction before changing its target");
        if(entity==null||maximumSpending<0)throw new IllegalArgumentException("Invalid visitor target/spending allowance");targetOverride=entity;spendingLimit=maximumSpending;}
    public Outcome outcome(){return outcome;}
    public long coinsSpent(){return spent;}
    @Override public Optional<String> preStartCheck(){
        historyPresentation.clear();
        if(client.player==null||!client.player.isAlive()||client.level==null)return Optional.of("Visitor player/world unavailable");
        if(!config.get(VisitorConfig.VISITOR_AUTO_SERVE)||!config.get(VisitorConfig.VISITOR_HAVE_MAX_PRICES)||config.get(VisitorConfig.VISITOR_MAX_COST)<1||config.get(VisitorConfig.VISITOR_MAX_SERVE_QUANTITY)<1)return Optional.of("Enable Auto Serve and positive quantity/cost guards with Max Prices");
        var candidate=read();if(candidate==null||candidate.snapshot().offer()==null)return selectedNpc()==null?Optional.of("Open a visitor offer or configure one exact loaded visitor NPC name"):Optional.empty();
        var structural=structural(candidate);if(structural.isPresent())return structural;
        if(candidate.snapshot().offer().allItemPricesKnown()){
            var counts=inventory(candidate);boolean ready=candidate.snapshot().needs().stream().allMatch(n->counts.getOrDefault(n.key(),0)>=n.quantity());
            if(ready)return VisitorServeRuntime.validate(candidate.snapshot(),counts,settings());
            if(!config.get(VisitorConfig.VISITOR_BUY_MISSING)&&!config.get(VisitorConfig.VISITOR_TRY_SACKS_FIRST))return Optional.of("Missing items: enable Buy Missing or Try Sacks First");
        }
        return findNpc(candidate.snapshot().title())==null?Optional.of("Unpriced offer requires the same loaded visitor NPC for return after Bazaar quotes"):Optional.empty();
    }
    private Optional<String> structural(Candidate c){
        long quantity=0;try{for(var need:c.snapshot().needs())quantity=Math.addExact(quantity,need.quantity());}catch(ArithmeticException invalid){return Optional.of("Visitor quantity overflow");}
        if(quantity<1||quantity>config.get(VisitorConfig.VISITOR_MAX_SERVE_QUANTITY)||!c.snapshot().cursorEmpty())return Optional.of("Visitor quantity cap or cursor guard failed");
        // Test the non-price filters before opening Bazaar; PROFITABLE may need quotes.
        var decision=VisitorPolicy.decide(c.snapshot().offer(),VisitorSettings.fromConfig(config));
        return decision.outcome()==VisitorDecision.Outcome.SKIP?Optional.of(decision.reason()):Optional.empty();
    }
    @Override public void onEnable(){
        historyPresentation.clear();release();world=client.level;owner=client.player;connection=client.getConnection();outcome=Outcome.RUNNING;prices.clear();spent=0;nextActionAt=now();pendingBazaar=null;ownedMenu=-1;original=null;templates=Map.of();
        if(!config.get(VisitorConfig.VISITOR_AUTO_SERVE)||!config.get(VisitorConfig.VISITOR_HAVE_MAX_PRICES)||config.get(VisitorConfig.VISITOR_MAX_COST)<1||config.get(VisitorConfig.VISITOR_MAX_SERVE_QUANTITY)<1){fail("Auto Serve and positive quantity/cost guards with Max Prices are required");return;}
        var candidate=read();
        if(candidate==null||candidate.snapshot().offer()==null){npc=selectedNpc();title=npc==null?config.get(VisitorConfig.VISITOR_NPC_NAME):LocationText.stripColorCodes(npc.getName().getString()).trim();phase=Phase.OPEN;beginApproach();return;}
        if(targetOverride!=null&&!candidate.snapshot().title().equals(LocationText.stripColorCodes(targetOverride.getName().getString()).trim())){fail("Open visitor does not match coordinator target");return;}
        begin(candidate);
    }
    private void begin(Candidate candidate){
        original=candidate;title=candidate.snapshot().title();templates=candidate.templates();ownedMenu=candidate.snapshot().menuId();npc=targetOverride==null?findNpc(title):selectedNpc();
        var invalid=structural(candidate);if(invalid.isPresent()){
            if(VisitorPolicy.decide(candidate.snapshot().offer(),VisitorSettings.fromConfig(config)).outcome()==VisitorDecision.Outcome.SKIP){status="Skipped: "+invalid.get();outcome=Outcome.SKIPPED;setEnabled(false);}
            else fail(invalid.get());return;
        }
        if(candidate.snapshot().offer().allItemPricesKnown()){
            var counts=inventory(candidate);
            boolean ready=candidate.snapshot().needs().stream().allMatch(n->counts.getOrDefault(n.key(),0)>=n.quantity());
            if(ready){phase=Phase.SERVE;if(!runtime.start(candidate.snapshot(),counts,settings(),now()))fail(runtime.status());return;}
            if(npc==null||!VisitorPolicy.decide(candidate.snapshot().offer(),VisitorSettings.fromConfig(config)).isAccept()
                    ||candidate.snapshot().offer().totalItemCost()>config.get(VisitorConfig.VISITOR_MAX_COST)){fail("Missing items require same loaded NPC and an affordable accepted offer");return;}
            for(var item:candidate.snapshot().offer().requiredItems())prices.put(item.name(),item.unitPrice());
            closeOwned();index=0;nextBuy();return;
        }
        if(npc==null){fail("Visitor NPC not loaded; cannot safely return after quotes");return;}
        phase=Phase.QUOTE;index=0;closeOwned();startBazaar(false);
    }
    private long now(){return services.clock().nowMillis();}
    private Entity selectedNpc(){
        if(targetOverride==null)return findNpc(config.get(VisitorConfig.VISITOR_NPC_NAME));
        return client.level!=null&&client.player!=null&&targetOverride.isAlive()&&client.level.getEntity(targetOverride.getId())==targetOverride
                &&targetOverride.distanceToSqr(client.player)<64*64?targetOverride:null;
    }
    private void beginApproach(){walking=false;interacted=false;deadline=now()+30000;}
    private Entity findNpc(String name){
        if(client.level==null||client.player==null||name==null||name.isBlank())return null;Entity result=null;
        for(var e:client.level.entitiesForRendering())if(e!=client.player&&e instanceof LivingEntity&&!(e instanceof net.minecraft.world.entity.decoration.ArmorStand a&&a.isMarker())&&e.isAlive()
                &&LocationText.stripColorCodes(e.getName().getString()).trim().equalsIgnoreCase(name)&&e.distanceToSqr(client.player)<64*64){if(result!=null)return null;result=e;}
        return result;
    }
    private void approach(){
        var offer=read();if(offer!=null&&offer.snapshot().offer()!=null&&offer.snapshot().title().equals(title)){
            travel.stop();if(phase==Phase.OPEN){begin(offer);return;}
            if(!sameRequirements(original.snapshot().offer(),offer.snapshot().offer())||!sameTemplates(original.templates(),offer.templates())){fail("Visitor requirements changed while away");return;}
            phase=Phase.SERVE;ownedMenu=offer.snapshot().menuId();original=offer;
            if(!runtime.start(offer.snapshot(),inventory(offer),settings(),now()))fail(runtime.status());return;
        }
        if(client.gui.screen()!=null){fail("Unexpected screen while approaching visitor");return;}
        if(npc==null||!npc.isAlive()||client.level.getEntity(npc.getId())!=npc||now()>=deadline){fail("Visitor NPC unavailable or approach timed out");return;}
        if(interacted)return;
        if(client.player.distanceTo(npc)>2.8||!client.player.hasLineOfSight(npc)){
            if(!walking){var p=npc.blockPosition();boolean found=false;for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
                if(travel.start(navigator.position(new dev.veltraph.client.world.BlockPos(p.getX()+offset[0],p.getY(),p.getZ()+offset[1])),.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL)){found=true;break;}
                if(!found){fail("No loaded safe barn path to visitor");return;}walking=true;}
            var state=travel.tick();if(state==McTravelNavigator.State.FAILED){fail("Visitor approach blocked");return;}if(state==McTravelNavigator.State.ARRIVED)walking=false;return;
        }
        travel.stop();var delta=npc.getBoundingBox().getCenter().subtract(client.player.getEyePosition());
        services.rotation().track((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        if(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity()==npc&&client.gameMode!=null){
            client.gameMode.interact(client.player,npc,hit,InteractionHand.MAIN_HAND);interacted=true;status="Waiting for visitor menu after NPC interaction";
        }
    }
    private static boolean sameRequirements(VisitorOffer a,VisitorOffer b){return a.visitorName().equals(b.visitorName())&&a.rarity()==b.rarity()&&a.isNew()==b.isNew()
            &&a.requiredItems().stream().map(i->i.name()+"|"+i.amount()).toList().equals(b.requiredItems().stream().map(i->i.name()+"|"+i.amount()).toList());}
    private static boolean sameTemplates(Map<String,ItemStack> a,Map<String,ItemStack> b){
        return a.keySet().equals(b.keySet())&&a.entrySet().stream().allMatch(e->ItemStack.isSameItemSameComponents(identity(e.getValue()),identity(b.get(e.getKey()))));
    }
    private void startBazaar(boolean buy){
        if(now()<nextActionAt){pendingBazaar=buy;return;}pendingBazaar=null;
        var item=original.snapshot().offer().requiredItems().get(index);var need=original.snapshot().needs().get(index);int have=inventory(original).getOrDefault(need.key(),0);
        int quantity=buy?Math.max(0,item.amount()-have):item.amount();
        if(buy&&quantity==0){nextBuy();return;}
        if(buy&&!config.get(VisitorConfig.VISITOR_BUY_MISSING)){fail("Missing items; enable Buy Missing or provide required items");return;}
        long cap=config.get(VisitorConfig.VISITOR_MAX_COST)-spent;
        if(buy)cap=Math.min(cap,spendingLimit-spent);
        if(buy){try{cap=Math.min(cap,Math.multiplyExact(prices.get(item.name()),quantity));}catch(RuntimeException invalid){fail("Missing/overflowed quote");return;}}
        bazaar.start(item.name(),quantity,have,buy,cap,now(),15000);
        if(!bazaar.active()||!new McServerCommandService(client).sendCommand("bz "+item.name())){fail("Bazaar command unavailable or request invalid");return;}
        ownedMenu=-1;status=bazaar.status();
        nextActionAt=now()+250;
    }
    private void nextBuy(){
        while(index<original.snapshot().needs().size()&&inventory(original).getOrDefault(original.snapshot().needs().get(index).key(),0)>=original.snapshot().needs().get(index).quantity())index++;
        if(index==original.snapshot().needs().size()){phase=Phase.RETURN;beginApproach();return;}
        if(config.get(VisitorConfig.VISITOR_TRY_SACKS_FIRST)){
            var item=original.snapshot().offer().requiredItems().get(index);var need=original.snapshot().needs().get(index);
            sackBefore=inventory(original).getOrDefault(need.key(),0);sackQuantity=item.amount()-sackBefore;
            if(!new McServerCommandService(client).sendCommand("gfs "+item.name()+" "+sackQuantity)){fail("Sacks command unavailable");return;}
            phase=Phase.SACKS;sacksRejected=false;deadline=now()+2500;status="Waiting for exact sacks inventory increase";
        }else{phase=Phase.BUY;startBazaar(true);}
    }
    @Override public void onTick(){
        boolean valid=client.level==world&&world!=null&&client.player==owner&&owner!=null&&owner.isAlive()&&client.getConnection()==connection;
        if(targetOverride!=null&&(phase!=Phase.SERVE||runtime.state()==VisitorServeRuntime.State.READY)&&selectedNpc()==null)valid=false;
        if(!valid||!config.get(VisitorConfig.VISITOR_AUTO_SERVE)||!config.get(VisitorConfig.VISITOR_HAVE_MAX_PRICES)){runtime.tick(false,null,Map.of(),settings(),now());fail("Visitor player/world or opt-in changed");return;}
        if(original!=null&&phase!=Phase.SERVE){var changed=structural(original);if(changed.isPresent()){fail("Visitor policy changed: "+changed.get());return;}}
        if(phase==Phase.BUY&&bazaar.state()!=VisitorBazaarFlow.State.ACK&&!config.get(VisitorConfig.VISITOR_BUY_MISSING)){fail("Buy Missing opt-in changed before purchase");return;}
        if(phase==Phase.OPEN||phase==Phase.RETURN){approach();return;}
        if(phase==Phase.SERVE){var current=read();runtime.tick(true,current==null?null:current.snapshot(),inventory(original),settings(),now());if(!runtime.active()){
            outcome=switch(runtime.state()){case COMPLETE->Outcome.COMPLETE;case CANCELLED->Outcome.CANCELLED;default->Outcome.FAILED;};setEnabled(false);
        }return;}
        if(phase==Phase.SACKS){var need=original.snapshot().needs().get(index);int have=inventory(original).getOrDefault(need.key(),0);
            if(have==sackBefore+sackQuantity){index++;nextBuy();}else if(have!=sackBefore){fail("Sacks inventory increase differs from requested quantity");}
            else if(sacksRejected){phase=Phase.BUY;startBazaar(true);}else if(now()>=deadline){fail("Sacks withdrawal not acknowledged; stopped before spending to avoid duplicate acquisition");}return;}
        if(pendingBazaar!=null){if(now()>=nextActionAt)startBazaar(pendingBazaar);return;}
        if(now()<nextActionAt)return;
        var menu=menus.read();if(menu!=null){ownedMenu=menu.id();if(!client.player.containerMenu.getCarried().isEmpty()){fail("Cursor became occupied during Bazaar flow");return;}}
        if(client.gui.screen()!=null&&menu==null&&!(client.gui.screen() instanceof AbstractSignEditScreen)){fail("Unexpected Bazaar screen");return;}
        bazaar.capAtMost(Math.min(config.get(VisitorConfig.VISITOR_MAX_COST)-spent,phase==Phase.BUY?spendingLimit-spent:Long.MAX_VALUE));
        var need=original.snapshot().needs().get(index);var action=bazaar.tick(menu,client.gui.screen() instanceof AbstractSignEditScreen,inventory(original).getOrDefault(need.key(),0),now());
        switch(action.kind()){
            case CLICK -> {var actual=menus.read();if(!Objects.equals(actual,action.menu())||!menus.click(actual.id(),action.slot().id(),action.slot().name())){fail("Bazaar menu changed before click");return;}}
            case SIGN -> {if(!submitAmount(action.text())){fail("Bazaar amount sign unavailable");return;}}
            case CLOSE -> closeOwned();
            case NONE -> {}
        }
        if(action.kind()!=VisitorBazaarFlow.Kind.NONE)nextActionAt=now()+250;
        status=bazaar.status();if(bazaar.state()==VisitorBazaarFlow.State.FAILED){fail(status);return;}
        if(bazaar.state()==VisitorBazaarFlow.State.COMPLETE){
            if(phase==Phase.QUOTE){prices.put(original.snapshot().offer().requiredItems().get(index).name(),bazaar.quote().unitCeiling());index++;
                if(index<original.snapshot().needs().size()){startBazaar(false);return;}
                Candidate priced=withPrices(original);var decision=VisitorPolicy.decide(priced.snapshot().offer(),VisitorSettings.fromConfig(config));
                if(!decision.isAccept()||priced.snapshot().offer().totalItemCost()>config.get(VisitorConfig.VISITOR_MAX_COST)){fail("Quoted offer rejected: "+decision.reason());return;}
                original=priced;index=0;nextBuy();
            }else{spent=Math.addExact(spent,bazaar.actualCost());index++;nextBuy();}
        }
    }
    private boolean submitAmount(String text){
        if(!(client.gui.screen() instanceof AbstractSignEditScreen sign)||!text.matches("[0-9]{1,10}"))return false;
        // 26.3 uses SDL shortcut keycodes in KeyEvent's second field, not the old GLFW key numbers.
        if(!sign.keyPressed(new KeyEvent(0,1073741898,0))||!sign.keyPressed(new KeyEvent(0,1073741901,1))
                ||!sign.keyPressed(new KeyEvent(0,8,0)))return false;
        for(int cp:text.codePoints().toArray())if(!sign.charTyped(new CharacterEvent(cp)))return false;
        sign.onClose();return true;
    }
    private Candidate withPrices(Candidate c){
        var items=new ArrayList<VisitorItem>();var needs=new ArrayList<VisitorServeRuntime.Need>();
        for(int i=0;i<c.snapshot().needs().size();i++){var n=c.snapshot().needs().get(i);long p=prices.getOrDefault(c.snapshot().offer().requiredItems().get(i).name(),n.unitPrice());items.add(c.snapshot().offer().requiredItems().get(i).withUnitPrice(p));needs.add(new VisitorServeRuntime.Need(n.key(),n.quantity(),p));}
        var o=c.snapshot().offer();var offer=new VisitorOffer(o.visitorName(),o.rarity(),o.isNew(),items,o.estimatedReward());var s=c.snapshot();
        return new Candidate(new VisitorServeRuntime.Snapshot(s.menu(),s.menuId(),s.revision(),s.title(),s.acceptSlot(),offer,needs,s.cursorEmpty()),c.templates());
    }
    @Override public void onServerMessage(String line){
        if(!isEnabled()||line==null)return;bazaar.message(line);
        String lower=LocationText.stripColorCodes(line).toLowerCase(Locale.ROOT);
        if(phase==Phase.SACKS&&lower.contains("sack")&&(lower.contains("not enough")||lower.contains("don't have")||lower.contains("couldn't find")))sacksRejected=true;
        if(lower.contains("you don't have enough of the required item")||lower.contains("please slow down")||lower.contains("not enough coins")||lower.contains("bazaar is currently disabled")){
            runtime.reject("Server rejected visitor request");fail("Server rejected visitor transaction");}
    }
    private dev.veltraph.client.garden.visitor.history.VisitorHistoryStore.Newness observeHistory(String name){
        return historyPresentation.update(history.observe(name)).newness();
    }
    private void fail(String why){status=why;outcome=Outcome.FAILED;runtime.reject(why);setEnabled(false);}
    @Override public void onDisable(){
        historyPresentation.clear();try{runtime.cancel();}finally{
        if(outcome==Outcome.RUNNING)outcome=Outcome.CANCELLED;
        try{if(client.player==owner&&client.level==world)closeOwned();}finally{travel.stop();release();world=null;owner=null;connection=null;npc=null;targetOverride=null;spendingLimit=Long.MAX_VALUE;title=null;templates=Map.of();original=null;ownedMenu=-1;pendingBazaar=null;prices.clear();}}}
    private void closeOwned(){var current=menus.read();if(current!=null&&current.id()==ownedMenu&&allowsScreen(current.title())&&client.player.containerMenu.getCarried().isEmpty())menus.close(ownedMenu);ownedMenu=-1;}
    private void release(){try{services.rotation().cancelRotation();}finally{try{services.input().releaseAllMovement();services.breaking().setBreaking(false);}finally{
        if(client.options!=null){McHeldKeys.setHeld(client.options.keyAttack,false);McHeldKeys.setHeld(client.options.keyUse,false);McHeldKeys.setHeld(client.options.keyUp,false);McHeldKeys.setHeld(client.options.keyDown,false);McHeldKeys.setHeld(client.options.keyLeft,false);McHeldKeys.setHeld(client.options.keyRight,false);McHeldKeys.setHeld(client.options.keyJump,false);McHeldKeys.setHeld(client.options.keyShift,false);}}}}
    public VisitorServeRuntime.State serveState(){return runtime.state();}
    @Override public String diagnostics(){return (phase==Phase.SERVE?runtime.status():status+" | spent "+spent+" coins")
            +" | history="+historyPresentation.current().newness()+" | "+historyPresentation.current().provenance();}
    private static String name(ItemStack stack){return LocationText.stripColorCodes(stack.getHoverName().getString()).trim();}
    private static List<String> lore(ItemStack stack){var lore=stack.get(DataComponents.LORE);return lore==null?List.of():lore.lines().stream().map(c->LocationText.stripColorCodes(c.getString()).trim()).toList();}
    private static ItemStack identity(ItemStack stack){var copy=stack.copy();copy.remove(DataComponents.LORE);copy.remove(DataComponents.CUSTOM_NAME);return copy;}
    private Candidate read(){
        historyPresentation.clear();
        if(client.player==null||!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)||screen.getMenu()!=client.player.containerMenu)return null;
        var menu=screen.getMenu();int accept=-1;List<String> acceptLore=List.of();var rows=new LinkedHashMap<String,ItemStack>();
        for(var slot:menu.slots){if(slot.container==client.player.getInventory())continue;var stack=slot.getItem();if(stack.isEmpty()||stack.is(Items.BARRIER))continue;String label=name(stack);
            if(label.equals(ACCEPT)){if(accept>=0)return null;accept=slot.index;acceptLore=lore(stack);continue;}
            if(label.equals(REFUSE)||label.isBlank()||label.equals("?")||label.equals("-"))continue;
            if(rows.putIfAbsent(label,stack.copy())!=null)return null;
        }
        String menuTitle=LocationText.stripColorCodes(screen.getTitle().getString()).trim();
        if(accept<0)return new Candidate(new VisitorServeRuntime.Snapshot(menu,menu.containerId,menu.getStateId(),menuTitle,-1,null,List.of(),menu.getCarried().isEmpty()),Map.of());
        var parsed=VisitorRequirements.read(acceptLore);if(VisitorRequirements.hasSection(acceptLore)&&parsed.isEmpty())return null;
        var required=parsed.orElseGet(()->rows.entrySet().stream().filter(e->VisitorUnitQuote.read(lore(e.getValue())).isPresent()).map(e->new VisitorItem(e.getKey(),e.getValue().getCount(),VisitorUnitQuote.read(lore(e.getValue())).orElse(-1))).toList());
        if(required.isEmpty())return null;var items=new ArrayList<VisitorItem>();var needs=new ArrayList<VisitorServeRuntime.Need>();var kinds=new LinkedHashMap<String,ItemStack>();
        for(var item:required){var row=rows.get(item.name());String key=row==null?"name|"+item.name():BuiltInRegistries.ITEM.getKey(row.getItem())+"|"+item.name();
            long price=prices.getOrDefault(item.name(),row==null?-1:VisitorUnitQuote.read(lore(row)).orElse(-1));items.add(item.withUnitPrice(price));needs.add(new VisitorServeRuntime.Need(key,item.amount(),price));if(row!=null)kinds.put(key,row.copy());}
        var npcRow=rows.get(menuTitle);var offer=new VisitorOffer(menuTitle,McVisitorIdentity.rarity(menuTitle,acceptLore,npcRow==null?null:npcRow.getHoverName()),observeHistory(menuTitle)==dev.veltraph.client.garden.visitor.history.VisitorHistoryStore.Newness.NEW,items,VisitorLoreReader.parseCoinReward(acceptLore).orElse(-1));
        return new Candidate(new VisitorServeRuntime.Snapshot(menu,menu.containerId,menu.getStateId(),menuTitle,accept,offer,needs,menu.getCarried().isEmpty()),Map.copyOf(kinds));
    }
    private Map<String,Integer> inventory(Candidate c){
        var counts=new LinkedHashMap<String,Integer>();if(c==null||client.player==null)return counts;
        for(int n=0;n<c.snapshot().needs().size();n++){var need=c.snapshot().needs().get(n);String wanted=c.snapshot().offer().requiredItems().get(n).name();var template=c.templates().get(need.key());int total=0;
            for(int i=0;i<36;i++){var stack=client.player.getInventory().getItem(i);if(stack.isEmpty()||!name(stack).equals(wanted))continue;
                boolean same=template!=null?ItemStack.isSameItemSameComponents(identity(stack),identity(template)):skyblockId(stack).equals(wanted.toUpperCase(Locale.ROOT).replace(' ','_'));
                if(same)total=Math.addExact(total,stack.getCount());}counts.put(need.key(),total);}
        return counts;
    }
    private static String skyblockId(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return "";var tag=data.copyTag();
        return tag.getString("id").orElseGet(()->tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id",""));}
}
