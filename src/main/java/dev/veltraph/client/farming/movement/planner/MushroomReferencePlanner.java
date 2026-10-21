package dev.veltraph.client.farming.movement.planner;
import dev.veltraph.client.config.MushroomFarmDesign;
import dev.veltraph.client.farming.movement.Angles;
import dev.veltraph.client.config.LaneSide;
/**
 * Fresh native policy for explicitly selected fixed45 / rotating30 / SDS geometries.
 * Behavior reference: JellyLab/FarmHelper b7a878e7, SShapeMushroom{,Rotate,SDS}Macro,
 * CC BY-NC-SA 4.0. No upstream code, hitbox mutation or input API is reused.
 * Physical geometry and action authority remain with the actual current native services.
 */
public final class MushroomReferencePlanner {
    public enum Clearance { CLEAR, BLOCKED, UNKNOWN }
    public enum State { LEFT, RIGHT, BACK, PAUSED }
    public record Keys(boolean forward,boolean left,boolean right,boolean back) {}
    private MushroomFarmDesign design;
    private State state=State.PAUSED,lastRow=State.LEFT;
    private float baseYaw,fixedYaw;
    public static boolean supports(MushroomFarmDesign design){
        return design==MushroomFarmDesign.FARMHELPER_FIXED_45||design==MushroomFarmDesign.FARMHELPER_ROTATE_30||design==MushroomFarmDesign.FARMHELPER_SDS;
    }
    public void start(MushroomFarmDesign selected,float cardinalYaw,float liveYaw,LaneSide initial){
        if(!supports(selected))throw new IllegalArgumentException("Distinct mushroom reference profile required");
        design=selected;baseYaw=Angles.wrapDegrees(cardinalYaw);
        // Preserve nearest diagonal; an exact cardinal tie takes the configured initial side.
        float delta=Angles.signedDelta(baseYaw,liveYaw);
        fixedYaw=Angles.wrapDegrees(baseYaw+(delta==0?(initial==LaneSide.LEFT?-45:45):delta<0?-45:45));
        state=lastRow=initial==LaneSide.LEFT?State.LEFT:State.RIGHT;
    }
    /** Only actual loaded collision/support classifications can advance a source state. */
    public void observe(Clearance left,Clearance right,Clearance back){
        if(left==Clearance.UNKNOWN||right==Clearance.UNKNOWN||design==MushroomFarmDesign.FARMHELPER_SDS&&back==Clearance.UNKNOWN){state=State.PAUSED;return;}
        if(state==State.PAUSED)state=lastRow;
        if(design==MushroomFarmDesign.FARMHELPER_SDS){
            if(state==State.BACK){if(back==Clearance.CLEAR)return;state=lastRow==State.LEFT?State.RIGHT:State.LEFT;}
            else if(back==Clearance.CLEAR){lastRow=state;state=State.BACK;return;}
        }
        Clearance own=state==State.LEFT?left:right,other=state==State.LEFT?right:left;
        if(own==Clearance.BLOCKED){if(other==Clearance.CLEAR)state=state==State.LEFT?State.RIGHT:State.LEFT;else state=State.PAUSED;}
        if(state==State.LEFT||state==State.RIGHT)lastRow=state;
    }
    public Keys keys(){
        if(state==State.PAUSED)return new Keys(false,false,false,false);
        if(design==MushroomFarmDesign.FARMHELPER_SDS)return new Keys(false,state==State.LEFT,state==State.RIGHT,state==State.BACK);
        if(design==MushroomFarmDesign.FARMHELPER_ROTATE_30)return new Keys(true,false,false,false);
        boolean lookLeft=Angles.signedDelta(baseYaw,fixedYaw)<0;
        return new Keys(state==State.LEFT?lookLeft:!lookLeft,state==State.LEFT&&!lookLeft,state==State.RIGHT&&lookLeft,false);
    }
    public float yaw(){return design==MushroomFarmDesign.FARMHELPER_FIXED_45?fixedYaw:Angles.wrapDegrees(baseYaw+(design==MushroomFarmDesign.FARMHELPER_ROTATE_30?(lastRow==State.LEFT?-30:30):0));}
    public float pitch(){return design==MushroomFarmDesign.FARMHELPER_SDS?7:0;}
    public State state(){return state;}
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState(){return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,"design",design,"state",state,"lastRow",lastRow,"baseYaw",baseYaw,"fixedYaw",fixedYaw);}
}
