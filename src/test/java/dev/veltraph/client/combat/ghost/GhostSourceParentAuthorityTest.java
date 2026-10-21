package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceParentAuthorityTest {
    @Test void passingThisCannotGrantOwnershipWhenAnotherRootControllerOwnsInputOrCamera(){
        Object parent=new Object(),other=new Object();
        assertFalse(GhostSourceParentAuthority.owned(parent,parent,other,1,true,parent));
        assertFalse(GhostSourceParentAuthority.owned(parent,parent,parent,1,true,other));
        assertTrue(GhostSourceParentAuthority.owned(parent,parent,parent,1,true,parent));
    }
    @Test void duplicateNoncanonicalInstanceDisabledParentAndMultipleControllersCannotIssue(){
        Object parent=new Object(),registeredSameIdElsewhere=new Object();
        assertFalse(GhostSourceParentAuthority.owned(parent,registeredSameIdElsewhere,parent,1,true,parent));
        assertFalse(GhostSourceParentAuthority.owned(parent,parent,parent,2,true,parent));
        assertFalse(GhostSourceParentAuthority.owned(parent,parent,parent,1,false,parent));
    }
    @Test void absentRootOwnerOrCameraNeverBecomesFixtureCompatibilityAuthority(){
        Object parent=new Object();
        assertFalse(GhostSourceParentAuthority.owned(parent,null,parent,1,true,parent));
        assertFalse(GhostSourceParentAuthority.owned(parent,parent,null,1,true,parent));
        assertFalse(GhostSourceParentAuthority.owned(parent,parent,parent,1,true,null));
    }
}
