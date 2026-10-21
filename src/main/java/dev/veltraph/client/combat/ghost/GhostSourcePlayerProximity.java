package dev.veltraph.client.combat.ghost;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/** Source RNG flag producer; ordinary player positions only, including local player as the original list does. */
public final class GhostSourcePlayerProximity {
    public record PlayerPoint(Object identity,double distance){public PlayerPoint {Objects.requireNonNull(identity);if(!Double.isFinite(distance)||distance<0)throw new IllegalArgumentException("Actual native player distance required");}}
    private final IdentityHashMap<Object,Integer> scores=new IdentityHashMap<>();
    private boolean randomized;private long revision;
    public void observe(List<PlayerPoint> actualPlayers){
        Objects.requireNonNull(actualPlayers);scores.keySet().removeIf(p->actualPlayers.stream().noneMatch(v->v.identity()==p));
        for(var p:actualPlayers){
            if(p.distance()<3)scores.put(p.identity(),Math.min(scores.getOrDefault(p.identity(),0)+2,100));
            else if(scores.containsKey(p.identity())){int next=scores.get(p.identity())-1;if(next<=0)scores.remove(p.identity());else scores.put(p.identity(),next);}
        }
        randomized=scores.values().stream().anyMatch(v->v>80);revision++;
    }
    public boolean randomized(){return randomized;}
    public long revision(){return revision;}
    public int score(Object player){return scores.getOrDefault(player,0);}
}
