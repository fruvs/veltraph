package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Optional;

/** Source developer area helper: fresh static nullable field, explicit setter/clear and case-insensitive first argument. */
public final class GhostSourceGraphDeveloperAreaOverride {
    private static final GhostSourceGraphDeveloperAreaOverride RUNTIME=new GhostSourceGraphDeveloperAreaOverride();
    private GhostSourceParentAreaResource selected;private long revision;
    public static GhostSourceGraphDeveloperAreaOverride runtime(){return RUNTIME;}
    public Optional<GhostSourceParentAreaResource> selected(){return Optional.ofNullable(selected);}
    public long revision(){return revision;}
    /** Only the mapped explicit command/setter may mutate this property. No per-query/world reset. */
    public void assignFromExplicitCommand(GhostSourceParentAreaResource actualSelection){selected=actualSelection;revision++;}
    public boolean applyExplicitArguments(List<String> arguments){
        java.util.Objects.requireNonNull(arguments);
        if(arguments.isEmpty()){assignFromExplicitCommand(null);return true;}
        for(var candidate:GhostSourceParentAreaResource.values())if(candidate.name().equalsIgnoreCase(arguments.getFirst())){
            assignFromExplicitCommand(candidate);return true;}
        return false; // Source invalid argument reports and retains the prior selection.
    }
}
