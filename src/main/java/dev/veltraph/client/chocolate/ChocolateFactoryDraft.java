package dev.veltraph.client.chocolate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
/** Interactive local capture; literal prefixes and suffixes are chosen by the user from actual rows. */
public final class ChocolateFactoryDraft {
    private final String scope,title;private final ChocolateFactoryProfile.Marker profileMarker;private final Map<String,ChocolateFactoryProfile.Field> fields=new HashMap<>();private final Map<String,ChocolateFactoryProfile.Upgrade> upgrades=new LinkedHashMap<>();
    public ChocolateFactoryDraft(String scope,ChocolateFactoryObservation o,int slot,int profileLoreLine){
        if(o==null)throw new IllegalArgumentException("Open the actual native Factory menu first");var row=o.rows().get(slot);if(row==null||row.playerInventory()||profileLoreLine<0||profileLoreLine>=row.lore().size())throw new IllegalArgumentException("Choose a native profile-specific lore line from /vchocolate show");
        this.scope=scope;title=o.title();profileMarker=new ChocolateFactoryProfile.Marker(slot,row.itemId(),row.name(),List.of(row.lore().get(profileLoreLine)));
    }
    private static ChocolateFactoryProfile.Marker marker(ChocolateFactoryObservation.Row row){if(row==null||row.playerInventory()||row.count()<1)throw new IllegalArgumentException("Choose an occupied native menu row");return new ChocolateFactoryProfile.Marker(row.slot(),row.itemId(),row.name(),List.of());}
    public void upgrade(String id,ChocolateFactoryProfile.Kind kind,ChocolateFactoryObservation o,int slot,int actionLoreLine){if(!id.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("Upgrade ID must be a short local key");var row=o.rows().get(slot);var m=marker(row);if(actionLoreLine<0||actionLoreLine>=row.lore().size())throw new IllegalArgumentException("Choose exact observed action cue lore line");m=new ChocolateFactoryProfile.Marker(m.slot(),m.itemId(),m.name(),List.of(row.lore().get(actionLoreLine)));upgrades.put(id,new ChocolateFactoryProfile.Upgrade(id,kind,m,null,null,null,null));}
    public void field(String role,ChocolateFactoryObservation o,int slot,ChocolateFactoryProfile.Source source,String prefix,String suffix){
        var m=marker(o.rows().get(slot));if(source==ChocolateFactoryProfile.Source.NAME)m=new ChocolateFactoryProfile.Marker(m.slot(),m.itemId(),m.name(),List.of(),prefix,suffix);
        var f=new ChocolateFactoryProfile.Field(m,prefix,suffix,source);if(f.read(o.rows()).isEmpty())throw new IllegalArgumentException("Literal field does not yield one exact visible number");
        if(role.contains(".")){var split=role.split("\\.",-1);if(split.length!=2||!Set.of("level","price").contains(split[1]))throw new IllegalArgumentException("Upgrade field requires id.level or id.price");var u=upgrades.get(split[0]);if(u==null)throw new IllegalArgumentException("Capture upgrade first");var button=source==ChocolateFactoryProfile.Source.NAME&&split[1].equals("level")?new ChocolateFactoryProfile.Marker(m.slot(),m.itemId(),m.name(),u.button().requiredLore(),prefix,suffix):u.button();upgrades.put(u.id(),new ChocolateFactoryProfile.Upgrade(u.id(),u.kind(),button,split[1].equals("level")?f:u.level(),split[1].equals("price")?f:u.price(),u.observedProductionDelta(),u.deltaAssumption(),u.rateEvidence()));}
        else{if(!Set.of("balance","production","capacity","prestigeProgress","prestigeGoal","rabbitsOwned","rabbitCapacity","collectionTotal","chocobits").contains(role))throw new IllegalArgumentException("Unknown field role");fields.put(role,f);}
    }
    public void delta(String id,BigDecimal delta,String assumption,ChocolateFactoryObservation o){
        var u=upgrades.get(id);var production=fields.get("production");if(u==null||u.level()==null||u.price()==null||production==null||o==null||delta.signum()<=0||assumption.isBlank())throw new IllegalArgumentException("Capture upgrade level/price and production, then supply a positive final-rate delta and evidence/assumption");
        var baseline=production.read(o.rows());var level=u.level().read(o.rows());var price=u.price().read(o.rows());var row=o.rows().get(u.button().slot());if(baseline.isEmpty()||level.isEmpty()||price.isEmpty()||!u.button().matches(row))throw new IllegalArgumentException("Fresh exact upgrade and production evidence required");
        var evidence=new ChocolateFactoryProfile.RateEvidence(level.get().intValueExact(),price.get(),baseline.get(),row.fullIdentity().toString(),o.observedMillis());
        upgrades.put(id,new ChocolateFactoryProfile.Upgrade(id,u.kind(),u.button(),u.level(),u.price(),delta,assumption,evidence));
    }
    public ChocolateFactoryProfile build(){return new ChocolateFactoryProfile(1,scope,LocalDate.now().toString(),title,profileMarker,fields.get("balance"),fields.get("production"),fields.get("capacity"),fields.get("prestigeProgress"),fields.get("prestigeGoal"),List.copyOf(upgrades.values()),5000,10000,fields.get("rabbitsOwned"),fields.get("rabbitCapacity"),fields.get("collectionTotal"),fields.get("chocobits"),false);}
}
