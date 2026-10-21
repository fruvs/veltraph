package dev.veltraph.client.combat.grind;
import dev.veltraph.client.service.InventoryService;
import java.util.function.BooleanSupplier;
import java.util.Objects;
/** The native adapter supplies exact enabled parent/child custody; readiness cannot select a slot. */
public final class OwnedWeaponStart {
    private final BestiaryWeaponStart start;
    private final BooleanSupplier authority;
    private java.util.List<?> loadout;
    public OwnedWeaponStart(InventoryService inventory, BooleanSupplier authority) {
        start=new BestiaryWeaponStart(inventory);this.authority=Objects.requireNonNull(authority);
    }
    public void prepare(BestiaryWeaponStart.Frame frame, BestiaryWeaponSelector.Choice choice){prepare(frame,choice,frame==null?java.util.List.of():frame.hotbar());}
    public void prepare(BestiaryWeaponStart.Frame frame, BestiaryWeaponSelector.Choice choice, java.util.List<?> exactLoadout){
        loadout=exactLoadout==null?null:java.util.List.copyOf(exactLoadout);start.prepare(frame,choice);
    }
    public BestiaryWeaponStart.Proposal proposal(){return start.proposal();}
    public void cancel(){loadout=null;start.cancel();}
    /** Fishing declares a parent policy branch, not the contextual source interface marker. */
    public static java.util.Optional<BestiaryWeaponSelector.Choice> selectParentPolicy(
            MeleeGrindModule.ParentWeaponDeclaration declaration,CombatWeaponPolicy policy,int fixedSlot,
            java.util.List<BestiaryWeaponSelector.Candidate> observed,String globalPreferred){
        if(declaration!=MeleeGrindModule.ParentWeaponDeclaration.FISHING_AUTO_KILLER||policy==null
                ||policy.mode()==CombatWeaponPolicy.Mode.CONTEXTUAL
                ||policy.mode()==CombatWeaponPolicy.Mode.FIXED_SLOT&&fixedSlot!=0)return java.util.Optional.empty();
        return policy.select(observed,fixedSlot,globalPreferred);
    }
    public static String parentPolicySignature(MeleeGrindModule.ParentWeaponDeclaration declaration,
            CombatWeaponPolicy policy,int fixedSlot,String rawSignature,String globalPreferred){
        if(declaration!=MeleeGrindModule.ParentWeaponDeclaration.FISHING_AUTO_KILLER||policy==null
                ||policy.mode()==CombatWeaponPolicy.Mode.CONTEXTUAL||rawSignature==null||globalPreferred==null
                ||policy.mode()==CombatWeaponPolicy.Mode.FIXED_SLOT&&fixedSlot!=0)
            throw new IllegalArgumentException("Fishing parent weapon policy requires common or native slot zero and a frozen signature");
        return new com.google.gson.Gson().toJson(java.util.List.of(declaration.name(),policy.signature(fixedSlot,globalPreferred),fixedSlot,rawSignature,globalPreferred));
    }
    public boolean commit(BestiaryWeaponStart.Frame frame){return commit(frame,frame==null?java.util.List.of():frame.hotbar());}
    public boolean commit(BestiaryWeaponStart.Frame frame, java.util.List<?> currentLoadout){
        if(!authority.getAsBoolean()||loadout==null||!loadout.equals(currentLoadout)){cancel();return false;}
        loadout=null;return start.commit(frame);
    }
}
