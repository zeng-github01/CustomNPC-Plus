package noppes.npcs.ai;

import net.minecraft.entity.ai.EntityAIBase;
import noppes.npcs.constants.AiMutex;
import noppes.npcs.entity.EntityNPCInterface;

public class EntityAITransform extends EntityAIBase {

    private EntityNPCInterface npc;

    public EntityAITransform(EntityNPCInterface npc) {
        this.npc = npc;
        // No mutex: this only polls the world time and flips the transform, it drives nothing.
        // Claiming PASSIVE let any running movement or look task starve it, so the day/night
        // swap only fired in the gaps where those yielded - such as while saying a line.
        setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() {
        if (npc.isKilled() || npc.isAttacking() || npc.transform.editingModus)
            return false;

        return npc.worldObj.getWorldTime() % 24000 < 12000 ? npc.transform.isActive : !npc.transform.isActive;
    }

    public void startExecuting() {
        npc.transform.transform(!npc.transform.isActive);
    }
}
