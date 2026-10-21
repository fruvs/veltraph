package dev.veltraph.client.observation.unexpected;

import java.util.*;
import java.util.function.Consumer;

/** Bounded local action attribution. An unexplained change never identifies who caused it. */
public final class UnexpectedBehaviorMonitor {
    public enum Kind { POSITION, ROTATION, GAMEMODE, SELECTED_SLOT, INVENTORY, BLOCK, CONTEXT, INPUT_STATE }
    public enum Classification { EXPLAINED, UNEXPECTED, UNCERTAIN }
    public enum Cause { MOD_INTENT, MOD_MOVEMENT_INPUT, NATIVE_KEY_EVENT, NATIVE_MOUSE_EVENT,
        NATIVE_CONTAINER_INPUT, NATIVE_NAVIGATION_COMMAND, FALLING_STATE, FLIGHT, VELOCITY_PACKET, DAMAGE_PACKET, EXPLOSION_PACKET, GRAVITY,
        COLLISION, FLUID, VEHICLE, PORTAL, JOIN_OR_RESPAWN, SERVER_POSITION_PACKET,
        SERVER_GAME_MODE_PACKET, SERVER_INVENTORY_PACKET, SERVER_BLOCK_PACKET, ROOT_ISSUED_FARM_RETURN }
    public record Activation(String owner, long generation, boolean active) {
        public Activation { if(owner==null||!owner.matches("[a-zA-Z0-9_.:-]{1,80}"))throw new IllegalArgumentException("Use a local owner identifier"); }
        public static Activation inactive() { return new Activation("observer",0,false); }
    }
    /** Opaque local epochs; no player names, addresses, account identifiers or chat. */
    public record Context(Activation activation,long player,long world,long connection) {
        public Context { Objects.requireNonNull(activation); }
        public boolean sameNativeContext(Context other) { return other!=null&&player==other.player&&world==other.world&&connection==other.connection; }
    }
    public record Value(double x,double y,double z,long code) {
        public Value { if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Nonfinite observation"); }
        public static Value number(long code) { return new Value(0,0,0,code); }
        public static Value position(double x,double y,double z) { return new Value(x,y,z,0); }
        public static Value rotation(double yaw,double pitch) { return new Value(yaw,pitch,0,0); }
    }
    public record Observation(Context context,Kind kind,Value before,Value after,long timeMillis) {
        public Observation { Objects.requireNonNull(context);Objects.requireNonNull(kind);Objects.requireNonNull(before);Objects.requireNonNull(after); }
    }
    public record Finding(long serial,Kind kind,Classification classification,Set<Cause> evidence,long timeMillis,String detail) {
        public Finding { evidence=Set.copyOf(evidence); }
    }
    private record Intent(long serial,Context context,Kind kind,Value target,double tolerance,long created,long expires,java.util.function.BooleanSupplier valid,boolean ownedPair) {}
    private record OwnedLanding(long receipt,Context context,Value position,Value rotation,long created,long expires,java.util.function.BooleanSupplier valid) {}
    private boolean ownedLandingPositionAcknowledged;
    private OwnedLanding ownedLanding;
    private record Signal(Context context,Kind kind,Cause cause,Value target,double tolerance,long created,long expires) {}
    private static final int MAX_INTENTS=64,MAX_SIGNALS=128,MAX_HISTORY=128;
    private final ArrayDeque<Intent> intents=new ArrayDeque<>();
    private final ArrayDeque<Signal> signals=new ArrayDeque<>();
    private final ArrayDeque<Finding> history=new ArrayDeque<>();
    private final Consumer<Finding> sink;
    private Context context;
    private long serial,lastTime=Long.MIN_VALUE;
    private int unexplainedCount;
    public UnexpectedBehaviorMonitor(Consumer<Finding> sink) { this.sink=Objects.requireNonNull(sink); }
    public void context(Context current,long now) {
        Objects.requireNonNull(current);clock(now);
        if(!current.equals(context)) {
            intents.clear();discardOwnedLanding();unexplainedCount=0;
            if(current.sameNativeContext(context)) {
                // Native UI intent belongs to this player/world/connection, independent of a mod owner.
                var navigation=signals.stream().filter(s->s.cause==Cause.NATIVE_NAVIGATION_COMMAND&&s.expires>=now)
                        .map(s->new Signal(current,s.kind,s.cause,s.target,s.tolerance,s.created,s.expires)).toList();
                signals.clear();signals.addAll(navigation);
            } else signals.clear();
            context=current;
        }
        expire(now);
    }
    /** Record before dispatch. No wildcard kinds, infinite windows, or cross-context acknowledgements. */
    public long intent(Context current,Kind kind,Value exactTarget,double tolerance,long now,long ttlMillis) {
        return intent(current,kind,exactTarget,tolerance,now,ttlMillis,()->true,false);
    }
    /** Revocable exact owner contract; predicate is checked on observation, never a packet wildcard. */
    public long intent(Context current,Kind kind,Value exactTarget,double tolerance,long now,long ttlMillis,java.util.function.BooleanSupplier valid) {
        return intent(current,kind,exactTarget,tolerance,now,ttlMillis,valid,false);
    }
    /** Only explicitly issued paired native landings may acknowledge their packet's view. */
    public long ownedLandingIntent(Context current,Value exactTarget,double tolerance,long now,long ttlMillis,java.util.function.BooleanSupplier valid){
        return intent(current,Kind.POSITION,exactTarget,tolerance,now,ttlMillis,valid,true);
    }
    private long intent(Context current,Kind kind,Value exactTarget,double tolerance,long now,long ttlMillis,java.util.function.BooleanSupplier valid,boolean ownedPair) {
        context(current,now);
        if(ttlMillis<1||ttlMillis>5000||!Double.isFinite(tolerance)||tolerance<0||tolerance>3)throw new IllegalArgumentException("Intent scope exceeds bounds");
        if((kind==Kind.ROTATION&&tolerance>2)||(kind!=Kind.POSITION&&kind!=Kind.ROTATION&&tolerance!=0))throw new IllegalArgumentException("Intent must match exact observed value");
        if(!current.activation.active())return -1;
        intents.removeIf(i->i.context.equals(current)&&i.kind==kind);
        if(kind==Kind.POSITION||kind==Kind.ROTATION)discardOwnedLanding();
        var entry=new Intent(++serial,current,Objects.requireNonNull(kind),Objects.requireNonNull(exactTarget),tolerance,now,now+ttlMillis,Objects.requireNonNull(valid),ownedPair);
        boundedAdd(intents,entry,MAX_INTENTS);return entry.serial;
    }
    public void revokeIntent(long receipt){intents.removeIf(intent->intent.serial==receipt);if(ownedLanding!=null&&ownedLanding.receipt==receipt){discardOwnedLanding();}}
    /** Called only after vanilla applied one actual native position packet. Binds its view to its exact owned landing. */
    public void ownedLandingPacket(Context current,Value position,Value rotation,long now){
        context(current,now);discardOwnedLanding();
        intents.removeIf(intent->intent.ownedPair&&!intentValid(intent));
        for(var intent:intents)if(intent.ownedPair&&intent.context.equals(current)&&intent.kind==Kind.POSITION&&intent.created<=now&&intentValid(intent)&&matches(intent,position)){
            ownedLanding=new OwnedLanding(intent.serial,current,position,rotation,now,Math.min(intent.expires,now+150),intent.valid);return;
        }
    }
    private static boolean landingValid(OwnedLanding landing){try{return landing.valid.getAsBoolean();}catch(RuntimeException unavailable){return false;}}
    private static boolean intentValid(Intent intent){try{return intent.valid.getAsBoolean();}catch(RuntimeException unavailable){return false;}}
    /** Signals are narrowly typed and finite. A server packet proves transport, not intent. */
    public void signal(Context current,Kind kind,Cause cause,long now,long ttlMillis) {
        context(current,now);
        if(ttlMillis<1||ttlMillis>(cause==Cause.NATIVE_NAVIGATION_COMMAND?5000:2000)||cause==Cause.MOD_INTENT)throw new IllegalArgumentException("Invalid evidence window");
        if(kind==Kind.ROTATION&&cause==Cause.SERVER_POSITION_PACKET)discardOwnedLanding();
        if((kind==Kind.ROTATION&&cause==Cause.SERVER_POSITION_PACKET)
                ||(kind==Kind.SELECTED_SLOT&&cause==Cause.SERVER_INVENTORY_PACKET)) {
            // The native adapter reports at TAIL, after vanilla applied the server view/held slot. It supersedes
            // older local writes/receipts, even if dispatches share one clock millisecond.
            // A later local write can record fresh intent; POSITION landing contracts stay separate.
            intents.removeIf(i->i.context.equals(current)&&i.kind==kind);
            signals.removeIf(s->s.context.equals(current)&&s.kind==kind&&s.target!=null);
        }
        if(cause==Cause.NATIVE_NAVIGATION_COMMAND) {if(kind!=Kind.POSITION)throw new IllegalArgumentException("Navigation correlation is position-only");signals.removeIf(s->s.cause==cause);}
        boundedAdd(signals,new Signal(current,Objects.requireNonNull(kind),Objects.requireNonNull(cause),null,0,now,now+ttlMillis),MAX_SIGNALS);
    }
    /** Revocation of stale persistent key history; never removes server, physics or target receipts. */
    void retireHeldMovementSignals(Cause cause){
        if(cause!=Cause.MOD_MOVEMENT_INPUT&&cause!=Cause.NATIVE_KEY_EVENT&&cause!=Cause.NATIVE_MOUSE_EVENT)throw new IllegalArgumentException("Held movement origin required");
        signals.removeIf(s->s.kind==Kind.POSITION&&s.cause==cause&&s.target==null);
    }
    /** Native UI receipts must match the actual applied outcome; mere proximity to input is insufficient. */
    public void nativeReceipt(Context current,Kind kind,Cause cause,Value target,double tolerance,long now,long ttlMillis) {
        if(cause!=Cause.NATIVE_MOUSE_EVENT&&cause!=Cause.NATIVE_KEY_EVENT&&cause!=Cause.NATIVE_NAVIGATION_COMMAND)throw new IllegalArgumentException("Native UI origin required");
        if(cause==Cause.NATIVE_NAVIGATION_COMMAND&&kind!=Kind.POSITION)throw new IllegalArgumentException("Navigation receipt is position-only");
        if(kind!=Kind.ROTATION&&kind!=Kind.SELECTED_SLOT&&kind!=Kind.INPUT_STATE&&!(kind==Kind.POSITION&&cause==Cause.NATIVE_NAVIGATION_COMMAND))throw new IllegalArgumentException("Unsupported native receipt");
        if(ttlMillis<1||ttlMillis>(cause==Cause.NATIVE_NAVIGATION_COMMAND?5000:250)||tolerance<0||tolerance>(cause==Cause.NATIVE_NAVIGATION_COMMAND?.5:.01)||!Double.isFinite(tolerance))throw new IllegalArgumentException("Native receipt exceeds bounds");
        context(current,now);if(cause==Cause.NATIVE_NAVIGATION_COMMAND)signals.removeIf(s->s.cause==cause);boundedAdd(signals,new Signal(current,kind,cause,Objects.requireNonNull(target),tolerance,now,now+ttlMillis),MAX_SIGNALS);
    }
    private boolean matchingNative(Observation event,Cause cause) {
        for(var iterator=signals.iterator();iterator.hasNext();) {
            var s=iterator.next();
            if(s.context.equals(event.context)&&s.kind==event.kind&&s.cause==cause&&s.target!=null&&s.created<=event.timeMillis&&
                    (event.kind==Kind.ROTATION?rotationDistance(s.target,event.after)<=s.tolerance:event.kind==Kind.POSITION?distance(s.target,event.after)<=s.tolerance:s.target.equals(event.after))) {
                iterator.remove();return true;
            }
        }
        return false;
    }
    /** Package-only port: the native adapter supplies an already consumed exact transition, never a permission. */
    interface IssuedReturnObservation { Observation consume(); default boolean pairedView(){return false;} }
    /** One already matched native event; no signal, intent window, or retained permission is installed. */
    Finding observeIssuedReturn(IssuedReturnObservation exact) {
        var event=exact==null?null:exact.consume();
        if(event==null)throw new IllegalStateException("Issued return observation already consumed");
        clock(event.timeMillis);expire(event.timeMillis);
        if(!event.context.equals(context)||(event.kind!=Kind.CONTEXT&&event.kind!=Kind.POSITION
                &&!(event.kind==Kind.ROTATION&&exact.pairedView())))return observe(event);
        return publish(event,Classification.EXPLAINED,Set.of(Cause.ROOT_ISSUED_FARM_RETURN),"One exact Root-issued native farm return transition acknowledged");
    }
    public Finding observe(Observation event) {
        if(context==null)context(event.context,event.timeMillis);
        clock(event.timeMillis);expire(event.timeMillis);
        if(!event.context.equals(context)) {
            return publish(event,Classification.UNCERTAIN,Set.of(),"Observation crossed activation or native context; no intent attribution");
        }
        intents.removeIf(i->!intentValid(i));
        var evidence=EnumSet.noneOf(Cause.class);
        for(var signal:signals)if(signal.context.equals(event.context)&&signal.kind==event.kind&&signal.created<=event.timeMillis)evidence.add(signal.cause);
        for(var iterator=intents.iterator();iterator.hasNext();) {
            var intent=iterator.next();
            if(intent.context.equals(event.context)&&intent.kind==event.kind&&intent.created<=event.timeMillis&&matches(intent,event.after)) {
                iterator.remove();evidence.add(Cause.MOD_INTENT);
                if(event.kind==Kind.POSITION&&ownedLanding!=null&&ownedLanding.receipt==intent.serial&&ownedLanding.context.equals(event.context)&&distance(ownedLanding.position,event.after)<=.35)ownedLandingPositionAcknowledged=true;
                return publish(event,Classification.EXPLAINED,evidence,"One exact scoped mod intent acknowledged");
            }
        }
        if(event.kind==Kind.ROTATION&&ownedLanding!=null&&(!ownedLandingPositionAcknowledged||rotationDistance(ownedLanding.rotation,event.after)>.01))discardOwnedLanding();
        if(event.kind==Kind.POSITION&&ownedLanding!=null&&distance(ownedLanding.position,event.after)>.35)discardOwnedLanding();
        if(event.kind==Kind.ROTATION&&ownedLanding!=null&&ownedLandingPositionAcknowledged&&landingValid(ownedLanding)&&ownedLanding.context.equals(event.context)
                &&ownedLanding.created<=event.timeMillis&&event.timeMillis<=ownedLanding.expires
                &&rotationDistance(ownedLanding.rotation,event.after)<=.01&&evidence.contains(Cause.SERVER_POSITION_PACKET)){
            discardOwnedLanding();evidence.add(Cause.MOD_INTENT);
            return publish(event,Classification.EXPLAINED,evidence,"View from the same exact owned native landing packet acknowledged once");
        }
        if(event.kind==Kind.CONTEXT&&evidence.contains(Cause.JOIN_OR_RESPAWN))return publish(event,Classification.UNCERTAIN,evidence,"Native join or respawn lifecycle observed; requested lifecycle is unknown");
        if(event.kind==Kind.ROTATION&&!evidence.contains(Cause.SERVER_POSITION_PACKET)&&matchingNative(event,Cause.NATIVE_MOUSE_EVENT))return publish(event,Classification.EXPLAINED,evidence,"Native mouse UI event observed; human identity is unknown");
        if(event.kind==Kind.SELECTED_SLOT&&!evidence.contains(Cause.SERVER_INVENTORY_PACKET)&&(matchingNative(event,Cause.NATIVE_KEY_EVENT)||matchingNative(event,Cause.NATIVE_MOUSE_EVENT)))return publish(event,Classification.EXPLAINED,evidence,"Native hotbar UI event observed; human identity is unknown");
        if(event.kind==Kind.INPUT_STATE&&(matchingNative(event,Cause.NATIVE_KEY_EVENT)||matchingNative(event,Cause.NATIVE_MOUSE_EVENT)))return publish(event,Classification.EXPLAINED,evidence,"Native gameplay input callback observed; human identity is unknown");
        double distance=distance(event.before,event.after);
        if(event.kind==Kind.POSITION&&evidence.contains(Cause.SERVER_POSITION_PACKET)&&matchingNative(event,Cause.NATIVE_NAVIGATION_COMMAND))
            return publish(event,Classification.EXPLAINED,evidence,"One exact native navigation target acknowledged; human identity is unknown");
        if(event.kind==Kind.POSITION&&!evidence.contains(Cause.SERVER_POSITION_PACKET)&&signals.stream().anyMatch(s->s.kind==Kind.POSITION&&s.cause==Cause.NATIVE_NAVIGATION_COMMAND&&s.target!=null&&distance(s.target,event.after)<=s.tolerance))
            return publish(event,Classification.UNCERTAIN,evidence,"Native navigation target aligns, but a server acknowledgment is not observed");
        if(event.kind==Kind.POSITION&&distance>3) {
            for(var iterator=signals.iterator();iterator.hasNext();) {
                var signal=iterator.next();
                if(signal.kind==Kind.POSITION&&signal.cause==Cause.NATIVE_NAVIGATION_COMMAND&&signal.target==null) {
                    iterator.remove();return publish(event,Classification.UNCERTAIN,evidence,"Native navigation submission preceded one relocation; destination and cause remain unknown");
                }
            }
        }
        if(event.kind==Kind.POSITION&&distance>3&&!evidence.contains(Cause.SERVER_POSITION_PACKET)&&physicsContext(evidence))
            return publish(event,Classification.UNCERTAIN,evidence,"Large motion accompanied by observed physics; exact displacement cannot be attributed");
        if(event.kind==Kind.POSITION&&!evidence.contains(Cause.SERVER_POSITION_PACKET)&&distance<=3&&positionCause(evidence))return publish(event,Classification.EXPLAINED,evidence,"Bounded displacement with observed movement or physics evidence");
        if((event.kind==Kind.POSITION&&distance>3)||(event.kind==Kind.ROTATION&&rotationDistance(event.before,event.after)>25)||event.kind==Kind.GAMEMODE||event.kind==Kind.SELECTED_SLOT) {
            return publish(event,event.context.activation.active()?Classification.UNEXPECTED:Classification.UNCERTAIN,evidence,
                    "Discontinuity has no matching mod or native UI intent; source identity and server motive are unknown");
        }
        return publish(event,Classification.UNCERTAIN,evidence,"Change observed with incomplete causal evidence");
    }
    private Finding publish(Observation event,Classification status,Set<Cause> causes,String detail) {
        var finding=new Finding(++serial,event.kind,status,causes,event.timeMillis,detail);boundedAdd(history,finding,MAX_HISTORY);
        if(status==Classification.UNEXPECTED)unexplainedCount=Math.min(1_000_000,unexplainedCount+1);
        sink.accept(finding);return finding;
    }
    private static boolean physicsContext(Set<Cause> evidence) {
        return evidence.stream().anyMatch(c->switch(c) {case VELOCITY_PACKET,DAMAGE_PACKET,EXPLOSION_PACKET,GRAVITY,FALLING_STATE,FLIGHT,FLUID,VEHICLE->true;default->false;});
    }
    private static boolean positionCause(Set<Cause> evidence) {
        return evidence.stream().anyMatch(c->switch(c) {case MOD_MOVEMENT_INPUT,NATIVE_KEY_EVENT,NATIVE_MOUSE_EVENT,VELOCITY_PACKET,DAMAGE_PACKET,EXPLOSION_PACKET,GRAVITY,FLIGHT,FLUID,VEHICLE->true;default->false;});
    }
    private static boolean matches(Intent intent,Value actual) {
        if(intent.kind==Kind.ROTATION)return rotationDistance(intent.target,actual)<=intent.tolerance;
        if(intent.kind==Kind.POSITION)return distance(intent.target,actual)<=intent.tolerance;
        return intent.target.equals(actual);
    }
    private static double distance(Value a,Value b) { return Math.sqrt(Math.pow(a.x-b.x,2)+Math.pow(a.y-b.y,2)+Math.pow(a.z-b.z,2)); }
    private static double rotationDistance(Value a,Value b) { return Math.max(Math.abs(Math.IEEEremainder(a.x-b.x,360)),Math.abs(a.y-b.y)); }
    private void discardOwnedLanding(){ownedLanding=null;ownedLandingPositionAcknowledged=false;}
    private void clock(long now) { if(now<lastTime) {intents.clear();signals.clear();discardOwnedLanding();}lastTime=now; }
    private void expire(long now) {intents.removeIf(i->i.expires<now);if(ownedLanding!=null&&(ownedLanding.expires<now||!landingValid(ownedLanding)))discardOwnedLanding();signals.removeIf(s->s.expires<now);}
    private static <T> void boundedAdd(ArrayDeque<T> queue,T value,int limit) {if(queue.size()==limit)queue.removeFirst();queue.addLast(value);}
    public List<Finding> history() { return List.copyOf(history); }
    public int pendingIntents() { return intents.size(); }
    public int unexplainedCount() { return unexplainedCount; }
    public void clear() { intents.clear();signals.clear();history.clear();context=null;discardOwnedLanding();unexplainedCount=0;lastTime=Long.MIN_VALUE; }
}
