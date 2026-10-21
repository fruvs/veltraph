package dev.veltraph.client.metrics;

import java.util.*;

/** Scope/selection/per-level source polling ledger, independent of exact earned XP and session counters. */
public final class SourceSlayerXpObserver {
    public enum Provenance { SOURCE_POLLED_TAB_WIDGET }
    public enum Availability { UNAVAILABLE, MAX, LEVEL_RESET, OBSERVED }
    public record Context(Object player,Object world,Object connection){
        public Context {Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(connection);}
        boolean same(Context o){return o!=null&&player==o.player&&world==o.world&&connection==o.connection;}
    }
    public record Snapshot(UUID generation,String selection,int level,long displayedCurrentXp,long displayedGoalXp,
                           long baselineDisplayedXp,long withinLevelObservedIncrease,boolean compactDisplay,
                           long lastPolledAt,long lastAcceptedAt,Provenance provenance) {}
    private Context context;private UUID generation;private SourceSlayerXpProjection.Widget selected;
    private Snapshot snapshot;private long baseline,lastNow=-1;private Availability availability=Availability.UNAVAILABLE;
    public void bind(Context next){if(context!=null&&context.same(next))return;context=Objects.requireNonNull(next);reset();}
    public void close(){context=null;reset();}
    private void reset(){selected=null;snapshot=null;generation=null;baseline=0;availability=Availability.UNAVAILABLE;lastNow=-1;}
    public void observe(Context current,List<String> rows,long now){
        if(context==null||!context.same(current)||now<0||now<lastNow)return;lastNow=now;
        var result=SourceSlayerXpProjection.tabRows(rows);
        if(result.state()!=SourceSlayerXpProjection.State.OBSERVED){
            selected=null;snapshot=null;generation=null;availability=result.state()==SourceSlayerXpProjection.State.MAX?Availability.MAX:Availability.UNAVAILABLE;return;
        }
        var widget=result.widget();
        if(selected==null||!selected.selection().equals(widget.selection())){baseline(widget,now);return;}
        if(widget.level()!=selected.level()){
            // Source clears and returns on a level increase. Also break continuity on a decreased level.
            selected=widget;generation=UUID.randomUUID();snapshot=null;availability=Availability.LEVEL_RESET;return;
        }
        if(snapshot==null){baseline(widget,now);return;}
        if(widget.currentXp()<=snapshot.displayedCurrentXp()){
            snapshot=new Snapshot(snapshot.generation(),snapshot.selection(),snapshot.level(),snapshot.displayedCurrentXp(),
                    widget.goalXp(),baseline,snapshot.withinLevelObservedIncrease(),snapshot.compactDisplay()||widget.compact(),now,snapshot.lastAcceptedAt(),Provenance.SOURCE_POLLED_TAB_WIDGET);return;
        }
        selected=widget;availability=Availability.OBSERVED;
        snapshot=new Snapshot(generation,widget.selection(),widget.level(),widget.currentXp(),widget.goalXp(),baseline,
                widget.currentXp()-baseline,widget.compact(),now,now,Provenance.SOURCE_POLLED_TAB_WIDGET);
    }
    private void baseline(SourceSlayerXpProjection.Widget widget,long now){
        selected=widget;generation=UUID.randomUUID();baseline=widget.currentXp();availability=Availability.OBSERVED;
        snapshot=new Snapshot(generation,widget.selection(),widget.level(),widget.currentXp(),widget.goalXp(),baseline,0,widget.compact(),now,now,Provenance.SOURCE_POLLED_TAB_WIDGET);
    }
    public Optional<Snapshot> snapshot(){return Optional.ofNullable(snapshot);}
    public Availability availability(){return availability;}
}
