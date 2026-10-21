package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.AutomaticCommandReceipt;
import dev.veltraph.client.navigation.AutomaticCommandInvocationReceipt;
import dev.veltraph.client.navigation.LegacyTravelPublicationIdentity;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

/** Passive exact-invocation command/position observations. No packet constructor, sender or pose mutator. */
public final class NativeAutomaticCommandReceipt {
    private static final class Session {
        final Minecraft client;final AutomaticCommandReceipt.Issue issue;final LegacyTravelPublicationLease.Leg leg;
        final AutomaticCommandReceipt.Submission proof;final AutomaticCommandInvocationReceipt invocations;
        Session(Minecraft c,AutomaticCommandReceipt.Issue i,LegacyTravelPublicationLease.Leg l){client=c;issue=i;leg=l;proof=new AutomaticCommandReceipt.Submission(i);invocations=new AutomaticCommandInvocationReceipt(this,i,proof);}
    }
    /** Minted only by the native send entry adapter; caller cannot construct or retarget this handle. */
    public static final class SendInvocation {
        private final Session session;private final Connection transport;private final Packet<?> packet;private final AutomaticCommandInvocationReceipt.Invocation invocation;
        private SendInvocation(Session s,Connection t,Packet<?> p,AutomaticCommandInvocationReceipt.Invocation i){session=s;transport=t;packet=p;invocation=i;}
    }
    /** Actual receive entry also captures PWC, even when no issued command is eligible at entry. */
    public static final class PositionInvocation {
        private final Minecraft client;private final Object listener,packet,player,world;private final Session session;private final AutomaticCommandInvocationReceipt.Invocation invocation;
        private PositionInvocation(Minecraft c,Object l,Object p,Object player,Object world,Session s,AutomaticCommandInvocationReceipt.Invocation i){client=c;listener=l;packet=p;this.player=player;this.world=world;session=s;invocation=i;}
    }
    private static Session active;private static long serial;
    private NativeAutomaticCommandReceipt() {}
    public static long baseline(Minecraft c){return c!=null&&c.isSameThread()?serial:-1;}
    public static boolean arm(Minecraft c,AutomaticCommandReceipt.Issue issue,LegacyTravelPublicationLease.Leg leg){
        if(c==null||!c.isSameThread()||active!=null||issue==null||leg==null||!nativeContext(c,issue.context())||!AutomaticCommandReceipt.same(issue.context(),leg.commandContext()))return false;
        active=new Session(c,issue,leg);return true;
    }
    public static void open(AutomaticCommandReceipt.Issue issue){if(same(issue))active.proof.open();}
    /** Last native-context gate before the ordinary send begins; a revoked observation window cannot issue. */
    public static boolean attempted(AutomaticCommandReceipt.Issue issue){
        if(!same(issue))return false;var a=active;
        var context=a.leg.commandContext();if(active!=a)return false;
        long now=System.currentTimeMillis();var p=a.client.player;
        boolean entrance=p!=null&&new Vec3(p.getX(),p.getY(),p.getZ()).distanceTo(a.issue.shortcut().entrance())<=.4;
        if(!AutomaticCommandReceipt.same(a.issue.context(),context)||!nativeContext(a.client,context)||serial!=a.issue.baseline()||!entrance||now<a.issue.issuedAt()||now>=a.issue.deadline()){a.proof.revoke();return false;}
        a.proof.attempted();return a.proof.attemptedSend()&&!a.proof.revoked();
    }
    public static void close(AutomaticCommandReceipt.Issue issue){if(same(issue))active.proof.close();}
    public static void returnedNormally(AutomaticCommandReceipt.Issue issue){if(same(issue))active.proof.returnedNormally();}
    public static boolean attemptedSend(AutomaticCommandReceipt.Issue issue){return same(issue)&&active.proof.attemptedSend();}
    public static boolean submitted(AutomaticCommandReceipt.Issue issue){return same(issue)&&active.proof.dispatched();}
    public static SendInvocation sendEntry(Connection transport,Packet<?> packet){
        var a=active;if(a==null||!a.proof.acceptingInvocation())return null;
        try{
            if(!a.client.isSameThread()||!(packet instanceof ServerboundChatCommandPacket command))return null;
            var listener=a.client.getConnection();if(listener==null||listener.getConnection()!=transport)return null;
            var actual=a.leg.commandContext();if(active!=a)return null;
            if(!nativeContext(a.client,actual)){failSession(a);return null;}
            var invocation=a.invocations.sendEntry(a,transport,packet,actual,command.command(),System.currentTimeMillis());
            return invocation==null?null:new SendInvocation(a,transport,packet,invocation);
        }catch(Throwable observationFailure){failSession(a);return null;}
    }
    public static void sendExited(SendInvocation entry,Connection transport,Packet<?> packet,boolean normal){
        if(entry==null)return;var a=entry.session;
        if(active!=a||!a.client.isSameThread())return;
        try{
            if(!normal){a.invocations.failed(entry.invocation,a);return;}
            if(entry.transport!=transport||entry.packet!=packet){a.invocations.failed(entry.invocation,a);return;}
            var actual=a.leg.commandContext();if(active!=a)return;
            if(!nativeContext(a.client,actual)){a.invocations.failed(entry.invocation,a);return;}
            a.invocations.sendReturned(entry.invocation,a,transport,packet,actual,System.currentTimeMillis());
        }catch(Throwable observationFailure){sendObservationFailed(entry);}
    }
    public static void sendObservationFailed(SendInvocation entry){if(entry!=null&&active==entry.session&&entry.session.client.isSameThread())entry.session.invocations.failed(entry.invocation,entry.session);}
    public static PositionInvocation positionEntry(Minecraft c,Object listener,Object packet){
        var candidate=active;boolean dispatchedAtHead=candidate!=null&&candidate.proof.dispatched();
        try{
            if(c==null||!c.isSameThread()||c.player==null||c.level==null||c.getConnection()!=listener||c.player.level()!=c.level||c.player.isPassenger()||packet==null)return null;
            var player=c.player;var world=c.level;AutomaticCommandInvocationReceipt.Invocation invocation=null;
            if(dispatchedAtHead&&candidate.client==c){
                try{var actual=candidate.leg.commandContext();if(active==candidate&&nativeContext(c,actual))invocation=candidate.invocations.positionEntry(candidate,listener,packet,actual);}
                catch(Throwable observationFailure){failSession(candidate);}
            }
            return new PositionInvocation(c,listener,packet,player,world,candidate,invocation);
        }catch(Throwable observationFailure){failSession(candidate);return null;}
    }
    public static void positionExited(PositionInvocation entry,Object listener,Object packet,boolean normal){
        if(entry==null)return;var c=entry.client;
        if(!c.isSameThread())return;
        try{
            if(!normal){positionObservationFailed(entry);return;}
            if(listener!=entry.listener||packet!=entry.packet||c.player!=entry.player||c.level!=entry.world||c.getConnection()!=listener||c.player.level()!=c.level||c.player.isPassenger()){positionObservationFailed(entry);return;}
            // Journal remains independent of an active issue. Its serial is a freshness veto, never authority.
            long receivedSerial=++serial;
            var a=entry.session;if(a==null||active!=a||entry.invocation==null)return;
            var actual=a.leg.commandContext();if(active!=a)return;
            if(!nativeContext(c,actual)){positionObservationFailed(entry);return;}
            a.invocations.positionReturned(entry.invocation,a,listener,packet,actual,receivedSerial,System.currentTimeMillis(),new Vec3(c.player.getX(),c.player.getY(),c.player.getZ()));
        }catch(Throwable observationFailure){positionObservationFailed(entry);}
    }
    public static void positionObservationFailed(PositionInvocation entry){if(entry!=null&&entry.session!=null&&active==entry.session&&entry.client.isSameThread()){
        if(entry.invocation!=null)entry.session.invocations.failed(entry.invocation,entry.session);else entry.session.proof.revoke();
    }}
    private static void failSession(Session a){if(a!=null&&active==a&&a.client.isSameThread())a.proof.revoke();}
    public static AutomaticCommandReceipt.Applied applied(AutomaticCommandReceipt.Issue issue){return same(issue)?active.proof.applied():null;}
    public static boolean revoked(AutomaticCommandReceipt.Issue issue){return same(issue)&&active.proof.revoked();}
    public static void retire(AutomaticCommandReceipt.Issue issue){if(same(issue)){active.proof.revoke();active=null;}}
    private static boolean same(AutomaticCommandReceipt.Issue issue){return active!=null&&active.client.isSameThread()&&active.issue==issue;}
    private static boolean nativeContext(Minecraft c,LegacyTravelPublicationIdentity.State s){return s!=null&&c.isSameThread()&&c.player==s.player()&&c.level==s.world()&&c.getConnection()==s.connection();}
}
