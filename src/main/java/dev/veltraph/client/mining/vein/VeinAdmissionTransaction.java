package dev.veltraph.client.mining.vein;
import java.util.Objects;
/** One fresh geometry transaction after immutable identity preparation; candidate is not permission. */
public final class VeinAdmissionTransaction {
 public record Publication(Object player,Object world,Object connection,Object owner,Object camera,long epoch,Object position,float yaw,float pitch,Object configuration,Object components,int count,int slot){}
 public interface PublicationPort {Object configuration();Object owner();Object camera();Publication nativeLeaves(Object configuration,Object owner,Object camera);}
 public static Publication capturePublication(PublicationPort port){Object configuration=port.configuration();Object owner=port.owner();Object camera=port.camera();return port.nativeLeaves(configuration,owner,camera);}
 public static VeinBreakSession.Scope admissionScope(Publication captured,Object tool,long activation){return new VeinBreakSession.Scope(captured.player(),captured.world(),captured.connection(),captured.owner(),captured.camera(),captured.configuration(),tool,captured.slot(),activation);}
 public enum PublicationResult { ADMIT, YIELD, REVOKE }
 /** A budget miss is permissionless cooperation; identity loss or the original deadline revokes. */
 public static PublicationResult publicationResult(boolean samePublication,long now,long deadline,long started,long sampled,int count){
  if(!samePublication||now>=deadline||sampled<started||count<0)return PublicationResult.REVOKE;
  return sampled-started>4_000_000||count>4096?PublicationResult.YIELD:PublicationResult.ADMIT;
 }
 public static boolean unchangedPublication(Publication expected,Publication actual){return expected!=null&&actual!=null&&expected.player()==actual.player()&&expected.world()==actual.world()&&expected.connection()==actual.connection()&&expected.owner()==actual.owner()&&expected.camera()==actual.camera()&&expected.epoch()==actual.epoch()&&java.util.Objects.equals(expected.position(),actual.position())&&Float.compare(expected.yaw(),actual.yaw())==0&&Float.compare(expected.pitch(),actual.pitch())==0&&java.util.Objects.equals(expected.configuration(),actual.configuration())&&java.util.Objects.equals(expected.components(),actual.components())&&expected.count()==actual.count()&&expected.slot()==actual.slot();}
 private final Object scope;private final long deadline,started;private int queries;private boolean incomplete,spent;
 public VeinAdmissionTransaction(Object scope,long deadline,long started){this.scope=Objects.requireNonNull(scope);if(deadline<=0||started<0)throw new IllegalArgumentException();this.deadline=deadline;this.started=started;}
 public boolean probe(long sampledNow,int count){if(count<0||sampledNow<started||count>4096||sampledNow-started>4_000_000){incomplete=true;return false;}queries=count;return true;}
 public boolean commitFresh(Object freshScope,long geometrySample,int count,long nowMillis,boolean fullGeometry,boolean exactNativeIdentity){return probe(geometrySample,count)&&commit(freshScope,nowMillis,fullGeometry,exactNativeIdentity);}
 public boolean commit(Object freshScope,long nowMillis,boolean fullGeometry,boolean exactNativeIdentity){if(spent||incomplete||!scope.equals(freshScope)||nowMillis>=deadline||!fullGeometry||!exactNativeIdentity)return false;spent=true;return true;}
 public int queries(){return queries;}
}
