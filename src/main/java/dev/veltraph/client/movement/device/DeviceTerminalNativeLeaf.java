package dev.veltraph.client.movement.device;
/** Read-only native equality evidence; no Frame can construct a terminal or Root lifecycle certificate. */
public final class DeviceTerminalNativeLeaf {
 private DeviceTerminalNativeLeaf(){}
 public record Frame(Object root,Object player,Object world,Object connection,Object store,Object options,Object gameMode,Object menu,Object camera,
                     long epoch,long generation,long publication,long ack,int slot,double x,double y,double z,float yaw,float pitch){}
 public static boolean same(Frame a,Frame b){return a!=null&&b!=null&&a.root()!=null&&a.root()==b.root()&&a.player()!=null&&a.player()==b.player()
  &&a.world()!=null&&a.world()==b.world()&&a.connection()!=null&&a.connection()==b.connection()&&a.store()!=null&&a.store()==b.store()
  &&a.options()!=null&&a.options()==b.options()&&a.gameMode()!=null&&a.gameMode()==b.gameMode()&&a.menu()!=null&&a.menu()==b.menu()&&a.camera()!=null&&a.camera()==b.camera()
  &&a.epoch()>=0&&a.epoch()==b.epoch()&&a.generation()>=0&&a.generation()==b.generation()&&a.publication()>0&&a.publication()==b.publication()
  &&a.ack()>0&&a.ack()==b.ack()&&a.slot()>=0&&a.slot()<9&&a.slot()==b.slot()
  &&Double.isFinite(a.x())&&Double.isFinite(a.y())&&Double.isFinite(a.z())&&Float.isFinite(a.yaw())&&Float.isFinite(a.pitch())
  &&a.x()==b.x()&&a.y()==b.y()&&a.z()==b.z()&&a.yaw()==b.yaw()&&a.pitch()==b.pitch();}
}
