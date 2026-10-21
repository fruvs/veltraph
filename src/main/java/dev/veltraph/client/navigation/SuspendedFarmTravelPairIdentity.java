package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;

/** Pure refusal model, not an issuing source or native permission. Only the concrete
 * bridge can produce its native observations from a private Root/Journey request. */
public final class SuspendedFarmTravelPairIdentity {
    private SuspendedFarmTravelPairIdentity() {}
    public record State(Object root,Object coordinator,Object header,Object checkpoint,
            Object farm,Object driver,Object journey,Object travel,Object child,
            Object player,Object world,Object connection,Object store,Object canonicalPoint,Vec3 target,
            SharedMovementPolicy.Purpose purpose,long serial,long journeySerial,
            boolean exactStoredPair,boolean sourceCurrent) {}
    public static boolean sameSource(State issued,State observed) {
        return valid(issued)&&valid(observed)
                &&issued.root()==observed.root()&&issued.coordinator()==observed.coordinator()
                &&issued.header()==observed.header()&&issued.checkpoint()==observed.checkpoint()
                &&issued.farm()==observed.farm()&&issued.driver()==observed.driver()
                &&issued.journey()==observed.journey()&&issued.travel()==observed.travel()
                &&issued.child()==observed.child()&&issued.player()==observed.player()
                &&issued.world()==observed.world()&&issued.connection()==observed.connection()
                &&issued.store()==observed.store()&&issued.canonicalPoint()==observed.canonicalPoint()&&issued.target().equals(observed.target())
                &&issued.purpose()==observed.purpose()&&issued.serial()==observed.serial()
                &&issued.journeySerial()==observed.journeySerial();
    }
    private static boolean valid(State s) {
        return s!=null&&s.root()!=null&&s.coordinator()!=null&&s.header()!=null&&s.checkpoint()!=null
                &&s.farm()!=null&&s.driver()!=null&&s.farm()!=s.driver()&&s.journey()!=null
                &&s.travel()!=null&&s.child()!=null&&s.player()!=null&&s.world()!=null
                &&s.connection()!=null&&s.store()!=null&&s.canonicalPoint()!=null&&SharedMovementPolicy.finite(s.target())
                &&s.purpose()==SharedMovementPolicy.Purpose.EXACT_RETURN&&s.serial()>0
                &&s.journeySerial()>0&&s.exactStoredPair()&&s.sourceCurrent();
    }
}
