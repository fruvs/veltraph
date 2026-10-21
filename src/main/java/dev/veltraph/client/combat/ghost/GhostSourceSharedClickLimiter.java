package dev.veltraph.client.combat.ghost;
/** Original shared wrapper limiter:1000ms strict cleanup,constructor10..14 cap,50ms spacing,record-before-key-emission. */
public final class GhostSourceSharedClickLimiter {
    public enum Result { ADMITTED, COUNT_LIMIT, SPACING, CLOCK_UNAVAILABLE }
    private final int maximum;private final java.util.ArrayList<Long> history=new java.util.ArrayList<>();
    public GhostSourceSharedClickLimiter(java.util.function.IntUnaryOperator constructionRandom){int draw=constructionRandom.applyAsInt(5);if(draw<0||draw>=5)throw new IllegalArgumentException("Source nextInt5 required");maximum=10+draw;}
    public synchronized Result admit(long actualNow){if(actualNow<0)return Result.CLOCK_UNAVAILABLE;history.removeIf(t->t<actualNow-1000);if(history.size()>=maximum)return Result.COUNT_LIMIT;long last=history.isEmpty()?0:history.getLast();if(actualNow-last<50)return Result.SPACING;history.add(actualNow);return Result.ADMITTED;}
    public synchronized int recordedCount(){return history.size();}
}
