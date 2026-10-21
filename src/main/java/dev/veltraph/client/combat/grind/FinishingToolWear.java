package dev.veltraph.client.combat.grind;
/** Only actual privately observed attack submissions may budget DAMAGE; all other components/counts stay exact. */
public final class FinishingToolWear {
    private FinishingToolWear(){}
    public static boolean allowed(int originalCount,int currentCount,int originalDamage,int currentDamage,long actualSubmittedAttacks,boolean unchangedOtherComponents){
        return unchangedOtherComponents&&originalCount>0&&currentCount==originalCount&&originalDamage>=0&&currentDamage>=originalDamage
                &&actualSubmittedAttacks>=0&&(long)currentDamage-originalDamage<=actualSubmittedAttacks;
    }
}
