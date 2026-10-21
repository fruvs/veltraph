package dev.veltraph.client.inventory;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Player opens Bazaar; this opt-in helper performs one explicitly quoted inventory sale. */
public final class BazaarAutoSellModule extends AbstractModule {
    public static final String ID="auto_sell_bazaar";
    private final Minecraft client;private final ConfigService config;private final McMenuAccess menus;
    private final BazaarSaleRuntime runtime=new BazaarSaleRuntime();
    private long tick;
    private Object levelIdentity,playerIdentity;
    private ItemStack predictedCursor=ItemStack.EMPTY;
    private Object predictionMenu;
    public BazaarAutoSellModule(Minecraft client,ConfigService config){
        super(ID,"Bazaar Inventory Sale",ModuleCategory.MISC);this.client=client;this.config=config;menus=new McMenuAccess(client);
    }
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public boolean ownsGameplayInput(){return true;}
    @Override public boolean allowsScreen(String title){return runtime.allowsTitle(title);}
    private BazaarSaleRuntime.Settings settings(){return new BazaarSaleRuntime.Settings(
            config.get(InventoryUtilityConfig.AUTO_SELL_MENU_TITLE),config.get(InventoryUtilityConfig.AUTO_SELL_BUTTON),
            config.get(InventoryUtilityConfig.AUTO_SELL_CONFIRM_TITLE),config.get(InventoryUtilityConfig.AUTO_SELL_CONFIRM_BUTTON),
            config.get(InventoryUtilityConfig.AUTO_SELL_MAX_QUANTITY),config.get(InventoryUtilityConfig.AUTO_SELL_MIN_PROCEEDS),
            config.get(InventoryUtilityConfig.AUTO_SELL_MAX_PROCEEDS),config.get(InventoryUtilityConfig.AUTO_SELL_DELAY_TICKS),
            config.get(InventoryUtilityConfig.AUTO_SELL_TIMEOUT_TICKS));}
    private List<BazaarSaleRuntime.Item> inventory(){
        List<BazaarSaleRuntime.Item> result=new ArrayList<>();if(client.player==null)return result;
        // Main inventory only; armor/offhand aren't saleable inventory rows.
        for(int i=0;i<36;i++){
            var item=client.player.getInventory().getItem(i);if(item.isEmpty())continue;
            String components=item.getComponents().stream().map(Object::toString).sorted().toList().toString();
            result.add(new BazaarSaleRuntime.Item(BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),item.getHoverName().getString(),item.getCount(),components));
        }return result;
    }
    private boolean cursorEmpty(){return client.player!=null&&client.player.containerMenu.getCarried().isEmpty();}
    private Optional<String> checkAndStart(BazaarSaleRuntime target){
        if(!config.get(MiscConfig.AUTO_SELL_ENABLED))return Optional.of("Enable Auto-sell explicitly before starting");
        if(config.get(MiscConfig.AUTO_SELL_MODE)!=AutoSellMode.BAZAAR)return Optional.of("Choose Bazaar as the auto-sell destination");
        if(config.get(MiscConfig.AUTO_SELL_SACKS))return Optional.of("Sack selling is not implemented; disable Auto-sell sacks");
        if(client.player==null||!client.player.isAlive())return Optional.of("A living player is required");
        try{target.start(menus.read(),inventory(),settings(),tick,cursorEmpty());}
        catch(IllegalArgumentException e){return Optional.of("Invalid sale labels, bounds or timing");}
        return target.state()==BazaarSaleRuntime.State.FAILED?Optional.of(target.reason()):Optional.empty();
    }
    @Override public Optional<String> preStartCheck(){return checkAndStart(new BazaarSaleRuntime());}
    private void release(){
        for(var key:List.of(client.options.keyUp,client.options.keyDown,client.options.keyLeft,client.options.keyRight,
                client.options.keyJump,client.options.keyShift,client.options.keySprint,client.options.keyAttack,client.options.keyUse))
            dev.veltraph.client.service.mc.McHeldKeys.setHeld(key,false);
    }
    @Override public void onEnable(){
        release();tick=0;runtime.reset();predictedCursor=ItemStack.EMPTY;predictionMenu=null;levelIdentity=client.level;playerIdentity=client.player;var blocker=checkAndStart(runtime);
        dev.veltraph.client.log.VeltraphLog.details(displayName(),blocker.orElse(runtime.reason()));
        if(blocker.isPresent()){runtime.cancel(blocker.get());setEnabled(false);}
    }
    @Override public void onTick(){
        tick++;release();
        if(client.player==null||!client.player.isAlive()||client.level==null||client.level!=levelIdentity||client.player!=playerIdentity){runtime.cancel("Player/world unavailable or replaced");setEnabled(false);return;}
        boolean predicted=!predictedCursor.isEmpty()&&client.player.containerMenu==predictionMenu
                &&ItemStack.matches(predictedCursor,client.player.containerMenu.getCarried());
        var action=runtime.tick(menus.read(),inventory(),tick,cursorEmpty(),predicted);
        if(action.isPresent()){
            var request=action.get();var latest=menus.read();
            boolean accepted=cursorEmpty()&&runtime.validates(request,latest)
                    &&(request.close()?menus.close(request.menuId()):menus.click(latest,request.slot()));
            runtime.applied(request,accepted,tick);
            if(accepted&&!request.close()){
                // Vanilla predicts PICKUP locally even when the server implements a non-item button.
                // Trust only this exact synchronous stack in this exact menu, until server resync.
                predictionMenu=client.player.containerMenu;predictedCursor=client.player.containerMenu.getCarried().copy();
            }
            dev.veltraph.client.log.VeltraphLog.details(displayName(),runtime.state()+": "+runtime.reason());
        }
        if(runtime.terminal())setEnabled(false);
    }
    @Override public void onServerMessage(String line){if(isEnabled())runtime.onMessage(line);}
    @Override public void onDisable(){
        release();runtime.cancel("Stopped by player or guard");
        var current=menus.read();if(client.level==levelIdentity&&client.player==playerIdentity&&cursorEmpty()&&current!=null&&runtime.ownedMenu().orElse(-1)==current.id()&&runtime.allowsTitle(current.title()))menus.close(current.id());
    }
    public BazaarSaleRuntime.State saleState(){return runtime.state();}
    @Override public String diagnostics(){return "Bazaar sale "+runtime.state()+": "+runtime.reason();}
}
