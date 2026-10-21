package dev.veltraph.client.rotation;
/** Evidence predicate only; native receipt constructors remain private in the actual service. */
public final class DirectRotationPublicationIdentity {
 private DirectRotationPublicationIdentity(){}
 public record Scope(Object root,Object parent,Object inputOwner,Object cameraOwner,Object player,Object world,Object connection,
                     Object configStore,Object service,long generation,long epoch,long publicationSerial,long rotationRevision){}
 public enum Mode { AIM, TRACK, SAME_TICK_TRACK }
 public record Proof(Scope before,Scope after,long ticket,Mode mode){}
 public static boolean scene(Scope a,Scope b){return a!=null&&b!=null&&a.root()!=null&&a.root()==b.root()&&a.parent()!=null&&a.parent()==b.parent()
  &&a.inputOwner()==a.parent()&&b.inputOwner()==b.parent()&&a.cameraOwner()==a.parent()&&b.cameraOwner()==b.parent()
  &&a.player()!=null&&a.player()==b.player()&&a.world()!=null&&a.world()==b.world()&&a.connection()!=null&&a.connection()==b.connection()
  &&a.configStore()!=null&&a.configStore()==b.configStore()&&a.service()!=null&&a.service()==b.service()
  &&a.rotationRevision()>=0&&a.rotationRevision()==b.rotationRevision();}
 public static boolean same(Scope a,Scope b){return scene(a,b)&&a.generation()==b.generation()&&a.epoch()==b.epoch()&&a.publicationSerial()==b.publicationSerial();}
 public static boolean publication(Proof p){if(p==null||p.mode()==null||p.mode()==Mode.SAME_TICK_TRACK||!scene(p.before(),p.after()))return false;
  var a=p.before();var b=p.after();long delta=p.mode()==Mode.TRACK?2:1;
  return a.generation()>=0&&b.generation()>a.generation()&&b.generation()-a.generation()==delta&&a.epoch()>=0&&b.epoch()>=a.epoch()&&b.epoch()-a.epoch()<=1
   &&p.ticket()>0&&p.ticket()>a.publicationSerial()&&p.ticket()==b.publicationSerial();}
 public static boolean retained(Proof p,Proof prior){return p!=null&&prior!=null&&p.mode()==Mode.SAME_TICK_TRACK&&same(prior.after(),p.before())
  &&scene(p.before(),p.after())&&p.before().generation()>=0&&p.after().generation()>p.before().generation()&&p.after().generation()-p.before().generation()==1
  &&p.after().epoch()==p.before().epoch()&&p.after().publicationSerial()==p.before().publicationSerial()
  &&p.ticket()>0&&p.ticket()==prior.ticket()&&p.ticket()==p.after().publicationSerial();}
 public static boolean current(Proof p,Scope actual){return p!=null&&p.ticket()>0&&same(p.after(),actual)&&p.ticket()==actual.publicationSerial();}
 public static boolean cleanupScene(Scope issued,Scope actual,boolean allowAbsent){return issued!=null&&actual!=null&&issued.root()!=null&&issued.root()==actual.root()&&issued.parent()!=null&&issued.parent()==actual.parent()
  &&issued.player()!=null&&issued.player()==actual.player()&&issued.world()!=null&&issued.world()==actual.world()&&issued.connection()!=null&&issued.connection()==actual.connection()&&issued.configStore()!=null&&issued.configStore()==actual.configStore()&&issued.service()!=null&&issued.service()==actual.service()
  &&(actual.inputOwner()==issued.parent()&&actual.cameraOwner()==issued.parent()||allowAbsent&&actual.inputOwner()==null&&actual.cameraOwner()==null);}
 public static boolean ownedCleanup(Proof issued,Scope actual,boolean allowAbsent){return issued!=null&&issued.ticket()>0&&cleanupScene(issued.after(),actual,allowAbsent)
  &&issued.after().generation()==actual.generation()&&issued.after().epoch()==actual.epoch()&&issued.ticket()==actual.publicationSerial();}
 public static boolean cancellation(Proof issued,Scope before,Scope after,boolean allowAbsent){return issued!=null&&issued.ticket()>0&&cleanupScene(issued.after(),before,allowAbsent)&&cleanupScene(before,after,allowAbsent)
  &&before.inputOwner()==after.inputOwner()&&before.cameraOwner()==after.cameraOwner()
  &&issued.after().generation()==before.generation()&&issued.after().epoch()==before.epoch()&&issued.ticket()==before.publicationSerial()
  &&before.generation()>=0&&after.generation()>before.generation()&&after.generation()-before.generation()==1
  &&before.epoch()>=0&&after.epoch()>before.epoch()&&after.epoch()-before.epoch()==1&&after.publicationSerial()==issued.ticket();}
 public static boolean cancellationCurrent(Scope after,Scope actual,boolean allowAbsent,boolean requireRevision){return cleanupScene(after,actual,allowAbsent)
  &&after.inputOwner()==actual.inputOwner()&&after.cameraOwner()==actual.cameraOwner()&&after.generation()==actual.generation()
  &&after.epoch()==actual.epoch()&&after.publicationSerial()==actual.publicationSerial()&&(!requireRevision||after.rotationRevision()==actual.rotationRevision());}
 public static boolean preplan(Scope before,Scope actual,Mode mode){return mode!=null&&mode!=Mode.SAME_TICK_TRACK&&scene(before,actual)
  &&before.generation()>=0&&actual.generation()>before.generation()&&actual.generation()-before.generation()==(mode==Mode.TRACK?2:1)
  &&before.epoch()==actual.epoch()&&before.publicationSerial()==actual.publicationSerial();}
}
