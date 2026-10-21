package dev.veltraph.client.fishing.worm;
import java.util.*;
/** Exact observed owned-reel spawn/take/inventory join, never permission from inventory timing alone. */
public final class WormOwnedCatch {
    public record Scope(Object owner,Object player,Object world,Object connection,long lease){public Scope{Objects.requireNonNull(owner);Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(connection);}public boolean same(Scope other){return other!=null&&owner==other.owner&&player==other.player&&world==other.world&&connection==other.connection&&lease==other.lease;}}
    public record Cell(Object unit,int count,Object full){public Cell{Objects.requireNonNull(unit);Objects.requireNonNull(full);if(count<0)throw new IllegalArgumentException();}}
    public record Spawn(UUID uuid,int id,Object unit,int count,long at,boolean newlySpawned,boolean atOwnedHook,boolean uniqueOrigin,boolean knownForeignOrigin){}
    public record Take(UUID uuid,int id,Object unit,int count,long at,boolean localPlayer,boolean nativeApplied){}
    public record Batch(Scope scope,List<Cell> before,List<Cell> after,long at,boolean serverApplied){public Batch{before=List.copyOf(before);after=List.copyOf(after);}}
    public record Proof(int slot,Cell before,Cell after,UUID item){}
    private final Scope scope;private final long issued,deadline;private final int rod,tool;
    private final Set<UUID> consumed=new HashSet<>();
    public WormOwnedCatch(Scope scope,long issued,long deadline,int rod,int tool){if(deadline<=issued||rod<0||rod>8||tool<0||tool>8||rod==tool)throw new IllegalArgumentException();this.scope=scope;this.issued=issued;this.deadline=deadline;this.rod=rod;this.tool=tool;}
    public Optional<Proof> correlate(Scope current,List<Cell> reserved,Spawn spawn,Take take,Batch batch,boolean ownedHookRemoved){
        if(!scope.same(current)||!scope.same(batch.scope)||!ownedHookRemoved||!batch.serverApplied||reserved.size()!=36||batch.before.size()!=36||batch.after.size()!=36||!reserved.equals(batch.before))return Optional.empty();
        if(spawn==null||take==null||spawn.uuid==null||!spawn.uuid.equals(take.uuid)||spawn.id!=take.id||spawn.id<0||consumed.contains(spawn.uuid)||!spawn.newlySpawned||!spawn.atOwnedHook||!spawn.uniqueOrigin||spawn.knownForeignOrigin||!take.localPlayer||!take.nativeApplied||!Objects.equals(spawn.unit,take.unit)||take.count<=0||take.count>spawn.count)return Optional.empty();
        if(spawn.at<issued||take.at<spawn.at||batch.at<issued||spawn.at>deadline||take.at>deadline||batch.at>deadline)return Optional.empty();
        int slot=-1;for(int i=0;i<36;i++)if(!batch.before.get(i).equals(batch.after.get(i))){if(slot>=0||i==rod||i==tool)return Optional.empty();slot=i;}
        if(slot<0)return Optional.empty();var before=batch.before.get(slot);var after=batch.after.get(slot);
        if(after.count-before.count!=take.count||!Objects.equals(after.unit,spawn.unit)||before.count>0&&!Objects.equals(before.unit,after.unit))return Optional.empty();
        return Optional.of(new Proof(slot,before,after,spawn.uuid));
    }
    public boolean consume(Scope current,Proof proof){return scope.same(current)&&proof!=null&&consumed.add(proof.item);}
}
