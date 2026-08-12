package kamkeel.npcs.controllers.sync;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.network.enums.SyncType;
import net.minecraft.nbt.NBTTagCompound;

import java.util.HashSet;
import java.util.Set;

/**
 * Contract for a synchronized data type. Implementors handle both
 * server-side serialization and client-side deserialization for their
 * specific data type.
 *
 * <p>Each handler is registered with {@link SyncRegistry} and covers
 * all three sync actions: RELOAD (full), UPDATE (single entity),
 * and REMOVE (delete entity).</p>
 *
 * <p>Handlers that don't support UPDATE or REMOVE should use the
 * default no-op implementations.</p>
 */
public interface SyncHandler {
    
    // ========== SERVER-SIDE ==========

    /**
     * Serialize ALL objects of this type into an NBTTagCompound.
     * This is the supplier for the cache system.
     *
     * <p>Called lazily when the cache is dirty and a player needs data.
     * The returned compound is cached and chunked for transmission.</p>
     *
     * @return the serialized compound, or null if this type has no independent cache
     */
    NBTTagCompound serializeAll();


    // ========== CLIENT-SIDE ==========

    /**
     * Handle a RELOAD action on the client. Deserialize the full
     * dataset and replace the client-side data.
     *
     * <p>Implementations should:
     * <ol>
     *   <li>Deserialize from the compound into a sync buffer</li>
     *   <li>Atomically swap the sync buffer to the primary map</li>
     *   <li>Reset the sync buffer for the next reload</li>
     * </ol>
     *
     * @param fullCompound the full serialized dataset from {@link #serializeAll()}
     */
    @SideOnly(Side.CLIENT)
    default void clientHandleReload(NBTTagCompound fullCompound){
        // No-op: type does not support individual updates
    }

    /**
     * Handle an UPDATE action on the client.
     * Deserialize a single object and insert/replace it in the client-side data.
     *
     * <p>Default: no-op. Types that don't support individual updates
     * use {@code syncAll()} for full reload instead.</p>
     *
     * @param compound   the serialized single object
     */
    @SideOnly(Side.CLIENT)
    default void clientHandleUpdate(NBTTagCompound compound) {
        // No-op: type does not support individual updates
    }
    
    /**
     * Handle an UPDATE action on the client.
     * Deserialize a single object and insert/replace it in the client-side data.
     *
     * <p>Default: no-op. Types that don't support individual updates
     * use {@code syncAll()} for full reload instead.</p>
     *
     * @param compound   the serialized single object
     * @param categoryKey the parent category key (null if not applicable)
     */
    @SideOnly(Side.CLIENT)
    default void clientHandleUpdate(NBTTagCompound compound, String categoryKey) {
        // No-op: type does not support individual updates
    }

    
    /**
     * Handle an UPDATE action on the client. Deserialize a single
     * entity and insert/replace it in the client-side data.
     *
     * <p>Default: no-op. Types that don't support individual updates
     * use {@code syncAll()} for full reload instead.</p>
     *
     * @param compound   the serialized single entity
     * @param categoryId the parent category ID (-1 if not applicable)
     */
    @SideOnly(Side.CLIENT)
    default void clientHandleUpdate(NBTTagCompound compound, int categoryId) {
        // No-op: type does not support individual updates
    }

    /**
     * Removes a single object from client-side data.
     * Full object NBTTagCompound sent finer query control
     * Int-keyed handlers should override {@link #clientHandleRemove(int)} instead.
     */
    @SideOnly(Side.CLIENT)
    default void clientHandleRemove(String key) {
    }

    /**
     * Removes a single object with the given ID from client-side data.
     *
     * @param id the ID of the object to remove
     */
    @SideOnly(Side.CLIENT)
    default void clientHandleRemove(int id) {
    }

    /**
     * Whether this handler supports the UPDATE action.
     * Used for validation and self-documentation.
     */
    default boolean supportsUpdate() {
        return false;
    }

    /**
     * Whether this handler supports the REMOVE action.
     * Used for validation and self-documentation.
     */
    default boolean supportsRemove() {
        return false;
    }

    /**
     * Whether this type should be included in login sync.
     * Types that return true will be sent to players on login
     * and checked for revision deltas on reconnect.
     *
     * <p>Default: true. Override to false for types like PLAYERDATA
     * that are synced through separate mechanisms, or child types
     * (DIALOG, QUEST) that share their parent's cache.</p>
     */
    default boolean isCachedType() {
        return true;
    }

    /**
     * Get the set of cache types that must be invalidated when
     * this type changes. Used for cascade invalidation.
     *
     * <p>Most types return just themselves. Parent-child types
     * (e.g. DIALOG maps to DIALOG_CATEGORY) point children
     * to the parent's cache.</p>
     *
     * @param self the SyncType this handler is registered under
     * @return the set of types to invalidate
     */
    default Set<SyncType> getInvalidationTargets(SyncType self) {
        Set<SyncType> set = new HashSet<>();
        set.add(self);
        return set;
    }
}
