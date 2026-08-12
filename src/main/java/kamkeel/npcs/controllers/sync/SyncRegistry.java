package kamkeel.npcs.controllers.sync;

import kamkeel.npcs.network.enums.SyncType;
import noppes.npcs.LogWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SyncRegistry {

    private static final LinkedHashMap<SyncType, SyncHandler> handlers = new LinkedHashMap<>();

    private static SyncType[] loginTypesCache = null;

    private SyncRegistry() {}

    public static void register(SyncType type, SyncHandler handler) {
        if (type == null) throw new IllegalArgumentException("SyncType must not be null");
        if (handler == null) throw new IllegalArgumentException("SyncHandler must not be null for " + type);
        if (handlers.containsKey(type)) throw new IllegalStateException("SyncHandler already registered for " + type);
        
        handlers.put(type, handler);
        loginTypesCache = null;
    }

    public static SyncHandler getHandler(SyncType type) {
        return handlers.get(type);
    }

    public static SyncType[] getLoginTypes() {
        if (loginTypesCache == null) {
            List<SyncType> list = new ArrayList<>();
            for (Map.Entry<SyncType, SyncHandler> e : handlers.entrySet()) 
                if (e.getValue().isCachedType()) 
                    list.add(e.getKey());
            
            loginTypesCache = list.toArray(new SyncType[0]);
        }
        return loginTypesCache;
    }

    public static Set<SyncType> getInvalidationTargets(SyncType type) {
        SyncHandler handler = handlers.get(type);
        if (handler == null) {
            return Collections.emptySet();
        }
        return handler.getInvalidationTargets(type);
    }

    public static void clear() {
        handlers.clear();
        loginTypesCache = null;
    }
    

    public static boolean isRegistered(SyncType type) {
        return handlers.containsKey(type);
    }

    public static Collection<SyncType> getRegisteredTypes() {
        return Collections.unmodifiableCollection(handlers.keySet());
    }
}
