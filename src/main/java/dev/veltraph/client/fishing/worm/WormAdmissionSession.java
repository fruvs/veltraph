package dev.veltraph.client.fishing.worm;
import java.util.random.RandomGenerator;
import java.util.Objects;
/** Only the enabled native underwater helper may sample/parse source admission. */
public final class WormAdmissionSession {
    private WormAdmission admission;private String entities,minutes;
    public boolean observe(boolean enabled,boolean eligible,String entities,String minutes,long now,RandomGenerator random){
        if(!enabled){clear();return false;}
        if(!eligible)return false;
        if(admission==null){var next=new WormAdmission();next.reset(WormAdmission.Range.parse(entities),WormAdmission.Range.parse(minutes),now,random);this.entities=entities;this.minutes=minutes;admission=next;}
        else if(!Objects.equals(this.entities,entities)||!Objects.equals(this.minutes,minutes))throw new IllegalArgumentException("Active worm admission configuration changed");
        return true;
    }
    public boolean ready(int count,long now){return admission!=null&&admission.ready(count,now);}
    public WormAdmission.Sample sample(){return admission==null?null:admission.sample();}
    public void accepted(long now,RandomGenerator random){if(admission==null)throw new IllegalStateException("No active worm admission");admission.reset(WormAdmission.Range.parse(entities),WormAdmission.Range.parse(minutes),now,random);}
    public void clear(){admission=null;entities=minutes=null;}
}
