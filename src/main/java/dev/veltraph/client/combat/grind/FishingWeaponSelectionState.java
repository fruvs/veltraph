package dev.veltraph.client.combat.grind;
/** Typed data decision. Only the native adapter's private actual setter boundary supplies AFTER_NATIVE_SETTER. */
public final class FishingWeaponSelectionState {
    private FishingWeaponSelectionState(){}
    public enum Stage { ORIGINAL, AFTER_NATIVE_SETTER, COMMITTED }
    public enum Phase { ORIGINAL_ROD, NATIVE_SETTER_RETURNED, COMMITTED_TOOL, REFUSED }
    public record Frame(Stage stage,boolean sameSource,boolean historyCurrent,boolean confirmedNativeLoan,int rodSlot,int toolSlot,int selectedSlot){}
    public static Phase observe(Frame frame){
        if(frame==null||frame.stage()==null||!frame.sameSource()||!frame.historyCurrent()||frame.rodSlot()<0||frame.rodSlot()>8||frame.selectedSlot()<0||frame.selectedSlot()>8)return Phase.REFUSED;
        return switch(frame.stage()){
            case ORIGINAL -> frame.selectedSlot()==frame.rodSlot()?Phase.ORIGINAL_ROD:Phase.REFUSED;
            case AFTER_NATIVE_SETTER -> frame.toolSlot()>=0&&frame.toolSlot()<9&&frame.selectedSlot()==frame.toolSlot()?Phase.NATIVE_SETTER_RETURNED:Phase.REFUSED;
            case COMMITTED -> frame.confirmedNativeLoan()&&frame.toolSlot()>=0&&frame.toolSlot()<9&&frame.selectedSlot()==frame.toolSlot()?Phase.COMMITTED_TOOL:Phase.REFUSED;
        };
    }
}
