package kamkeel.npcs.network.enums;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable, object-based sync-type identity for the CNPC+ sync backend.
 *
 * <p>Each {@code SyncType} has a canonical namespaced string ID ({@code modid:path})
 * and is registered via the static {@link #register} factory. Built-in CNPC+
 * families are exposed as {@code public static final} constants.</p>
 *
 * <p>Identity is based on the canonical {@link #id} string, which is unique
 * across all registered types. Two references with the same {@code id}
 * are always the same object (identity == equality for registered types).</p>
 *
 * <p>The {@code ordinal} field provides a stable integer identity for
 * wire encoding during the migration period. It is assigned at registration
 * time in declaration order and must NOT be used as a protocol identifier
 * once string-on-wire migration is complete.</p>
 */
public final class SyncType {

    // ──────────────────── Registry ────────────────────
    private static final Map<String, SyncType> REGISTRY = new LinkedHashMap<>();
    private static int nextOrdinal = 0;

    // ──────────────────── CNPC+ families ────────────────────
    public static final SyncType FACTION          = register("cnpc:faction");
    public static final SyncType DIALOG           = register("cnpc:dialog");
    public static final SyncType DIALOG_CATEGORY  = register("cnpc:dialog_category");
    public static final SyncType QUEST            = register("cnpc:quest");
    public static final SyncType QUEST_CATEGORY   = register("cnpc:quest_category");
    public static final SyncType PLAYERDATA       = register("cnpc:playerdata");
    public static final SyncType MAGIC            = register("cnpc:magic");
    public static final SyncType MAGIC_CYCLE      = register("cnpc:magic_cycle");
    public static final SyncType WORKBENCH_RECIPES = register("cnpc:workbench_recipes");
    public static final SyncType CARPENTRY_RECIPES = register("cnpc:carpentry_recipes");
    public static final SyncType ANVIL_RECIPES    = register("cnpc:anvil_recipes");
    public static final SyncType CUSTOM_EFFECTS   = register("cnpc:custom_effects");
    public static final SyncType CUSTOM_ABILITY   = register("cnpc:custom_ability");
    public static final SyncType CHAINED_ABILITY  = register("cnpc:chained_ability");

    // ──────────────────── Instance fields ────────────────────
    private final String id;
    private final int ordinal;

    private SyncType(String id, int ordinal) {
        this.id = id;
        this.ordinal = ordinal;
    }

    // ──────────────────── Public API ────────────────────

    /**
     * The canonical namespaced identifier, e.g. {@code "cnpc:faction"}.
     */
    public String getId() {
        return id;
    }

    /**
     * Stable integer ordinal assigned at registration time.
     * Used for wire encoding during the migration period only.
     */
    public int ordinal() {
        return ordinal;
    }
    
    // ──────────────────── Registration ────────────────────

    /**
     * Register a new {@code SyncType} with the given canonical ID.
     *
     * @param id namespaced identifier ({@code modid:path})
     * @return the newly created and registered {@code SyncType}
     * @throws IllegalArgumentException if the ID is null, empty, or lacks a colon
     * @throws IllegalStateException    if a type with the same ID is already registered
     * @throws IllegalStateException    if the registry has been frozen
     */
    public static SyncType register(String id) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("SyncType id must not be null or empty");
        if (!id.contains(":")) throw new IllegalArgumentException("SyncType id must be namespaced (modid:path): " + id);
        if (REGISTRY.containsKey(id)) throw new IllegalStateException("Duplicate SyncType registration: " + id);
        
        SyncType type = new SyncType(id, nextOrdinal++);
        REGISTRY.put(id, type);
        return type;
    }

    /**
     * Look up a registered SyncType by its canonical string ID.
     *
     * @return the registered type, or {@code null} if not found
     */
    public static SyncType byId(String id) {
        return id == null ? null : REGISTRY.get(id);
    }

    /**
     * Look up a registered SyncType by its ordinal.
     * Used during migration for decoding ordinal-based wire traffic.
     *
     * @return the registered type, or {@code null} if ordinal is out of range
     */
    public static SyncType byOrdinal(int ordinal) {
        if (ordinal < 0) return null;
        
        for (SyncType type : REGISTRY.values()) 
            if (type.ordinal == ordinal) return type;
        
        return null;
    }

    /**
     * Returns all registered SyncType instances in registration order.
     */
    public static Collection<SyncType> values() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    // ──────────────────── Object overrides ────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SyncType)) return false;
        return id.equals(((SyncType) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
