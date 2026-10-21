package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.Objects;

/** Per-session opaque native invocation identity. This core observes; it cannot send or apply packets. */
public final class AutomaticCommandInvocationReceipt {
    private final Object session;private final AutomaticCommandReceipt.Issue issue;private final AutomaticCommandReceipt.Submission proof;
    private Invocation sending;
    public AutomaticCommandInvocationReceipt(Object session,AutomaticCommandReceipt.Issue issue,AutomaticCommandReceipt.Submission proof){this.session=Objects.requireNonNull(session);this.issue=Objects.requireNonNull(issue);this.proof=Objects.requireNonNull(proof);}
    public static final class Invocation {
        private final AutomaticCommandInvocationReceipt owner;private final Object transport,packet;
        private final LegacyTravelPublicationIdentity.State context;private final boolean position;private final String command;
        private boolean finished;
        private Invocation(AutomaticCommandInvocationReceipt owner,Object transport,Object packet,LegacyTravelPublicationIdentity.State context,boolean position,String command){this.owner=owner;this.transport=transport;this.packet=packet;this.context=context;this.position=position;this.command=command;}
    }
    public Invocation sendEntry(Object currentSession,Object transport,Object packet,LegacyTravelPublicationIdentity.State context,String command,long now){
        if(currentSession!=session||!proof.acceptingInvocation())return null;
        if(transport==null||packet==null||sending!=null||!proof.canSubmit(context,command,now)){proof.revoke();return null;}
        sending=new Invocation(this,transport,packet,context,false,command);return sending;
    }
    public Invocation positionEntry(Object currentSession,Object listener,Object packet,LegacyTravelPublicationIdentity.State context){
        if(currentSession!=session||!proof.dispatched())return null;
        if(listener==null||packet==null||!AutomaticCommandReceipt.same(issue.context(),context)){proof.revoke();return null;}
        return new Invocation(this,listener,packet,context,true,null);
    }
    private boolean own(Invocation invocation,Object currentSession){return invocation!=null&&invocation.owner==this&&currentSession==session;}
    public boolean sendReturned(Invocation invocation,Object currentSession,Object transport,Object packet,LegacyTravelPublicationIdentity.State context,long now){
        if(!own(invocation,currentSession))return false;
        if(invocation.finished||invocation.position||invocation.transport!=transport||invocation.packet!=packet||sending!=invocation||!AutomaticCommandReceipt.same(invocation.context,context)){failed(invocation,currentSession);return false;}
        invocation.finished=true;sending=null;return proof.submitted(context,invocation.command,now);
    }
    public boolean positionReturned(Invocation invocation,Object currentSession,Object listener,Object packet,LegacyTravelPublicationIdentity.State context,long serial,long now,Vec3 position){
        if(!own(invocation,currentSession))return false;
        if(invocation.finished||!invocation.position||invocation.transport!=listener||invocation.packet!=packet||!AutomaticCommandReceipt.same(invocation.context,context)){failed(invocation,currentSession);return false;}
        invocation.finished=true;proof.observe(context,serial,now,position);return proof.applied()!=null;
    }
    /** An old normal/throw/observer-failure callback never touches a replacement session. */
    public void failed(Invocation invocation,Object currentSession){
        if(!own(invocation,currentSession))return;invocation.finished=true;if(sending==invocation)sending=null;proof.revoke();
    }
}
