package dev.veltraph.client.metrics;

import dev.veltraph.client.inventory.bottle.BottleAcquisitionRuntime;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BottleFinancialContinuationTest {
    @Test void exactAdmittedScopeCanBeConsumedOnlyOnce(){Object owner=new Object(),p=new Object(),w=new Object(),c=new Object();var t=new BottleAcquisitionRuntime.Travel(1,p,w,c,UUID.randomUUID(),new BottleAcquisitionRuntime.Landing("nativehub",GameArea.HUB,"minecraft:overworld",new Vec3(.5,70,.5)),1,0,500,List.of());var previous=new FinancialOutcomeBindings.Scope(p,w,c);var current=new FinancialOutcomeBindings.Scope(new Object(),new Object(),c);var receipt=new BottleFinancialContinuation(owner,t,previous,current);assertTrue(receipt.consume(owner,t,previous,current));assertFalse(receipt.consume(owner,t,previous,current));}
    @Test void foreignScopeOwnerPermitOrConnectionCannotJoinActivation(){Object owner=new Object(),p=new Object(),w=new Object(),c=new Object();var uuid=UUID.randomUUID();var landing=new BottleAcquisitionRuntime.Landing("nativehub",GameArea.HUB,"minecraft:overworld",new Vec3(.5,70,.5));var t=new BottleAcquisitionRuntime.Travel(1,p,w,c,uuid,landing,1,0,500,List.of());var previous=new FinancialOutcomeBindings.Scope(p,w,c);var current=new FinancialOutcomeBindings.Scope(new Object(),new Object(),c);var proof=new BottleFinancialContinuation(owner,t,previous,current);assertFalse(proof.consume(new Object(),t,previous,current));assertFalse(proof.consume(owner,new BottleAcquisitionRuntime.Travel(1,p,w,c,uuid,landing,1,0,500,List.of()),previous,current));assertFalse(proof.consume(owner,t,previous,new FinancialOutcomeBindings.Scope(p,w,c)));assertThrows(IllegalArgumentException.class,()->new BottleFinancialContinuation(owner,t,previous,new FinancialOutcomeBindings.Scope(p,w,new Object())));}
}
