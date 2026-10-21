package dev.veltraph.client.navigation;
import java.util.List;
/** Exact offered-result identity within one active approach episode. Data never grants native effects. */
public final class GroundApproachCommitment {
    private Object episode,offered;private List<GroundJumpApproach.Key> offeredSequence=List.of(),remaining=List.of();
    public void begin(Object token){reset();episode=java.util.Objects.requireNonNull(token);}
    public void offer(Object token,Object result,List<GroundJumpApproach.Key> sequence){
        offered=null;offeredSequence=List.of();
        if(episode==null||token!=episode||result==null||sequence==null||sequence.isEmpty()||sequence.size()>3)return;
        offered=result;offeredSequence=List.copyOf(sequence);
    }
    public boolean commit(Object token,Object result){
        if(episode==null||token!=episode||result==null||result!=offered)return false;
        remaining=List.copyOf(offeredSequence.subList(1,offeredSequence.size()));offered=null;offeredSequence=List.of();return true;
    }
    public void refuse(Object token){if(token==episode){offered=null;offeredSequence=List.of();}}
    public boolean abandonTail(Object token){if(episode==null||token!=episode)return false;offered=null;offeredSequence=List.of();remaining=List.of();return true;}
    public List<GroundJumpApproach.Key> remaining(Object token){return token==episode?remaining:List.of();}
    public void reset(){episode=null;offered=null;offeredSequence=List.of();remaining=List.of();}
}
