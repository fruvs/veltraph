package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Bounded qualified ACK retention across ordinary controller retirement and later parent observation. */
public final class SharedMovementExpectedAckWindow {
    private static final int MAX=2;
    private record Entry(SharedMovementCoordinator.Lease owner,Object journey,SharedMovementCoordinator.Issue issue,
                         SharedMovementCoordinator.AppliedReceipt applied,Vec3 goal,double tolerance,long deadline) {}
    private final ArrayList<Entry> entries=new ArrayList<>();private final Set<Object> consumed=Collections.newSetFromMap(new IdentityHashMap<>());
    public boolean retain(SharedMovementCoordinator.Lease owner,Object journey,SharedMovementCoordinator.Issue issue,
                          SharedMovementCoordinator.AppliedReceipt applied,Vec3 goal,double tolerance,long deadline){
        if(owner==null||journey==null||issue==null||!issue.dispatched()||applied==null||!SharedMovementPolicy.finite(goal)
            ||!Double.isFinite(tolerance)||tolerance<=0||tolerance>.35||deadline<=issue.issuedAt()
            ||applied.token()!=issue.token()||applied.domain()!=issue.receiptDomain()||applied.serial()<=issue.baseline()
            ||applied.appliedAt()<issue.issuedAt()||applied.appliedAt()>=deadline
            ||applied.player()!=owner.player()||applied.world()!=owner.world()||applied.connection()!=owner.connection()
            ||!SharedMovementPolicy.finite(applied.actual())||applied.actual().distanceTo(goal)>tolerance)return false;
        for(var e:entries)if(e.issue().token()==issue.token())return e.issue().receiptDomain()==issue.receiptDomain()&&e.applied().serial()==applied.serial();
        if(entries.size()>=MAX)return false;
        entries.add(new Entry(owner,journey,issue,applied,goal,tolerance,deadline));return true;
    }
    /** latest serial is a veto against superseded packets, never an authorization source. */
    public boolean consume(SharedMovementCoordinator.Lease actual,Object journey,Vec3 actualPosition,long now,long latestSerial){
        if(actual==null||journey==null||!SharedMovementPolicy.finite(actualPosition))return false;
        for(var e:entries){
            if(e.owner()!=actual||e.journey()!=journey||consumed.contains(e.issue().token())||now<e.applied().appliedAt()
                ||now>=e.deadline()||latestSerial!=e.applied().serial()||actualPosition.distanceTo(e.applied().actual())>.05
                ||actualPosition.distanceTo(e.goal())>e.tolerance())continue;
            consumed.add(e.issue().token());return true;
        }
        return false;
    }
    public void clear(){entries.clear();consumed.clear();}
    public int size(){return entries.size();}
}
