package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.garden.pests.PestBindings;
import dev.veltraph.client.garden.pests.PestSprayonatorBindings;
import dev.veltraph.client.world.Vec3;
import java.lang.classfile.*;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Evaluates the actual compiled escape argument expression; no Minecraft initialization or physics. */
class SprayonatorEscapeRadiusNativeTraceTest {
    static final String ACTIONS="dev/veltraph/client/garden/pests/mc/McPestSprayonatorMaintenanceModule$1";
    static final String ESCAPE="dev/veltraph/client/garden/pests/PestSprayonatorBindings$Escape";
    static final String NAVIGATOR="dev/veltraph/client/navigation/mc/McNavigator";
    static MethodModel method(String type,String name,String descriptor)throws Exception{
        var model=ClassFile.of().parse(Files.readAllBytes(Path.of("build/classes/java/main/"+type+".class")));
        return model.methods().stream().filter(m->m.methodName().stringValue().equals(name)&&m.methodType().stringValue().equals(descriptor)).findFirst().orElseThrow();
    }
    /** Restricted expression evaluator: constants, real Escape radius accessor, real Math.min call. */
    static double actualWalkingRadius(double configured)throws Exception{
        var doubles=new ArrayDeque<Double>();var integers=new ArrayList<Integer>();boolean accessor=false;
        for(var element:method(ACTIONS,"escape","()Z").code().orElseThrow().elementStream().toList()){
            if(element instanceof ConstantInstruction constant){
                if(constant.constantValue() instanceof Double value)doubles.push(value);
                if(constant.constantValue() instanceof Integer value)integers.add(value);
            }
            if(element instanceof InvokeInstruction invoke){
                String owner=invoke.owner().asInternalName(),name=invoke.name().stringValue(),descriptor=invoke.type().stringValue();
                if(owner.equals(ESCAPE)&&name.equals("arrivalRadius")&&descriptor.equals("()D")){doubles.push(configured);accessor=true;}
                else if(owner.equals("java/lang/Math")&&name.equals("min")&&descriptor.equals("(DD)D")){
                    assertTrue(doubles.size()>=2,"Unknown compiled radius expression");double right=doubles.pop(),left=doubles.pop();doubles.push(Math.min(left,right));
                }
                else if(owner.equals(NAVIGATOR)&&name.equals("start")&&descriptor.equals("(Ldev/veltraph/client/world/Vec3;IID)Z")){
                    assertTrue(accessor,"Actual configured radius is not wired");assertEquals(1,doubles.size(),"Unknown actual radius expression");
                    assertEquals(List.of(2500,32),integers,"Original per-search budgets changed");return doubles.pop();
                }
            }
        }
        throw new AssertionError("Actual escape never invokes ordinary guarded walking");
    }
    static double actualWalkingMaximum()throws Exception{
        var capMethod=method(NAVIGATOR,"start","(Ldev/veltraph/client/world/Vec3;IID)Z");
        var constants=capMethod.code().orElseThrow().elementStream().filter(e->e instanceof ConstantInstruction)
                .map(e->((ConstantInstruction)e).constantValue()).filter(v->v instanceof Double).toList();
        if(constants.isEmpty()){
            // Current ordinary wrapper delegates to the same private bounded walking API.
            var delegates=capMethod.code().orElseThrow().elementStream().filter(e->e instanceof InvokeInstruction).map(e->(InvokeInstruction)e).toList();
            assertEquals(1,delegates.size());var delegate=delegates.getFirst();assertEquals(NAVIGATOR,delegate.owner().asInternalName());assertEquals("start",delegate.name().stringValue());
            assertEquals("(Ldev/veltraph/client/world/Vec3;IIDZ)Z",delegate.type().stringValue());
            capMethod=method(NAVIGATOR,"start",delegate.type().stringValue());
            constants=capMethod.code().orElseThrow().elementStream().filter(e->e instanceof ConstantInstruction)
                    .map(e->((ConstantInstruction)e).constantValue()).filter(v->v instanceof Double).toList();
        }
        assertTrue(constants.size()>=2);assertEquals(0.0,constants.get(0));assertEquals(.35,constants.get(1));return (Double)constants.get(1);
    }
    static boolean strictApiAccepts(double radius)throws Exception{return Double.isFinite(radius)&&radius>0&&radius<=actualWalkingMaximum();}
    static PestSprayonatorBindings.Escape binding(double radius){return new PestSprayonatorBindings.Escape(new PestBindings.Entity("minecraft:armor_stand","Spray Trap",""),new Vec3(9.5,70,-96.5),radius);}

    @Test void observedHalfBlockEscapeMustPassTheActualCompiledWalkingApiContract()throws Exception{
        var escape=binding(.5);double passed=actualWalkingRadius(escape.arrivalRadius());
        assertTrue(strictApiAccepts(passed),"Native P passed configured .5 directly to strict McNavigator API; valid escape is refused before movement");
        assertEquals(.35,passed);assertEquals(.5,escape.arrivalRadius(),"Configured physical arrival oracle must remain unchanged");
    }
    @Test void stricterConfiguredRadiusRemainsStrictAndMaximumBindingIsAdapted()throws Exception{
        assertEquals(.2,actualWalkingRadius(binding(.2).arrivalRadius()));assertEquals(.35,actualWalkingRadius(binding(1).arrivalRadius()));
        for(double radius:new double[]{.01,.2,.35,.5,1})assertTrue(strictApiAccepts(actualWalkingRadius(binding(radius).arrivalRadius())));
    }
    @Test void invalidBindingsRemainRefusedBeforeAnyWalkingExpression(){
        for(double invalid:new double[]{0,-.1,1.01,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->binding(invalid));
    }
    @Test void actualArrivalOracleStillUsesConfiguredRadiusAndLoadedGroundTrapProof()throws Exception{
        var calls=method(ACTIONS,"escaped","()Ldev/veltraph/client/garden/pests/PestSprayonatorRuntime$Progress;").code().orElseThrow().elementStream()
                .filter(e->e instanceof InvokeInstruction).map(e->{var i=(InvokeInstruction)e;return i.owner().asInternalName()+"."+i.name().stringValue();}).toList();
        assertTrue(calls.contains(ESCAPE+".arrivalRadius"));assertTrue(calls.contains(NAVIGATOR+".standingClear"));
        assertTrue(calls.contains("dev/veltraph/client/garden/pests/mc/McPestSprayonatorMaintenanceModule.trapFree"));
    }
}
