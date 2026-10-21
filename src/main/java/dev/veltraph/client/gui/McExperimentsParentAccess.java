package dev.veltraph.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import java.util.*;

/** Native observations and final dispatch gate for the table parent, sharing its existing menu owner. */
public final class McExperimentsParentAccess {
    private final Minecraft client;
    private final McMenuAccess menus;
    private record Issued(Object player,Object world,Object connection,Object menu,int menuId,int revision,ItemStack predictedCursor) {}
    private Issued issued;
    private String diagnostic="native parent observation unavailable";
    public void clear(){issued=null;}
    public String diagnostics(){return diagnostic;}
    public McExperimentsParentAccess(Minecraft client,McMenuAccess menus){this.client=client;this.menus=menus;}
    public ExperimentsParentRuntime.Observation read() {
        if(!client.isSameThread())throw new IllegalStateException("Experiments parent read requires client thread");
        if(client.player==null||client.level==null)throw new IllegalStateException("Missing experiment world/player");
        var inventory=new ArrayList<String>();
        for(int i=0;i<client.player.getInventory().getContainerSize();i++) {
            ItemStack stack=client.player.getInventory().getItem(i);
            inventory.add(stack.isEmpty()?"empty":net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())+"|"+stack.getCount()+"|"+stack.getComponents());
        }
        var rows=new ArrayList<String>();var board=client.level.getScoreboard();var objective=board.getDisplayObjective(DisplaySlot.SIDEBAR);
        if(objective!=null)for(var score:board.listPlayerScores(objective)) {
            var team=board.getPlayersTeam(score.owner());
            rows.add(MenuSnapshot.clean(team==null?score.owner():team.getPlayerPrefix().getString()+score.owner()+team.getPlayerSuffix().getString()));
        }
        var menu=client.player.containerMenu;var snapshot=menus.read();
        boolean context=issued!=null&&issued.player()==client.player&&issued.world()==client.level&&issued.connection()==client.getConnection()&&issued.menu()==menu;
        boolean revision=!context||menu.getStateId()!=issued.revision();
        boolean own=context&&!revision&&!issued.predictedCursor().isEmpty()&&ItemStack.matches(issued.predictedCursor(),menu.getCarried());
        diagnostic="native menu="+menu.containerId+"@"+menu.getStateId()+"; issued="+(issued==null?"none":issued.menuId()+"@"+issued.revision())
                +"; XP="+client.player.experienceLevel+"; cursor="+menu.getCarried()+"; ownedPrediction="+own+"; serverRevision="+revision;
        return new ExperimentsParentRuntime.Observation(snapshot,client.player.experienceLevel,inventory,rows,menu.getCarried().isEmpty(),own,revision);
    }
    public boolean dispatch(ExperimentsParentRuntime.Action a,ExperimentsParentRuntime.Observation before) {
        if(!client.isSameThread())throw new IllegalStateException("Experiments parent dispatch requires client thread");
        if(client.player==null||!client.player.isAlive()||client.level==null||client.gameMode==null||client.getConnection()==null)return false;
        var fresh=read();
        if(!fresh.cursorEmpty()||fresh.level()!=before.level()||!fresh.inventory().equals(before.inventory())||!fresh.resourceRows().equals(before.resourceRows()))return false;
        if(a.kind()==ExperimentsParentRuntime.Kind.COMMAND) {
            if(fresh.menu()!=null||a.command().isBlank()||!a.command().matches("/?[a-zA-Z0-9 _:-]+"))return false;
            client.getConnection().sendCommand(a.command().startsWith("/")?a.command().substring(1):a.command());return true;
        }
        if(a.menu()==null||fresh.menu()==null||!fresh.menu().equals(a.menu()))return false;
        return switch(a.kind()) {
            case NONE -> true;
            case CLICK -> {
                if(!menus.click(a.menu(),a.slot()))yield false;
                // Vanilla clicked() has now predicted PICKUP locally. Retain only its exact native
                // stack/context/revision; any new server revision or context revokes attribution.
                var menu=client.player.containerMenu;
                issued=new Issued(client.player,client.level,client.getConnection(),menu,menu.containerId,menu.getStateId(),menu.getCarried().copy());
                yield true;
            }
            case CLOSE -> menus.close(a.menu().id());
            case COMMAND -> false;
        };
    }
}
