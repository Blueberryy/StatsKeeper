package terrails.statskeeper.event;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import terrails.statskeeper.api.capabilities.HealthManager;
import terrails.statskeeper.config.SKHealthConfig;
import terrails.statskeeper.health.HealthCapability;
import terrails.statskeeper.health.HealthHelper;

import java.util.Map;

public class PlayerHealthHandler {

    @SubscribeEvent
    public void playerJoin(PlayerLoggedInEvent event) {
        if (!SKHealthConfig.ENABLED) {
            HealthHelper.removeModifier(event.getPlayer());
            return;
        }

        HealthManager.getInstance(event.getPlayer()).ifPresent(HealthManager::update);
    }

    @SubscribeEvent
    public void playerClone(PlayerEvent.Clone event) {
        if (!SKHealthConfig.ENABLED) {
            return;
        }

        HealthManager.getInstance(event.getEntityPlayer()).ifPresent(health -> {

            CompoundNBT compound = event.getOriginal().writeWithoutTypeId(new CompoundNBT());
            if (compound.contains("ForgeCaps", Constants.NBT.TAG_COMPOUND)) {
                health.deserialize(compound.getCompound("ForgeCaps").getCompound(HealthCapability.NAME.toString()));
                health.setHealth(health.getHealth());
            }

            if (SKHealthConfig.STARTING_HEALTH == SKHealthConfig.MAX_HEALTH && SKHealthConfig.MIN_HEALTH <= 0) {
                health.update();
                return;
            }

            if (event.isWasDeath() && SKHealthConfig.HEALTH_DECREASE > 0 && health.isHealthRemovable()) {
                int prevHealth = health.getHealth();
                health.addHealth(-SKHealthConfig.HEALTH_DECREASE);
                double removedAmount = health.getHealth() - prevHealth;
                if (SKHealthConfig.HEALTH_MESSAGE && removedAmount > 0) {
                    HealthHelper.playerMessage(event.getEntityPlayer(), "health.statskeeper.death_remove", removedAmount);
                }
            }

        });
    }

    @SubscribeEvent
    public void itemInteract(PlayerInteractEvent.RightClickItem event) {
        if (!SKHealthConfig.ENABLED || event.getWorld().isRemote())
            return;

        LazyOptional<HealthManager> optional = HealthManager.getInstance(event.getEntityPlayer());

        optional.ifPresent(health -> {

            ItemStack stack = event.getEntityPlayer().getHeldItem(event.getHand());
            Food food = stack.getItem().func_219967_s();

            if (food != null && event.getEntityPlayer().canEat(food.func_221468_d())) {
                return;
            }

            if (stack.getUseAction() == UseAction.DRINK) {
                return;
            }

            for (Map.Entry<ResourceLocation, Tuple<Integer, Boolean>> entry : SKHealthConfig.REGENERATIVE_ITEMS.entrySet()) {

                Item item = stack.getItem();
                if (item.getRegistryName() == null || !item.getRegistryName().equals(entry.getKey())) {
                    continue;
                }

                if (health.addHealth(entry.getValue().getA(), !entry.getValue().getB())) {
                    stack.shrink(1);
                    return;
                }

                break;
            }
        });
    }

    @SubscribeEvent
    public void itemInteractFinished(LivingEntityUseItemEvent.Finish event) {
        if (!SKHealthConfig.ENABLED || !(event.getEntity() instanceof ServerPlayerEntity)) {
            return;
        }

        for (Map.Entry<ResourceLocation, Tuple<Integer, Boolean>> entry : SKHealthConfig.REGENERATIVE_ITEMS.entrySet()) {

            Item item = event.getItem().getItem();
            if (item.getRegistryName() == null || !item.getRegistryName().equals(entry.getKey())) {
                continue;
            }

            HealthManager.getInstance((PlayerEntity) event.getEntity()).ifPresent(health -> health.addHealth(entry.getValue().getA(), !entry.getValue().getB()));
            break;
        }
    }

}
