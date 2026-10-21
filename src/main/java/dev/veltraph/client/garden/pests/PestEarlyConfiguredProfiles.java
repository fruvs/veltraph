package dev.veltraph.client.garden.pests;

import dev.veltraph.client.garden.loadout.NamedLoadoutFlow;
import dev.veltraph.client.gui.MenuSnapshot;
import java.util.Optional;

/** Immutable configured data only. A complete pair never grants Root, pause, loadout or gear authority. */
public final class PestEarlyConfiguredProfiles {
    private PestEarlyConfiguredProfiles(){}
    public record Pair(NamedLoadoutFlow.Profile main,NamedLoadoutFlow.Profile alt){}
    /** Both source roles must be resolvable before taking native gear history or reserving the farm. */
    public static Optional<Pair> read(NamedLoadoutFlow.Settings settings,String main,String alt){
        if(settings==null||main==null||alt==null||main.isBlank()||alt.isBlank()
                ||!main.equals(MenuSnapshot.clean(main))||!alt.equals(MenuSnapshot.clean(alt))||main.equalsIgnoreCase(alt))return Optional.empty();
        NamedLoadoutFlow.Profile original=null,pest=null;
        for(var profile:settings.profiles()){
            if(profile.name().equals(main)){if(original!=null)return Optional.empty();original=profile;}
            if(profile.name().equals(alt)){if(pest!=null)return Optional.empty();pest=profile;}
        }
        return original==null||pest==null?Optional.empty():Optional.of(new Pair(original,pest));
    }
}
