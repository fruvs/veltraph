package dev.veltraph.client.fishing.offense;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FishingReturnMediumTest {
    private FishingReturnMedium choose(boolean declared,boolean available,boolean grounded,boolean lava,boolean submerged,double vy,double speed,boolean dry,boolean water,boolean wet){return FishingReturnMedium.select(declared,available,grounded,lava,submerged,vy,speed,dry,dry,water,water,wet);}
    @Test void legacyDryProfileDoesNotTurnWetBodyIntoDryNavigation(){assertEquals(FishingReturnMedium.WAIT,choose(false,true,true,false,false,-.005,0,false,true,true));assertEquals(FishingReturnMedium.DRY_GROUND,choose(false,true,true,false,false,-.0784,0,true,true,false));}
    @Test void explicitSupportedShallowWaterUsesWaterDriver(){assertEquals(FishingReturnMedium.SUPPORTED_WATER,choose(true,true,true,false,false,-.005,0,false,true,true));assertEquals(FishingReturnMedium.DRY_GROUND,choose(true,true,true,false,false,-.0784,0,true,true,false));}
    @Test void airborneAndUnsettledWaterCannotPlan(){assertEquals(FishingReturnMedium.WAIT,choose(true,true,false,false,false,.3,.14,false,true,true));assertEquals(FishingReturnMedium.WAIT,choose(true,true,true,false,false,-.005,.05,false,true,true));assertEquals(FishingReturnMedium.WAIT,choose(true,true,true,false,false,.08,0,false,true,true));}
    @Test void lavaAndDeepSubmersionRemainUnavailable(){assertEquals(FishingReturnMedium.WAIT,choose(true,true,true,true,false,0,0,false,true,true));assertEquals(FishingReturnMedium.WAIT,choose(true,true,true,false,true,0,0,false,true,true));}
    @Test void loadedSupportBodyClearAndExactDestinationCannotBeAssumed(){assertEquals(FishingReturnMedium.WAIT,FishingReturnMedium.select(true,true,true,false,false,0,0,false,false,true,false,true));assertEquals(FishingReturnMedium.WAIT,FishingReturnMedium.select(true,true,true,false,false,0,0,false,false,false,true,true));assertEquals(FishingReturnMedium.WAIT,choose(true,false,true,false,false,0,0,false,true,true));}
    @Test void invalidNativeMotionCannotAuthorizeReturn(){assertEquals(FishingReturnMedium.WAIT,choose(true,true,true,false,false,Double.NaN,0,false,true,true));assertEquals(FishingReturnMedium.WAIT,choose(true,true,true,false,false,0,Double.POSITIVE_INFINITY,false,true,true));}
    @Test void existingProfileDefaultsRemainDryAndWaterRequiresExplicitJson(){var dry=FishingOffenseProfile.parse("{\"itemId\":\"minecraft:diamond_sword\",\"itemName\":\"Skill\",\"itemLore\":[\"Skill\"],\"targetType\":\"minecraft:silverfish\",\"abilityReceiptTemplate\":\"Skill {target}\",\"manaCost\":100,\"pacingMinMillis\":100,\"pacingMaxMillis\":100,\"timeoutMillis\":3000,\"maxUses\":1}");assertFalse(dry.supportedWaterReturn());var wet=FishingOffenseProfile.parse(new com.google.gson.Gson().toJson(dry).replace("\"supportedWaterReturn\":false","\"supportedWaterReturn\":true"));assertTrue(wet.supportedWaterReturn());}
}
