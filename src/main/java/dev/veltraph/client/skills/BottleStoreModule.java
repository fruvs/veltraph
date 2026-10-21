package dev.veltraph.client.skills;

import dev.veltraph.client.config.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.world.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

/** Configured quoted bottle bundle -> observed acquisition -> walk/open real chest -> exact storage ACK.
 * The source salted bottle predicate does not justify assuming XP rather than water bottles. */
public final class BottleStoreModule extends GuardedSkillModule {
    public static final String ID="bottle_buy_store";
    private final McNavigator navigator;private final McSharedMovementNavigator mobility;private final dev.veltraph.client.navigation.mc.McParentMotionCleanup motionCleanup;
    private BlockPos chest;private boolean walking,interacted;private int chestBefore;
    private Object verificationOldMenu;
    private SkillMenuSession.Item purchasePredictedCursor;
    public BottleStoreModule(Minecraft client,ConfigService config,FarmServices services){super(ID,"Bottle Acquisition and Storage",client,config,services);navigator=new McNavigator(client,services.input(),config);mobility=new McSharedMovementNavigator(client,services,config);motionCleanup=new dev.veltraph.client.navigation.mc.McParentMotionCleanup(client,services,()->this);}
    @Override public boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return isEnabled()&&context()&&walking&&mobility.consumeExpectedTeleport(position,now);}
    private String itemId(){return config.get(SkillAutomationConfig.BOTTLE_ITEM_ID);}
    private String name(){return config.get(SkillAutomationConfig.BOTTLE_ITEM_NAME);}
    private int quantity(){return config.get(SkillAutomationConfig.BOTTLE_QUANTITY);}
    private String buyTitle(){return config.get(SkillAutomationConfig.BOTTLE_BUY_MENU_TITLE);}
    private String chestTitle(){return config.get(SkillAutomationConfig.BOTTLE_CHEST_TITLE);}
    @Override protected boolean ownsCursor(SkillMenuSession.Item cursor){return purchasePredictedCursor!=null&&cursor.equals(purchasePredictedCursor);}
    private List<SkillMenuSession.Slot> buy(SkillMenuSession.View menu){return menu.slots().stream().filter(s->!s.player()&&s.item().id().equals(itemId())&&s.item().name().equals(config.get(SkillAutomationConfig.BOTTLE_BUY_LABEL))&&s.item().count()>0&&(s.item().name().equals(name())||s.lore().contains("Item: "+name()))).toList();}
    @Override public boolean allowsScreen(String current){var title=SkillMenuSession.clean(current);return title.equals(buyTitle())||title.equals(chestTitle());}
    @Override public Optional<String> preStartCheck(){return invalid(()->{
        if(!config.get(SkillAutomationConfig.BOTTLE_ENABLED))throw new IllegalArgumentException("Enable bottle acquisition/storage explicitly");
        SkillContracts.identity(itemId(),name());SkillContracts.quantity(quantity(),Math.min(64,config.get(SkillAutomationConfig.SKILL_MAX_QUANTITY)));label(buyTitle());label(chestTitle());label(config.get(SkillAutomationConfig.BOTTLE_BUY_LABEL));
        if(quantity()>menus.stackLimit(itemId()))throw new IllegalArgumentException("Bottle bundle exceeds one ordinary item stack; configure a supported quantity");
        var target=SkillContracts.position(config.get(SkillAutomationConfig.BOTTLE_CHEST_POSITION));
        if(!services.world().isLoaded(target))throw new IllegalArgumentException("Storage chest chunk is not loaded");
        var block=client.level.getBlockState(new net.minecraft.core.BlockPos(target.x(),target.y(),target.z())).getBlock();
        if(block!=Blocks.CHEST&&block!=Blocks.TRAPPED_CHEST&&block!=Blocks.BARREL)throw new IllegalArgumentException("Storage position must be a loaded chest/barrel");
        var menu=menus.read();if(menu==null||!menu.title().equals(buyTitle())||menu.cursor().count()!=0||buy(menu).size()!=1)throw new IllegalArgumentException("Open one quoted bottle offer with empty cursor");
        var quote=SkillContracts.quotedCost(buy(menu).getFirst().lore(),quantity());if(quote.isEmpty())throw new IllegalArgumentException("Unreadable/ambiguous bottle quantity or price quote");
        SkillContracts.value(quote.getAsLong(),Math.min(config.get(SkillAutomationConfig.BOTTLE_MAX_PURCHASE_COST),config.get(SkillAutomationConfig.SKILL_MAX_RESOURCE_VALUE)));
        if(menus.count(itemId(),name())!=0||!menus.capacity(1))throw new IllegalArgumentException("Start with no matching bottles and an empty inventory slot");
    });}
    @Override public void onEnable(){begin();purchasePredictedCursor=null;var error=preStartCheck();if(error.isPresent()){fail(error.get());return;}chest=SkillContracts.position(config.get(SkillAutomationConfig.BOTTLE_CHEST_POSITION));ownedMenu=menus.read().menu();verificationOldMenu=null;walking=interacted=false;phase="BUY_BOTTLES";}
    @Override protected void release(){motionCleanup.release(mobility);}
    @Override public void onDisable(){super.onDisable();}
    @Override public void onServerMessage(String line){if(isEnabled()&&(line.contains("cannot afford")||line.contains("not enough coins")))fail("Server refused bottle purchase");}
    @Override public void onTick(){try{
        if(!context()){fail("Bottle player/world/config changed or timed out");return;}
        switch(phase){
            case "BUY_BOTTLES"->{var menu=expected(buyTitle());if(menu==null||menu.cursor().count()!=0||buy(menu).size()!=1||menus.count(itemId(),name())!=0){fail("Bottle offer/cursor/inventory changed");return;}var quote=SkillContracts.quotedCost(buy(menu).getFirst().lore(),quantity());if(quote.isEmpty()){fail("Bottle quote changed");return;}SkillContracts.value(quote.getAsLong(),Math.min(config.get(SkillAutomationConfig.BOTTLE_MAX_PURCHASE_COST),config.get(SkillAutomationConfig.SKILL_MAX_RESOURCE_VALUE)));purchasePredictedCursor=buy(menu).getFirst().item();if(click(menu,buy(menu).getFirst().id(),ContainerInput.PICKUP))phase="WAIT_PURCHASE_ACK";}
            case "WAIT_PURCHASE_ACK"->{var menu=expected(buyTitle());if(menu==null||menu.cursor().count()!=0&&!ownsCursor(menu.cursor())){fail("Bottle purchase menu/cursor replaced");return;}int count=menus.count(itemId(),name());if(menu.revision()>revision&&menu.cursor().count()==0&&count==quantity()){client.player.closeContainer();ownedMenu=null;phase="TRAVEL_TO_CHEST";}else if(count!=0&&count!=quantity())fail("Purchased bottle quantity differs from contract");}
            case "TRAVEL_TO_CHEST"->approach();
            case "STORE_BOTTLES"->{var menu=expected(chestTitle());if(menu==null||menu.cursor().count()!=0){fail("Storage menu/cursor replaced");return;}var bottles=menu.matching(itemId(),name(),true);if(bottles.size()!=1||bottles.getFirst().item().count()!=quantity()){fail("Bottle stack split or changed before storage");return;}
                long capacity=menu.slots().stream().filter(s->!s.player()&&s.item().count()==0).count()*64;if(capacity<quantity()){fail("Storage chest lacks empty capacity");return;}chestBefore=menu.matching(itemId(),name(),false).stream().mapToInt(s->s.item().count()).sum();if(click(menu,bottles.getFirst().id(),ContainerInput.QUICK_MOVE))phase="WAIT_STORAGE_ACK";}
            case "WAIT_STORAGE_ACK"->{var menu=expected(chestTitle());if(menu==null||menu.cursor().count()!=0){fail("Storage acknowledgment menu/cursor replaced");return;}
                // Vanilla can accept an exactly predicted QUICK_MOVE without sending a correction.
                // A fresh ordinary close/reopen forces server-owned initial container contents;
                // never credit the client's matching prediction as the storage outcome.
                if(delay()){verificationOldMenu=menu.menu();client.player.closeContainer();ownedMenu=null;walking=interacted=false;phase="REOPEN_STORAGE";}}
            case "REOPEN_STORAGE"->approach();
            case "VERIFY_STORAGE"->{var menu=expected(chestTitle());if(menu==null||menu.menu()==verificationOldMenu||menu.cursor().count()!=0){fail("Storage verification menu replaced");return;}
                if(menu.revision()>0){int stored=menu.matching(itemId(),name(),false).stream().mapToInt(s->s.item().count()).sum();if(stored==chestBefore+quantity()&&menus.count(itemId(),name())==0)complete("Fresh server chest contents confirm exact bundle storage and player removal");else fail("Fresh server chest/inventory disagrees with storage request");}}
            default->fail("Invalid bottle phase");
        }
    }catch(RuntimeException e){fail("Bottle guard: "+e.getMessage());}}
    private void approach(){
        var opened=menus.read();if(opened!=null){if(!interacted||!opened.title().equals(chestTitle())||opened.cursor().count()!=0){fail("Unrelated menu during storage travel");return;}mobility.stop();ownedMenu=opened.menu();phase=phase.equals("REOPEN_STORAGE")?"VERIFY_STORAGE":"STORE_BOTTLES";return;}
        if(client.gui.screen()!=null){fail("Unexpected screen during storage travel");return;}
        if(!services.world().isLoaded(chest)){fail("Storage chest chunk unloaded");return;}
        var pos=new net.minecraft.core.BlockPos(chest.x(),chest.y(),chest.z());
        if(walking||client.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos))>4){
            if(!walking){for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})if(mobility.start(navigator.position(chest.add(d[0],0,d[1])),.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL)){walking=true;break;}if(!walking){fail("No safe storage approach");return;}}
            var state=mobility.tick();if(state==McTravelNavigator.State.FAILED){fail("Storage approach blocked");return;}if(state==McTravelNavigator.State.ARRIVED)walking=false;return;
        }
        mobility.stop();if(motionCleanup.beforePublication(1)){services.rotation().lookAt(new dev.veltraph.client.world.Vec3(chest.x()+.5,chest.y()+.5,chest.z()+.5));motionCleanup.remember();}
        if(!interacted&&client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos)&&delay()){
            client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,hit);interacted=true;reason="Awaiting storage menu";}
    }
}
