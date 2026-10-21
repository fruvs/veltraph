package dev.veltraph.client.combat.ghost;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Native read-only authority for an actually issued worker job, separate from committed target selection. */
public final class GhostSourceIssuedSearchJob {
    private GhostSourceIssuedSearchJob() {}
    public static final class Receipt {
        private final Minecraft client;private final GhostGrindModule parent;private final GhostSourceWalkJob job;
        private final McGhostSourceNativeCohortSnapshot cohort;private final Object child;
        private final GhostSourceOwnedParentArea.Stamp stamp;private final long cameraEpoch;
        private final GhostSourceJobCameraCustody cameraCustody;
        private final List<McGhostSourceNativeCohortSnapshot.Body> candidates;
        private Receipt(Minecraft client,GhostGrindModule parent,GhostSourceWalkJob job){
            this.client=client;this.parent=parent;this.job=job;cohort=job.issuedCohort();child=job.sourceChildIdentity();
            stamp=parent.sourceParentAreaStamp(parent).orElseThrow(()->new IllegalStateException("Actual native owned parent required"));
            cameraEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
            var authority=dev.veltraph.client.service.mc.McOwnedRotation.authority(client);
            if(authority!=parent)throw new IllegalStateException("Actual issued parent camera authority required");
            cameraCustody=new GhostSourceJobCameraCustody(authority,cameraEpoch);candidates=List.copyOf(job.candidateBodies());
        }
        public GhostSourceWalkJob job(){return job;}
        public long issuedCameraEpoch(){return cameraEpoch;}
        public GhostSourcePathJobTransaction.Scope scope(){return new GhostSourcePathJobTransaction.Scope(job,child,stamp);}
        public boolean contains(Entity target){return target!=null&&candidates.stream().anyMatch(b->b.entity()==target&&b.candidate().id().equals(target.getUUID()));}
        /** Persistent async callback scope. The initial cohort's tick-bound current() is deliberately absent. */
        public boolean scopeCurrent(){
            if(!client.isSameThread()||parent.sourceRegisteredWalkJob()!=job)return false;
            var actual=parent.sourceParentAreaStamp(parent).orElse(null);
            return actual!=null&&stamp.same(actual)&&parent.sourceNativeChildIdentity(parent)==child
                    &&cameraCustody.current(dev.veltraph.client.service.mc.McOwnedRotation.authority(client),
                            dev.veltraph.client.service.mc.McOwnedRotation.epoch(client));
        }
        /** Same issuance frame only, for client-thread geometry capture before the worker is submitted. */
        public boolean candidateFrameCurrent(Entity target){
            return scopeCurrent()&&cohort.current()&&contains(target)
                    &&dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)==cameraEpoch
                    &&target.level()==client.level&&client.level.getEntity(target.getId())==target;
        }
    }
    static Receipt observeIssuedJob(Minecraft client,GhostGrindModule parent,GhostSourceWalkJob job){
        if(client==null||!client.isSameThread()||parent==null||job==null||job.parent()!=parent||!job.issuedCohort().current()
                ||job.sourceChildIdentity()!=parent.sourceNativeChildIdentity(parent))
            throw new IllegalStateException("Actual typed issued worker and current native cohort required");
        var cohort=job.issuedCohort().observedBodies();
        if(job.candidateBodies().stream().anyMatch(b->cohort.stream().noneMatch(actual->actual==b)))
            throw new IllegalStateException("Worker candidate outside actual issued cohort");
        return new Receipt(client,parent,job);
    }
}
