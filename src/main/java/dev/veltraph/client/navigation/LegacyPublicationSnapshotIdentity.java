package dev.veltraph.client.navigation;
/** Callback-free identity equality only; the native Leg alone mints opaque snapshot handles. */
public final class LegacyPublicationSnapshotIdentity {
 private LegacyPublicationSnapshotIdentity(){}
 public record State(Object activeLeg,Object identity,Object root,Object player,Object world,Object connection,Object store,
                     long admission,long generation,long epoch,long serial){}
 public static boolean same(State captured,State live){return captured!=null&&live!=null&&captured.activeLeg()!=null
  &&captured.identity()!=null&&captured.root()!=null&&captured.player()!=null&&captured.world()!=null&&captured.connection()!=null&&captured.store()!=null
  &&captured.admission()>=0&&captured.generation()>=0&&captured.epoch()>=0&&captured.serial()>=0
  &&captured.activeLeg()==live.activeLeg()&&captured.identity()==live.identity()&&captured.root()==live.root()
  &&captured.player()==live.player()&&captured.world()==live.world()&&captured.connection()==live.connection()&&captured.store()==live.store()
  &&captured.admission()==live.admission()&&captured.generation()==live.generation()&&captured.epoch()==live.epoch()&&captured.serial()==live.serial();}
}
