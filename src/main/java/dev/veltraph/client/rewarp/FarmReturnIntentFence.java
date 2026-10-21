package dev.veltraph.client.rewarp;
import java.util.UUID;
/** Pure comparison only. These values cannot create a Root intent, source checkpoint or native receipt. */
public final class FarmReturnIntentFence {
    private FarmReturnIntentFence() {}
    public record Header(Object root,Object store,Object farm,Object player,Object world,Object connection,Object options,
                         UUID session,UUID playerId,String profile,long requestedAt) {}
    public static boolean current(Header original,Header actual,long now,boolean originalHistoryCurrent){
        return originalHistoryCurrent&&original!=null&&actual!=null&&original.root()!=null&&original.root()==actual.root()
            &&original.store()!=null&&original.store()==actual.store()&&original.farm()!=null&&original.farm()==actual.farm()
            &&original.player()!=null&&original.player()==actual.player()&&original.world()!=null&&original.world()==actual.world()
            &&original.connection()!=null&&original.connection()==actual.connection()&&original.options()!=null&&original.options()==actual.options()
            &&original.session()!=null&&original.session().equals(original.playerId())&&original.session().equals(actual.session())
            &&original.playerId().equals(actual.playerId())&&original.profile()!=null&&!original.profile().isBlank()&&original.profile().equals(actual.profile())
            &&original.requestedAt()>=0&&now>=original.requestedAt()&&now-original.requestedAt()<2000;
    }
}
