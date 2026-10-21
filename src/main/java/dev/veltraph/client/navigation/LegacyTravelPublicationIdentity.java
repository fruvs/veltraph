package dev.veltraph.client.navigation;

/** Pure identity kernel. Receipts never manufacture Root, scene, or input permission. */
public final class LegacyTravelPublicationIdentity {
    private LegacyTravelPublicationIdentity() {}
    public record State(Object root,Object parent,Object inputOwner,Object cameraAuthority,
            Object player,Object world,Object connection,Object config,long revision,long journey,
            Object service,long requestGeneration,long epoch,long publicationSerial,Object activeLeg,
            boolean registered,boolean enabled,boolean usable) {}
    public record Publication(State before,State after,long actualTicket) {}
    public static final class Lease {
        private final State captured;
        private long generation,epoch,ticket,serial;
        private boolean keys,sourceQueued,inputAttempted,cameraAttempted,sourceAttempted,interactionIssued,closed,guiCleanup;
        private Lease(State captured){this.captured=captured;generation=captured.requestGeneration();epoch=captured.epoch();serial=captured.publicationSerial();}
        public static Lease capture(State s){return validInitial(s)?new Lease(s):null;}
        /** Pure evidence only: native admission supplies a privately captured provider Binding, never a caller opaque owner. */
        public static Lease admitMaintenance(State before,State after,Object candidate,MaintenanceMovementAuthorityFrame frame,long versionBefore,long versionAfter){
            if(frame==null||before==null||after==null||candidate==null||versionBefore<0||versionBefore!=versionAfter
                    ||before.activeLeg()!=null||after.activeLeg()!=null||!maintenanceInitial(before,frame)||!maintenanceInitial(after,frame))return null;
            var next=new Lease(withLeg(before,candidate));return next.current(withLeg(after,candidate))?next:null;
        }
        public static Lease admitMaintenanceAfter(State before,State after,Object candidate,MaintenanceMovementAuthorityFrame frame,
                State predecessorBefore,State predecessorAfter,long retiredVersion,long versionBefore,long versionAfter){
            if(retiredVersion<0||retiredVersion==Long.MAX_VALUE||retiredVersion!=versionBefore||retiredVersion!=versionAfter
                ||predecessorBefore==null||predecessorAfter==null||predecessorBefore.activeLeg()!=null||predecessorAfter.activeLeg()!=null
                ||!maintenanceInitial(predecessorBefore,frame)||!maintenanceInitial(predecessorAfter,frame)
                ||!sameHandoff(predecessorBefore,predecessorAfter)||predecessorBefore.journey()!=predecessorAfter.journey()
                ||!sameHandoff(predecessorBefore,before)||!sameHandoff(predecessorAfter,after))return null;
            return admitMaintenance(before,after,candidate,frame,versionBefore,versionAfter);
        }
        private static boolean maintenanceInitial(State s,MaintenanceMovementAuthorityFrame frame){
            var pair=frame.pair();var ctx=frame.context();
            return validFields(s)&&s.parent()==pair.helper()&&s.inputOwner()==pair.helper()&&s.cameraAuthority()==pair.token()
                &&s.player()==ctx.player()&&s.world()==ctx.world()&&s.connection()==ctx.connection()&&s.config()==ctx.configStore();
        }
        /** Failed/reentrant admission cannot replace an old active lease or its cleanup rights. */
        public static Lease admit(State before,State after,Object candidate,long versionBefore,long versionAfter){
            if(before==null||after==null||candidate==null||versionBefore<0||versionBefore!=versionAfter
                    ||before.activeLeg()!=null||after.activeLeg()!=null)return null;
            var next=capture(withLeg(before,candidate));return next!=null&&next.current(withLeg(after,candidate))?next:null;
        }
        public static Lease admitExpected(State before,State after,Object candidate,Object expectedParent,long versionBefore,long versionAfter){
            if(expectedParent==null||before==null||after==null||before.parent()!=expectedParent||after.parent()!=expectedParent
                    ||before.inputOwner()!=expectedParent||after.inputOwner()!=expectedParent||before.cameraAuthority()!=expectedParent||after.cameraAuthority()!=expectedParent)return null;
            return admit(before,after,candidate,versionBefore,versionAfter);
        }
        /** Native caller supplies its actual closed predecessor; a no-effect admission ABA still revokes handoff. */
        public static Lease admitAfter(State before,State after,Object candidate,State predecessorBefore,State predecessorAfter,
                long retiredVersion,long versionBefore,long versionAfter){
            if(retiredVersion<0||retiredVersion==Long.MAX_VALUE||retiredVersion!=versionBefore||retiredVersion!=versionAfter
                    ||predecessorBefore==null||predecessorAfter==null||predecessorBefore.activeLeg()!=null||predecessorAfter.activeLeg()!=null
                    ||!validContinuation(predecessorBefore)||!validContinuation(predecessorAfter)
                    ||!sameHandoff(predecessorBefore,predecessorAfter)||predecessorBefore.journey()!=predecessorAfter.journey()
                    ||!sameHandoff(predecessorBefore,before)||!sameHandoff(predecessorAfter,after))return null;
            return admit(before,after,candidate,versionBefore,versionAfter);
        }
        private static boolean validContinuation(State s){return s.registered()&&s.enabled()&&s.usable()&&s.parent()!=null
                &&s.inputOwner()==s.parent()&&s.cameraAuthority()==s.parent();}
        private static boolean sameHandoff(State a,State b){return b!=null&&a.root()==b.root()&&a.parent()==b.parent()
                &&a.inputOwner()==b.inputOwner()&&a.cameraAuthority()==b.cameraAuthority()&&a.player()==b.player()&&a.world()==b.world()
                &&a.connection()==b.connection()&&a.config()==b.config()&&a.revision()==b.revision()&&a.service()==b.service()
                &&a.requestGeneration()==b.requestGeneration()&&a.epoch()==b.epoch()&&a.publicationSerial()==b.publicationSerial();}
        private static State withLeg(State s,Object leg){return new State(s.root(),s.parent(),s.inputOwner(),s.cameraAuthority(),s.player(),s.world(),s.connection(),s.config(),s.revision(),s.journey(),s.service(),s.requestGeneration(),s.epoch(),s.publicationSerial(),leg,s.registered(),s.enabled(),s.usable());}
        public boolean current(State live){return !closed&&!guiCleanup&&same(live,false)&&live.enabled()&&live.usable();}
        public boolean cleanupCurrent(State live){return !closed&&same(live,true);}
        private boolean same(State s,boolean cleanup){
            return s!=null&&s.registered()&&captured.root()==s.root()&&captured.parent()==s.parent()
                &&captured.player()==s.player()&&captured.world()==s.world()&&captured.connection()==s.connection()
                &&captured.config()==s.config()&&(cleanup||captured.revision()==s.revision())&&captured.journey()==s.journey()
                &&captured.service()==s.service()&&generation==s.requestGeneration()&&epoch==s.epoch()
                &&serial==s.publicationSerial()&&captured.activeLeg()==s.activeLeg()
                &&(s.inputOwner()==captured.parent()||cleanup&&!s.enabled()&&s.inputOwner()==null)
                &&(s.cameraAuthority()==captured.cameraAuthority()||cleanup&&(!s.enabled()||guiCleanup)&&s.cameraAuthority()==null);
        }
        /** Conservative actual-attempt accounting after a valid before guard; these flags adopt no live identity. */
        /** Native leaf attempt presence only; this never adopts a State, generation or authority. */
        public void inputNativeAttemptRecorded(){inputAttempted=true;}
        public boolean inputAttempted(State before){if(!current(before))return false;inputAttempted=true;return true;}
        public boolean cameraAttempted(State before,long actualTicket){if(actualTicket<=0||!current(before))return false;cameraAttempted=true;return true;}
        public boolean sourceAttempted(State before){if(!current(before))return false;sourceAttempted=true;return true;}
        /** Before and after belong to the very same synchronous native plan call. */
        public boolean published(Publication p){
            if(p==null||p.before()==null||p.after()==null||p.actualTicket()<=0||p.actualTicket()!=p.after().publicationSerial()||p.after().publicationSerial()!=serial+1||!current(p.before())||!sameExceptEpoch(p.before(),p.after())
                    ||!p.after().usable()||!p.after().enabled()||!p.after().registered()
                    ||p.after().inputOwner()!=captured.parent()||p.after().cameraAuthority()!=captured.cameraAuthority()
                    ||p.after().epoch()<p.before().epoch()||p.after().epoch()-p.before().epoch()>1)return false;
            epoch=p.after().epoch();serial=p.after().publicationSerial();ticket=p.actualTicket();return true;
        }
        /** A queued request gets only its own independent service generation; it grants no camera ticket. */
        public boolean queued(State before,State after){
            if(after==null||!current(before)||before.publicationSerial()!=after.publicationSerial()||!sameExceptGeneration(before,after)||after.requestGeneration()!=before.requestGeneration()+1
                    ||!after.enabled()||!after.usable())return false;
            generation=after.requestGeneration();ticket=0;sourceQueued=true;return true;
        }
        /** C3 has already certified this exact request's own publication transition. */
        public boolean refreshedSource(State before,State after,long ownedGeneration,long ownedEpoch,long actualSourceTicket){
            if(closed||!sourceQueued||before==null||after==null||ownedGeneration!=generation||ownedEpoch!=after.epoch()
                    ||before.requestGeneration()!=generation||before.epoch()!=epoch||before.publicationSerial()!=serial
                    ||actualSourceTicket<=0||actualSourceTicket!=after.publicationSerial()||after.publicationSerial()!=serial+1
                    ||!sameExceptEpoch(before,after)||!sameExceptEpoch(capturedWithCurrent(),before)
                    ||!after.registered()||!after.enabled()||!after.usable()
                    ||after.inputOwner()!=captured.parent()||after.cameraAuthority()!=captured.cameraAuthority()
                    ||after.epoch()<epoch||after.epoch()-epoch>1)return false;
            epoch=after.epoch();serial=after.publicationSerial();ticket=actualSourceTicket;return true;
        }
        /** Cleanup-only own END publication refresh; native adapter must certify exact original source receipt and Root role. */
        public boolean refreshedSourceCleanup(State after,long ownedGeneration,long ownedEpoch,long actualSourceTicket){
            if(closed||!sourceQueued||after==null||ownedGeneration!=generation||after.requestGeneration()!=generation||ownedEpoch!=after.epoch()
                ||actualSourceTicket<=0||actualSourceTicket!=after.publicationSerial()||after.publicationSerial()!=serial+1
                ||!after.registered()||after.inputOwner()!=captured.parent()||after.cameraAuthority()!=captured.cameraAuthority()
                ||after.epoch()<epoch||after.epoch()-epoch>1)return false;
            var normalized=new State(after.root(),after.parent(),after.inputOwner(),after.cameraAuthority(),after.player(),after.world(),after.connection(),after.config(),captured.revision(),after.journey(),after.service(),after.requestGeneration(),after.epoch(),after.publicationSerial(),after.activeLeg(),after.registered(),after.enabled(),after.usable());
            if(!sameExceptEpoch(capturedWithCurrent(),normalized))return false;
            epoch=after.epoch();serial=after.publicationSerial();ticket=actualSourceTicket;return true;
        }
        private State capturedWithCurrent(){return new State(captured.root(),captured.parent(),captured.parent(),captured.cameraAuthority(),captured.player(),captured.world(),captured.connection(),captured.config(),captured.revision(),captured.journey(),captured.service(),generation,epoch,serial,captured.activeLeg(),true,true,true);}
        public boolean inputPublished(State before,State after){if(!current(before)||!current(after))return false;keys=true;return true;}
        public boolean mayReleaseKeys(State live){return (keys||inputAttempted)&&cleanupCurrent(live);}
        public boolean mayCancelCamera(State live){return ticket>0&&cleanupCurrent(live);}
        /** Only the exact guarded cancellation just executed by this leg may refresh cleanup identity. */
        public boolean cancelled(State before,State after,boolean source){
            if((source?!sourceQueued:ticket<=0)||!cleanupCurrent(before)||!sameExceptCancellation(before,after,source)
                    ||!after.registered()||after.publicationSerial()!=serial||after.epoch()!=before.epoch()+1)return false;
            epoch=after.epoch();generation=after.requestGeneration();ticket=0;sourceQueued=false;cameraAttempted=sourceAttempted=false;return true;
        }
        /** Native facade requires an opaque exact-source GUI retirement and authentic role/header proof before this kernel update. */
        public boolean retiredGuiCleanup(State after,long originalGuiEpoch,long certifiedGuiEpoch,long actualSourceTicket){
            if(closed||guiCleanup||!sourceQueued||generation==Long.MAX_VALUE||after==null||actualSourceTicket<=0
                ||after.requestGeneration()!=generation+1||after.epoch()!=certifiedGuiEpoch||certifiedGuiEpoch<=originalGuiEpoch
                ||originalGuiEpoch<epoch||originalGuiEpoch-epoch>1||after.publicationSerial()!=actualSourceTicket
                ||(ticket>0?actualSourceTicket!=ticket||actualSourceTicket!=serial:actualSourceTicket!=serial+1)
                ||!sameGuiFields(after))return false;
            generation=after.requestGeneration();epoch=certifiedGuiEpoch;serial=actualSourceTicket;ticket=0;sourceQueued=false;
            cameraAttempted=sourceAttempted=false;guiCleanup=true;return true;
        }
        /** Only a privately minted descendant of the already consumed GUI cancel may update cleanup E. */
        public boolean advancedGuiCleanup(State after,long priorCertifiedEpoch,long certifiedEpoch){
            if(!guiCleanup||closed||after==null||epoch!=priorCertifiedEpoch||certifiedEpoch<priorCertifiedEpoch
                ||after.requestGeneration()!=generation||after.epoch()!=certifiedEpoch||after.publicationSerial()!=serial||!sameGuiFields(after))return false;
            epoch=certifiedEpoch;return true;
        }
        private boolean sameGuiFields(State s){return s.registered()&&s.enabled()&&!s.usable()&&s.root()==captured.root()&&s.parent()==captured.parent()
            &&s.inputOwner()==captured.parent()&&s.cameraAuthority()==null&&s.player()==captured.player()&&s.world()==captured.world()
            &&s.connection()==captured.connection()&&s.config()==captured.config()&&s.journey()==captured.journey()
            &&s.service()==captured.service()&&s.activeLeg()==captured.activeLeg();}
        public boolean guiCleanupOnly(){return guiCleanup;}
        public void closed(){closed=true;keys=false;sourceQueued=false;inputAttempted=cameraAttempted=sourceAttempted=false;ticket=0;}
        /** Read-only closed-leg fail-stop proof; no revision/epoch/serial or permission is adopted. */
        public boolean ownsRetiredParent(State live,boolean soleParent,long retiredVersion,long liveVersion){
            if(guiCleanup||!closed||!soleParent||retiredVersion<0||retiredVersion!=liveVersion||live==null||live.activeLeg()!=null
                    ||!live.enabled()||live.inputOwner()!=captured.parent()||live.cameraAuthority()!=captured.cameraAuthority())return false;
            return same(withLeg(live,captured.activeLeg()),true);
        }
        /** Cleanup restitution alone may observe the captured disabled parent with no replacement authority. */
        public boolean ownsRetiredCleanupParent(State live,boolean soleParentOrNoReplacement,long retiredVersion,long liveVersion){
            if(guiCleanup||!closed||!soleParentOrNoReplacement||retiredVersion<0||retiredVersion!=liveVersion||live==null||live.activeLeg()!=null)return false;
            boolean authority=live.enabled()?live.inputOwner()==captured.parent()&&live.cameraAuthority()==captured.cameraAuthority()
                    :live.inputOwner()==null&&(live.cameraAuthority()==captured.cameraAuthority()||live.cameraAuthority()==null);
            return authority&&same(withLeg(live,captured.activeLeg()),true);
        }
        /** Continuation preserves original policy/scene permission, unlike cleanup-only local fail-stop. */
        public boolean ownsRetiredContinuation(State live,boolean soleParent,long retiredVersion,long liveVersion){
            return ownsRetiredParent(live,soleParent,retiredVersion,liveVersion)&&live.usable()&&live.revision()==captured.revision();
        }
        public boolean consumeRetiredInteraction(State live,boolean soleParent,long retiredVersion,long liveVersion){
            if(interactionIssued||!ownsRetiredContinuation(live,soleParent,retiredVersion,liveVersion))return false;
            interactionIssued=true;return true;
        }
        public boolean keysPublished(){return keys||inputAttempted;}public boolean cameraPublishedOrQueued(){return ticket>0||sourceQueued||cameraAttempted||sourceAttempted;}
        public long serial(){return serial;}public long epoch(){return epoch;}public long generation(){return generation;}public long ticket(){return ticket;}
    }
    private static boolean validFields(State s){return s!=null&&s.root()!=null&&s.parent()!=null&&s.player()!=null&&s.world()!=null&&s.connection()!=null
        &&s.config()!=null&&s.service()!=null&&s.revision()>=0&&s.journey()>0&&s.requestGeneration()>=0&&s.epoch()>=0&&s.publicationSerial()>=0
        &&s.registered()&&s.enabled()&&s.usable();}
    private static boolean validInitial(State s){return validFields(s)&&s.activeLeg()!=null&&s.inputOwner()==s.parent()&&s.cameraAuthority()==s.parent();}
    private static boolean sameExceptEpoch(State a,State b){return b!=null&&a.root()==b.root()&&a.parent()==b.parent()&&a.inputOwner()==b.inputOwner()&&a.cameraAuthority()==b.cameraAuthority()
        &&a.player()==b.player()&&a.world()==b.world()&&a.connection()==b.connection()&&a.config()==b.config()&&a.revision()==b.revision()&&a.journey()==b.journey()
        &&a.service()==b.service()&&a.requestGeneration()==b.requestGeneration()&&a.activeLeg()==b.activeLeg();}
    private static boolean sameExceptGeneration(State a,State b){return b!=null&&a.epoch()==b.epoch()&&sameExceptEpoch(a,new State(b.root(),b.parent(),b.inputOwner(),b.cameraAuthority(),b.player(),b.world(),b.connection(),b.config(),b.revision(),b.journey(),b.service(),a.requestGeneration(),b.epoch(),b.publicationSerial(),b.activeLeg(),b.registered(),b.enabled(),b.usable()));}
    private static boolean sameExceptCancellation(State a,State b,boolean source){return b!=null&&b.requestGeneration()==a.requestGeneration()+(source?1:0)
        &&sameExceptEpoch(a,new State(b.root(),b.parent(),b.inputOwner(),b.cameraAuthority(),b.player(),b.world(),b.connection(),b.config(),a.revision(),b.journey(),b.service(),a.requestGeneration(),b.epoch(),b.publicationSerial(),b.activeLeg(),b.registered(),b.enabled(),b.usable()));}
}
