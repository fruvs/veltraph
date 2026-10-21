package dev.veltraph.client.rewarp;
import dev.veltraph.client.inventory.selling.GeorgeWarpPermit;
import java.util.UUID;
/** Narrow Root George lease admission; original capture and admitted current context are distinct. */
public final class GeorgeMaintenanceAdmission {
    public record Context(Object player,Object world,Object connection,UUID playerId){public Context{java.util.Objects.requireNonNull(player);java.util.Objects.requireNonNull(world);java.util.Objects.requireNonNull(connection);java.util.Objects.requireNonNull(playerId);}}
    private final Object farm,helper,token;private final Context original;private Context current;private GeorgeWarpPermit issued;
    public GeorgeMaintenanceAdmission(Object farm,Object helper,Object token,Context original){this.farm=java.util.Objects.requireNonNull(farm);this.helper=java.util.Objects.requireNonNull(helper);this.token=java.util.Objects.requireNonNull(token);this.original=java.util.Objects.requireNonNull(original);current=original;}
    public boolean bind(Object farm,Object helper,Object token,GeorgeWarpPermit permit,boolean checkpointProtected){
        if(this.farm!=farm||this.helper!=helper||this.token!=token||issued!=null||permit==null||permit.state()!=GeorgeWarpPermit.State.WAITING||!checkpointProtected)return false;
        var p=permit.pending();if(p.player()!=current.player()||p.previousWorld()!=current.world()||p.connection()!=original.connection()||!p.uuid().equals(original.playerId()))return false;
        issued=permit;return true;
    }
    public boolean commit(Object farm,Object helper,Object token,GeorgeWarpPermit permit,GeorgeWarpPermit.Observation arrival,boolean checkpointProtected){
        if(this.farm!=farm||this.helper!=helper||this.token!=token||permit!=issued||permit==null||permit.state()!=GeorgeWarpPermit.State.COMMITTED||!checkpointProtected||arrival==null)return false;
        var saved=permit.cachedNativeReceipt().orElse(null);var pending=permit.pending();
        if(saved==null||arrival.player()!=saved.player()||arrival.world()!=saved.world()||arrival.connection()!=original.connection()
                ||!original.playerId().equals(arrival.uuid())||arrival.nativeSerial()!=saved.nativeSerial()||!java.util.Objects.equals(arrival.nativeTeleport(),saved.nativeTeleport())
                ||!arrival.healthy()||arrival.loading()||!arrival.loaded()||!arrival.supported()||arrival.area()!=pending.destination().area()
                ||!java.util.Objects.equals(arrival.dimension(),pending.destination().dimension())||arrival.tick()>=pending.deadline())return false;
        current=new Context(arrival.player(),arrival.world(),arrival.connection(),arrival.uuid());issued=null;return true;
    }
    public Context original(){return original;}public Context current(){return current;}
    public boolean pending(Object helper,Object token,GeorgeWarpPermit permit){return this.helper==helper&&this.token==token&&issued==permit&&permit!=null&&permit.state()!=GeorgeWarpPermit.State.CANCELLED;}
    public void cancel(){if(issued!=null)issued.cancel();issued=null;}
}
