package dev.veltraph.client.metrics.mc;

import dev.veltraph.client.metrics.SourceSlayerXpObserver;
import dev.veltraph.client.mixin.PlayerListHudAccessor;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Explicit source polling observation. Native tab cache edits cannot become trusted XP receipts. */
public final class McSourceSlayerXpObserver {
    private final Minecraft client;private final SourceSlayerXpObserver observer=new SourceSlayerXpObserver();
    private SourceSlayerXpObserver.Context context;
    public McSourceSlayerXpObserver(Minecraft client){this.client=Objects.requireNonNull(client);}
    private SourceSlayerXpObserver.Context current(){return client.player==null||client.level==null||client.getConnection()==null?null:
            new SourceSlayerXpObserver.Context(client.player,client.level,client.getConnection());}
    private boolean same(SourceSlayerXpObserver.Context value){return context!=null&&value!=null&&context.player()==value.player()&&context.world()==value.world()&&context.connection()==value.connection();}
    public void poll(long now){
        if(!client.isSameThread())return;var nativeScope=current();if(nativeScope==null){close();return;}
        if(!same(nativeScope)){context=nativeScope;observer.bind(context);}
        if(!(client.gui.hud.getTabList() instanceof PlayerListHudAccessor rows)){observer.observe(context,List.of(),now);return;}
        var observed=rows.veltraph$getPlayerInfos().stream().map(info->info.getTabListDisplayName()).filter(Objects::nonNull).map(component->component.getString()).toList();
        observer.observe(context,observed,now);
    }
    public Optional<SourceSlayerXpObserver.Snapshot> snapshot(){return client.isSameThread()&&same(current())?observer.snapshot():Optional.empty();}
    public SourceSlayerXpObserver.Availability availability(){return client.isSameThread()&&same(current())?observer.availability():SourceSlayerXpObserver.Availability.UNAVAILABLE;}
    public void close(){context=null;observer.close();}
}
