package dev.veltraph.client.navigation;

import dev.veltraph.client.rotation.DirectRotationPublicationIdentity;

/** Pure read-only parent interaction lineage. Native callers alone can supply opaque C7 receipts. */
public final class LegacyRetiredParentInteractionIdentity {
    private LegacyRetiredParentInteractionIdentity(){}
    public static final class Lease {
        private final LegacyTravelPublicationIdentity.State original;
        private final long admissionVersion,rotationRevision;
        private long generation,epoch,serial;
        private DirectRotationPublicationIdentity.Proof own;
        private boolean cancelled,cleanupOnly;
        private Lease(LegacyTravelPublicationIdentity.State original,long version,long rotationRevision){this.original=original;admissionVersion=version;this.rotationRevision=rotationRevision;generation=original.requestGeneration();epoch=original.epoch();serial=original.publicationSerial();}
        public static Lease capture(LegacyTravelPublicationIdentity.State s,long version,long rotationRevision){
            if(s==null||version<0||rotationRevision<0||s.activeLeg()!=null||s.root()==null||s.parent()==null||s.player()==null||s.world()==null||s.connection()==null
                    ||s.config()==null||s.service()==null||s.requestGeneration()<0||s.epoch()<0||s.publicationSerial()<0||s.revision()<0
                    ||!s.registered()||!s.enabled()||!s.usable()||s.inputOwner()!=s.parent()||s.cameraAuthority()!=s.parent())return null;
            return new Lease(s,version,rotationRevision);
        }
        private boolean scene(LegacyTravelPublicationIdentity.State s,boolean roster,long version,boolean cleanup){
            return s!=null&&roster&&version==admissionVersion&&s.activeLeg()==null&&s.registered()&&s.root()==original.root()&&s.parent()==original.parent()
                &&s.player()==original.player()&&s.world()==original.world()&&s.connection()==original.connection()&&s.config()==original.config()&&s.service()==original.service()
                &&s.journey()==original.journey()&&(cleanup||s.revision()==original.revision()&&s.enabled()&&s.usable())
                &&(s.inputOwner()==original.parent()||cleanup&&!s.enabled()&&s.inputOwner()==null)
                &&(s.cameraAuthority()==original.parent()||cleanup&&!s.enabled()&&s.cameraAuthority()==null);
        }
        public boolean current(LegacyTravelPublicationIdentity.State s,boolean sole,long version){return !cancelled&&!cleanupOnly&&scene(s,sole,version,false)&&control(s);}
        public boolean cleanupCurrent(LegacyTravelPublicationIdentity.State s,boolean roster,long version){return scene(s,roster,version,true)&&control(s);}
        /** C7 alone checks its in-progress owned generation span; this veto cannot grant camera authority. */
        public boolean publicationFence(LegacyTravelPublicationIdentity.State s,boolean sole,long version){return !cancelled&&!cleanupOnly&&scene(s,sole,version,false)&&s.epoch()==epoch&&s.publicationSerial()==serial;}
        private boolean control(LegacyTravelPublicationIdentity.State s){return s.requestGeneration()==generation&&s.epoch()==epoch&&s.publicationSerial()==serial;}
        private boolean scope(DirectRotationPublicationIdentity.Scope s,long g,long e,long t){return s!=null&&s.root()==original.root()&&s.parent()==original.parent()
            &&s.inputOwner()==original.parent()&&s.cameraOwner()==original.parent()&&s.player()==original.player()&&s.world()==original.world()&&s.connection()==original.connection()
            &&s.configStore()==original.config()&&s.service()==original.service()&&s.rotationRevision()==rotationRevision&&s.generation()==g&&s.epoch()==e&&s.publicationSerial()==t;}
        /** Initial same-tick retention may use only a real current C7 OWN publication matching the factory context. */
        public boolean seedOwn(DirectRotationPublicationIdentity.Proof p,boolean opaqueRetained){
            if(cancelled||cleanupOnly||p==null||!scope(p.after(),generation,epoch,serial))return false;
            boolean valid=DirectRotationPublicationIdentity.publication(p)||opaqueRetained&&p.mode()==DirectRotationPublicationIdentity.Mode.SAME_TICK_TRACK
                    &&DirectRotationPublicationIdentity.scene(p.before(),p.after())&&p.after().generation()-p.before().generation()==1
                    &&p.after().epoch()==p.before().epoch()&&p.after().publicationSerial()==p.before().publicationSerial()&&p.ticket()>0&&p.ticket()==serial;
            if(!valid)return false;own=p;return true;
        }
        public boolean advance(DirectRotationPublicationIdentity.Proof p,boolean newPublication,LegacyTravelPublicationIdentity.State live,boolean sole,long version){
            if(cancelled||cleanupOnly||p==null||!scene(live,sole,version,false)||!scope(p.before(),generation,epoch,serial))return false;
            boolean valid=newPublication?DirectRotationPublicationIdentity.publication(p)&&p.ticket()==serial+1:DirectRotationPublicationIdentity.retained(p,own);
            if(!valid||!scope(p.after(),live.requestGeneration(),live.epoch(),live.publicationSerial()))return false;
            generation=p.after().generation();epoch=p.after().epoch();serial=p.after().publicationSerial();own=p;return true;
        }
        /** Evidence predicate only: the native adapter requires an actual opaque C9 receipt and its exact old handle. */
        public boolean advanceCleanup(DirectRotationPublicationIdentity.Scope before,DirectRotationPublicationIdentity.Scope after,long ticket,
                LegacyTravelPublicationIdentity.State live,boolean roster,long version){
            if(cancelled||own==null||ticket!=own.ticket()||!scene(live,roster,version,true)
                    ||!DirectRotationPublicationIdentity.cancellation(own,before,after,!live.enabled())
                    ||before.generation()!=generation||before.epoch()!=epoch||before.publicationSerial()!=serial
                    ||!cleanupScope(after,live))return false;
            generation=after.generation();epoch=after.epoch();serial=after.publicationSerial();cancelled=true;return true;
        }
        private boolean cleanupOrigin(DirectRotationPublicationIdentity.Scope s,boolean absent){return s!=null&&s.root()==original.root()&&s.parent()==original.parent()
            &&(s.inputOwner()==original.parent()||absent&&s.inputOwner()==null)&&(s.cameraOwner()==original.parent()||absent&&s.cameraOwner()==null)
            &&s.player()==original.player()&&s.world()==original.world()&&s.connection()==original.connection()&&s.configStore()==original.config()&&s.service()==original.service();}
        private boolean cleanupScope(DirectRotationPublicationIdentity.Scope s,LegacyTravelPublicationIdentity.State live){
            return s!=null&&s.root()==original.root()&&s.parent()==original.parent()&&(s.inputOwner()==live.inputOwner()||!live.enabled()&&live.inputOwner()==null&&s.inputOwner()==original.parent())&&(s.cameraOwner()==live.cameraAuthority()||!live.enabled()&&live.cameraAuthority()==null&&s.cameraOwner()==original.parent())
                &&s.player()==original.player()&&s.world()==original.world()&&s.connection()==original.connection()&&s.configStore()==original.config()
                &&s.service()==original.service()&&s.generation()==live.requestGeneration()&&s.epoch()==live.epoch()&&s.publicationSerial()==live.publicationSerial();
        }
        /** Opaque native SourceAimOutcome is checked by the consumer; these public scopes alone never grant ownership. */
        public boolean advanceAimCleanup(DirectRotationPublicationIdentity.Scope before,DirectRotationPublicationIdentity.Scope after,
                DirectRotationPublicationIdentity.Proof publication,boolean metadataOnly,LegacyTravelPublicationIdentity.State live,boolean roster,long version){
            if(cancelled||cleanupOnly||!scene(live,roster,version,true)||before==null||after==null||before.generation()!=generation||before.epoch()!=epoch||before.publicationSerial()!=serial
                ||!cleanupOrigin(before,!live.enabled())
                ||!DirectRotationPublicationIdentity.cleanupScene(before,after,!live.enabled())||!cleanupScope(after,live))return false;
            if(metadataOnly){if(publication!=null||after.generation()<=generation||after.generation()-generation!=1||after.epoch()!=epoch||after.publicationSerial()!=serial)return false;}
            else {if(publication==null||!DirectRotationPublicationIdentity.publication(publication)||publication.mode()!=DirectRotationPublicationIdentity.Mode.AIM
                ||!DirectRotationPublicationIdentity.same(publication.before(),before)||!DirectRotationPublicationIdentity.same(publication.after(),after)||publication.ticket()<=0||serial==Long.MAX_VALUE||publication.ticket()!=serial+1||publication.ticket()!=after.publicationSerial())return false;
                own=publication;}
            generation=after.generation();epoch=after.epoch();serial=after.publicationSerial();cleanupOnly=true;return true;
        }
        public boolean advanceSourceAimCancellation(DirectRotationPublicationIdentity.Scope before,DirectRotationPublicationIdentity.Scope after,boolean nativeCancelled,long actualTicket,
                LegacyTravelPublicationIdentity.State live,boolean roster,long version){
            if(cancelled||!cleanupOnly||before==null||after==null||!scene(live,roster,version,true)||!cleanupOrigin(before,!live.enabled())||!DirectRotationPublicationIdentity.cleanupScene(before,after,!live.enabled())
                ||before.generation()!=generation||before.epoch()!=epoch||before.publicationSerial()!=serial||after.generation()<=generation||after.generation()-generation!=1||after.publicationSerial()!=serial||!cleanupScope(after,live))return false;
            if(nativeCancelled){if(own==null||actualTicket<=0||actualTicket!=own.ticket()||actualTicket!=serial||after.epoch()<=epoch||after.epoch()-epoch!=1)return false;}
            else if(own!=null||actualTicket!=-1||after.epoch()!=epoch)return false;
            generation=after.generation();epoch=after.epoch();cancelled=true;return true;
        }
        public boolean cleanupOnly(){return cleanupOnly;}
        public boolean cancelled(){return cancelled;}
        public long admissionVersion(){return admissionVersion;}public long generation(){return generation;}public long epoch(){return epoch;}public long serial(){return serial;}
    }
}
