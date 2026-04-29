package cn.taotxi;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.DoubleArgumentType;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;;

// TODO: 拆分代码
// TODO: 使用配置文件存储配置
public class Attacker implements ModInitializer {
	public static final String MOD_ID = "attacker";
    private static Map<Entity, Integer> waitList = new HashMap<>();
    private static boolean isAttacking = false;
    private static int attackInterval = 1;
    private static double attackRange = 2.5;
    private static int delay = 10;
    private static int ticker = 0;
    private static String attackTarget = "";    // TODO: 改成列表，相应地指令也改
    private static boolean isAttackAll = false;
    private static int maxAttackCount = 10;
    private static boolean isDebug = false;

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);    // TODO: 输出使用日志模块

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

    // TODO: 语言国际化
    // TODO: 优化排版，附带多种颜色
    private static int showHelp(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Component.literal("===========宇宙超级无敌伟大的拾玖光环==========="));
        context.getSource().sendFeedback(Component.literal("/at on/off:          开启/关闭。当前状态：" + (isAttacking ? "on" : "off")));
        context.getSource().sendFeedback(Component.literal("/at it <int>:        攻击间隔，单位为gt。默认1。当前值：" + attackInterval));
        context.getSource().sendFeedback(Component.literal("/at range <double>:  攻击范围，默认2.5。当前值：" + attackRange));
        context.getSource().sendFeedback(Component.literal("/at delay <int>:     下一次攻击同一生物的最小时间间隔，默认10gt（也就是伤害免疫时间）。当前值：" + delay));
        context.getSource().sendFeedback(Component.literal("/at to:              将攻击目标设置为准星指向的实体。"));
        context.getSource().sendFeedback(Component.literal("/at target <string>  设置攻击目标的id, 详细ID请查询wiki。当前值：" + attackTarget));
        context.getSource().sendFeedback(Component.literal("/at all on/off       范围攻击，在一个运行周期内攻击所有符合条件的目标。关闭后一个周期只攻击一个目标。当前状态：" + (isAttackAll ? "on" : "off")));
        context.getSource().sendFeedback(Component.literal("/at maxCount <int>:  一个运行周期内攻击的最大目标数量，默认10，-1代表全部。当前值：" + maxAttackCount));
        context.getSource().sendFeedback(Component.literal("/at debug on/off     开关，是否开启调试模式（控制台输出）。当前状态：" + (isDebug ? "on" : "off")));
        context.getSource().sendFeedback(Component.literal("============================================="));
        return 1;
    }

    // TODO: 优化命令的代码结构，回调另外写成方法
    private static void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("at")
            .executes(Attacker::showHelp)
            .then(ClientCommandManager.literal("help").executes(Attacker::showHelp))
            .then(ClientCommandManager.literal("on").executes(context -> {
                isAttacking = true;
                context.getSource().sendFeedback(Component.literal("拾玖光环已开启"));
                return 1;
            }))
            .then(ClientCommandManager.literal("off").executes(context -> {
                isAttacking = false;
                context.getSource().sendFeedback(Component.literal("拾玖光环已关闭"));
                return 1;
            }))
            .then(ClientCommandManager.literal("it")
                .then(ClientCommandManager.argument("interval", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        attackInterval = IntegerArgumentType.getInteger(context, "interval");
                        context.getSource().sendFeedback(Component.literal("攻击间隔设置为：" + attackInterval));
                        return 1;
                    })
                )
            )
            .then(ClientCommandManager.literal("range")
                .then(ClientCommandManager.argument("range", DoubleArgumentType.doubleArg(0.0))
                    .executes(context -> {
                        attackRange = DoubleArgumentType.getDouble(context, "range");
                        context.getSource().sendFeedback(Component.literal("攻击范围设置为：" + attackRange));
                        return 1;
                    })
                )
            )
            .then(ClientCommandManager.literal("delay")
                .then(ClientCommandManager.argument("delay", IntegerArgumentType.integer(0))
                    .executes(context -> {
                        delay = IntegerArgumentType.getInteger(context, "delay");
                        context.getSource().sendFeedback(Component.literal("攻击同一生物的最小时间间隔设置为：" + delay));
                        return 1;
                    })
                )
            )
            .then(ClientCommandManager.literal("target")
                .then(ClientCommandManager.argument("target", StringArgumentType.string())
                    .executes(context -> {
                        attackTarget = StringArgumentType.getString(context, "target");
                        context.getSource().sendFeedback(Component.literal("攻击目标设置为：" + attackTarget));
                        return 1;
                    })
                )
            )
            .then(ClientCommandManager.literal("all")
                .then(ClientCommandManager.literal("on").executes(context -> {
                    isAttackAll = true;
                    context.getSource().sendFeedback(Component.literal("范围攻击已开启"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("off").executes(context -> {
                    isAttackAll = false;
                    context.getSource().sendFeedback(Component.literal("范围攻击已关闭"));
                    return 1;
                }))
            )
            .then(ClientCommandManager.literal("maxCount")
                .then(ClientCommandManager.argument("maxCount", IntegerArgumentType.integer(-1))
                    .executes(context -> {
                        maxAttackCount = IntegerArgumentType.getInteger(context, "maxCount");
                        context.getSource().sendFeedback(Component.literal("范围最大攻击数量设置为：" + maxAttackCount));
                        return 1;
                    })
                )
            )
            .then(ClientCommandManager.literal("debug")
                .then(ClientCommandManager.literal("on").executes(context -> {
                    isDebug = true;
                    context.getSource().sendFeedback(Component.literal("调试模式已开启"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("off").executes(context -> {
                    isDebug = false;
                    context.getSource().sendFeedback(Component.literal("调试模式已关闭"));
                    return 1;
                }))
            )
            .then(ClientCommandManager.literal("to").executes(context -> {
                Entity targetEntity = context.getSource().getClient().crosshairPickEntity;
                if (targetEntity != null) {
                    attackTarget = targetEntity.getType().toShortString();
                    context.getSource().sendFeedback(Component.literal("攻击目标设置为：" + attackTarget));
                } else {
                    context.getSource().sendFeedback(Component.literal("准星指向实体不存在"));
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
            isAttacking = false;    // TODO: 待斟酌变更世界后，是否需要关闭光环
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

    private List<Entity> getAttackList() {
        if (maxAttackCount == 0) {
            return List.of();
        }

        Minecraft mc = Minecraft.getInstance();
        AABB attackArea = mc.player.getBoundingBox().inflate(attackRange * 1.5);
        List<Entity> attackList = new ArrayList<>();
        for (Entity entity : mc.level.getEntities(mc.player, attackArea)) {
            if (canAttack(mc.player, entity)) {
                attackList.add(entity);
            }
        }
        if (maxAttackCount == -1) {
            return attackList;
        }
        if (attackList.size() > maxAttackCount) {
            attackList.subList(maxAttackCount, attackList.size()).clear();
        }
        return attackList;
    }

    private boolean canAttack(Player player, Entity entity) {
        if (isDebug) {
            System.out.println("In waitList: " + waitList.containsKey(entity));
            System.out.println("Name: " + entity.getName());
            System.out.println("Type: " + entity.getType().toShortString());
            System.out.println("isTarget: " + entity.getType().toShortString().equals(attackTarget));
            System.out.println("isAlive: " + entity.isAlive());
            System.out.println("isRemoved: " + entity.isRemoved());
            System.out.println("In attack range: " + (entity.distanceToSqr(player) < attackRange * attackRange));
            System.out.println("isAttackable: " + entity.isAttackable());
            System.out.println("\n");
        }

        if (waitList.containsKey(entity)) {
            if (waitList.get(entity) + delay >= ticker) {
                return false;
            } else {
                waitList.remove(entity);
            }
        }

        boolean isTarget = attackTarget.isEmpty() ||
                        (!attackTarget.isEmpty() && entity.getType().toShortString().equals(attackTarget));

        // TODO: 优化实体的选择范围
        return 
            entity instanceof Mob &&
            !(entity instanceof Player) &&
            isTarget &&
            entity.isAlive() &&
            !entity.isRemoved() &&
            entity.isAttackable() &&
            entity.distanceToSqr(player) < attackRange * attackRange;
    }

    private void tryToAttack() {
        Minecraft mc = Minecraft.getInstance();
        if (isAttackAll) {
            List<Entity> attackList = getAttackList();
            if (attackList.isEmpty()) {
                return;
            }
            for (Entity entity : attackList) {
                mc.gameMode.attack(mc.player, entity);
                waitList.put(entity, ticker);
            }
            mc.player.swing(InteractionHand.MAIN_HAND);
            return;
        }

        Entity target = getAttackTarget();
        if (target == null) {
            return;
        }
                
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        waitList.put(target, ticker);
    }
}