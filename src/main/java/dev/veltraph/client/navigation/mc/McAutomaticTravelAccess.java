package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.fishing.FishingManaParser;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.navigation.*;
import java.util.*;
import java.util.function.*;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;

/** Read-only actual capability and received-stat/access evidence, scoped to native ownership. */
public final class McAutomaticTravelAccess {
    public record Snapshot(boolean valid,Object player,Object world,Object connection,GameArea area,String dimension,long revision,
            AutomaticTravelPolicy.Access capabilities,long now) {}
    private record Proof(WeakReference<Object> player,WeakReference<Object> world,WeakReference<Object> connection,GameArea area,String dimension,long revision,long at) {}
    private final Minecraft client;private final ConfigService config;private final Supplier<GameArea> area;private final LongSupplier revision;
    private TravelAtlas atlas;private final Map<String,Proof> permissions=new HashMap<>();private Proof manaProof;private int mana=-1;
    public McAutomaticTravelAccess(Minecraft client,ConfigService config,Supplier<GameArea> area,LongSupplier revision) {
        this.client=client;this.config=config;this.area=area;this.revision=revision;
    }
    public void bind(TravelAtlas atlas) {if(!Objects.equals(this.atlas,atlas)){permissions.clear();this.atlas=atlas;}}
    private Proof proof() {
        return new Proof(new WeakReference<>(client.player),new WeakReference<>(client.level),new WeakReference<>(client.getConnection()),area.get(),
                client.level.dimension().identifier().toString(),revision.getAsLong(),System.currentTimeMillis());
    }
    private boolean current(Proof p,long now,long ttl) {return p!=null&&client.player==p.player().get()&&client.level==p.world().get()&&client.getConnection()==p.connection().get()
            &&area.get()==p.area()&&client.level!=null&&client.level.dimension().identifier().toString().equals(p.dimension())&&revision.getAsLong()==p.revision()
            &&now>=p.at()&&now-p.at()<=ttl;}
    public void onServerMessage(String text) {
        if(text==null||atlas==null||client.player==null||client.level==null||client.getConnection()==null)return;
        String clean=text.replaceAll("§.","").strip();
        for(var s:atlas.shortcuts())if(s.kind()==TravelAtlas.Kind.FAST_WARP&&s.area()==area.get()&&s.dimension().equals(client.level.dimension().identifier().toString())) {
            if(clean.equals(s.grantReceipt()))permissions.put(s.permissionKey(),proof());
            if(clean.equals(s.rejectionReceipt()))permissions.remove(s.permissionKey());
        }
    }
    public void onActionBar(String text) {
        if(client.player==null||client.level==null||client.getConnection()==null)return;
        var parsed=FishingManaParser.parse(text);if(parsed.isPresent()){mana=parsed.getAsInt();manaProof=proof();}
        else {mana=-1;manaProof=null;}
    }
    public Snapshot snapshot() {
        long now=System.currentTimeMillis();var p=client.player;GameArea a=area.get();
        boolean valid=p!=null&&client.level!=null&&client.getConnection()!=null&&p.level()==client.level&&p.isAlive()&&!p.isInLava()&&!p.isPassenger()&&client.gui.screen()==null&&a!=null&&a.isSkyBlock();
        Set<String> allowed=new HashSet<>();for(var e:permissions.entrySet())if(current(e.getValue(),now,30000))allowed.add(e.getKey());
        boolean ether=false;if(p!=null){String token=config.get(TravelConfig.ETHERWARP_ABILITY_TOKEN).strip().toLowerCase(Locale.ROOT);
            for(int slot=0;slot<9&&!token.isEmpty();slot++){var lore=p.getInventory().getItem(slot).get(DataComponents.LORE);
                if(lore!=null&&lore.lines().stream().anyMatch(line->line.getString().replaceAll("§.","").toLowerCase(Locale.ROOT).contains(token)))ether=true;}}
        return new Snapshot(valid,p,client.level,client.getConnection(),a,client.level==null?"":client.level.dimension().identifier().toString(),revision.getAsLong(),
                new AutomaticTravelPolicy.Access(p!=null&&p.getAbilities().mayfly,ether,current(manaProof,now,5000)?mana:-1,allowed),now);
    }
}
