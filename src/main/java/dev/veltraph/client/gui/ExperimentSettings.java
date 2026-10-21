package dev.veltraph.client.gui;

/** Local menu labels are explicit adaptations; original salted labels were not recovered. */
public record ExperimentSettings(long clickDelayMillis,long acknowledgmentTimeoutMillis,
                                 String showLabel,String inputLabel,String hiddenLabel,
                                 String rewardLabel,String completedLabel,String failedLabel,boolean maximizeXp,RewardThreshold rewardThreshold,boolean avoidXpSuperpairs,SuperpairsRewardBindings superpairsRewards) {
    /** Bound from a server tier quote or explicit local solver profile; no inferred lore/title. */
    public record RewardThreshold(int rounds,String rewardMenuTitle) {
        public RewardThreshold {
            rewardMenuTitle=MenuSnapshot.clean(rewardMenuTitle);
            if(rounds<0||rewardMenuTitle.isBlank())throw new IllegalArgumentException("Reward threshold needs a nonnegative count and exact reward title");
        }
    }
    public ExperimentSettings(long delay,long timeout,String show,String input,String hidden,String reward,String completed,String failed,boolean maximizeXp,RewardThreshold threshold) {
        this(delay,timeout,show,input,hidden,reward,completed,failed,maximizeXp,threshold,false,SuperpairsRewardBindings.empty());
    }
    public ExperimentSettings withSuperpairsRewards(boolean avoid,SuperpairsRewardBindings bindings) {
        return new ExperimentSettings(clickDelayMillis,acknowledgmentTimeoutMillis,showLabel,inputLabel,hiddenLabel,rewardLabel,completedLabel,failedLabel,maximizeXp,rewardThreshold,avoid,bindings);
    }
    public ExperimentSettings(long delay,long timeout,String show,String input,String hidden,String reward,String completed,String failed) {
        this(delay,timeout,show,input,hidden,reward,completed,failed,false,null);
    }
    public ExperimentSettings withRewardThreshold(RewardThreshold threshold) {
        return new ExperimentSettings(clickDelayMillis,acknowledgmentTimeoutMillis,showLabel,inputLabel,hiddenLabel,rewardLabel,completedLabel,failedLabel,maximizeXp,threshold,avoidXpSuperpairs,superpairsRewards);
    }
    public ExperimentSettings {
        java.util.Objects.requireNonNull(superpairsRewards);
        showLabel=MenuSnapshot.clean(showLabel);inputLabel=MenuSnapshot.clean(inputLabel);
        hiddenLabel=MenuSnapshot.clean(hiddenLabel);rewardLabel=MenuSnapshot.clean(rewardLabel);
        completedLabel=MenuSnapshot.clean(completedLabel);failedLabel=MenuSnapshot.clean(failedLabel);
        if(clickDelayMillis<0||acknowledgmentTimeoutMillis<100) throw new IllegalArgumentException("Invalid experiment timing");
        if(java.util.stream.Stream.of(showLabel,inputLabel,hiddenLabel,rewardLabel,completedLabel,failedLabel)
                .anyMatch(String::isBlank)) throw new IllegalArgumentException("Experiment labels must be nonblank");
    }
    public static ExperimentSettings defaults() {
        return new ExperimentSettings(300,5000,"Remember the pattern!","Timer:","?","Claim Rewards","Rewards Claimed","Experiment Failed");
    }
}
