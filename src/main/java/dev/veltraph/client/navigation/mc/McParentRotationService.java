package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.core.Module;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import java.util.function.Supplier;

/** Parent interaction publication adapter; it cannot borrow a child's or newer request's cleanup. */
public final class McParentRotationService implements RotationService {
    private final RotationService actual;
    private final McParentMotionCleanup cleanup;
    public McParentRotationService(Minecraft client,FarmServices services,Supplier<Module> parent){
        actual=services.rotation();cleanup=new McParentMotionCleanup(client,services,parent);
    }
    private void publish(int increments,Runnable action){
        if(!cleanup.beforePublication(increments))return;
        try{action.run();}finally{cleanup.remember();}
    }
    @Override public float yaw(){return actual.yaw();}
    @Override public float pitch(){return actual.pitch();}
    @Override public void aim(float yaw,float pitch){publish(1,()->actual.aim(yaw,pitch));}
    @Override public void track(float yaw,float pitch){publish(2,()->actual.track(yaw,pitch));}
    @Override public void lookAt(Vec3 target){publish(1,()->actual.lookAt(target));}
    @Override public void rotateTo(float yaw,float pitch,Runnable completed){
        publish(1,()->actual.rotateTo(yaw,pitch,()->{
            // Actual McRotationService completes from ordinary ticks after the publication receipt.
            // Refresh only its proved own queued endpoint; revoked requests cannot hand off credit.
            if(cleanup.ownsOwnInteraction()&&completed!=null)completed.run();
        }));
    }
    @Override public boolean sourceBusy(){return actual.sourceBusy();}
    @Override public void tickRotation(){actual.tickRotation();}
    @Override public void cancelRotation(){cleanup.cancelOwnInteraction();}
    /** Freshness of this exact parent publication, never generic authority equality. */
    public boolean currentRequest(){return cleanup.ownsOwnInteraction();}
    public void release(McTravelNavigator movement){
        // H owns concrete legacy flight/Ether/ground legs as well as shared journeys.
        // Only a retained exact leg can invoke its typed cleanup; never borrow an unbound primitive.
        if(movement.hasOwnedLegacyLeg()){movement.stop();return;}
        cleanup.release(movement);
    }
}
