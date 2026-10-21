package dev.veltraph.client.mining.commission;

import dev.veltraph.client.mining.vein.MiningMaterial;
import java.util.*;
import static dev.veltraph.client.mining.commission.CommissionObservation.*;

/** Work compatibility is independent from the native worker's action permissions. */
public final class CommissionWorkBinding {
    private CommissionWorkBinding() {}
    public record Identity(String name,Work work,String region,String resource) {
        static Identity of(Task task){return new Identity(task.name(),task.work(),task.region(),task.resource());}
    }
    public record Binding(List<Identity> tasks,Map<MiningMaterial,Integer> priorities) {
        public Binding {tasks=List.copyOf(tasks);priorities=Map.copyOf(priorities);if(tasks.isEmpty()||priorities.isEmpty())throw new IllegalArgumentException("No bound work");}
        public boolean current(CommissionObservation observation){
            if(observation==null||!observation.completeSection())return false;
            // Any completed task pauses native work so it cannot continue past a claim transition.
            if(observation.tasks().stream().anyMatch(Task::done))return false;
            for(var identity:tasks){int matching=0;for(var task:observation.tasks())if(Identity.of(task).equals(identity)){if(!task.active())return false;matching++;}if(matching!=1)return false;}
            return true;
        }
    }
    /** Regional/geological membership, mob identity and tunnel resource contracts are not captured. */
    public static Optional<Binding> choose(CommissionObservation observation){
        if(observation==null||!observation.completeSection()||observation.tasks().stream().anyMatch(Task::done))return Optional.empty();
        var supported=observation.tasks().stream().filter(Task::active).filter(t->t.region().isEmpty()&&(t.work()==Work.MITHRIL||t.work()==Work.TITANIUM)).toList();
        if(supported.isEmpty())return Optional.empty();
        var identities=new ArrayList<Identity>();boolean titanium=false,mithril=false;
        for(var task:supported){var identity=Identity.of(task);if(identities.contains(identity))return Optional.empty();identities.add(identity);titanium|=task.work()==Work.TITANIUM;mithril|=task.work()==Work.MITHRIL;}
        // Mithril mining can reveal Titanium. Such incidental work is explicitly part of the
        // Titanium worker, but it never advances the task's percent without a native update.
        var priorities=new EnumMap<MiningMaterial,Integer>(MiningMaterial.class);
        priorities.put(MiningMaterial.GRAY_MITHRIL,mithril?10:3);priorities.put(MiningMaterial.GREEN_MITHRIL,mithril?6:2);priorities.put(MiningMaterial.BLUE_MITHRIL,mithril?3:1);
        if(titanium)priorities.put(MiningMaterial.TITANIUM,20);
        return Optional.of(new Binding(identities,priorities));
    }
    public static String unavailable(CommissionObservation observation){
        if(observation==null||!observation.completeSection())return "Complete native commission section required";
        if(observation.tasks().stream().anyMatch(Task::done))return "Commission completed; live claim protocol UNKNOWN";
        if(observation.tasks().stream().anyMatch(t->t.active()&&!t.region().isEmpty()))return "Regional target membership UNKNOWN; no archived coordinate permission";
        if(observation.tasks().stream().anyMatch(t->t.active()&&t.work()==Work.MOB))return "Commission mob identity and kill attribution UNKNOWN";
        return "No uniquely observed supported global ore commission";
    }
}
