package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostPreflightProfileTest {
    static final String JSON="""
        {"schema":1,"warpRetryIntervalMs":1000,"warpCommand":"private_warp","denial":"Private denied","anchor":[8.5,70,0.5],"timeoutMs":2500,
         "modifyJumpBoost":true,"buffCooldownMs":1000,"buff":{"skyblockItemId":"minecraft:compass","skyblockItemName":"Private opener",
         "skyblockMenu":"Private SkyBlock","cookieButton":"Private Cookie","cookieMenu":"Private Cookie Menu","potionButton":"Private Potions",
         "potionMenuPrefix":"Private Potions ","effectNames":["Private Jump Boost"],"disabledLoreMarker":"Private desired state",
         "nextPageButton":"Private Next","footerPattern":"Private Cookie", "footerStart":"Private Cookie", "footerEnd":"END",
         "footerInactive":"inactive", "rowLabel":"Private row", "rowNeighborMarker":"details", "nextRowInactivePrefix":"inactive"}}
        """;
    @Test void explicitProfileRetainsCurrentContractsWithoutManufacturingFallback(){var p=GhostPreflightProfile.parse(JSON);assertEquals("private_warp",p.warpCommand());assertEquals(8.5,p.anchor().x());assertTrue(p.modifyJumpBoost());assertEquals(1000,p.buffCooldownMs());assertEquals(1000,p.warpRetryIntervalMs());assertEquals("",p.buff().openCommand());assertEquals("",p.buffCloseMessagePrefix());assertEquals("Private desired state",p.buff().disabledLoreMarker());}
    @Test void sourceClosePrefixMustBeExplicitWhenBound(){assertEquals("Private close",GhostPreflightProfile.parse(JSON.replace("\"schema\":1","\"schema\":1,\"buffCloseMessagePrefix\":\"Private close\"")).buffCloseMessagePrefix());}
    @Test void missingContractsAndUnknownFieldsCannotProduceDefaults(){assertThrows(RuntimeException.class,()->GhostPreflightProfile.parse(""));assertThrows(RuntimeException.class,()->GhostPreflightProfile.parse(JSON.replace("\"denial\":\"Private denied\",","")));assertThrows(RuntimeException.class,()->GhostPreflightProfile.parse(JSON.replace("\"schema\":1","\"schema\":1,\"automaticBooster\":true")));}
    @Test void commandsCannotContainOtherChatOrMultilineInstructions(){assertThrows(RuntimeException.class,()->GhostPreflightProfile.parse(JSON.replace("private_warp","/warp dwarven")));assertThrows(RuntimeException.class,()->GhostPreflightProfile.parse(JSON.replace("private_warp","warp; another")));}
    @Test void explicitSourceBooleanAndBoundsAreStrict(){assertThrows(RuntimeException.class,()->GhostPreflightProfile.parse(JSON.replace("\"modifyJumpBoost\":true","\"modifyJumpBoost\":\"true\"")));assertThrows(RuntimeException.class,()->GhostPreflightProfile.parse(JSON.replace("\"timeoutMs\":2500","\"timeoutMs\":60001")));assertThrows(RuntimeException.class,()->GhostPreflightProfile.parse(JSON.replace("[8.5,70,0.5]","[8.5,1e999,0.5]")));}
    @Test void unboundOrInvalidWarpIntervalCannotSilentlyUseOneCommandMode(){assertThrows(RuntimeException.class,()->GhostPreflightProfile.parse(JSON.replace("\"warpRetryIntervalMs\":1000,","")));assertThrows(RuntimeException.class,()->GhostPreflightProfile.parse(JSON.replace("\"warpRetryIntervalMs\":1000","\"warpRetryIntervalMs\":0")));}
    @Test void originalSourceModeNamesMustBeExplicitAndDistinct(){assertThrows(RuntimeException.class,()->GhostPreflightProfile.parse(JSON.replace("[\"Private Jump Boost\"]","[]")));assertThrows(RuntimeException.class,()->GhostPreflightProfile.parse(JSON.replace("[\"Private Jump Boost\"]","[\"Private Jump Boost\",\"Private Jump Boost\"]")));}
}
