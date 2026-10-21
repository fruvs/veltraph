package dev.veltraph.client.combat.grind;

/** Only issued native attacks authorize bounded vanilla durability wear; other components stay exact. */
public final class BestiaryToolWear {
    private BestiaryToolWear() {}
    public static boolean allowed(int originalCount, int count, int initialDamage, int currentDamage,
                                  long issuedAttacks, boolean sameOtherComponents) {
        return sameOtherComponents && originalCount > 0 && originalCount == count && issuedAttacks >= 0
                && initialDamage >= 0 && currentDamage >= initialDamage
                && (long) currentDamage - initialDamage <= Math.min(Integer.MAX_VALUE, issuedAttacks) * 2;
    }
}
