package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceLeavingPolicyLifetimeTest {
    @Test void sourceQuarterBoundaryAndSingleLifetimeDraw(){for(double draw:new double[]{0,.249999,.25,.999999}){int[] calls={0};var lifetime=new GhostSourceLeavingPolicyLifetime(()->{calls[0]++;return draw;});for(int i=0;i<100;i++)assertEquals(draw<.25?GhostSourceLeavingLifecycle.Policy.LEAVE_INSTANTLY:GhostSourceLeavingLifecycle.Policy.ETHERWARP_OUT,lifetime.policy());assertEquals(1,calls[0]);}}
    @Test void unavailableRandomDoesNotSupplyDefaultPolicy(){assertThrows(IllegalArgumentException.class,()->new GhostSourceLeavingPolicyLifetime(()->Double.NaN));assertThrows(IllegalArgumentException.class,()->new GhostSourceLeavingPolicyLifetime(()->1));}
}
