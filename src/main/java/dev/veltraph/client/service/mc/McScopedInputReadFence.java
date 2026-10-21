package dev.veltraph.client.service.mc;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.ModuleManager;
import net.minecraft.client.Minecraft;
/** Additional callback-free native leaf, not publication permission. Actual caller custody independently fences service/ADMISSIONS. */
final class McScopedInputReadFence {
 private final Minecraft client;private final ModuleManager root;private final ModuleManager.LifecycleSnapshot lifecycle;
 private final Object player,world,connection,config,options,gameMode,input,camera;private final long epoch,serial;
 private McScopedInputReadFence(Minecraft client,ModuleManager root,ModuleManager.LifecycleSnapshot lifecycle){
  this.client=client;this.root=root;this.lifecycle=lifecycle;player=client.player;world=client.level;connection=client.getConnection();
  config=VeltraphBootstrap.config();options=client.options;gameMode=client.gameMode;epoch=McOwnedRotation.epoch(client);serial=McOwnedRotation.publicationSerial(client);input=root.inputOwner().orElse(null);camera=McOwnedRotation.authority(client);
 }
 static McScopedInputReadFence capture(Minecraft client){
  if(client==null||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null)return null;
  var root=VeltraphBootstrap.moduleManager();var lifecycle=root==null?null:root.lifecycleSnapshot();
  if(lifecycle==null||!lifecycle.known())return null;var fence=new McScopedInputReadFence(client,root,lifecycle);return fence.current()?fence:null;
 }
 boolean current(){return client.isSameThread()&&VeltraphBootstrap.moduleManager()==root&&root.lifecycleCurrent(lifecycle)
  &&client.player==player&&client.level==world&&client.getConnection()==connection&&VeltraphBootstrap.config()==config
  &&client.options==options&&client.gameMode==gameMode&&McOwnedRotation.epoch(client)==epoch&&McOwnedRotation.publicationSerial(client)==serial;}
}
