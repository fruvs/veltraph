package dev.veltraph.client.garden.plotcleaner;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;

/** Source-shaped desk transaction. A click never acknowledges its own success. */
public final class PlotCleanerWorkflow {
    public enum Phase { PREFLIGHT,FLY_INTO_AIR,OPEN_DESK,OPEN_PLOT_CONFIGURATION,SELECT_PLOT,MAKE_SUPERFLAT,CONFIRM_SUPERFLAT,WAIT,ROW_DELETE,BREAK_DIRT,RETURN,COMPLETE,FAILED,CANCELLED }
    public record Click(MenuSnapshot menu,int slot) {}
    private final PlotCleanerPlan plan;private final PlotCleanerBindings b;
    private Phase phase=Phase.PREFLIGHT;private String reason="Preflight";
    private MenuSnapshot lastClicked;private List<String> beforeProgress=List.of();private boolean commandSent;private long confirmationAt;
    public PlotCleanerWorkflow(PlotCleanerPlan p,PlotCleanerBindings b){plan=p;this.b=b;}
    public void preflightComplete(){require(Phase.PREFLIGHT);phase=Phase.FLY_INTO_AIR;}
    public void arrivedInAir(){require(Phase.FLY_INTO_AIR);phase=Phase.OPEN_DESK;}
    public boolean needDeskCommand(){return phase==Phase.OPEN_DESK&&!commandSent;}
    public void commandSent(){commandSent=true;}
    public Optional<Click> menu(MenuSnapshot m){
        if(m==null)return Optional.empty();
        if(lastClicked!=null&&m.equals(lastClicked))return Optional.empty();
        String expected=switch(phase){case OPEN_DESK->b.desk();case OPEN_PLOT_CONFIGURATION,SELECT_PLOT->b.plots();case MAKE_SUPERFLAT->b.plotTitle();case CONFIRM_SUPERFLAT->b.confirmTitle();default->null;};
        if(expected==null)return Optional.empty();
        if(!m.title().equals(b.bind(expected,plan)))return Optional.empty();
        if(phase==Phase.OPEN_DESK)phase=Phase.OPEN_PLOT_CONFIGURATION;
        String label=switch(phase){case OPEN_PLOT_CONFIGURATION->b.configure();case SELECT_PLOT->b.plotName();case MAKE_SUPERFLAT->b.superflat();case CONFIRM_SUPERFLAT->b.confirm();default->"";};
        var matches=m.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0&&MenuSnapshot.clean(s.name()).equals(b.bind(label,plan))).toList();
        if(matches.size()!=1){fail("Ambiguous or unavailable exact menu action");return Optional.empty();}
        var s=matches.getFirst();
        if(phase==Phase.OPEN_PLOT_CONFIGURATION&&!s.itemId().equals("minecraft:grass_block")){fail("Source plot configuration must be GRASS_BLOCK");return Optional.empty();}
        if(phase==Phase.SELECT_PLOT||phase==Phase.MAKE_SUPERFLAT||phase==Phase.CONFIRM_SUPERFLAT){
            if(!s.lore().contains(plan.plotMarker())||!s.lore().contains(plan.scopeMarker())){fail("Selected plot/bounds do not match the explicit request");return Optional.empty();}
        }
        if(phase==Phase.CONFIRM_SUPERFLAT&&!s.lore().contains(b.bind(b.readyLore(),plan))){fail("Confirmation is not ready");return Optional.empty();}
        return Optional.of(new Click(m,s.id()));
    }
    public void clicked(Click action,long now){clicked(action,now,List.of());}
    public void clicked(Click action,long now,List<String> rows){
        lastClicked=action.menu();
        phase=switch(phase){case OPEN_PLOT_CONFIGURATION->Phase.SELECT_PLOT;case SELECT_PLOT->Phase.MAKE_SUPERFLAT;case MAKE_SUPERFLAT->Phase.CONFIRM_SUPERFLAT;case CONFIRM_SUPERFLAT->Phase.WAIT;default->throw new IllegalStateException("Unexpected click");};
        if(phase==Phase.WAIT){confirmationAt=now;beforeProgress=signal(rows);}
        reason="Waiting for observed "+phase;
    }
    public boolean progress(List<String> rows,boolean screenClosed,long now){
        if(phase!=Phase.WAIT||!screenClosed||now<=confirmationAt||!rows.contains(plan.plotMarker())||signal(rows).equals(beforeProgress))return false;
        var values=new ArrayList<Double>();String prefix=b.bind(b.progress(),plan);
        for(String row:rows)if(row.startsWith(prefix)){try{double n=Double.parseDouble(row.substring(prefix.length()).trim().replace("%",""));if(Double.isFinite(n)&&n>=0&&n<=100)values.add(n);}catch(NumberFormatException ignored){}}
        if(values.size()!=1)return false;
        if(values.getFirst()>=90){phase=Phase.ROW_DELETE;reason="Source progress>=90; vanilla bounded row counterpart";return true;}return false;
    }
    private List<String> signal(List<String> rows){String prefix=b.bind(b.progress(),plan);return rows.stream().filter(r->r.equals(plan.plotMarker())||r.startsWith(prefix)).sorted().toList();}
    public void rowsComplete(){require(Phase.ROW_DELETE);phase=Phase.BREAK_DIRT;reason="Source dirt/grass cleanup above Y70";}
    public void dirtComplete(){require(Phase.BREAK_DIRT);phase=Phase.RETURN;}
    public void returned(){require(Phase.RETURN);phase=Phase.COMPLETE;reason="Physical return and cleanup complete";}
    public void fail(String s){phase=Phase.FAILED;reason=s;}
    public void cancel(String s){phase=Phase.CANCELLED;reason=s;}
    private void require(Phase p){if(phase!=p)throw new IllegalStateException("Expected "+p);}
    public Phase phase(){return phase;}public String reason(){return reason;}
    public boolean active(){return phase!=Phase.COMPLETE&&phase!=Phase.FAILED&&phase!=Phase.CANCELLED;}
}
