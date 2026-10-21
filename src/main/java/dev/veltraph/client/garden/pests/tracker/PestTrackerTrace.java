package dev.veltraph.client.garden.pests.tracker;

import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Source tracker request scope, one-tick continuation, four-unique buffer and two-half prediction. */
public final class PestTrackerTrace {
    public record Particle(String kind,String options){public Particle {
        if(!Set.of("minecraft:dust","minecraft:angry_villager","minecraft:enchant").contains(kind)||options==null||options.isBlank())throw new IllegalArgumentException("Exact source particle kind/options required");}}
    public record Settings(List<Particle> particles,int eventThreshold,double predictionScale,int lifetimeTicks){public Settings {
        particles=List.copyOf(particles);if(particles.isEmpty()||particles.size()>8||eventThreshold<11||eventThreshold>12||!Double.isFinite(predictionScale)||predictionScale<1.2||predictionScale>=1.3||lifetimeTicks<1||lifetimeTicks>200)
            throw new IllegalArgumentException("Source particle buffer constants and bounded explicit lifetime required");}}
    public record Goal(Vec3 origin,Vec3 direction,Vec3 predicted,Vec3 latest,int events,long receiptSerial){}
    private record Pending(GardenTrackerParticles.Receipt receipt,long readyTick){}
    private final Settings settings;private final LinkedHashMap<Vec3,Long> points=new LinkedHashMap<>();private final ArrayDeque<Pending> pending=new ArrayDeque<>();
    private Object player,world,connection;private Vec3 origin,latest;private long issued,lastSerial,lastTick;private int count;private Goal goal;private boolean active;
    public PestTrackerTrace(Settings settings){this.settings=Objects.requireNonNull(settings);}
    public void begin(Object player,Object world,Object connection,Vec3 origin,long receiptSerial,long tick){
        clear();if(player==null||world==null||connection==null||!finite(origin))throw new IllegalArgumentException("Live tracker scope and actual native origin required");
        this.player=player;this.world=world;this.connection=connection;this.origin=origin;issued=lastSerial=receiptSerial;lastTick=tick;active=true;
    }
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    public boolean receive(Object player,Object world,Object connection,Vec3 currentPlayer,GardenTrackerParticles.Receipt r,long tick){
        if(!active||this.player!=player||this.world!=world||this.connection!=connection||tick<lastTick||r.serial()<=issued||r.serial()<=lastSerial||!finite(currentPlayer)||!finite(r.position()))return false;
        lastSerial=r.serial();long age=tick*50-r.observed();if(!r.added()||age<0||age>settings.lifetimeTicks()*50L||currentPlayer.distanceTo(r.position())>=50
            ||settings.particles().stream().noneMatch(p->p.kind().equals(r.kind())&&p.options().equals(r.options())))return false;
        var previous=pending.isEmpty()?latest:pending.getLast().receipt().position();
        if(previous==null&&origin.distanceTo(r.position())>3||previous!=null&&previous.distanceTo(r.position())>8)return false;
        pending.addLast(new Pending(r,tick+1));return true;
    }
    public void tick(long tick){if(!active)return;if(tick<lastTick){clear();return;}lastTick=tick;
        while(!pending.isEmpty()&&pending.getFirst().readyTick()<=tick){var receipt=pending.removeFirst().receipt();latest=receipt.position();count++;points.put(latest,tick*50);
            points.entrySet().removeIf(e->tick*50-e.getValue()>10000);var samples=List.copyOf(points.keySet());
            if(samples.size()>5)points.remove(samples.getFirst());
            if((samples.size()>4||count>settings.eventThreshold())&&samples.size()>1){int half=samples.size()/2;var a=mean(samples.subList(0,half));var b=mean(samples.subList(half,samples.size()));
                double dx=b.x()-a.x(),dy=b.y()-a.y(),dz=b.z()-a.z(),length=Math.sqrt(dx*dx+dy*dy+dz*dz);
                if(length>.0001){var direction=new Vec3(dx/length,dy/length,dz/length);var center=mean(samples);double advance=length*settings.predictionScale();
                    goal=new Goal(a,direction,new Vec3(center.x()+direction.x()*advance,center.y()+direction.y()*advance,center.z()+direction.z()*advance),latest,count,receipt.serial());}}
        }
    }
    private static Vec3 mean(List<Vec3> points){double x=0,y=0,z=0;for(var p:points){x+=p.x();y+=p.y();z+=p.z();}return new Vec3(x/points.size(),y/points.size(),z/points.size());}
    public Optional<Goal> goal(){return Optional.ofNullable(goal);}public Optional<Vec3> latest(){return Optional.ofNullable(latest);}public int events(){return count;}
    public void clear(){active=false;player=world=connection=null;origin=latest=null;points.clear();pending.clear();goal=null;count=0;issued=lastSerial=lastTick=0;}
}
