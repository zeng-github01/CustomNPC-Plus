package kamkeel.npcs.controllers.data.ability.type;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kamkeel.npcs.addon.DBCAddon;
import noppes.npcs.AbstractDataAbilities;
import noppes.npcs.controllers.AnimationController;
import kamkeel.npcs.controllers.data.ability.Ability;
import kamkeel.npcs.controllers.data.ability.enums.UserType;
import kamkeel.npcs.controllers.data.ability.gui.AbilityFieldDefs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import noppes.npcs.controllers.data.Animation;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.api.ability.type.IAbilityCounter;
import noppes.npcs.client.gui.builder.FieldDef;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Arrays;
import java.util.List;

/**
 * Counter ability: Absorbs incoming damage and counter-attacks the attacker.
 */
public class AbilityCounter extends AbilityDefend implements IAbilityCounter {

    private CounterType counterType = CounterType.FLAT;
    private float counterValue = 6.0f;
    private int counterAnimationId = -1;
    private String counterAnimationName = "Ability_Guard_Counter";

    // True only while this counter is synchronously dispatching its retaliation damage.
    // Another counter that is hit by that retaliation inspects this flag on the source
    // entity's active counter to avoid reacting and creating infinite mutual retaliation.
    private transient boolean retaliating;

    public AbilityCounter() {
        this.typeId = "ability.cnpc.counter";
        this.name = "Counter";
        this.cooldownTicks = 0;
        this.windUpTicks = 20;
        this.allowedBy = UserType.BOTH;
        this.activeSound = "random.anvil_land";

        this.activeAnimationName = "Ability_Guard_Active";
        this.defaultIconLayers = new DefaultIconLayer[]{
            new DefaultIconLayer("customnpcs:textures/gui/ability/guard.png")
        };
    }

    public enum CounterType {
        FLAT,
        PERCENT;

        @Override
        public String toString() {
            switch (this) {
                case FLAT:
                    return "ability.counter.flat";
                case PERCENT:
                    return "ability.counter.percent";
                default:
                    return name();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEFEND HOOKS
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected boolean isValidDamageSource(DamageSource source) {
        if (!super.isValidDamageSource(source)) {
            return false;
        }
        // Break counter-vs-counter recursion: if the hit is itself a retaliation
        // from another counter that is mid-dispatch, don't counter it back.
        Entity sourceEntity = source.getEntity();
        AbstractDataAbilities abilities = getAbilitiesOf(sourceEntity);
        if (abilities != null) {
            Ability current = abilities.getCurrentAbility();
            if (current instanceof AbilityCounter && ((AbilityCounter) current).retaliating) {
                return false;
            }
        }
        return true;
    }

    private static AbstractDataAbilities getAbilitiesOf(Entity entity) {
        if (entity instanceof EntityPlayer) {
            PlayerData pData = PlayerData.get((EntityPlayer) entity);
            return pData != null ? pData.abilityData : null;
        }
        if (entity instanceof EntityNPCInterface) {
            return ((EntityNPCInterface) entity).abilities;
        }
        return null;
    }

    @Override
    protected float performDefend(EntityLivingBase attacker, float amount) {
        // For PERCENT mode with DBC, use the full DBC-calculated damage, not vanilla base
        float incomingDamage = amount;
        if (DBCAddon.IsAvailable()) {
            incomingDamage = DBCAddon.instance.getAttackerDBCDamage(amount);
        }

        // Calculate counter damage
        float damage;
        if (counterType == CounterType.FLAT) {
            damage = counterValue;
        } else {
            damage = incomingDamage * (counterValue / 100.0f);
        }

        // Counter-attack through the standard ability damage pipeline.
        // DBCAbilityExtender handles DBC stat routing (ignore flags, scaling, etc.) automatically.
        if (caster != null && attacker != null && attacker.isEntityAlive() && damage > 0) {
            retaliating = true;
            try {
                applyAbilityDamage(caster, attacker, damage, 0);
            } finally {
                retaliating = false;
            }
        }

        return 0;
    }

    @Override
    public void reset() {
        super.reset();
        retaliating = false;
    }

    // ═══════════════════════════════════════════════════════════════════
    // OVERRIDES
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean hasDamage() {
        return true;
    }

    @Override
    protected Animation getDefendAnimation() {
        return getCounterAnimation();
    }

    public Animation getCounterAnimation() {
        if (AnimationController.Instance == null) return null;
        if (counterAnimationId >= 0) {
            return (Animation) AnimationController.Instance.get(counterAnimationId);
        }
        if (counterAnimationName != null && !counterAnimationName.isEmpty()) {
            return (Animation) AnimationController.Instance.get(counterAnimationName, true);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // NBT
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void writeSubTypeNBT(NBTTagCompound nbt) {
        nbt.setString("counterType", counterType.name());
        nbt.setFloat("counterValue", counterValue);
        nbt.setInteger("counterAnimationId", counterAnimationId);
        nbt.setString("counterAnimationName", resolveAnimationName(counterAnimationId, counterAnimationName));
    }

    @Override
    protected void readSubTypeNBT(NBTTagCompound nbt) {
        try {
            this.counterType = CounterType.valueOf(nbt.getString("counterType"));
        } catch (Exception e) {
            this.counterType = CounterType.FLAT;
        }
        this.counterValue = nbt.getFloat("counterValue");
        this.counterAnimationId = nbt.getInteger("counterAnimationId");
        this.counterAnimationName = nbt.getString("counterAnimationName");
    }

    // ═══════════════════════════════════════════════════════════════════
    // GUI
    // ═══════════════════════════════════════════════════════════════════

    @SideOnly(Side.CLIENT)
    @Override
    protected void getTypeDefinitions(List<FieldDef> defs) {
        defs.addAll(Arrays.asList(
            FieldDef.enumField("gui.type", CounterType.class, this::getCounterTypeEnum, this::setCounterTypeEnum)
                .hover("ability.hover.counterType"),
            FieldDef.floatField("gui.value", this::getCounterValue, this::setCounterValue),
            AbilityFieldDefs.effectsListField("ability.effects", this::getEffects, this::setEffects)
        ));

        FieldDef.insertAfter(defs, "ability.dazedAnimation",
            FieldDef.animSubGui("ability.counterAnimation",
                    this::getCounterAnimationId, this::setCounterAnimationId,
                    this::getCounterAnimationName, this::setCounterAnimationName)
                .tab("Effects"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS / SETTERS
    // ═══════════════════════════════════════════════════════════════════

    public CounterType getCounterTypeEnum() {
        return counterType;
    }

    public void setCounterTypeEnum(CounterType counterType) {
        this.counterType = counterType;
    }

    @Override
    public int getCounterType() {
        return counterType.ordinal();
    }

    @Override
    public void setCounterType(int type) {
        CounterType[] values = CounterType.values();
        this.counterType = type >= 0 && type < values.length ? values[type] : CounterType.FLAT;
    }

    @Override
    public float getCounterValue() {
        return counterValue;
    }

    @Override
    public void setCounterValue(float counterValue) {
        this.counterValue = counterValue;
    }

    @Override
    public int getCounterAnimationId() {
        return counterAnimationId;
    }

    @Override
    public void setCounterAnimationId(int counterAnimationId) {
        this.counterAnimationId = counterAnimationId;
    }

    public String getCounterAnimationName() {
        return counterAnimationName;
    }

    public void setCounterAnimationName(String name) {
        this.counterAnimationName = name != null ? name : "";
    }
}
