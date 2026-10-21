package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** One source-selected ordinary command, qualified by native submit-return and applied position. */
public final class AutomaticCommandReceipt {
    private AutomaticCommandReceipt() {}
    public record Issue(LegacyTravelPublicationIdentity.State context,TravelAtlas.Shortcut shortcut,
            long nonce,long baseline,long issuedAt,long deadline) {
        public Issue {
            Objects.requireNonNull(context);Objects.requireNonNull(shortcut);
            if(!valid(context)||shortcut.kind()!=TravelAtlas.Kind.FAST_WARP||nonce<=0||baseline<0||issuedAt<0||deadline<=issuedAt)
                throw new IllegalArgumentException("Invalid actual automatic command issue");
        }
        public String command(){return "warp "+shortcut.warpDestination();}
    }
    public record Applied(Issue issue,long serial,long receivedAt,Vec3 position) {}
    public static boolean valid(LegacyTravelPublicationIdentity.State s){return s!=null&&s.root()!=null&&s.parent()!=null
        &&s.inputOwner()==s.parent()&&s.cameraAuthority()==s.parent()&&s.player()!=null&&s.world()!=null&&s.connection()!=null
        &&s.config()!=null&&s.service()!=null&&s.activeLeg()!=null&&s.revision()>=0&&s.journey()>0&&s.requestGeneration()>=0
        &&s.epoch()>=0&&s.publicationSerial()>=0&&s.registered()&&s.enabled()&&s.usable();}
    public static boolean same(LegacyTravelPublicationIdentity.State a,LegacyTravelPublicationIdentity.State b){return valid(a)&&valid(b)
        &&a.root()==b.root()&&a.parent()==b.parent()&&a.inputOwner()==b.inputOwner()&&a.cameraAuthority()==b.cameraAuthority()
        &&a.player()==b.player()&&a.world()==b.world()&&a.connection()==b.connection()&&a.config()==b.config()&&a.revision()==b.revision()
        &&a.journey()==b.journey()&&a.service()==b.service()&&a.requestGeneration()==b.requestGeneration()&&a.epoch()==b.epoch()
        &&a.publicationSerial()==b.publicationSerial()&&a.activeLeg()==b.activeLeg();}
    /** A new leg may inherit only the exact closed transaction's still-current native state. */
    public static boolean continuation(LegacyTravelPublicationIdentity.State before,LegacyTravelPublicationIdentity.State next){
        return before!=null&&valid(next)&&before.activeLeg()==null&&before.root()==next.root()&&before.parent()==next.parent()
            &&before.inputOwner()==next.inputOwner()&&before.cameraAuthority()==next.cameraAuthority()
            &&before.player()==next.player()&&before.world()==next.world()&&before.connection()==next.connection()
            &&before.config()==next.config()&&before.revision()==next.revision()&&before.service()==next.service()
            &&before.requestGeneration()==next.requestGeneration()&&before.epoch()==next.epoch()&&before.publicationSerial()==next.publicationSerial()
            &&before.registered()&&before.enabled()&&before.usable();
    }
    /** Source geometry/access callbacks finish before the final exact native context is read. No sender. */
    public static boolean readyToSend(Issue issue,BooleanSupplier sourceReads,Supplier<LegacyTravelPublicationIdentity.State> finalContext){
        return issue!=null&&sourceReads!=null&&finalContext!=null&&sourceReads.getAsBoolean()&&same(issue.context(),finalContext.get());
    }
    public static final class Submission {
        private final Issue issue;private boolean window,attempted,submitted,returned,revoked;private long submittedAt;private Applied applied;
        public Submission(Issue issue){this.issue=Objects.requireNonNull(issue);}
        public void open(){if(!attempted&&!revoked)window=true;}
        /** Once the ordinary native send is invoked, an unproved outcome must never be retried. */
        public void attempted(){if(window&&!revoked)attempted=true;else revoke();}
        public boolean canSubmit(LegacyTravelPublicationIdentity.State actual,String command,long now){return window&&attempted&&!submitted&&!returned&&!revoked&&same(issue.context(),actual)&&issue.command().equals(command)&&now>=issue.issuedAt()&&now<issue.deadline();}
        public void close(){window=false;}
        public boolean acceptingInvocation(){return window&&attempted&&!revoked;}
        public boolean submitted(LegacyTravelPublicationIdentity.State actual,String command,long now){
            if(!window||!attempted||submitted||revoked||!same(issue.context(),actual)||!issue.command().equals(command)
                ||now<issue.issuedAt()||now>=issue.deadline()){revoke();return false;}
            submitted=true;submittedAt=now;return true;
        }
        public void returnedNormally(){if(submitted&&!revoked)returned=true;else revoke();}
        public boolean attemptedSend(){return attempted;}
        public boolean dispatched(){return submitted&&returned&&!revoked;}
        public void observe(LegacyTravelPublicationIdentity.State actual,long serial,long receivedAt,Vec3 position){
            if(!dispatched()||!same(issue.context(),actual)||serial<=issue.baseline()||receivedAt<submittedAt||receivedAt>=issue.deadline()
                ||position==null||!Double.isFinite(position.x())||!Double.isFinite(position.y())||!Double.isFinite(position.z())
                ||position.distanceTo(issue.shortcut().arrival())>=.9){revoke();return;}
            if(applied==null)applied=new Applied(issue,serial,receivedAt,position);
        }
        public void revoke(){revoked=true;window=false;}
        public Applied applied(){return dispatched()?applied:null;}
        public boolean revoked(){return revoked;}
    }
}
