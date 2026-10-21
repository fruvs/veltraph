package dev.veltraph.client.hunting;

/** Cleanup may undo only the run's own temporary selection while native tool custody survives. */
final class ShardSelectionCustody {
    private ShardSelectionCustody() {}
    static boolean restore(boolean sameContext,boolean originalToolExact,int original,int owned,int selected) {
        return sameContext&&originalToolExact&&original>=0&&original<9&&owned>=0&&owned<9&&selected==owned;
    }
}
