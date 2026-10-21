package dev.veltraph.client.galatea.workflow;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.galatea.GalateaForagingModule;
import dev.veltraph.client.galatea.GalateaPlanner;
import dev.veltraph.client.galatea.trash.LushlilacTrashModule;
import dev.veltraph.client.game.GameContextService;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;

/** Registered source-parent workflows behind one Root ownership/activation surface.
 * Helpers always use Root's ordinary guarded activation; enabled=false never grants a resume. */
public final class McGalateaWorkflowFamily {
    private record Entry(Module helper,McGalateaWorkflowCoordinator coordinator){}
    private final Minecraft client;private final List<Entry> entries;
    private final GalateaSourceCommandModule reset,murkwater;private final LushlilacTrashModule trash;
    private BooleanSupplier external=()->false;
    public McGalateaWorkflowFamily(Minecraft client,FarmServices services,ConfigService config,
                                  ModuleManager modules,GameContextService context){
        this(client,services,config,modules,context,()->false);
    }
    /** Bind the priority callback during client initialization without inspecting native state. */
    public McGalateaWorkflowFamily(Minecraft client,FarmServices services,ConfigService config,
                                  ModuleManager modules,GameContextService context,BooleanSupplier external){
        this.client=Objects.requireNonNull(client);
        this.external=Objects.requireNonNull(external);
        var fig=parent(modules,"galatea_fig",GalateaPlanner.Kind.FIG);
        var mangrove=parent(modules,"galatea_mangrove",GalateaPlanner.Kind.MANGROVE);
        var material=parent(modules,"galatea_materials",GalateaPlanner.Kind.MATERIALS);
        if(!(modules.get("lushlilac_trash").orElse(null) instanceof LushlilacTrashModule existing))throw new IllegalStateException("Register the existing exact Lushlilac trash module first");
        if(modules.get("galatea_source_reset").isPresent()||modules.get("galatea_murkwater").isPresent())throw new IllegalStateException("Source workflow helper ids already registered");
        trash=existing;reset=new GalateaSourceCommandModule(GalateaSourceTravelCycle.Kind.RESET,client,config,services);
        murkwater=new GalateaSourceCommandModule(GalateaSourceTravelCycle.Kind.MURKWATER,client,config,services);
        var resetLease=new McGalateaWorkflowCoordinator(client,services,config,modules,context);
        var murkLease=new McGalateaWorkflowCoordinator(client,services,config,modules,context);
        var trashLease=new McGalateaWorkflowCoordinator(client,services,config,modules,context);
        resetLease.register(fig,reset);resetLease.register(mangrove,reset);resetLease.register(material,reset);
        murkLease.register(mangrove,murkwater);trashLease.register(material,trash);
        entries=List.of(new Entry(reset,resetLease),new Entry(murkwater,murkLease),new Entry(trash,trashLease));
        modules.register(reset);modules.register(murkwater);
        for(var entry:entries)entry.coordinator().externalControlOwned(()->external.getAsBoolean()||entries.stream().anyMatch(other->other!=entry&&other.coordinator().active()));
    }
    private static GalateaForagingModule parent(ModuleManager modules,String id,GalateaPlanner.Kind kind){
        if(!(modules.get(id).orElse(null) instanceof GalateaForagingModule parent)||parent.kind()!=kind)throw new IllegalStateException("Register exact "+id+" parent before source workflows");return parent;
    }
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Galatea workflows require client thread");}
    private Optional<Entry> running(){thread();return entries.stream().filter(e->e.coordinator().active()).findFirst();}
    public McGalateaWorkflowCoordinator coordinator(Module helper){thread();return entries.stream().filter(e->e.helper()==helper).findFirst().orElseThrow(()->new IllegalArgumentException("Unregistered workflow helper")).coordinator();}
    public List<Module> helpers(){return entries.stream().map(Entry::helper).toList();}
    public GalateaSourceCommandModule resetHelper(){return reset;}
    public GalateaSourceCommandModule murkwaterHelper(){return murkwater;}
    public LushlilacTrashModule trashHelper(){return trash;}
    public void externalControlOwned(BooleanSupplier supplier){thread();external=Objects.requireNonNull(supplier);}
    public boolean requestReset(){thread();return !active()&&coordinator(reset).requestReset();}
    public boolean activate(Module module,BooleanSupplier standardActivation){
        thread();for(var entry:entries)if(entry.helper()==module)return entry.coordinator().activate(module,standardActivation);
        return standardActivation.getAsBoolean();
    }
    /** Root calls before module ticks, retaining its normal guard, higher-priority blockers and activation transaction. */
    public void tick(Function<Module,Boolean> guardedActivation){
        thread();var active=running();if(active.isPresent()){active.get().coordinator().tick(()->false);return;}
        if(external.getAsBoolean())return;
        for(var entry:entries){entry.coordinator().tick(()->guardedActivation.apply(entry.helper()));if(entry.coordinator().active())return;}
    }
    public boolean permitsActivationConflict(Module running,Module starting){thread();return entries.stream().anyMatch(e->e.coordinator().permitsActivationConflict(running,starting));}
    public boolean permitsScreenForParent(Module module,String title){thread();return entries.stream().anyMatch(e->e.coordinator().permitsScreenForParent(module,title));}
    public Module effectiveInputOwner(){return running().map(e->e.coordinator().effectiveInputOwner()).orElse(null);}
    public String controlOwnerId(){return running().map(e->e.coordinator().controlOwnerId()).orElse(null);}
    public boolean consumeExpectedTeleport(Vec3 position,long now){thread();return entries.stream().anyMatch(e->e.coordinator().consumeExpectedTeleport(position,now));}
    public boolean ownsParent(Module module){thread();return entries.stream().anyMatch(e->e.coordinator().ownsParent(module));}
    public void beforeDisable(Module module){thread();entries.forEach(e->e.coordinator().beforeDisable(module));}
    public void cancelActive(String reason){thread();entries.forEach(e->e.coordinator().cancelActive(reason));}
    public boolean ownsControl(){return active();}
    public boolean active(){return running().isPresent();}
    public String status(){return running().map(e->e.helper().id()+" | "+e.coordinator().status()).orElse("Galatea source workflows: idle");}
}
