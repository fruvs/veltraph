package dev.veltraph.client.mixinbootstrap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethodApplicatorExtension;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Actual pinned extension registry/selection lifecycle, without global bootstrap or Minecraft target loading. */
class RootNativeTickIngressExtensionLifecycleTest {
    private static final class Binding implements IExtension {
        final List<String> order;Binding(List<String> order){this.order=order;}
        public boolean checkActive(MixinEnvironment environment){return true;}
        public void preApply(ITargetClassContext context){}
        public void postApply(ITargetClassContext context){order.add("binding");}
        public void export(MixinEnvironment environment,String name,boolean force,ClassNode node){}
    }
    @Test void actualRegistryHasRegisteredWrapperBeforeActiveSelectionExists(){
        var registry=new Extensions(null);var wrapper=new WrapMethodApplicatorExtension();registry.add(wrapper);
        assertTrue(registry.getActiveExtensions().isEmpty());assertSame(wrapper,RootNativeTickIngressPlugin.retainRegisteredWrapMethodExtension(registry.getExtensions(),null));
        assertThrows(IllegalStateException.class,()->RootNativeTickIngressPlugin.retainRegisteredWrapMethodExtension(registry.getActiveExtensions(),null));
    }
    @Test void laterActualSelectionConservesExactRegisteredWrapperBeforeAppendedBinding(){
        var registry=new Extensions(null);var wrapper=new WrapMethodApplicatorExtension();var binding=new Binding(new ArrayList<>());registry.add(wrapper);
        var retained=RootNativeTickIngressPlugin.retainRegisteredWrapMethodExtension(registry.getExtensions(),null);registry.add(binding);
        assertTrue(registry.getActiveExtensions().isEmpty());registry.select(null);
        assertSame(retained,registry.getActiveExtensions().getFirst());assertSame(binding,registry.getActiveExtensions().get(1));
        assertTrue(registry.getActiveExtensions().indexOf(binding)>registry.getActiveExtensions().indexOf(retained));
    }
    @Test void missingRegisteredWrapperRemainsHardFailure(){
        var registry=new Extensions(null);registry.add(new Binding(new ArrayList<>()));
        assertTrue(assertThrows(IllegalStateException.class,()->RootNativeTickIngressPlugin.retainRegisteredWrapMethodExtension(registry.getExtensions(),null)).getMessage().contains("pinned deferred WrapMethod extension unavailable"));
    }
    @Test void duplicateRealRegisteredWrapperInstancesRemainHardFailure(){
        var registry=new Extensions(null);registry.add(new WrapMethodApplicatorExtension());registry.add(new WrapMethodApplicatorExtension());
        assertTrue(assertThrows(IllegalStateException.class,()->RootNativeTickIngressPlugin.retainRegisteredWrapMethodExtension(registry.getExtensions(),null)).getMessage().contains("duplicate deferred WrapMethod extension"));
    }
    @Test void repeatedSelectionCannotReplaceRetainedSourceInstance(){
        var registry=new Extensions(null);var wrapper=new WrapMethodApplicatorExtension();registry.add(wrapper);var retained=RootNativeTickIngressPlugin.retainRegisteredWrapMethodExtension(registry.getExtensions(),null);
        assertThrows(IllegalStateException.class,()->RootNativeTickIngressPlugin.retainRegisteredWrapMethodExtension(registry.getExtensions(),retained));
        registry.select(null);assertSame(retained,registry.getActiveExtensions().getFirst());
    }
}
