package dev.veltraph.client.skills;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.inventory.selling.*;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.*;
import java.util.*;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Bounded acquisition and craft batches continue only after exact Bazaar/cookie output custody receipts. */
public final class CarpentryFlowModule extends AbstractModule {
    public static final String ID="carpentry_craft_batches";
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private final CarpentryFlowMenus io;private final SellingMenuAccess selling;private CarpentrySaleParent sale=new CarpentrySaleParent();private final dev.veltraph.client.gui.McMenuAccess saleMenus;private Object salePredictionMenu;private net.minecraft.world.item.ItemStack salePrediction=net.minecraft.world.item.ItemStack.EMPTY;private final McNavigator navigator;private final McSharedMovementNavigator mobility;private final dev.veltraph.client.navigation.mc.McParentMotionCleanup motionCleanup;
    private CarpentryFlowProfile profile;private String raw,phase="IDLE",reason="Idle",afterOpen;
    private Object owner,world,connection,menu,previous;private boolean opened,walking,cleaning,cleanupOpened,restoreCamera;
    private long tick,due,runDue,next,spent,value;private int openedTick,batches,crafts,generated,sold;
    private Vec3 start;private float yaw,pitch;private int selection;
    private Purchase purchase;private Craft pendingCraft;private Deposit deposit;
    private CarpentryFlowMenus.Item iconPrediction;
    private String terminal="CANCELLED";
    private final List<dev.veltraph.client.metrics.FinancialActionReceipt> financialReceipts=new ArrayList<>();
    private long financialSequence,purchaseReceiptSequence;
    private Object purchaseMenu;private int purchaseMenuId,purchaseRequestRevision,purchaseReceiptRevision;
    private String purchaseOutcomeId;
    private dev.veltraph.client.metrics.FinancialOutcomeBindings.Scope financialScope(){return new dev.veltraph.client.metrics.FinancialOutcomeBindings.Scope(owner,world,connection);}
    private void acknowledgeCost(dev.veltraph.client.metrics.FinancialActionReceipt.Kind kind,String id,long cost,Object nativeMenu,int menuId,int beforeRevision,int afterRevision,String channel){
        if(connection==null)return;
        financialReceipts.add(new dev.veltraph.client.metrics.FinancialActionReceipt(id,this,kind,kind==dev.veltraph.client.metrics.FinancialActionReceipt.Kind.PURCHASE?dev.veltraph.client.metrics.FinancialActionReceipt.Denomination.COINS:dev.veltraph.client.metrics.FinancialActionReceipt.Denomination.UNKNOWN,BigDecimal.valueOf(cost),financialScope(),List.of(new dev.veltraph.client.metrics.FinancialActionReceipt.Evidence(nativeMenu,menuId,beforeRevision,afterRevision,++financialSequence,channel))));
    }
    public List<dev.veltraph.client.metrics.FinancialActionReceipt> acknowledgedFinancialActions(){
        if(!client.isSameThread())throw new IllegalStateException("Carpentry evidence requires client thread");
        return client.player==owner&&client.level==world&&client.getConnection()==connection?List.copyOf(financialReceipts):List.of();
    }
    private record Purchase(CarpentryFlowProfile.Material material,int before,long cost,BigDecimal purse,boolean received) {}
    private record Craft(Map<String,Integer> before,int outputs,int cursor,long fee,BigDecimal purse,int revision) {}
    private record Deposit(int before,int amount) {}
    public CarpentryFlowModule(Minecraft client,ConfigService config,FarmServices services){
        super(ID,"Carpentry Craft Batches",ModuleCategory.MISC);this.client=client;this.config=config;this.services=services;
        io=new CarpentryFlowMenus(client);selling=new SellingMenuAccess(client);saleMenus=new dev.veltraph.client.gui.McMenuAccess(client);navigator=new McNavigator(client,services.input(),config);mobility=new McSharedMovementNavigator(client,services,config);motionCleanup=new dev.veltraph.client.navigation.mc.McParentMotionCleanup(client,services,()->this);
    }
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){return isEnabled()&&profile!=null&&now()<=runDue&&config.get(CarpentryFlowConfig.CARPENTRY_FLOW_ENABLED)&&raw.equals(config.get(CarpentryFlowConfig.CARPENTRY_FLOW_PROFILE))&&client.player==owner&&client.level==world&&client.getConnection()==connection&&walking&&mobility.consumeExpectedTeleport(position,now);}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public boolean allowsScreen(String title){return profile!=null&&(CarpentryFlowMenus.clean(title).equals(profile.craft().title())||profile.shop()!=null&&CarpentryFlowMenus.clean(title).equals(profile.shop().title())||saleScreen(title));}
    private boolean saleScreen(String title){if(profile==null||profile.sale()==null)return false;var s=profile.sale();var text=CarpentryFlowMenus.clean(title);var protocol=s.protocol();var f=protocol.fallback();return text.equals(s.target().title())||text.equals(protocol.confirmationTitle())||f!=null&&(text.equals(f.skyblock().title())||text.equals(f.cookieTitle()));}
    private long now(){return services.clock().nowMillis();}
    private boolean overlay(){return client.gui.screen() instanceof dev.veltraph.client.ui.VeltraphScreen||client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;}
    private void release(){motionCleanup.release(mobility);}
    private BigDecimal purse(){var observed=selling.read(profile.currencyPrefix());if(observed.coins()==null)throw new IllegalStateException("Exact currency sidebar observation unavailable");return observed.coins();}
    private void target(CarpentryFlowProfile.Target t){if(t.command().isEmpty()){
        var p=t.position();if(!client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4))throw new IllegalStateException("Craft target chunk unloaded");
        var block=BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(new net.minecraft.core.BlockPos(p.x(),p.y(),p.z())).getBlock()).toString();if(!block.equals(t.block()))throw new IllegalStateException("Configured craft/merchant block changed");
    }}
    @Override public Optional<String> preStartCheck(){try{
        if(cleaning)return Optional.of("Wait for owned crafting result restitution");
        if(!config.get(CarpentryFlowConfig.CARPENTRY_FLOW_ENABLED))return Optional.of("Enable repeated carpentry explicitly");
        if(client.player==null||client.level==null||!client.player.isAlive()||client.gui.screen()!=null&&!overlay()||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Start from gameplay/Veltraph with living player and empty cursor");
        var p=CarpentryFlowProfile.parse(config.get(CarpentryFlowConfig.CARPENTRY_FLOW_PROFILE));
        target(p.craft());if(p.shop()!=null)target(p.shop());if(p.sale()!=null)target(p.sale().target());
        var outputs=io.count(p.output());if(outputs!=0||!client.player.getInventory().getItem(p.sink()).isEmpty()||io.free()<2)return Optional.of("Reserve empty main output sink/capacity and start with no matching products");
        for(var m:p.materials()){if(!BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(m.item().id())))throw new IllegalArgumentException("Unknown material");if(!p.buyMissing()&&io.count(m.item())<m.perCraft()*p.craftsPerBatch())return Optional.of("Missing batch materials and acquisition disabled");}
        if(selling.read(p.currencyPrefix()).coins()==null)return Optional.of("Configure one exact readable currency prefix");
        int max=BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(p.output().id())).getDefaultMaxStackSize();if(p.outputPerCraft()*p.craftsPerBatch()>Math.min(99,max))return Optional.of("Batch output exceeds source/native stack bound");
        return Optional.empty();
    }catch(RuntimeException invalid){return Optional.of("Carpentry profile: "+invalid.getMessage());}}
    private void phase(String name){phase=name;due=now()+profile.timeout();}
    @Override public void onEnable(){
        var blocked=preStartCheck();if(blocked.isPresent()){phase="FAILED";reason=blocked.get();setEnabled(false);return;}
        profile=CarpentryFlowProfile.parse(config.get(CarpentryFlowConfig.CARPENTRY_FLOW_PROFILE));raw=config.get(CarpentryFlowConfig.CARPENTRY_FLOW_PROFILE);
        owner=client.player;world=client.level;connection=client.getConnection();financialReceipts.clear();start=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());yaw=client.player.getYRot();pitch=client.player.getXRot();selection=client.player.getInventory().getSelectedSlot();
        tick=spent=value=0;batches=crafts=generated=sold=0;purchase=null;pendingCraft=null;deposit=null;iconPrediction=null;cleaning=false;restoreCamera=false;next=now();runDue=now()+profile.runTimeout();sale=new CarpentrySaleParent();salePredictionMenu=null;salePrediction=net.minecraft.world.item.ItemStack.EMPTY;selling.clear();saleMenus.clear();release();if(overlay())client.gui.setScreen(null);open(profile.craft(),"STOCK");
    }
    private CarpentryFlowProfile.Target opening(){return phase.equals("OPEN_SHOP")?profile.shop():phase.equals("OPEN_SALE")?profile.sale().target():profile.craft();}
    private void open(CarpentryFlowProfile.Target target,String after){mobility.stop();motionCleanup.release(mobility);walking=opened=false;previous=client.player.containerMenu;menu=null;afterOpen=after;phase(target==profile.craft()?"OPEN_CRAFT":target==profile.shop()?"OPEN_SHOP":"OPEN_SALE");}
    private void close(){if(client.player!=null&&client.player.containerMenu==menu&&client.player.containerMenu.getCarried().isEmpty()){client.player.closeContainer();menu=null;}}
    private void reopen(CarpentryFlowProfile.Target target,String after){close();open(target,after);}
    private void ingress(){
        var t=opening();target(t);var view=io.read();if(view!=null){if(!opened||view.menu()==previous||!view.title().equals(t.title())||view.cursor().count()!=0)throw new IllegalStateException("Unexpected crafting/merchant ingress menu");if(menu!=view.menu()){menu=view.menu();openedTick=(int)tick;}if(view.revision()>0&&tick-openedTick>=2){opened=false;phase(afterOpen);}return;}
        if(client.gui.screen()!=null)throw new IllegalStateException("Unrelated ingress screen");if(opened)return;
        if(!t.command().isEmpty()){client.player.connection.sendCommand(t.command());opened=true;return;}
        var p=t.position();var center=net.minecraft.world.phys.Vec3.atCenterOf(new net.minecraft.core.BlockPos(p.x(),p.y(),p.z()));
        if(walking||client.player.getEyePosition().distanceTo(center)>3.4){if(!walking){for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})if(mobility.start(navigator.position(p.add(d[0],0,d[1])),.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL)){walking=true;break;}if(!walking)throw new IllegalStateException("No safe crafting/merchant approach");}var s=mobility.tick();if(s==McTravelNavigator.State.FAILED)throw new IllegalStateException("Craft approach blocked");if(s==McTravelNavigator.State.ARRIVED)walking=false;return;}
        mobility.stop();if(motionCleanup.beforePublication(1)){services.rotation().lookAt(new Vec3(center.x,center.y,center.z));motionCleanup.remember();}if(client.player.getDeltaMovement().length()<.1&&client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(new net.minecraft.core.BlockPos(p.x(),p.y(),p.z()))){client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,hit);opened=true;}
    }
    @Override public void onTick(){try{
        tick++;if(client.player!=owner||client.level!=world||client.getConnection()!=connection||client.player==null||!client.player.isAlive()||now()>runDue||!config.get(CarpentryFlowConfig.CARPENTRY_FLOW_ENABLED)||!raw.equals(config.get(CarpentryFlowConfig.CARPENTRY_FLOW_PROFILE)))throw new IllegalStateException("Craft world/player/config changed or session timed out");
        if(now()>due)throw new IllegalStateException("Missing authoritative outcome in "+phase);
        if(phase.startsWith("OPEN_")){ingress();return;}if(phase.equals("RETURN")){returning();return;}if(phase.equals("SELL")){selling();return;}
        var view=io.read();if(view==null||view.menu()!=menu)throw new IllegalStateException("Craft/quote menu replaced");
        if(phase.equals("BUY_WAIT")){buyWait(view);return;}if(phase.equals("CRAFT_WAIT")){craftWait(view);return;}if(phase.equals("DEPOSIT")){deposit(view);return;}
        if(view.cursor().count()!=0&&!profile.cursorResults())throw new IllegalStateException("Foreign craft cursor");
        switch(phase){
            case "STOCK"->stock();case "SHOP"->buy(view);case "BUY_VERIFY"->{if(io.count(purchase.material().item())!=purchase.before()+purchase.material().offer().amount()||purse().compareTo(purchase.purse().subtract(BigDecimal.valueOf(purchase.cost())))!=0)throw new IllegalStateException("Fresh server resource/spend disagrees");acknowledgeCost(dev.veltraph.client.metrics.FinancialActionReceipt.Kind.PURCHASE,purchaseOutcomeId,purchase.cost(),purchaseMenu,purchaseMenuId,purchaseRequestRevision,purchaseReceiptRevision,"CARPENTRY_RESOURCE_RECEIPT_"+purchaseReceiptSequence);purchase=null;phase("SHOP");}
            case "CRAFT"->craft(view);
            case "OUTPUT_VERIFY"->{if(view.cursor().count()!=0||io.count(profile.output())!=generated-sold||!view.inventory(profile.sink()).item().matches(profile.output())||view.inventory(profile.sink()).item().count()!=generated-sold)throw new IllegalStateException("Fresh server output sink/count differs");deposit=null;crafts=0;if(profile.sale()!=null){reopen(profile.sale().target(),"SELL_START");}else finishBatch();}
            case "SELL_START"->sellStart();default->throw new IllegalStateException("Invalid carpentry flow state");
        }
    }catch(RuntimeException error){fail("Carpentry guard: "+error.getMessage());}}
    private boolean stocked(){return profile.materials().stream().allMatch(m->io.count(m.item())>=m.perCraft()*profile.craftsPerBatch());}
    private void stock(){long risk=0;for(var m:profile.materials())risk=Math.addExact(risk,Math.multiplyExact(m.item().value(),(long)m.perCraft()*profile.craftsPerBatch()));if(value+risk>profile.valueBudget())throw new IllegalStateException("Repeat exceeds resource value cap");if(stocked()){phase("CRAFT");return;}if(!profile.buyMissing())throw new IllegalStateException("Missing repeat materials; acquisition disabled");reopen(profile.shop(),"SHOP");}
    private void buy(CarpentryFlowMenus.View view){
        if(view.cursor().count()!=0)throw new IllegalStateException("Foreign shop cursor");if(stocked()){reopen(profile.craft(),"CRAFT");return;}var material=profile.materials().stream().filter(m->io.count(m.item())<m.perCraft()*profile.craftsPerBatch()).findFirst().orElseThrow();var offer=material.offer();var buttons=view.slots().stream().filter(s->!s.player()&&s.item().name().equals(offer.label())&&s.item().count()>0).toList();if(buttons.size()!=1)throw new IllegalStateException("Ambiguous material quote");var button=buttons.getFirst();long cost=quote(button.lore(),"^Price: (?<cost>[0-9]+) Coins$");if(!button.lore().contains("Amount: "+offer.amount()+"x")||cost<1||cost>offer.maxCost()||spent+cost>profile.spendBudget()||io.free()<2)throw new IllegalStateException("Material price/quantity/capacity/session cap failed");if(now()<next)return;
        purchaseOutcomeId=UUID.randomUUID().toString();purchaseMenu=view.menu();purchaseMenuId=view.id();purchaseRequestRevision=view.revision();purchaseReceiptRevision=view.revision();purchase=new Purchase(material,io.count(material.item()),cost,purse(),false);iconPrediction=button.item();request(view,button.id());spent=Math.addExact(spent,cost);phase("BUY_WAIT");
    }
    private static long quote(List<String> lore,String pattern){var p=Pattern.compile(pattern);var matches=lore.stream().map(p::matcher).filter(java.util.regex.Matcher::matches).toList();if(matches.size()!=1)throw new IllegalStateException("Ambiguous or unreadable cost");return new BigDecimal(matches.getFirst().group("cost").replace(",","")).longValueExact();}
    private void buyWait(CarpentryFlowMenus.View view){
        if(view.cursor().count()!=0&&!view.cursor().equals(iconPrediction))throw new IllegalStateException("Shop cursor changed");int count=io.count(purchase.material().item());if(count!=purchase.before()&&count!=purchase.before()+purchase.material().offer().amount())throw new IllegalStateException("Resource delta differs from quote");
        if(purchase.received()&&count==purchase.before()+purchase.material().offer().amount()&&view.cursor().count()==0&&purse().compareTo(purchase.purse().subtract(BigDecimal.valueOf(purchase.cost())))==0){purchaseReceiptRevision=view.revision();iconPrediction=null;reopen(profile.shop(),"BUY_VERIFY");}
    }
    private List<CarpentryFlowMenus.Slot> recipes(CarpentryFlowMenus.View view){return view.slots().stream().filter(s->!s.player()&&s.item().id().equals(profile.output().id())&&s.item().name().equals(profile.output().name())&&s.item().components().equals(profile.recipeComponents())&&s.item().count()==profile.outputPerCraft()&&s.lore().contains(profile.recipeLore())).toList();}
    private void craft(CarpentryFlowMenus.View view){
        if(crafts>=profile.craftsPerBatch()){phase("DEPOSIT");return;}var rows=recipes(view);if(rows.size()!=1)throw new IllegalStateException("Exact recipe lore/result unavailable");var row=rows.getFirst();long fee=quote(row.lore(),profile.costLore());if(fee<0||fee>profile.maxCraftCost()||spent+fee>profile.spendBudget())throw new IllegalStateException("Craft fee/session spend cap failed");int cursor=profile.cursorResults()?crafts*profile.outputPerCraft():0;if(view.cursor().count()!=cursor||cursor>0&&!view.cursor().matches(profile.output()))throw new IllegalStateException("Cursor is not owned compatible craft result");var counts=new HashMap<String,Integer>();for(var m:profile.materials()){int count=io.count(m.item());if(count<m.perCraft())throw new IllegalStateException("Craft material changed/short");counts.put(m.item().key(),count);}if(now()<next)return;
        pendingCraft=new Craft(Map.copyOf(counts),io.count(profile.output()),cursor,fee,purse(),view.revision());iconPrediction=row.item();request(view,row.id());spent=Math.addExact(spent,fee);phase("CRAFT_WAIT");
    }
    private void craftWait(CarpentryFlowMenus.View view){
        boolean inputs=true;for(var m:profile.materials()){int count=io.count(m.item());int expected=pendingCraft.before().get(m.item().key())-m.perCraft();if(count!=expected)inputs=false;if(count<expected)throw new IllegalStateException("Excess/unrelated craft consumption");}
        int expectedCursor=profile.cursorResults()?pendingCraft.cursor()+profile.outputPerCraft():0;
        boolean outputs=profile.cursorResults()?view.cursor().count()==expectedCursor&&view.cursor().matches(profile.output()):view.cursor().count()==0&&io.count(profile.output())==pendingCraft.outputs()+profile.outputPerCraft();
        if(view.cursor().count()>0&&!view.cursor().matches(profile.output())&&!view.cursor().equals(iconPrediction))throw new IllegalStateException("Unexpected crafted cursor/result");
        if(view.revision()!=pendingCraft.revision()&&inputs&&outputs&&purse().compareTo(pendingCraft.purse().subtract(BigDecimal.valueOf(pendingCraft.fee())))==0){for(var m:profile.materials())value=Math.addExact(value,Math.multiplyExact(m.item().value(),m.perCraft()));crafts++;generated+=profile.outputPerCraft();acknowledgeCost(dev.veltraph.client.metrics.FinancialActionReceipt.Kind.CRAFT_FEE,UUID.randomUUID().toString(),pendingCraft.fee(),view.menu(),view.id(),pendingCraft.revision(),view.revision(),"CARPENTRY_CRAFT_INPUT_OUTPUT_REVISION");pendingCraft=null;iconPrediction=null;phase("CRAFT");}
    }
    private void deposit(CarpentryFlowMenus.View view){
        int amount=generated-sold;var sink=view.inventory(profile.sink());if(sink==null)throw new IllegalStateException("Main sink not exposed by native menu");if(now()<next)return;
        if(profile.cursorResults()){
            if(view.cursor().count()!=crafts*profile.outputPerCraft()||!view.cursor().matches(profile.output())||sink.item().count()>0&&(!sink.item().matches(profile.output())||sink.item().count()!=amount-view.cursor().count()))throw new IllegalStateException("Owned output cursor/sink changed");deposit=new Deposit(io.count(profile.output()),view.cursor().count());request(view,sink.id());
        }else{
            if(sink.item().matches(profile.output())&&sink.item().count()==amount){deposit=new Deposit(amount,0);}
            else{if(view.cursor().count()!=0){if(!view.cursor().matches(profile.output()))throw new IllegalStateException("Foreign result transfer cursor");request(view,sink.id());}
                else{var source=view.slots().stream().filter(s->s.player()&&s.inventoryIndex()!=profile.sink()&&s.item().matches(profile.output())&&s.item().count()==crafts*profile.outputPerCraft()).findFirst().orElseThrow(()->new IllegalStateException("Exact owned result stack unavailable"));request(view,source.id());return;}deposit=new Deposit(amount,0);}
        }
        // Require a fresh ordinary menu after deposit, never the client's matching prediction.
        if(client.player.containerMenu.getCarried().isEmpty())reopen(profile.craft(),"OUTPUT_VERIFY");
    }
    private void request(CarpentryFlowMenus.View view,int slot){if(!io.request(view,slot,ContainerInput.PICKUP))throw new IllegalStateException("Stale crafting/stock/cursor request");next=now()+profile.delay();}
    @Override public void onServerMessage(String line){if(isEnabled())sale.onMessage(line);if(purchase!=null){var m=Pattern.compile(purchase.material().offer().receipt()).matcher(CarpentryFlowMenus.clean(line));if(m.matches())try{if(Long.parseLong(m.group("quantity"))==purchase.material().offer().amount()&&Long.parseLong(m.group("cost"))==purchase.cost()){if(!purchase.received())purchaseReceiptSequence++;purchase=new Purchase(purchase.material(),purchase.before(),purchase.cost(),purchase.purse(),true);}else if(isEnabled())fail("Purchase receipt disagrees with owned quote");}catch(RuntimeException bad){if(isEnabled())fail("Malformed purchase receipt");}}}
    private boolean cookieActive(){
        var fallback=profile.sale().protocol().fallback();if(fallback==null)return false;
        if(!(client.gui.hud.getTabList() instanceof dev.veltraph.client.mixin.PlayerListHudAccessor accessor))return false;
        String footer=accessor.getFooter()==null?"":accessor.getFooter().getString();
        return CookieEvidence.active(footer,List.of(),fallback.activeCookieLine(),"");
    }
    /** Preserve native SellingMenuAccess identity bindings unless adding an owned Bazaar prediction. */
    static SellingObservation saleObservation(SellingObservation value,boolean prediction,CarpentrySaleParent.State state){
        return state==CarpentrySaleParent.State.COOKIE_SALE||!prediction||value.ownCursorPrediction()?value:new SellingObservation(value.menu(),value.inventory(),value.slots(),value.cursor(),value.coins(),value.droppedItems(),true);
    }
    private CarpentrySaleParent.Observation saleRead(){
        var value=selling.read(profile.currencyPrefix());boolean prediction=salePredictionMenu==client.player.containerMenu&&!salePrediction.isEmpty()&&net.minecraft.world.item.ItemStack.matches(salePrediction,client.player.containerMenu.getCarried());
        value=saleObservation(value,prediction,sale.state());
        return new CarpentrySaleParent.Observation(new CarpentrySaleParent.Scope(client.player,client.level,client.getConnection(),config.get(CarpentryFlowConfig.CARPENTRY_FLOW_PROFILE)),value.menu()==null?null:client.player.containerMenu,client.player.getInventory().getSelectedSlot(),cookieActive(),value);
    }
    private void sellStart(){
        var observed=saleRead();var stack=observed.value().inventoryStack(profile.sink()).orElseThrow();int quantity=generated-sold;
        if(quantity!=profile.craftsPerBatch()*profile.outputPerCraft()||!io.item(client.player.getInventory().getItem(profile.sink())).matches(profile.output())||stack.count()!=quantity)throw new IllegalStateException("Selling handoff is not exact script-produced main stack");
        sale.start(observed,profile.sale(),profile.sink(),quantity,tick,(int)Math.max(20,profile.timeout()/50));
        if(sale.terminal())throw new IllegalStateException("Bazaar parent handoff rejected: "+sale.status());phase("SELL");
    }
    private void selling(){
        var observed=saleRead();var action=sale.tick(observed,tick);
        if(action.isPresent()){
            var a=action.get();var current=saleRead();boolean accepted=sale.valid(a,current);
            if(accepted)switch(a.kind()){
                case BAZAAR->{var child=(BazaarCustodyRuntime.Action)a.child();var bound=saleMenus.read();accepted=bound!=null&&bound.equals(current.value().menu())&&(child.close()?saleMenus.close(bound.id()):saleMenus.click(bound,child.slot()));}
                case NPC->accepted=selling.act((SellingRuntime.Action)a.child(),sale.fallbackRule(),profile.currencyPrefix());
                case CLOSE->{var bound=saleMenus.read();accepted=bound!=null&&bound.equals(current.value().menu())&&current.value().cursorEmpty()&&saleMenus.close(bound.id());}
                case COMMAND->{accepted=client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();if(accepted)client.getConnection().sendCommand(a.command());}
                case COOKIE_BUTTON->{var bound=saleMenus.read();accepted=bound!=null&&bound.equals(current.value().menu())&&saleMenus.click(bound,a.slot());}
            }
            sale.applied(a,accepted,tick);
            if(accepted&&(a.kind()==CarpentrySaleParent.Kind.BAZAAR||a.kind()==CarpentrySaleParent.Kind.COOKIE_BUTTON)){salePredictionMenu=client.player.containerMenu;salePrediction=client.player.containerMenu.getCarried().copy();}
        }
        if(sale.state()==CarpentrySaleParent.State.COMPLETE){var proof=sale.acknowledged().orElseThrow();
            financialReceipts.add(new dev.veltraph.client.metrics.FinancialActionReceipt(UUID.randomUUID().toString(),this,dev.veltraph.client.metrics.FinancialActionReceipt.Kind.SALE,dev.veltraph.client.metrics.FinancialActionReceipt.Denomination.UNKNOWN,proof.proceeds(),financialScope(),List.of(new dev.veltraph.client.metrics.FinancialActionReceipt.Evidence(proof.nativeMenu(),proof.menuId(),proof.requestRevision(),proof.receiptRevision(),proof.receiptSerial(),"CARPENTRY_"+proof.channel()+"_SALE"))));
            sold+=proof.quantity();menu=null;finishBatch();
        }else if(sale.terminal())throw new IllegalStateException("Output sale failed: "+sale.status());
    }
    private void finishBatch(){batches++;close();if(batches<profile.batches()){open(profile.craft(),"STOCK");return;}if(profile.returnToStart()){walking=false;phase("RETURN");}else complete();}
    private void returning(){if(restoreCamera)return;if(!walking){motionCleanup.release(mobility);if(!mobility.start(start,.35,SharedMovementPolicy.Purpose.EXACT_RETURN))throw new IllegalStateException("No safe return to starting position");walking=true;}var state=mobility.tick();if(state==McTravelNavigator.State.FAILED)throw new IllegalStateException("Craft return blocked: "+mobility.status()+" position="+client.player.position()+" screen="+client.gui.screen());if(state==McTravelNavigator.State.ARRIVED){mobility.stop();restoreCamera=true;if(motionCleanup.beforePublication(1)){services.rotation().rotateTo(yaw,pitch,()->{if(isEnabled()&&phase.equals("RETURN"))complete();});motionCleanup.remember();}}}
    private void complete(){reason="Finite craft batches, spend/outcomes and output sale acknowledged";phase="COMPLETE";setEnabled(false);}
    private void fail(String message){reason=message;phase="FAILED";setEnabled(false);}
    @Override public void onDisable(){
        release();sale.cancel("Carpentry owner stopped; no sale fallback or continuation");terminal=phase.equals("COMPLETE")?"COMPLETE":phase.equals("FAILED")?"FAILED":"CANCELLED";
        if(profile==null)return;
        if(client.player==owner&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.containerMenu==sale.ownedNativeMenu()&&client.player.containerMenu.getCarried().isEmpty()&&client.gui.screen()!=null&&saleScreen(client.gui.screen().getTitle().getString())){client.player.closeContainer();menu=null;}
        var view=io.read();boolean ownedCursor=view!=null&&view.cursor().matches(profile.output())&&view.cursor().count()<=profile.craftsPerBatch()*profile.outputPerCraft();
        cleaning=!terminal.equals("COMPLETE")&&(purchase!=null||pendingCraft!=null||ownedCursor||opened&&menu==null)&&client.player==owner&&client.level==world&&client.getConnection()==connection;
        if(client.player==owner&&client.level==world&&client.getConnection()==connection&&view!=null&&view.menu()==menu&&(view.cursor().count()==0||ownedCursor)){client.player.closeContainer();menu=null;}
        if(cleaning){due=now()+5000;cleanupOpened=false;previous=client.player.containerMenu;phase="CLEANUP";}else phase=terminal;
        if(client.player==owner&&client.player!=null&&client.player.getInventory().getSelectedSlot()==selection)client.player.getInventory().setSelectedSlot(selection);
    }
    @Override public boolean hasPendingCleanup(){return cleaning;}
    @Override public void maintenanceTick(){if(!cleaning)return;try{
        release();if(client.player!=owner||client.level!=world||client.getConnection()!=connection||client.player==null||!client.player.isAlive()||now()>due){cleanupEnd("Uncertain outcome retained; no retry");return;}
        var view=io.read();if(view!=null&&view.cursor().count()>0){if(view.menu()==menu&&view.cursor().matches(profile.output())){client.player.closeContainer();menu=null;}return;}
        if(purchase!=null){if(purchase.received()&&io.count(purchase.material().item())==purchase.before()+purchase.material().offer().amount()&&purse().compareTo(purchase.purse().subtract(BigDecimal.valueOf(purchase.cost())))==0){purchase=null;if(pendingCraft==null&&generated==sold){close();cleanupEnd("Issued acquisition acknowledged; stock retained");return;}}else return;}
        if(view!=null&&view.menu()!=previous&&view.title().equals(profile.craft().title())&&view.revision()>0){menu=view.menu();if(pendingCraft!=null){boolean inputs=profile.materials().stream().allMatch(m->io.count(m.item())==pendingCraft.before().get(m.item().key())-m.perCraft());if(inputs&&io.count(profile.output())==pendingCraft.outputs()+pendingCraft.cursor()+profile.outputPerCraft()&&purse().compareTo(pendingCraft.purse().subtract(BigDecimal.valueOf(pendingCraft.fee())))==0){pendingCraft=null;}else{cleanupEnd("Craft outcome differs; products preserved for inspection");return;}}
            close();cleanupEnd("Owned result/cursor and issued outcomes drained");return;}
        if(view!=null)return;
        if(!cleanupOpened){var t=profile.craft();target(t);if(!t.command().isEmpty()){client.player.connection.sendCommand(t.command());cleanupOpened=true;}else{var p=t.position();if(client.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(new net.minecraft.core.BlockPos(p.x(),p.y(),p.z())))>3.4){cleanupEnd("Stopped beyond restitution reach");return;}if(motionCleanup.beforePublication(1)){services.rotation().lookAt(new Vec3(p.x()+.5,p.y()+.5,p.z()+.5));motionCleanup.remember();}if(client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(new net.minecraft.core.BlockPos(p.x(),p.y(),p.z()))){client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,hit);cleanupOpened=true;}}}
    }catch(RuntimeException invalid){cleanupEnd("Restitution guard: "+invalid.getMessage());}}
    private void cleanupEnd(String message){cleaning=false;phase=terminal;reason+="; "+message;purchase=null;pendingCraft=null;iconPrediction=null;selling.clear();}
    public int completedBatches(){return batches;}public int produced(){return generated;}public int sold(){return sold;}public long reservedSpend(){return spent;}
    @Override public String diagnostics(){return phase+": "+reason+"; batches="+batches+" crafts="+crafts+" outputs="+generated+" sold="+sold+" sale="+sale.status()+" spend="+spent+" value="+value;}
}
