package dev.veltraph.client.combat.ghost;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.game.GameArea;
import java.io.IOException;
import java.util.*;

/** Bounded local route variety. Original source enum has only WALK and ETHER. */
public final class GhostRouteCatalog {
    private GhostRouteCatalog() {}
    public static List<LocalRoute> load(LocalRouteStore store,String names,String dimension)throws IOException{
        if(names==null||names.isBlank())throw new IllegalArgumentException("Configure at least one local Ghost route");
        List<LocalRoute> result=new ArrayList<>();for(String name:names.split(",")){
            if(result.size()>=64)throw new IllegalArgumentException("At most 64 Ghost routes");var route=store.load(name.strip());
            if(route.area()!=GameArea.DWARVEN_MINES||!route.dimension().equals(dimension))throw new IllegalArgumentException("Ghost route must belong to this Dwarven Mines dimension");
            if(route.nodes().stream().anyMatch(n->n.kind()==LocalRoute.Kind.FLY))throw new IllegalArgumentException("Ghost source routes support WALK/ETHER only");result.add(route);
        }return List.copyOf(result);
    }
}
