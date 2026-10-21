package dev.veltraph.client.scheduling.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import java.util.Objects;
import dev.veltraph.client.mixin.ScheduledConnectScreenAccessor;
import dev.veltraph.client.mixin.ScheduledLevelLoadingScreenAccessor;
import dev.veltraph.client.mixin.ScheduledClientLevelLoadAccessor;
import dev.veltraph.client.scheduling.ScheduledLoadingScreenProof;

/** Vanilla transport using Minecraft's existing session and exact captured native server entry only. */
public final class NativeRestTransport {
    private final Minecraft client;private ServerData target;private String address,name;private Screen restScreen,connectScreen;
    private net.minecraft.network.Connection originalNativeConnection;
    private boolean disconnected,issued;private int disconnects,rejoins;
    public NativeRestTransport(Minecraft client){this.client=client;}
    public boolean capture(Runnable cancel){
        return capture(cancel,()->"Resting before reconnecting to the saved server.");
    }
    public boolean capture(Runnable cancel,java.util.function.Supplier<String> status){
        thread();var current=client.getCurrentServer();
        if(current==null||current.ip==null||current.ip.isBlank()||client.getConnection()==null||client.hasSingleplayerServer())return false;
        originalNativeConnection=client.getConnection().getConnection();
        target=current;address=current.ip;name=current.name;disconnected=issued=false;restScreen=new RestScreen(cancel,status);return true;
    }
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Rest transport requires client thread");}
    public static boolean cancellationAvailable(){return ScheduledConnectScreenAccessor.class.isAssignableFrom(ConnectScreen.class)
            &&ScheduledLevelLoadingScreenAccessor.class.isAssignableFrom(net.minecraft.client.gui.screens.LevelLoadingScreen.class)
            &&ScheduledClientLevelLoadAccessor.class.isAssignableFrom(net.minecraft.client.multiplayer.ClientPacketListener.class);}
    public boolean originalTargetUnchanged(){return target!=null&&Objects.equals(target.ip,address)&&Objects.equals(target.name,name);}
    public boolean disconnect(){
        thread();if(disconnected||!originalTargetUnchanged()||client.getCurrentServer()!=target||client.getConnection()==null)return false;
        disconnected=true;disconnects++;client.disconnect(restScreen,false);return true;
    }
    public boolean rejoin(){
        thread();if(!disconnected||issued||!originalTargetUnchanged()||client.level!=null||client.player!=null||client.gui.screen()!=restScreen)return false;
        issued=true;rejoins++;ConnectScreen.startConnecting(restScreen,client,ServerAddress.parseString(address),target,false,null);connectScreen=client.gui.screen();return connectScreen instanceof ConnectScreen;
    }
    public boolean matchingServer(){
        thread();var listener=client.getConnection();
        var issuedConnection=connectScreen instanceof ScheduledConnectScreenAccessor connector?connector.veltraph$getConnection():null;
        return issued&&originalTargetUnchanged()&&client.getCurrentServer()==target&&listener!=null
                &&issuedConnection!=null&&listener.getConnection()==issuedConnection&&issuedConnection!=originalNativeConnection;
    }
    public boolean restingScreenOwned(){thread();return client.gui.screen()==restScreen;}
    public boolean connectionScreenAllowed(){return connectionScreenAllowed(false);}
    public boolean connectionScreenAllowed(boolean savedReturnStarted){
        thread();var screen=client.gui.screen();if(screen==null)return true;
        if(savedReturnStarted)return false;
        if(screen==restScreen)return disconnected&&originalTargetUnchanged();
        if(screen==connectScreen)return issued&&originalTargetUnchanged();
        var listener=client.getConnection();
        Object issuedConnection=connectScreen instanceof ScheduledConnectScreenAccessor connector?connector.veltraph$getConnection():null;
        Object listenerTracker=listener instanceof ScheduledClientLevelLoadAccessor source?source.veltraph$levelLoadTracker():null;
        Object screenTracker=screen instanceof ScheduledLevelLoadingScreenAccessor loading?loading.veltraph$loadTracker():null;
        // The actual 26.3 receiving-level screen is LevelLoadingScreen. Its tracker must be
        // the identical object installed by the new listener on our exact issued connection.
        // Generic/progress/resource-pack/manual screens carry no such remote-join authority.
        return ScheduledLoadingScreenProof.permits(issued,true,matchingServer(),
                screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen,
                originalNativeConnection,issuedConnection,listener==null?null:listener.getConnection(),listenerTracker,screenTracker);
    }
    public void cancelConnect(){
        thread();
        if(issued&&client.level==null)abortOwnedAttempt(client,connectScreen);
        if(client.level==null&&(client.gui.screen()==restScreen||client.gui.screen()==connectScreen))client.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());issued=false;
    }
    /** Completed native joins are no longer cancellable connection attempts owned by this ticket. */
    public void complete(){thread();issued=false;disconnected=false;connectScreen=null;restScreen=null;target=null;originalNativeConnection=null;address=name=null;}
    /** Caller supplies the exact pinned screen; no current/foreign UI is changed by native abort. */
    static boolean abortOwnedAttempt(Minecraft client,Screen owned){
        if(!client.isSameThread())throw new IllegalStateException("Owned native connector abort requires client thread");
        if(owned instanceof ScheduledConnectScreenAccessor access){
            // The same abort operations as vanilla Cancel, applied only to the pinned attempt.
            synchronized(owned){
                access.veltraph$setAborted(true);var future=access.veltraph$getChannelFuture();
                if(future!=null){future.cancel(true);access.veltraph$setChannelFuture(null);}
                var connection=access.veltraph$getConnection();if(connection!=null)connection.disconnect(ConnectScreen.ABORT_CONNECTION);
            }
            return true;
        }
        return false;
    }
    /** Immutable raw native transport identity snapshot; read-only and never a permission by itself. */
    public final class IssuedFarmSnapshot {
        private final ServerData savedTarget=target;private final String savedAddress=address,savedName=name;
        private final Screen savedRest=restScreen,savedConnect=connectScreen;private final net.minecraft.network.Connection savedOriginal=originalNativeConnection;
        private final boolean savedDisconnected=disconnected,savedIssued=issued;
        private IssuedFarmSnapshot(){}
        /** Exact old raw record retirement only; never touches a channel, future or screen. */
        public boolean retireMetadata(){if(!current())return false;issued=false;disconnected=false;connectScreen=null;restScreen=null;target=null;originalNativeConnection=null;address=name=null;return true;}
        /** Negative disposal of this exact unissued capture, even if external ServerData metadata changed.
         * No channel, screen or newer transport record is touched; this grants no transport action. */
        public boolean discardUnissuedCapture(){
            if(savedDisconnected||savedIssued||target!=savedTarget||address!=savedAddress||name!=savedName||restScreen!=savedRest||connectScreen!=savedConnect
                ||originalNativeConnection!=savedOriginal||disconnected!=savedDisconnected||issued!=savedIssued)return false;
            issued=false;disconnected=false;connectScreen=null;restScreen=null;target=null;originalNativeConnection=null;address=name=null;return true;
        }
        public NativeRestTransport transport(){return NativeRestTransport.this;}public Minecraft client(){return client;}
        public ServerData target(){return savedTarget;}public Screen restScreen(){return savedRest;}public Screen connectScreen(){return savedConnect;}
        public net.minecraft.network.Connection originalConnection(){return savedOriginal;}
        public boolean current(){return target==savedTarget&&address==savedAddress&&name==savedName&&restScreen==savedRest&&connectScreen==savedConnect
            &&originalNativeConnection==savedOriginal&&disconnected==savedDisconnected&&issued==savedIssued&&savedTarget!=null
            &&java.util.Objects.equals(savedTarget.ip,savedAddress)&&java.util.Objects.equals(savedTarget.name,savedName);}
    }
    public IssuedFarmSnapshot issuedFarmSnapshot(){return new IssuedFarmSnapshot();}
    public int disconnects(){return disconnects;}public int rejoins(){return rejoins;}
    /** ESC is an explicit scheduler cancel, never a rejoin/restart shortcut. */
    private static final class RestScreen extends Screen {
        private final Runnable cancel;private final java.util.function.Supplier<String> status;
        RestScreen(Runnable cancel,java.util.function.Supplier<String> status){super(Component.literal("Veltraph scheduled rest"));this.cancel=cancel;this.status=status;}
        @Override protected void init(){addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("Cancel rest"),button->onClose()).bounds(width/2-100,height/2+25,200,20).build());}
        @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
            graphics.fill(0,0,width,height,0xff101820);graphics.centeredText(font,title,width/2,height/2-45,0xffe2f1ef);
            graphics.centeredText(font,status.get(),width/2,height/2-20,0xffb5cfca);super.extractRenderState(graphics,mouseX,mouseY,delta);
        }
        @Override public void onClose(){cancel.run();Minecraft.getInstance().gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());}
        @Override public boolean isPauseScreen(){return false;}
    }
}
