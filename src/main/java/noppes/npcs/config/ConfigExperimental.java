package noppes.npcs.config;

import cpw.mods.fml.common.FMLLog;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.apache.logging.log4j.Level;

import java.io.File;

public class ConfigExperimental {
    public static Configuration config;

    public final static String CLIENT = "Client";
    public final static String SERVER = "Server";
    public static Property ModernGuiSystemProperty;
    public static boolean ModernGuiSystem = false;
    public static Property LegacyDropProperty;
    public static boolean LegacyDrop = false;
    public static Property useLegacyKnockbackProperty;
    public static boolean useLegacyKnockback = false;
    public static Property useLegacyRenderProperty;
    public static boolean useLegacyRender = false;
    public static Property LegacyTraderProperty;
    public static boolean useLegacyTrader = false;
    public static Property LegacyAttackBehaviorProperty;
    public static boolean LegacyAttackBehavior = false;


    public static void init(File configFile) {
        config = new Configuration(configFile);

        try {
            config.load();

            ModernGuiSystemProperty = config.get(CLIENT, "Experimental Dialog GUI", false, "Enables the new CNPC+ Modern GUI for Dialog and Quest information");
            ModernGuiSystem = ModernGuiSystemProperty.getBoolean(false);
            LegacyDropProperty = config.get(SERVER, "Use Legacy DropItem for npc", false, "Use Legacy Drop Item for NPCs for compatibility");
            LegacyDrop = LegacyDropProperty.getBoolean(false);
            useLegacyKnockbackProperty = config.get(SERVER, "Use legacy knockback", false, "Use legacy knockback for compatibility ");
            useLegacyKnockback = useLegacyKnockbackProperty.getBoolean(false);
            useLegacyRenderProperty = config.get(CLIENT, "Legacy Render", false, "Use legacy Render for compatibility");
            useLegacyRender = useLegacyRenderProperty.getBoolean(false);
            LegacyTraderProperty = config.get(SERVER, "Use Legacy Trader Logical", false, "Use Legacy Trader Logical");
            useLegacyTrader = LegacyTraderProperty.getBoolean(false);
            LegacyAttackBehaviorProperty = config.get(SERVER, "Use Legacy Attack Logical", false, "Use Legacy Attack Logical");
            LegacyAttackBehavior = LegacyAttackBehaviorProperty.getBoolean(false);
        } catch (Exception e) {
            FMLLog.log(Level.ERROR, e, "CNPC+ has had a problem loading its experimental configuration");
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}
