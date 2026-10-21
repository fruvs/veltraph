package dev.veltraph.client.skills;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Shared world/input/timeout ownership; skill state machines remain separate. */
abstract class GuardedSkillModule extends AbstractModule {
    protected final Minecraft client;protected final ConfigService config;protected final FarmServices services;protected final SkillMenuSession menus;
    protected Object world,player,ownedMenu;protected long deadline,nextClick;protected int revision=-1;protected String phase="IDLE",reason="Idle";
    private Map<String,Object> runSettings=Map.of();
    protected GuardedSkillModule(String id,String name,Minecraft client,ConfigService config,FarmServices services){super(id,name,ModuleCategory.MISC);this.client=client;this.config=config;this.services=services;menus=new SkillMenuSession(client);}
    @Override public boolean requiresSkyBlock(){return true;}
    protected long now(){return services.clock().nowMillis();}
    private Map<String,Object> settings(){var values=new TreeMap<String,Object>();try{for(var field:SkillAutomationConfig.class.getDeclaredFields())if(java.lang.reflect.Modifier.isStatic(field.getModifiers())&&ConfigKey.class.isAssignableFrom(field.getType())){var key=(ConfigKey<?>)field.get(null);values.put(key.name(),config.get(key));}}catch(IllegalAccessException e){throw new IllegalStateException(e);}return Map.copyOf(values);}
    protected void begin(){world=client.level;player=client.player;runSettings=settings();ownedMenu=null;revision=-1;phase="READY";reason="Ready";deadline=now()+Math.max(1000,Math.min(120000,config.get(SkillAutomationConfig.SKILL_TIMEOUT_MS)));nextClick=now();release();}
    protected void release(){services.input().releaseAllMovement();services.breaking().setBreaking(false);services.rotation().cancelRotation();if(client.options!=null){dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyUse,false);dev.veltraph.client.service.mc.McHeldKeys.setHeld(client.options.keyAttack,false);}}
    /** Inert original private skill runtime; no context/provider/clock permission is evaluated. */
    final class OriginalSkillRuntimeSnapshot {
        private final Object originalWorld=world,originalPlayer=player,originalMenu=ownedMenu;
        private final Map<String,Object> originalSettings=runSettings;private final long originalDeadline=deadline;
        private OriginalSkillRuntimeSnapshot(){}
        final boolean rawCurrent(){return world==originalWorld&&player==originalPlayer&&ownedMenu==originalMenu
            &&runSettings==originalSettings&&deadline==originalDeadline;}
    }
    final OriginalSkillRuntimeSnapshot originalFairyRuntimeSnapshot(){
        boolean actual=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(1).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==FairySoulModule.class&&caller.getMethodName().equals("captureNativeNormalReturnCleanupAdmission");});
        return actual?new OriginalSkillRuntimeSnapshot():null;
    }
    protected boolean context(){return client.level!=null&&client.level==world&&client.player==player&&client.player!=null&&client.player.isAlive()&&now()<deadline&&runSettings.equals(settings());}
    protected void fail(String why){phase="FAILED";reason=why;setEnabled(false);}
    protected void complete(String why){phase="COMPLETE";reason=why;setEnabled(false);}
    protected boolean delay(){return now()>=nextClick;}
    protected boolean click(SkillMenuSession.View menu,int slot,net.minecraft.world.inventory.ContainerInput input){
        if(!delay())return false;if(!menus.request(menu,slot,input)){fail("Stale or invalid container request");return false;}
        ownedMenu=menu.menu();revision=menu.revision();nextClick=now()+Math.max(0,config.get(SkillAutomationConfig.SKILL_CLICK_DELAY_MS));return true;
    }
    protected SkillMenuSession.View expected(String title){var menu=menus.read();return menu!=null&&menu.title().equals(title)&&(ownedMenu==null||ownedMenu==menu.menu())?menu:null;}
    protected static void label(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Configure exact menu/button/lore labels");}
    protected Optional<String> invalid(Runnable check){try{if(client.player==null||client.level==null||!client.player.isAlive())return Optional.of("A living player/world is required");check.run();return Optional.empty();}catch(RuntimeException e){return Optional.of(e.getMessage());}}
    protected boolean ownsCursor(SkillMenuSession.Item cursor){return false;}
    @Override public void onDisable(){release();if(client.level==world&&client.player==player&&client.player!=null&&client.player.containerMenu==ownedMenu){var cursor=SkillMenuSession.item(client.player.containerMenu.getCarried());if(cursor.count()==0||ownsCursor(cursor))client.player.closeContainer();}if(!phase.equals("FAILED")&&!phase.equals("COMPLETE")){phase="CANCELLED";reason="Stopped";}}
    @Override public String diagnostics(){return phase+": "+reason;}
}
