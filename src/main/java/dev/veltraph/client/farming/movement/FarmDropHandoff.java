package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Vec3;

/** Navigator completion is provisional until actual supported lower ground is observed. */
public final class FarmDropHandoff {
    private final FarmRouteDriver driver;
    private Vec3 target;
    private long started,reportedAt;
    private boolean reported;
    private String reason="Idle";
    public FarmDropHandoff(FarmRouteDriver driver){this.driver=driver;}
    public boolean start(Vec3 feetTarget,long now){
        stop();target=feetTarget;started=now;reported=false;
        if(!driver.startDrop(feetTarget,3)){reason="Rejected: "+driver.status();target=null;return false;}
        reason="Explicit drop toward "+feetTarget;return true;
    }
    public FarmRouteDriver.State tick(Vec3 actual,boolean grounded,boolean landingValid,long now){
        if(target==null||!landingValid||now<started||now-started>15000)return fail("Landing/context/deadline invalid");
        if(!reported){
            var result=driver.tick();reason=driver.status();
            if(result==FarmRouteDriver.State.BLOCKED)return fail("Navigator blocked: "+reason);
            if(result==FarmRouteDriver.State.ACTIVE)return result;
            driver.stop();reported=true;reportedAt=now;
        }
        if(actual.horizontalDistanceTo(target)>.6||Math.abs(actual.y()-target.y())>.5)
            return fail("Navigator result has no matching actual pose");
        if(grounded&&Math.abs(actual.y()-target.y())<=.12){reason="Observed exact lower standing arrival";return FarmRouteDriver.State.ARRIVED;}
        if(now-reportedAt>5000)return fail("Lower standing arrival never observed");
        reason="Waiting for actual lower ground contact";return FarmRouteDriver.State.ACTIVE;
    }
    private FarmRouteDriver.State fail(String reason){this.reason=reason;driver.stop();target=null;return FarmRouteDriver.State.BLOCKED;}
    public void stop(){driver.stop();target=null;reported=false;}
    public Vec3 target(){return target;} public String status(){return reason;}

    /** Passive exact maintenance checkpoint. No services, geometry, clocks or planner callbacks run. */
    public dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "driver", driver,
                "target", target,
                "started", started,
                "reportedAt", reportedAt,
                "reported", reported,
                "reason", reason);
    }
}
