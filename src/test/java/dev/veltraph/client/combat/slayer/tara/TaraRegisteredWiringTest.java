package dev.veltraph.client.combat.slayer.tara;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Parses freshly compiled production methods without initializing Minecraft. An unwired registered path is a hard red. */
class TaraRegisteredWiringTest {
    private List<String> calls(String type,String method)throws Exception{
        var model=ClassFile.of().parse(Files.readAllBytes(Path.of("build/classes/java/main/"+type+".class")));
        return model.methods().stream().filter(m->m.methodName().stringValue().equals(method)).flatMap(m->m.code().stream())
                .flatMap(code->code.elementStream()).filter(e->e instanceof InvokeInstruction).map(e->{var i=(InvokeInstruction)e;return i.owner().asInternalName()+"."+i.name().stringValue();}).toList();
    }
    @Test void registeredSlayerConstructsConfiguresAndBindsSpecializedExistingChild()throws Exception{
        var p="dev/veltraph/client/combat/slayer/SlayerModule";
        var start=calls(p,"preStartCheck");assertTrue(start.contains("dev/veltraph/client/combat/slayer/tara/McTaraParent.configure"));
        assertTrue(start.contains("dev/veltraph/client/combat/grind/MeleeGrindModule.ownedMechanic"),"Registered Slayer's Tara option is unwired");
        assertTrue(calls(p,"tickQuest").contains("dev/veltraph/client/combat/grind/MeleeGrindModule.onTick"));
        assertTrue(calls(p,"onServerMessage").contains("dev/veltraph/client/combat/slayer/tara/McTaraParent.message"));
        assertTrue(calls(p,"onDisable").contains("dev/veltraph/client/combat/slayer/tara/McTaraParent.stop"));
    }
    @Test void ownedChildHookExecutesBeforeGenericNativeUseAndGenericEggProtocol()throws Exception{
        var calls=calls("dev/veltraph/client/combat/grind/MeleeGrindModule","tickCombat");
        int hook=calls.indexOf("dev/veltraph/client/combat/grind/MeleeGrindModule$OwnedMechanic.tick");
        assertTrue(hook>=0,"Actual enabled child never dispatches Tara ownership");
        assertTrue(hook<calls.indexOf("net/minecraft/client/multiplayer/MultiPlayerGameMode.useItem"));
        assertTrue(hook<calls.indexOf("dev/veltraph/client/combat/slayer/ObjectiveRequests.tick"));
        assertTrue(calls.contains("dev/veltraph/client/combat/grind/MeleeGrindModule$OwnedMechanic.suppresses"));
    }

}
