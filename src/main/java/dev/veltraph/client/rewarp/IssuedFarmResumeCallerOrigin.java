package dev.veltraph.client.rewarp;

import java.util.List;
import java.util.Set;

/** Pure refusal-only stack observation. An accepted stack supplies no checkpoint or native authority. */
public final class IssuedFarmResumeCallerOrigin {
    private IssuedFarmResumeCallerOrigin() {}
    public enum Operation { KEY_RELEASE, CAMERA_RETIRE, CROP_INSTALL, BREAKING_RETIRE }
    public record Frame(Class<?> declaringClass,String method) {}
    public static boolean matches(List<Frame> frames,Class<?> concreteFarm,Class<?> farmBase,
            Class<?> shapeEngine,Set<Class<?>> producerInternals,Operation operation) {
        if(frames==null||frames.isEmpty()||frames.size()>48||concreteFarm==null||farmBase==null||shapeEngine==null
                ||producerInternals==null||operation==null||frames.stream().anyMatch(f->f==null||f.declaringClass()==null||f.method()==null))return false;
        var first=frames.stream().filter(f->!producerInternals.contains(f.declaringClass())).findFirst().orElse(null);
        if(first==null)return false;
        boolean family=frames.stream().anyMatch(f->f.declaringClass()==concreteFarm&&f.method().equals("onResumeIssuedReturn"));
        var source=frames.stream().filter(f->f.declaringClass()==farmBase&&Set.of("resumeAfterRewarp","resumeIssuedReturn","rebindForScheduledRest").contains(f.method())).findFirst().orElse(null);
        if(!family||source==null||!source.method().equals("resumeIssuedReturn")
                ||frames.stream().anyMatch(f->f.declaringClass()==concreteFarm&&Set.of("onEnable","onDisableFarming","onTickFarming").contains(f.method())))return false;
        if(operation==Operation.CROP_INSTALL)return first.declaringClass()==farmBase&&first.method().equals("installFarmTargetGuard");
        if(operation==Operation.CAMERA_RETIRE)return first.declaringClass()==concreteFarm&&first.method().equals("onResumeIssuedReturn");
        if(first.declaringClass()==shapeEngine)return frames.stream().anyMatch(f->f.declaringClass()==shapeEngine&&f.method().equals("resumeFromObservedPosition"));
        return first.declaringClass()==concreteFarm||first.declaringClass().getEnclosingClass()==concreteFarm;
    }
}
