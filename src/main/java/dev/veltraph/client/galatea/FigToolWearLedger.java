package dev.veltraph.client.galatea;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
import java.util.function.BooleanSupplier;
import dev.veltraph.client.galatea.FigHarvestPickupLedger.Scope;

/** Narrow correlated native tool cost; it cannot ignore DAMAGE or authorize unrelated inventory. */
public final class FigToolWearLedger {
    public enum Evidence { CORRELATED_NATIVE_ACTION }
    public record Cell(int index,int count,int damage,Object full,Object withoutDamage,Object nativeTool,int damagePerBlock) {}
    public record Action(Scope scope,long attemptGeneration,long issueSerial,BlockPos target,Object originalLogState,Object targetGuard,
            int slot,Cell before,long epoch,String settings,long originalDeadline,long issued,int startSequence,long stopSerial,int stopSequence) {}
    public record Ack(long serial,long at,int upToSequence,boolean authoritativeAir) {}
    public record Receipt(Evidence evidence,Action action,Ack ack,long appliedInventorySerial,long appliedAt,Cell before,Cell after) {}
    public static final class Permit {
        private final Scope scope;private final long generation,epoch,deadline;private final String settings;
        private final List<?> before,after;private final List<Receipt> receipts;private boolean spent;
        private Permit(Scope s,long g,long e,String cfg,long d,List<?> before,List<?> after,List<Receipt> rs){scope=s;generation=g;epoch=e;settings=cfg;deadline=d;this.before=List.copyOf(before);this.after=List.copyOf(after);receipts=List.copyOf(rs);}
        public boolean matches(Object owner,Object player,Object world,Object connection,long gen,List<?> expected,List<?> projected){return !spent
                &&scope.owner()==owner&&scope.player()==player&&scope.world()==world&&scope.connection()==connection&&generation==gen&&before.equals(expected)&&after.equals(projected);}
        public boolean boundTo(long gen,long epoch,String settings,long deadline){return !spent&&generation==gen&&this.epoch==epoch&&Objects.equals(this.settings,settings)&&this.deadline==deadline;}
        public List<?> after(){return after;}public List<Receipt> receipts(){return receipts;}
    }
    private final List<Receipt> pending=new ArrayList<>();private final Set<Long> consumedOperations=new HashSet<>(),consumedDeliveries=new HashSet<>();
    public boolean stage(Receipt r){if(!valid(r)||consumedOperations.contains(r.action().issueSerial())||consumedDeliveries.contains(r.appliedInventorySerial())
            ||pending.stream().anyMatch(p->p.action().issueSerial()==r.action().issueSerial()||p.appliedInventorySerial()==r.appliedInventorySerial()))return false;pending.add(r);return true;}
    private boolean valid(Receipt r){if(r==null||r.evidence()!=Evidence.CORRELATED_NATIVE_ACTION||r.action()==null||r.ack()==null||r.before()==null||r.after()==null)return false;
        var a=r.action();var b=r.before();var z=r.after();int required=a.stopSerial()>0?a.stopSequence():a.startSequence();
        return a.scope()!=null&&a.scope().owner()!=null&&a.scope().player()!=null&&a.scope().world()!=null&&a.scope().connection()!=null
                &&a.attemptGeneration()>=0&&a.issueSerial()>0&&a.target()!=null&&a.originalLogState()!=null&&a.targetGuard()!=null&&a.startSequence()>=0
                &&a.before()!=null&&a.slot()>=0&&a.slot()<36&&a.epoch()>=0&&a.settings()!=null&&a.originalDeadline()>a.issued()&&a.originalDeadline()!=Long.MAX_VALUE
                &&a.stopSerial()>=0&&(a.stopSerial()==0?a.stopSequence()==-1:a.stopSerial()>a.issueSerial()&&a.stopSequence()>=a.startSequence())
                &&r.ack().serial()>0&&r.ack().authoritativeAir()&&(r.ack().upToSequence()==-1||required>=0&&r.ack().upToSequence()>=required)
                &&r.ack().at()>=a.issued()&&r.ack().at()<=a.originalDeadline()&&r.appliedInventorySerial()>0
                &&r.appliedAt()>=a.issued()&&r.appliedAt()<=a.originalDeadline()&&a.before().equals(b)
                &&b.index()==a.slot()&&z.index()==a.slot()&&b.count()>0&&b.count()==z.count()&&b.damage()>=0&&z.damage()>b.damage()
                &&b.damagePerBlock()>0&&b.damagePerBlock()==z.damagePerBlock()&&(long)z.damage()-b.damage()<=b.damagePerBlock()&&b.nativeTool()!=null&&Objects.equals(b.nativeTool(),z.nativeTool())
                &&b.full()!=null&&z.full()!=null&&b.withoutDamage()!=null&&Objects.equals(b.withoutDamage(),z.withoutDamage());
    }
    /** The result projects only the validated tool cell; caller must prove all remaining changes separately. */
    public Optional<Permit> review(Scope current,long generation,int axeSlot,List<?> original,List<?> actual,long now,long epoch,String settings,long deadline){
        if(current==null||original==null||actual==null||original.size()!=actual.size()||axeSlot<0||axeSlot>=original.size())return Optional.empty();
        var projected=new ArrayList<Object>(original);var used=new ArrayList<Receipt>();
        for(var r:pending.stream().sorted(Comparator.comparingLong(Receipt::appliedInventorySerial)).toList()){
            var a=r.action();if(!a.scope().same(current)||a.attemptGeneration()!=generation||a.slot()!=axeSlot||a.epoch()!=epoch||!Objects.equals(a.settings(),settings)
                    ||a.originalDeadline()!=deadline||now<a.issued()||now>deadline||!Objects.equals(projected.get(axeSlot),r.before().full()))continue;
            projected.set(axeSlot,r.after().full());used.add(r);
        }
        if(used.isEmpty()||!Objects.equals(projected.get(axeSlot),actual.get(axeSlot)))return Optional.empty();
        return Optional.of(new Permit(current,generation,epoch,settings,deadline,original,projected,used));
    }
    public boolean commit(Permit permit,Scope expectedCurrent,BooleanSupplier internalCas){
        if(permit==null||permit.spent||expectedCurrent==null||!permit.scope.same(expectedCurrent)||internalCas==null||!pending.containsAll(permit.receipts))return false;
        if(!internalCas.getAsBoolean())return false;
        permit.spent=true;for(var r:permit.receipts){consumedOperations.add(r.action().issueSerial());consumedDeliveries.add(r.appliedInventorySerial());pending.remove(r);}return true;
    }
    public List<Receipt> receipts(){return List.copyOf(pending);}
    public void clear(){pending.clear();consumedOperations.clear();consumedDeliveries.clear();}
}
