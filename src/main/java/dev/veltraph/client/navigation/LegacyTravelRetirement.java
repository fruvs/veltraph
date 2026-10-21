package dev.veltraph.client.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Pure cleanup accounting. This kernel cannot release a key or cancel a camera by itself. */
public final class LegacyTravelRetirement {
    private LegacyTravelRetirement() {}
    public enum Key { FORWARD,BACK,LEFT,RIGHT,JUMP,SNEAK,SPRINT,ATTACK,USE }
    public enum Result { NOT_OWNED,NOT_ATTEMPTED,CONFIRMED,FAILED }
    public enum Status { CLEAN,MISMATCH,REVOKED }
    public enum Removal { REMOVED_OWN,ALREADY_ABSENT,FOREIGN_PRESERVED,NOT_RETIRED }
    public interface Effects {
        boolean keysPublished();boolean cameraPublishedOrQueued();
        boolean activeLegMatches();boolean cleanupFence();
        boolean release(Key key);boolean cancelCamera();Removal retireLogical();
    }
    public record RetirementOutcome(Status status,boolean keysRequired,List<Result> keys,
            boolean cameraRequired,Result camera,boolean initialFence,boolean postFence,Removal removal) {
        public RetirementOutcome {
            Objects.requireNonNull(status);Objects.requireNonNull(camera);Objects.requireNonNull(removal);
            keys=List.copyOf(keys);if(keys.size()!=Key.values().length)throw new IllegalArgumentException("Nine key outcomes required");
        }
        public boolean logicalRetired(){return removal!=Removal.NOT_RETIRED;}
        public boolean allNineReleased(){return status==Status.CLEAN&&keysRequired&&postFence&&keys.stream().allMatch(r->r==Result.CONFIRMED);}
        public boolean cameraCancelled(){return status==Status.CLEAN&&cameraRequired&&postFence&&camera==Result.CONFIRMED;}
        public boolean cleanSuccess(){return status==Status.CLEAN&&initialFence&&postFence&&logicalRetired()
                &&(!keysRequired||allNineReleased())&&(!cameraRequired||cameraCancelled());}
        /** CLEAN helper-diagnostic failures and foreign revocation cannot qualify a parent disable. */
        public boolean localFailStopCandidate(){return status==Status.MISMATCH&&removal==Removal.REMOVED_OWN;}
        /** Diagnostic requiring exact local fail-stop assessment, never broad or foreign release. */
        public boolean hasUnconfirmedOwnedKeys(){return keysRequired&&!allNineReleased();}
        /** Compatibility diagnostic only; Root must qualify its local parent proof before fail-stop. */
        @Deprecated public boolean requiresGlobalStop(){return hasUnconfirmedOwnedKeys();}
    }
    public static RetirementOutcome notRetired(boolean keysRequired,boolean cameraRequired){
        return new RetirementOutcome(Status.REVOKED,keysRequired,Collections.nCopies(Key.values().length,keysRequired?Result.NOT_ATTEMPTED:Result.NOT_OWNED),
                cameraRequired,cameraRequired?Result.NOT_ATTEMPTED:Result.NOT_OWNED,false,false,Removal.NOT_RETIRED);
    }
    public static RetirementOutcome retire(Effects effects){
        Objects.requireNonNull(effects);
        boolean keysRequired=effects.keysPublished(),cameraRequired=effects.cameraPublishedOrQueued();
        var keys=new ArrayList<Result>(Collections.nCopies(Key.values().length,keysRequired?Result.NOT_ATTEMPTED:Result.NOT_OWNED));
        Result camera=cameraRequired?Result.NOT_ATTEMPTED:Result.NOT_OWNED;
        boolean initialFence=fence(effects),postFence=false;Removal removal;
        try {
            if(initialFence&&keysRequired)for(var key:Key.values()){
                if(!fence(effects))break;
                boolean released;
                try {released=effects.release(key);}catch(RuntimeException refused){released=false;}
                // A callback may have changed generation after the actual mutation.
                boolean after=fence(effects);keys.set(key.ordinal(),released&&after?Result.CONFIRMED:Result.FAILED);
                if(!released||!after)break;
            }
            if(cameraRequired&&fence(effects)){
                boolean cancelled;
                try {cancelled=effects.cancelCamera();}catch(RuntimeException refused){cancelled=false;}
                camera=cancelled&&fence(effects)?Result.CONFIRMED:Result.FAILED;
            }
            postFence=fence(effects);
        }finally{
            // Native effect retires its own logical token even after partial cleanup or supersession.
            removal=effects.retireLogical();
        }
        boolean keysComplete=!keysRequired||keys.stream().allMatch(r->r==Result.CONFIRMED);
        boolean cameraComplete=!cameraRequired||camera==Result.CONFIRMED;
        Status status=removal==Removal.FOREIGN_PRESERVED||removal==Removal.ALREADY_ABSENT||removal==Removal.NOT_RETIRED?Status.REVOKED
                :initialFence&&postFence&&keysComplete&&cameraComplete?Status.CLEAN:Status.MISMATCH;
        return new RetirementOutcome(status,keysRequired,keys,cameraRequired,camera,initialFence,postFence,removal);
    }
    private static boolean fence(Effects effects){
        try {return effects.activeLegMatches()&&effects.cleanupFence();}catch(RuntimeException refused){return false;}
    }
}
