package dev.veltraph.client.navigation;
/** Pure elapsed-time checks; the controller supplies original private starts and its actual clocks. */
public final class CapturedProfileFinalDeadline {
    private CapturedProfileFinalDeadline(){}
    public enum Result { CURRENT, EPISODE_EXPIRED, TERMINAL_EXPIRED, CLOCK_ROLLBACK }
    public static Result check(long now,long episodeAt,long terminalAt,boolean episodeActive) {
        if(episodeActive&&episodeAt!=0) {
            long elapsed=now-episodeAt;if(elapsed<0)return Result.CLOCK_ROLLBACK;
            if(elapsed>1_800_000_000L)return Result.EPISODE_EXPIRED;
        }
        if(terminalAt!=0) {
            long elapsed=now-terminalAt;if(elapsed<0)return Result.CLOCK_ROLLBACK;
            if(elapsed>4_000_000_000L)return Result.TERMINAL_EXPIRED;
        }
        return Result.CURRENT;
    }
}
