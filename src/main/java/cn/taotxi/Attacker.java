package cn.taotxi;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;;

public class Attacker implements ModInitializer {
	public static final String MOD_ID = "attacker";
    private static Map<Entity, Integer> waitList = new HashMap<>();
    private static boolean isAttacking = false;
    private static int attackInterval = 1;
    private static double attackRange = 2.5;
    private static int delay = 10;
    private static int ticker = 0;
    private static String attackTarget = "";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		registerTickEvents();
		registerCommand();
		registerWorldEvents();
	}

    private void registerTickEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                return;
            }
            if (!isAttacking) {
                return;
            }
            ticker++;
            if (ticker % attackInterval == 0) {
                tryToAttack();
            }
        });
    }

    private static void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("at")
            .then(ClientCommandManager.literal("on").executes(context -> {
                isAttacking = true;
                return 1;
            }))
            .then(ClientCommandManager.literal("off").executes(context -> {
                isAttacking = false;
                return 1;
            }))
            .then(ClientCommandManager.literal("it")
                .then(ClientCommandManager.argument("interval", IntegerArgumentType.integer())
                    .executes(context -> {
                        attackInterval = IntegerArgumentType.getInteger(context, "interval");
                        return 1;
                    })
                )
            )
            .then(ClientCommandManager.literal("range")
                .then(ClientCommandManager.argument("range", DoubleArgumentType.doubleArg())
                    .executes(context -> {
                        attackRange = DoubleArgumentType.getDouble(context, "range");
                        return 1;
                    })
                )
            )
            .then(ClientCommandManager.literal("delay")
                .then(ClientCommandManager.argument("delay", IntegerArgumentType.integer())
                    .executes(context -> {
                        delay = IntegerArgumentType.getInteger(context, "delay");
                        return 1;
                    })
                )
            )
            .then(ClientCommandManager.literal("target")
                .then(ClientCommandManager.argument("target", StringArgumentType.string())
                    .executes(context -> {
                        attackTarget = StringArgumentType.getString(context, "target");
                        return 1;
                    })
                )
            )
            .then(ClientCommandManager.literal("to").executes(context -> {
                Entity targetEntity = context.getSource().getClient().crosshairPickEntity;
                if (targetEntity != null) {
                    attackTarget = targetEntity.getType().toShortString();
                    context.getSource().sendFeedback(Component.literal("Target set to " + attackTarget));
                } else {
                    context.getSource().sendFeedback(Component.literal("No entity under crosshair"));
                    return 0;
                }
                return 1;
            }))
        );
        });
    }

    private void registerWorldEvents() {
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((mc, level) -> {
            waitList.clear();
            isAttacking = false;
        });
    }

    private Entity getAttackTarget() {
        Minecraft mc = Minecraft.getInstance();
        AABB attackArea = mc.player.getBoundingBox().inflate(attackRange * 1.5);
        for (Entity entity : mc.level.getEntities(mc.player, attackArea)) {
            if (canAttack(mc.player, entity)) {
                return entity;
            }
        }
        return null;
    }

    private boolean canAttack(Player player, Entity entity) {
        if (waitList.containsKey(entity)) {
            if (waitList.get(entity) + delay >= ticker) {
                return false;
            } else {
                waitList.remove(entity);
            }
        }

        if (!attackTarget.isEmpty() &&
            entity != player &&
            entity.getType().toShortString().equals(attackTarget) &&
            entity.isAlive() &&
            !entity.isRemoved() &&
            entity.distanceToSqr(player) < attackRange * attackRange) {
            return true;
        }

        return 
            entity != player &&
            entity.isAlive() && 
            !entity.isRemoved() &&
            !(entity instanceof LivingEntity) &&
            !(entity instanceof Player) && 
            !(entity instanceof ItemEntity) && 
            entity.isAttackable() &&
            entity.distanceToSqr(player) < attackRange * attackRange;
    }

    private void tryToAttack() {
        Minecraft mc = Minecraft.getInstance();
        Entity target = getAttackTarget();
        if (target == null) {
            return;
        }
                
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        waitList.put(target, ticker);
    }
}