package dev.veltraph.client.mining.commission;

import dev.veltraph.client.mixin.PlayerListHudAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Actual bounded native observation. No packet submission, action receipt, or profile identity claim. */
public final class McCommissionObservation {
    public record Snapshot(Object player,Object world,Object connection,long epoch,String region,
                           CommissionObservation observation,String source,boolean actionable) {}
    private static final Map<Minecraft,java.lang.ref.WeakReference<McCommissionObservation>> INSTALLED=new WeakHashMap<>();
    private final Minecraft client;private Object player,world,connection;private long epoch,serial,lastNow=-1;
    private CommissionWorkBinding.Binding armed;private Snapshot admission;private boolean revoked,ambiguous;
    private CommissionProgressHistory progress;
    private Object signature;private Snapshot latest;
    public McCommissionObservation(Minecraft client){this.client=Objects.requireNonNull(client);
        synchronized(INSTALLED){var old=INSTALLED.get(client);var actual=old==null?null:old.get();if(actual!=null){actual.ambiguous=true;ambiguous=true;}INSTALLED.put(client,new java.lang.ref.WeakReference<>(this));}}
    /** Passive native setter ingress. A caller can only cause observation/revocation; no receipt is minted. */
    public static void nativeMutation(Minecraft client){
        if(client==null||!client.isSameThread())return;McCommissionObservation observer;
        synchronized(INSTALLED){var ref=INSTALLED.get(client);observer=ref==null?null:ref.get();}
        if(observer==null||observer.armed==null)return;
        try{if(observer.read()==null)observer.revoked=true;}catch(RuntimeException invalid){observer.revoked=true;}
    }
    void arm(CommissionWorkBinding.Binding binding,Snapshot exact){
        if(binding==null||exact==null||exact!=latest||!exact.actionable()||ambiguous||!binding.current(exact.observation()))throw new IllegalStateException("Unknown native task admission");
        armed=binding;admission=exact;revoked=false;progress=new CommissionProgressHistory(binding,exact.observation());note(exact);
    }
    private void note(Snapshot actual){
        if(armed==null)return;
        if(actual==null||!actual.actionable()||actual.player()!=admission.player()||actual.world()!=admission.world()
            ||actual.connection()!=admission.connection()||actual.epoch()!=admission.epoch()||!armed.current(actual.observation())){revoked=true;return;}
        if(progress==null||!progress.observe(actual.observation()))revoked=true;
    }
    boolean historyCurrent(){return armed!=null&&!revoked&&!ambiguous;}
    public Snapshot latest(){return latest;}
    public void clear(){armed=null;admission=null;progress=null;revoked=false;player=world=connection=null;signature=null;latest=null;lastNow=-1;}
    public Snapshot read(){
        if(!client.isSameThread())throw new IllegalStateException("Commission observation requires client thread");
        long now=System.nanoTime()/1_000_000;var p=client.player;var w=client.level;var c=client.getConnection();
        if(p!=player||w!=world||c!=connection){if(epoch==Long.MAX_VALUE)throw new IllegalStateException("Commission context exhausted");epoch++;player=p;world=w;connection=c;signature=null;}
        if(p==null||w==null||c==null||now<lastNow){latest=null;lastNow=now;if(armed!=null)revoked=true;return null;}lastNow=now;
        if(!(c instanceof CommissionMutationCarrier listener)||listener.commissionNativeMutationVersion()<1||listener.commissionNativeMutationVersion()==Long.MAX_VALUE){latest=null;if(armed!=null)revoked=true;return null;}
        if(!(w.getScoreboard() instanceof CommissionMutationCarrier board)||board.commissionNativeMutationVersion()==Long.MAX_VALUE){latest=null;if(armed!=null)revoked=true;return null;}
        String context="commission-native-context-"+epoch;
        CommissionObservation result;String source;String region="";
        try{
            var overlay=client.gui.hud.getTabList();if(!(overlay instanceof CommissionMutationCarrier carrier)||carrier.commissionNativeMutationVersion()<1||carrier.commissionNativeMutationVersion()==Long.MAX_VALUE){latest=null;if(armed!=null)revoked=true;return null;}if(!(overlay instanceof PlayerListHudAccessor access)){latest=null;if(armed!=null)revoked=true;return null;}
            var rows=new ArrayList<String>();var nativeRows=access.veltraph$getPlayerInfos();if(nativeRows.size()>80)throw new IllegalArgumentException("Oversized tab roster");
            for(var info:nativeRows){if(!(info instanceof CommissionMutationCarrier infoCarrier)||infoCarrier.commissionNativeMutationVersion()==Long.MAX_VALUE)throw new IllegalArgumentException("Native player display history unavailable");var team=info.getTeam();if(team!=null&&(!(team instanceof CommissionMutationCarrier teamHistory)||teamHistory.commissionNativeMutationVersion()==Long.MAX_VALUE))throw new IllegalArgumentException("Native team history unavailable");rows.add(text(overlay.getNameForDisplay(info),512));}
            String header=text(access.getHeader(),4096),footer=text(access.getFooter(),4096);
            var sources=List.of(lines(header),lines(footer),List.copyOf(rows));
            var complete=new ArrayList<CommissionObservation>();
            for(var lines:sources){var parsed=CommissionParser.tab(context,serial,now,lines);if(parsed.completeSection())complete.add(parsed);}
            if(complete.isEmpty()){result=menu(context,now);source="native-menu-readonly";}
            else{
                result=complete.getFirst();for(var other:complete)if(!other.tasks().equals(result.tasks()))throw new IllegalArgumentException("Conflicting native commission sections");
                source="native-tab";
            }
            var all=new ArrayList<String>();all.addAll(rows);all.addAll(lines(header));all.addAll(lines(footer));region=region(all);
            var nextSignature=List.of(result.tasks(),result.completeSection(),source,region);
            if(!nextSignature.equals(signature)){if(serial==Long.MAX_VALUE)throw new IllegalStateException("Commission observation exhausted");serial++;signature=nextSignature;}
            result=new CommissionObservation(context,serial,now,result.tasks(),result.completeSection());
            boolean consistent=client.player==p&&client.level==w&&client.getConnection()==c;
            latest=new Snapshot(p,w,c,epoch,region,result,source,consistent&&!ambiguous&&source.equals("native-tab")&&result.completeSection());
            note(latest);return latest;
        }catch(RuntimeException unavailable){latest=null;signature=null;if(armed!=null)revoked=true;return null;}
    }
    private CommissionObservation menu(String context,long now){
        var p=client.player;
        if(!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)||screen.getMenu()!=p.containerMenu||p.containerMenu==p.inventoryMenu)
            return new CommissionObservation(context,serial,now,List.of(),false);
        String title=text(screen.getTitle(),512);
        if(!CommissionParser.plain(title).equals("Commissions")||!BuiltInRegistries.MENU.getKey(p.containerMenu.getType()).toString().equals("minecraft:generic_9x4"))
            return new CommissionObservation(context,serial,now,List.of(),false);
        var rows=new ArrayList<CommissionParser.Row>();if(p.containerMenu.slots.size()>90)throw new IllegalArgumentException("Oversized commission menu");
        for(var slot:p.containerMenu.slots){if(slot.container==p.getInventory())continue;var item=slot.getItem();String name=text(item.getHoverName(),512);
            if(!CommissionParser.plain(name).matches("Commission #[1-9][0-9]?"))continue;
            var nativeLore=item.get(DataComponents.LORE);var lore=new ArrayList<String>();if(nativeLore!=null){if(nativeLore.lines().size()>128)throw new IllegalArgumentException("Oversized commission lore");for(var line:nativeLore.lines())lore.add(text(line,512));}
            rows.add(new CommissionParser.Row(slot.index,name,BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),lore));
        }
        return CommissionParser.menu(context,serial,now,title,true,rows);
    }
    private static List<String> lines(String value){var rows=Arrays.asList(value.split("\\n",-1));if(rows.size()>256)throw new IllegalArgumentException("Oversized native section");return rows;}
    private static String region(List<String> lines){
        String found="";for(var raw:lines){String row=CommissionParser.plain(raw);if(row.startsWith("⏣"))row=row.substring(1).strip();
            if(List.of("Upper Mines","Royal Mines","Lava Springs","Cliffside Veins","Rampart's Quarry","Glacite Tunnels").contains(row)){
                if(!found.isEmpty()&&!found.equals(row))return "";found=row;}}
        return found;
    }
    /** Bound while visiting; never copy or silently truncate an oversized Component. */
    private static String text(Component component,int limit){
        if(component==null)return "";var out=new StringBuilder();boolean[] overflow={false};
        component.visit(part->{if(part.length()>limit-out.length()){overflow[0]=true;return Optional.of(Boolean.TRUE);}out.append(part);return Optional.<Boolean>empty();});
        if(overflow[0])throw new IllegalArgumentException("Native commission text bound exceeded");return out.toString();
    }
}
