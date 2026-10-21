package dev.veltraph.client.galatea.agatha;

import dev.veltraph.client.config.*;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.mixin.AgathaChatAccessor;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.chat.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Read-only rich server invitation + ordinary native command/menu input and observed receipts. */
public final class McAgathaContest implements AgathaContestCycle.Port {
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private net.minecraft.client.player.LocalPlayer player;private net.minecraft.client.multiplayer.ClientLevel world;private Object connection;
    private GuiMessage invitation;private String command="",problem="";private long invitationAt,claimSentAt=-1,commandSentAt=-1;
    private List<Object> settings=List.of();
    private int originalSlot,menuId=-1,claimSlot=-1,revisionBefore=-1;
    private AbstractContainerMenu ownedMenu;private AbstractContainerScreen<?> ownedScreen;
    private NativeStackIdentity.Snapshot tool,button;private Set<GuiMessage> beforeClaim=Set.of(),beforeCommand=Set.of();private boolean commandSent,cleanup;
    public McAgathaContest(Minecraft client,ConfigService config,FarmServices services){this.client=client;this.config=config;this.services=services;}
    private static String clean(String text){return text.replaceAll("§.","");}
    private String value(ConfigKey<String> key){return config.get(key).strip();}
    private List<Object> signature(){return List.of(config.get(AgathaConfig.FORAGING_COLLECT_CONTESTS),value(AgathaConfig.AGATHA_INVITATION_PREFIX),value(AgathaConfig.AGATHA_LINK_LABEL),value(AgathaConfig.AGATHA_CLAIM_COMMAND),value(AgathaConfig.AGATHA_MENU_TITLE),value(AgathaConfig.AGATHA_CLAIM_LABEL),value(AgathaConfig.AGATHA_CLAIM_ITEM_ID),value(AgathaConfig.AGATHA_REWARD_ITEM_ID),value(AgathaConfig.AGATHA_RECEIPT_PREFIX),value(AgathaConfig.AGATHA_FAILURE_PREFIX),config.get(AgathaConfig.AGATHA_MIN_REWARD),config.get(AgathaConfig.AGATHA_MAX_CLAIM_ATTEMPTS),config.get(AgathaConfig.AGATHA_INVITATION_TTL_MS),config.get(AgathaConfig.AGATHA_STAGE_TIMEOUT_MS),config.get(AgathaConfig.AGATHA_ACK_MS));}
    private List<GuiMessage> messages(){return client.gui.hud.getChat() instanceof AgathaChatAccessor a?List.copyOf(a.veltraphAgathaMessages()):List.of();}
    public static boolean commandSafe(String command){return command!=null&&command.matches("/[a-zA-Z0-9_:-]+(?: [a-zA-Z0-9_:-]+)*");}
    private Optional<String> invitationCommand(Component root){
        ArrayDeque<Component> pending=new ArrayDeque<>();pending.add(root);String found=null;int visits=0;
        while(!pending.isEmpty()&&visits++<64){var part=pending.remove();if(clean(part.getString()).contains(value(AgathaConfig.AGATHA_LINK_LABEL))&&part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand click){
            if(!click.command().equals(value(AgathaConfig.AGATHA_CLAIM_COMMAND))||!commandSafe(click.command()))return Optional.empty();
            if(found!=null&&!found.equals(click.command()))return Optional.empty();found=click.command();}
            pending.addAll(part.getSiblings());}
        return pending.isEmpty()?Optional.ofNullable(found):Optional.empty();
    }
    private int countReward(){if(client.player==null)return 0;int result=0;for(int i=0;i<36;i++){var s=client.player.getInventory().getItem(i);if(BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(value(AgathaConfig.AGATHA_REWARD_ITEM_ID)))result+=s.getCount();}return result;}
    private boolean bound(){return player!=null&&client.player==player&&client.level==world&&client.getConnection()==connection&&player.isAlive()&&client.gameMode!=null;}
    private boolean cursorEmpty(){return client.player!=null&&client.player.containerMenu.getCarried().isEmpty();}
    public Optional<String> preStart(){
        if(!config.get(AgathaConfig.FORAGING_COLLECT_CONTESTS))return Optional.of("Enable the source Foraging Collect Contests option.");
        if(client.player==null||client.level==null||client.getConnection()==null||client.gameMode==null||!client.player.isAlive())return Optional.of("Load the Galatea world first.");
        if(!(client.gui.hud.getChat() instanceof AgathaChatAccessor))return Optional.of("Native Agatha chat history accessor is unavailable.");
        if(client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!cursorEmpty())return Optional.of("Close the menu and clear the cursor before contest collection.");
        for(var key:List.of(AgathaConfig.AGATHA_INVITATION_PREFIX,AgathaConfig.AGATHA_LINK_LABEL,AgathaConfig.AGATHA_MENU_TITLE,AgathaConfig.AGATHA_CLAIM_LABEL,AgathaConfig.AGATHA_RECEIPT_PREFIX))if(value(key).isEmpty())return Optional.of("Configure the explicit local Agatha invitation/menu/receipt metadata.");
        if(!commandSafe(value(AgathaConfig.AGATHA_CLAIM_COMMAND)))return Optional.of("Configure the exact one-line native Agatha claim command.");
        for(var key:List.of(AgathaConfig.AGATHA_CLAIM_ITEM_ID,AgathaConfig.AGATHA_REWARD_ITEM_ID))if(!value(key).matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))return Optional.of("Configure explicit Agatha claim/reward item registry IDs.");
        int tick=client.gui.hud.getGuiTicks();long ttl=Math.max(1000,Math.min(600000,config.get(AgathaConfig.AGATHA_INVITATION_TTL_MS)));
        invitation=null;for(var message:messages()){
            long age=(long)(tick-message.addedTime())*50;
            if(message.source()!=GuiMessageSource.SYSTEM_SERVER||age<0||age>ttl||!clean(message.content().getString()).startsWith(value(AgathaConfig.AGATHA_INVITATION_PREFIX)))continue;
            var candidate=invitationCommand(message.content());if(candidate.isPresent()){invitation=message;command=candidate.get();invitationAt=services.clock().nowMillis()-age;break;}
        }
        return invitation==null?Optional.of("A recent exact server Agatha invitation with its configured RunCommand link is required."):Optional.empty();
    }
    public void start(){player=client.player;world=client.level;connection=client.getConnection();originalSlot=player.getInventory().getSelectedSlot();tool=NativeStackIdentity.capture(player.getMainHandItem(),world.registryAccess());settings=signature();commandSent=cleanup=false;ownedMenu=null;ownedScreen=null;button=null;menuId=claimSlot=revisionBefore=-1;claimSentAt=commandSentAt=-1;beforeClaim=beforeCommand=Set.of();problem="";}
    private boolean protectedTool(){return bound()&&settings.equals(signature())&&player.getInventory().getSelectedSlot()==originalSlot&&tool.matches(player.getMainHandItem(),world.registryAccess());}
    private boolean matchingPendingMenu(){return commandSent&&ownedMenu==null&&client.gui.screen() instanceof AbstractContainerScreen<?> screen&&screen.getMenu()!=player.inventoryMenu&&screen.getTitle().getString().equals(value(AgathaConfig.AGATHA_MENU_TITLE));}
    private void adoptMenu(){if(bound()&&matchingPendingMenu()){ownedScreen=(AbstractContainerScreen<?>)client.gui.screen();ownedMenu=ownedScreen.getMenu();menuId=ownedMenu.containerId;}}
    private boolean owned(){return bound()&&ownedMenu!=null&&client.player.containerMenu==ownedMenu&&ownedMenu.containerId==menuId&&client.gui.screen()==ownedScreen;}
    private List<Slot> claims(){if(!owned())return List.of();return ownedMenu.slots.stream().filter(s->s.container!=player.getInventory()&&!s.getItem().isEmpty()
            &&BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).toString().equals(value(AgathaConfig.AGATHA_CLAIM_ITEM_ID))&&clean(s.getItem().getHoverName().getString()).equals(value(AgathaConfig.AGATHA_CLAIM_LABEL))).toList();}
    private boolean cursorSafe(){if(cursorEmpty())return true;return bound()&&owned()&&claimSentAt>=0&&button!=null&&button.matches(player.containerMenu.getCarried(),world.registryAccess());}
    private boolean receipt(boolean rejected){String prefix=value(rejected?AgathaConfig.AGATHA_FAILURE_PREFIX:AgathaConfig.AGATHA_RECEIPT_PREFIX);if(prefix.isEmpty()||(rejected?commandSentAt<0:claimSentAt<0))return false;var earlier=rejected?beforeCommand:beforeClaim;
        return messages().stream().anyMatch(m->m.source()==GuiMessageSource.SYSTEM_SERVER&&!earlier.contains(m)&&clean(m.content().getString()).startsWith(prefix));}
    public AgathaContestCycle.Observation read(){
        if(bound())adoptMenu();if(bound()&&!settings.equals(signature()))problem="Agatha profile changed";boolean healthy=protectedTool();boolean menuOpen=client.gui.screen()!=null||client.player!=null&&client.player.containerMenu!=client.player.inventoryMenu;
        boolean menuOwned=owned();boolean enabled=menuOwned&&claims().size()==1;
        boolean changed=menuOwned&&claimSlot>=0&&button!=null&&!button.matches(ownedMenu.getSlot(claimSlot).getItem(),world.registryAccess());
        boolean revision=menuOwned&&revisionBefore>=0&&ownedMenu.getStateId()!=revisionBefore;
        return new AgathaContestCycle.Observation(healthy,messages().contains(invitation),services.clock().nowMillis()-invitationAt<=Math.max(1000,Math.min(600000,config.get(AgathaConfig.AGATHA_INVITATION_TTL_MS))),menuOpen,menuOwned,cursorSafe(),cursorEmpty(),enabled,changed,revision,receipt(false),receipt(true),countReward(),services.clock().nowMillis());
    }
    @Override public void release(){services.breaking().setBreaking(false);McHeldKeys.setHeld(client.options.keyUse,false);services.input().releaseAllMovement();services.rotation().cancelRotation();}
    @Override public boolean closeStarting(){return bound()&&cursorEmpty()&&client.gui.screen()==null&&player.containerMenu==player.inventoryMenu;}
    @Override public boolean claimMessage(){if(!protectedTool()||commandSent||!cursorEmpty()||client.gui.screen()!=null||!messages().contains(invitation)||!command.equals(value(AgathaConfig.AGATHA_CLAIM_COMMAND)))return false;beforeCommand=Set.copyOf(messages());commandSentAt=services.clock().nowMillis();client.getConnection().sendCommand(command.substring(1));commandSent=true;return true;}
    @Override public boolean claimAll(){
        if(!protectedTool()||!owned()||!cursorEmpty())return false;var candidates=claims();if(candidates.size()!=1)return false;var slot=candidates.getFirst();
        claimSlot=slot.index;button=NativeStackIdentity.capture(slot.getItem(),world.registryAccess());revisionBefore=ownedMenu.getStateId();beforeClaim=Set.copyOf(messages());claimSentAt=services.clock().nowMillis();
        client.gameMode.handleContainerInput(menuId,claimSlot,0,ContainerInput.PICKUP,player);return true;
    }
    @Override public boolean closeEnding(){if(!bound()||!cursorEmpty())return false;if(owned()){player.closeContainer();client.gui.setScreen(null);return false;}return client.gui.screen()==null&&player.containerMenu==player.inventoryMenu;}
    public boolean allowsScreen(){return bound()&&(owned()||matchingPendingMenu());}
    public void beginCleanup(){release();cleanup=true;cleanupTick();}
    public void cleanupTick(){if(!cleanup)return;if(!bound()){ownedMenu=null;ownedScreen=null;cleanup=false;return;}adoptMenu();if(!cursorEmpty())return;if(owned()){player.closeContainer();client.gui.setScreen(null);return;}
        if(ownedMenu==null&&commandSent&&services.clock().nowMillis()-commandSentAt<Math.max(1000,Math.min(60000,config.get(AgathaConfig.AGATHA_STAGE_TIMEOUT_MS))))return;
        cleanup=false;ownedMenu=null;ownedScreen=null;}
    public boolean pendingCleanup(){return cleanup;}public String problem(){return problem;}
}
