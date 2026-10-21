package dev.veltraph.client.combat.ghost;

import java.util.Objects;
import dev.veltraph.client.combat.ghost.GhostSourceOrderedStandingProvider.ParentAreaObservation;

/** Owned modern parent property corresponding to the source nullable area slot. No original runtime reads. */
public final class GhostSourceOwnedParentArea {
    public record Stamp(Object parent,long generation,long settingsRevision,Object player,Object world,Object connection){
        public Stamp {
            if(parent==null||player==null||world==null||connection==null||generation<0||settingsRevision<0)
                throw new IllegalArgumentException("Actual owned parent/generation/settings/PWC required");
        }
        boolean same(Stamp other){return other!=null&&parent==other.parent&&generation==other.generation
                &&settingsRevision==other.settingsRevision&&player==other.player&&world==other.world&&connection==other.connection;}
    }
    public final class Receipt {
        private final Stamp stamp;private final long revision;
        private final ParentAreaObservation observation;private final GhostSourceParentField field;
        private Receipt(Stamp stamp,long revision,ParentAreaObservation observation,GhostSourceParentField field){
            this.stamp=stamp;this.revision=revision;this.observation=observation;this.field=field;
        }
        public ParentAreaObservation observation(){return observation;}
        public boolean current(Stamp actual){return observation!=ParentAreaObservation.UNKNOWN&&revision==GhostSourceOwnedParentArea.this.revision
                &&stamp!=null&&stamp.same(actual)&&stamp.same(GhostSourceOwnedParentArea.this.stamp);}
        public double fieldValue(GhostSourceOrderedStandingProvider.Cell cell,Stamp actual){
            if(!current(actual)||observation!=ParentAreaObservation.PRESENT||cell==null)
                throw new IllegalStateException("Current PRESENT source field required; ABSENT has no field read");
            return field.at(cell.x(),cell.y(),cell.z());
        }
    }
    private final Object parent;
    private long revision;
    private Stamp stamp;
    private ParentAreaObservation observation=ParentAreaObservation.UNKNOWN;
    private GhostSourceParentField field;
    public GhostSourceOwnedParentArea(Object actualParent){parent=Objects.requireNonNull(actualParent);}
    /** Called by the actual owned Ghost construction/setup only after its source-mapped nullable slot is observed null. */
    public void observeOwnedConstructorAbsence(Stamp actual){
        invalidate();requireOwn(actual);stamp=actual;observation=ParentAreaObservation.ABSENT;
    }
    /** Configuration/parent/area changes revoke old receipts before decoding; failure leaves UNKNOWN. */
    public void observePresentResource(Stamp actual,byte[] observedBytes,GhostSourceParentAreaResource resource,int maximumValues){
        invalidate();requireOwn(actual);
        var decoded=GhostSourceParentFieldCodec.decodeObservedResource(observedBytes,resource,maximumValues);
        field=decoded;stamp=actual;observation=ParentAreaObservation.PRESENT;
    }
    public void invalidate(){revision++;stamp=null;field=null;observation=ParentAreaObservation.UNKNOWN;}
    public Receipt capture(Stamp actual){
        if(stamp==null||!stamp.same(actual))return new Receipt(actual,revision,ParentAreaObservation.UNKNOWN,null);
        return new Receipt(stamp,revision,observation,field);
    }
    private void requireOwn(Stamp actual){if(actual==null||actual.parent()!=parent)throw new IllegalArgumentException("Different owned parent");}
}
