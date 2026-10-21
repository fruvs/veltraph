package dev.veltraph.client.inventory.selling;

import com.google.gson.JsonParser;
import dev.veltraph.client.config.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.garden.pests.philip.SourceContactCommand;
import dev.veltraph.client.world.Vec3;
import java.math.BigDecimal;
import java.util.*;

/** All source Hub inputs are supplied contracts. The Garden command landing is independent of the saved farm pose. */
public record CapturedGeorgeContract(GeorgeSourceHubProfile hub, GeorgeWarpPermit.Destination garden,
        GeorgeBatchPlan plan, List<SellingRule> rules, SourceContactCommand.Settings dialogue,
        SellingRuntime.Settings selling, String currencyPrefix, SellingAcknowledgment.Denomination denomination,
        long maintenanceTimeoutMs) {
    public CapturedGeorgeContract {
        rules=List.copyOf(rules);
        Objects.requireNonNull(hub);Objects.requireNonNull(garden);Objects.requireNonNull(plan);
        Objects.requireNonNull(dialogue);Objects.requireNonNull(selling);Objects.requireNonNull(denomination);
        if(garden.area()!=GameArea.GARDEN||!dialogue.title().equals(selling.menuTitle())||currencyPrefix==null||currencyPrefix.isBlank()
                ||maintenanceTimeoutMs<1000||maintenanceTimeoutMs>1_200_000)
            throw new IllegalArgumentException("Explicit bounded Hub, NPC sale and independent Garden landing required");
    }
    public static GeorgeWarpPermit.Destination parseGardenLanding(String json) {
        var o=JsonParser.parseString(json).getAsJsonObject();
        if(!o.keySet().equals(Set.of("dimension","x","y","z","radius")))throw new IllegalArgumentException("Exact independent Garden landing fields required");
        var dimension=o.get("dimension");
        if(dimension==null||!dimension.isJsonPrimitive()||!dimension.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Literal Garden dimension required");
        return new GeorgeWarpPermit.Destination(GameArea.GARDEN,dimension.getAsString(),new Vec3(number(o,"x"),number(o,"y"),number(o,"z")),number(o,"radius"));
    }
    private static double number(com.google.gson.JsonObject o,String field) {
        var value=o.get(field);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Native numeric Garden "+field+" required");
        return value.getAsDouble();
    }
    public static CapturedGeorgeContract read(ConfigService config) {
        if(!config.get(AutoSellMaintenanceConfig.AUTOSELL_MAINTENANCE_ENABLED)||!config.get(MiscConfig.AUTO_SELL_ENABLED)
                ||config.get(MiscConfig.AUTO_SELL_MODE)!=AutoSellMode.GEORGE_PETS||config.get(MiscConfig.AUTO_SELL_SACKS))
            throw new IllegalArgumentException("Explicit George farm auto-sell with no sacks required");
        var selling=new SellingRuntime.Settings(AutoSellMode.GEORGE_PETS,config.get(SellingConfig.SELLING_GEORGE_MENU_TITLE),
                config.get(SellingConfig.SELLING_GEORGE_ACCEPT_LABEL),config.get(SellingConfig.SELLING_GEORGE_CONFIRM_TITLE),
                config.get(SellingConfig.SELLING_GEORGE_CONFIRM_LABEL),config.get(SellingConfig.SELLING_GEORGE_DEPOSIT_SLOT),
                config.get(SellingConfig.SELLING_GEORGE_CONFIRM_PET_SLOT),config.get(SellingConfig.SELLING_GEORGE_RECEIPT_TEMPLATE),
                config.get(SellingConfig.SELLING_MAX_QUANTITY),BigDecimal.valueOf(config.get(SellingConfig.SELLING_MIN_PROCEEDS)),
                BigDecimal.valueOf(config.get(SellingConfig.SELLING_MAX_PROCEEDS)),BigDecimal.valueOf(config.get(SellingConfig.SELLING_TRASH_MAX_VALUE)),
                config.get(SellingConfig.SELLING_DELAY_TICKS),config.get(SellingConfig.SELLING_TIMEOUT_TICKS));
        return new CapturedGeorgeContract(GeorgeSourceHubProfile.parse(config.get(GeorgeBatchConfig.GEORGE_SOURCE_HUB_PROFILE)),
                parseGardenLanding(config.get(GeorgeBatchConfig.GEORGE_GARDEN_WARP_LANDING)),
                GeorgeBatchPlan.parse(config.get(GeorgeBatchConfig.GEORGE_BATCH_PROFILE)),SellingRule.parse(config.get(SellingConfig.SELLING_RULES_JSON)),
                GeorgeContactProfile.parse(config.get(SellingConfig.SELLING_GEORGE_CONTACT_JSON)),selling,
                config.get(SellingConfig.SELLING_CURRENCY_PREFIX),config.get(SellingConfig.SELLING_CURRENCY_DENOMINATION),
                config.get(AutoSellMaintenanceConfig.AUTOSELL_MAINTENANCE_TIMEOUT_MS));
    }
    public FrozenSale freeze(SellingObservation observation) {
        var entries=plan.freeze(rules,observation,selling);
        // Pin configured-rule mode to the actual Garden approvals before any outgoing command.
        var pinned=new GeorgeBatchPlan(plan.maxPets(),plan.maxProceeds(),plan.timeoutTicks(),plan.uuidPath(),
                entries.stream().map(e->new GeorgeBatchPlan.Pet(e.rule().inventoryIndex(),e.uuid())).toList());
        return new FrozenSale(pinned,entries.stream().map(GeorgeBatchPlan.Entry::rule).toList(),observation.ledger(),observation.coins());
    }
    public record FrozenSale(GeorgeBatchPlan plan,List<SellingRule> rules,Map<Integer,String> ledger,BigDecimal purse) {
        public FrozenSale {rules=List.copyOf(rules);ledger=Map.copyOf(ledger);Objects.requireNonNull(purse);}
        public boolean unchanged(SellingObservation actual) {
            if(actual==null||actual.menu()!=null||!actual.cursorEmpty()||!ledger.equals(actual.ledger())||actual.coins()==null||purse.compareTo(actual.coins())!=0)return false;
            try {
                for(var pet:plan.pets()) {
                    var stack=actual.inventoryStack(pet.inventoryIndex()).orElse(null);
                    if(stack==null||!pet.uuid().equals(GeorgeBatchPlan.nativeUuid(stack.customData().get(plan.uuidPath())))
                            ||rules.stream().filter(r->r.inventoryIndex()==pet.inventoryIndex()&&r.matches(stack)).count()!=1
                            ||actual.inventory().stream().filter(i->!i.empty()&&pet.uuid().equals(GeorgeBatchPlan.nativeUuid(i.customData().get(plan.uuidPath())))).count()!=1)return false;
                }
                return true;
            }catch(RuntimeException invalid){return false;}
        }
    }
    /** Includes all mutable inputs affecting this owner; comparison never reparses or changes current approvals. */
    public static String settings(ConfigService config) {
        var values=new TreeMap<String,String>();
        try {for(var holder:List.of(SellingConfig.class,AutoSellMaintenanceConfig.class,GeorgeBatchConfig.class))for(var field:holder.getFields())
            if(field.get(null) instanceof ConfigKey<?> key)values.put(key.name(),String.valueOf(config.get(key)));}
        catch(IllegalAccessException error){throw new IllegalStateException(error);}
        values.put("AUTO_SELL_ENABLED",String.valueOf(config.get(MiscConfig.AUTO_SELL_ENABLED)));
        values.put("AUTO_SELL_MODE",String.valueOf(config.get(MiscConfig.AUTO_SELL_MODE)));
        values.put("AUTO_SELL_SACKS",String.valueOf(config.get(MiscConfig.AUTO_SELL_SACKS)));
        return values.toString();
    }
}
