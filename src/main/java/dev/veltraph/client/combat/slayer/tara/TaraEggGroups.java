package dev.veltraph.client.combat.slayer.tara;

import java.util.*;

/** Source ArmorStand grouping/aim bookkeeping only. These bounds are never native colliders or reach. */
public final class TaraEggGroups {
    public record Stand(UUID id,double x,double y,double z) {}
    public record Group(List<UUID> members,UUID median,double centerX,double centerZ,double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        public Group {members=List.copyOf(members);}
    }
    private record Column(double x,double z) {}
    private TaraEggGroups(){}
    public static double sourceTenth(double value){return (double)(int)(value*10)/10;}
    public static List<Group> plan(double bossX,double bossY,double bossZ,List<Stand> eligible){
        var columns=new LinkedHashMap<Column,List<Stand>>();var identities=new HashSet<UUID>();
        for(var stand:eligible){if(stand.id()==null||!Double.isFinite(stand.x())||!Double.isFinite(stand.y())||!Double.isFinite(stand.z())||!identities.add(stand.id()))throw new IllegalArgumentException("Group requires finite distinct native stands");
            if(stand.x()<bossX-5||stand.x()>bossX+5||stand.z()<bossZ-5||stand.z()>bossZ+5||stand.y()<bossY||stand.y()>bossY+10)continue;
            columns.computeIfAbsent(new Column(sourceTenth(stand.x()),sourceTenth(stand.z())),key->new ArrayList<>()).add(stand);
        }
        var result=new ArrayList<Group>();for(var values:columns.values()){
            values.sort(Comparator.comparingDouble(Stand::y));double x=values.stream().mapToDouble(Stand::x).average().orElseThrow(),z=values.stream().mapToDouble(Stand::z).average().orElseThrow();
            result.add(new Group(values.stream().map(Stand::id).toList(),values.get(values.size()/2).id(),x,z,x-.35,values.getFirst().y()+.45,z-.35,x+.35,values.getLast().y()+1.35,z+.35));
        }return List.copyOf(result);
    }
}
