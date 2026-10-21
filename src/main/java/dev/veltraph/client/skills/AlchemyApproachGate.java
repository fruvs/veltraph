package dev.veltraph.client.skills;

/** Alchemy retains a route through arrival; interaction reach never cancels physical travel. */
final class AlchemyApproachGate {
    enum Decision { START, WALK, SETTLE, AIM }
    private boolean travelling;
    void reset(){travelling=false;}
    void started(){travelling=true;}
    void arrived(){travelling=false;}
    Decision decide(double eyeDistance,double speed){
        if(!Double.isFinite(eyeDistance)||!Double.isFinite(speed)||eyeDistance<0||speed<0)throw new IllegalArgumentException("Finite native motion required");
        if(travelling)return Decision.WALK;
        if(eyeDistance>3.4)return Decision.START;
        return speed<=.01?Decision.AIM:Decision.SETTLE;
    }
}
