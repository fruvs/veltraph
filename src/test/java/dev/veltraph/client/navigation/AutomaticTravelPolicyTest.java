package dev.veltraph.client.navigation;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticTravelPolicyTest {
    private final Vec3 from=new Vec3(.5,70,.5),goal=new Vec3(26.5,70,.5);
    private final AutomaticTravelPolicy.Options all=new AutomaticTravelPolicy.Options(true,true,true,true,1,1,1);
    private TravelAtlas.Shortcut ether(String id,double cost) {return new TravelAtlas.Shortcut(id,TravelAtlas.Kind.ETHERWARP,GameArea.GARDEN,"minecraft:overworld",
            new Vec3(2.5,70,.5),new Vec3(20.5,70,.5),new BlockPos(20,69,0),Direction.UP,"","","","",cost,50);}
    private AutomaticTravelPolicy.Request request(){return new AutomaticTravelPolicy.Request(goal,GameArea.GARDEN,"minecraft:overworld",all);}
    private final AutomaticTravelPolicy.Scene scene=new AutomaticTravelPolicy.Scene(){
        public double distance(LocalRoute.Kind kind,Vec3 a,Vec3 b){return a.distanceTo(b)+(kind==LocalRoute.Kind.FLY?2.25:0);}
        public String shortcutRejection(TravelAtlas.Shortcut s,Vec3 from){return "";}
    };
    @Test void actualAdditivePrefixShortcutSuffixCostWinsWithoutImplicitFlight() {
        var p=AutomaticTravelPolicy.select(scene,request(),new TravelAtlas("test",List.of(ether("short",1))),new AutomaticTravelPolicy.Access(false,true,50,Set.of()),from,Set.of());
        assertTrue(p.found());assertEquals("short",p.shortcutId());assertEquals(9,p.cost(),1e-8);assertEquals(3,p.legs().size());
        assertEquals(LocalRoute.Kind.WALK,p.legs().getFirst().physical().kind());assertEquals(goal,p.legs().getLast().destination());
    }
    @Test void sourceAtlasOrderBreaksEqualCostTiesAndCostlyShortcutIsNotForced() {
        var p=AutomaticTravelPolicy.select(scene,request(),new TravelAtlas("test",List.of(ether("first",1),ether("second",1))),new AutomaticTravelPolicy.Access(false,true,50,Set.of()),from,Set.of());
        assertEquals("first",p.shortcutId());
        p=AutomaticTravelPolicy.select(scene,request(),new TravelAtlas("test",List.of(ether("slow",25))),new AutomaticTravelPolicy.Access(false,true,50,Set.of()),from,Set.of());
        assertEquals("",p.shortcutId());assertEquals(26,p.cost(),1e-8);
    }
    @Test void insufficientOrUnknownManaToolAndBlacklistedAttemptDenyShortcut() {
        for(var a:List.of(new AutomaticTravelPolicy.Access(false,true,49,Set.of()),new AutomaticTravelPolicy.Access(false,true,-1,Set.of()),new AutomaticTravelPolicy.Access(false,false,100,Set.of()))) {
            var p=AutomaticTravelPolicy.select(scene,request(),new TravelAtlas("test",List.of(ether("short",1))),a,from,Set.of());assertEquals("",p.shortcutId());assertFalse(p.denied().isEmpty());
        }
        assertEquals("",AutomaticTravelPolicy.select(scene,request(),new TravelAtlas("test",List.of(ether("short",1))),new AutomaticTravelPolicy.Access(false,true,50,Set.of()),from,Set.of("short")).shortcutId());
    }
    @Test void unprovenSuffixAndRayCannotAuthorizeUse() {
        var blocked=new AutomaticTravelPolicy.Scene(){public double distance(LocalRoute.Kind k,Vec3 a,Vec3 b){return a.x()>10?Double.POSITIVE_INFINITY:scene.distance(k,a,b);}public String shortcutRejection(TravelAtlas.Shortcut s,Vec3 from){return "";}};
        assertEquals("",AutomaticTravelPolicy.select(blocked,request(),new TravelAtlas("test",List.of(ether("short",1))),new AutomaticTravelPolicy.Access(false,true,50,Set.of()),from,Set.of()).shortcutId());
        var ray=new AutomaticTravelPolicy.Scene(){public double distance(LocalRoute.Kind k,Vec3 a,Vec3 b){return scene.distance(k,a,b);}public String shortcutRejection(TravelAtlas.Shortcut s,Vec3 from){return "Blocked face";}};
        assertEquals("",AutomaticTravelPolicy.select(ray,request(),new TravelAtlas("test",List.of(ether("short",1))),new AutomaticTravelPolicy.Access(false,true,50,Set.of()),from,Set.of()).shortcutId());
    }
    @Test void rankedWarpRequiresAnObservedPermissionAndValidatedPhysicalLegs() {
        var warp=new TravelAtlas.Shortcut("ranked",TravelAtlas.Kind.FAST_WARP,GameArea.GARDEN,"minecraft:overworld",from,new Vec3(20.5,70,.5),null,null,"express","rank:express","Express unlocked","Express rejected",1,0);
        var atlas=new TravelAtlas("rank",List.of(warp));
        assertEquals("",AutomaticTravelPolicy.select(scene,request(),atlas,new AutomaticTravelPolicy.Access(false,false,-1,Set.of()),from,Set.of()).shortcutId());
        assertEquals("ranked",AutomaticTravelPolicy.select(scene,request(),atlas,new AutomaticTravelPolicy.Access(false,false,-1,Set.of("rank:express")),from,Set.of()).shortcutId());
    }
    @Test void onlyActualFlightCapabilityAllowsViableFlightCandidate() {
        var air=new AutomaticTravelPolicy.Scene(){public double distance(LocalRoute.Kind k,Vec3 a,Vec3 b){return k==LocalRoute.Kind.FLY?10:Double.POSITIVE_INFINITY;}public String shortcutRejection(TravelAtlas.Shortcut s,Vec3 from){return "";}};
        assertFalse(AutomaticTravelPolicy.select(air,request(),new TravelAtlas("empty",List.of()),new AutomaticTravelPolicy.Access(false,false,-1,Set.of()),from,Set.of()).found());
        var p=AutomaticTravelPolicy.select(air,request(),new TravelAtlas("empty",List.of()),new AutomaticTravelPolicy.Access(true,false,-1,Set.of()),from,Set.of());assertEquals(LocalRoute.Kind.FLY,p.legs().getFirst().physical().kind());
    }
    @Test void dataRejectsInjectedCommandsUnknownCostsAndDuplicateProviderIdentity() {
        assertThrows(IllegalArgumentException.class,()->new TravelAtlas("dup",List.of(ether("x",1),ether("x",2))));
        assertThrows(IllegalArgumentException.class,()->ether("x",Double.NaN));
        assertThrows(IllegalArgumentException.class,()->new TravelAtlas.Shortcut("ranked",TravelAtlas.Kind.FAST_WARP,GameArea.GARDEN,"minecraft:overworld",from,goal,null,null,"express; op @a","rank","grant","deny",1,0));
    }
}
