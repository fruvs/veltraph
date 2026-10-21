package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.core.*;
import dev.veltraph.client.config.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BazaarStandaloneEffectFenceTest {
    private static final class Owner extends AbstractModule {Owner(String id){super(id,id,ModuleCategory.MISC);}}
    private final ModuleManager manager=new ModuleManager();private final Owner owner=new Owner("custody");
    BazaarStandaloneEffectFenceTest(){manager.register(owner);owner.setEnabled(true);}
    @Test void unchangedAuthenticStoredLifecycleAndLeavesAreEligible(){var before=manager.lifecycleSnapshot();assertTrue(BazaarStandaloneEffectFence.current(manager,before,()->manager.inputOwner().orElse(null)==owner,()->true));}
    @Test void actualSameInstanceDisableEnableCallbackCannotReauthorizeOldFrame(){manager.leasedInputOwner(()->{owner.setEnabled(false);owner.setEnabled(true);return owner;});var before=manager.lifecycleSnapshot();assertFalse(BazaarStandaloneEffectFence.current(manager,before,()->manager.inputOwner().orElse(null)==owner,()->true));assertTrue(owner.isEnabled());}
    @Test void callbackReplacementOfNativeIdentityCannotUseStalePositiveRole(){var player=new AtomicReference<>(new Object());Object captured=player.get();var before=manager.lifecycleSnapshot();assertFalse(BazaarStandaloneEffectFence.current(manager,before,()->{player.set(new Object());return true;},()->player.get()==captured));}
    @Test void callbackInputBindingChangeCannotAuthorizeAnOldSnapshot(){var before=manager.lifecycleSnapshot();assertFalse(BazaarStandaloneEffectFence.current(manager,before,()->{manager.leasedInputOwner(()->owner);return true;},()->true));}
    @Test void callbackRegistrationChangeCannotAuthorizeAnOldSnapshot(){var before=manager.lifecycleSnapshot();assertFalse(BazaarStandaloneEffectFence.current(manager,before,()->{manager.register(new Owner("new"));return true;},()->true));}
    @Test void mutationAtLastNativeComparisonIsStillVetoedByStoredLifecycle(){var before=manager.lifecycleSnapshot();assertFalse(BazaarStandaloneEffectFence.current(manager,before,()->true,()->{owner.setEnabled(false);owner.setEnabled(true);return true;}));}
    @Test void explicitRoleRefusalNeverEvaluatesNativeCommitLeaves(){var before=manager.lifecycleSnapshot();assertFalse(BazaarStandaloneEffectFence.current(manager,before,()->false,()->{throw new AssertionError("No commit leaves after denied role");}));}
    @Test void actualPolicyChangeAndRevertIsDetectedByStoredEpoch(){var store=new MapConfigService();store.set(SellingConfig.SELLING_BAZAAR_PROFILE_JSON,"exact");var configBefore=BazaarStandaloneStoredConfig.capture(store);var before=manager.lifecycleSnapshot();assertFalse(BazaarStandaloneEffectFence.current(manager,before,()->{store.set(SellingConfig.SELLING_BAZAAR_PROFILE_JSON,"changed");store.set(SellingConfig.SELLING_BAZAAR_PROFILE_JSON,"exact");return true;},()->configBefore.same(BazaarStandaloneStoredConfig.capture(store))));}
    @Test void sameValueWritePreservesKnownStoreEvidence(){var store=new MapConfigService();store.set(SellingConfig.SELLING_BAZAAR_PROFILE_JSON,"exact");var before=BazaarStandaloneStoredConfig.capture(store);store.set(SellingConfig.SELLING_BAZAAR_PROFILE_JSON,"exact");assertTrue(before.same(BazaarStandaloneStoredConfig.capture(store)));}
    @Test void replacementStoreAndUnknownImplementationsCannotAttestStoredState(){var store=new MapConfigService();assertFalse(BazaarStandaloneStoredConfig.capture(store).same(BazaarStandaloneStoredConfig.capture(new MapConfigService())));ConfigService unsupported=new ConfigService(){public <T>T get(ConfigKey<T> key){return key.defaultValue();}public <T>void set(ConfigKey<T> key,T value){}public boolean isSet(ConfigKey<?> key){return false;}public void reset(ConfigKey<?> key){}};assertNull(BazaarStandaloneStoredConfig.capture(unsupported));}
}
