package baritone.command.defaults;

import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.utils.Helper;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.IBaritone;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Custom mgoto command for Baritone 1.21.3.
 * Goes to X/Z coordinates, pausing to kill all hostile mobs in a 100-block radius.
 */
public class MGotoCommand extends Command {
    public MGotoCommand(IBaritone baritone) {
        // Register command name "mgoto"
        super(baritone, "mgoto");
    }

    @Override
    public void execute(String label, IArgConsumer args) {
        // Require exactly two arguments: x and z coordinates
        args.requireExactly(2);
        // Parse coordinates
        int x = args.getAs(Integer.class);
        int z = args.getAs(Integer.class);

        // Set primary goal to the given X/Z coordinates (long-range, Y unspecified)
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(x, z));

        // Start a new thread to monitor and chase nearby hostile mobs
        new Thread(() -> {
            try {
                while (true) {
                    // Get player and world instances
                    var world = Helper.mc.world;
                    var player = Helper.mc.player;
                    if (world == null || player == null) {
                        // If world or player not loaded, wait and retry
                        Thread.sleep(100);
                        continue;
                    }

                    // Find all hostile mobs within 100 blocks
                    List<Monster> hostiles = new ArrayList<>();
                    for (Entity e : world.getEntities()) {
                        if (e instanceof Monster mob) {
                            double distance = mob.distanceTo(player);
                            if (distance <= 100.0) {
                                hostiles.add(mob);
                            }
                        }
                    }

                    if (!hostiles.isEmpty()) {
                        // Sort by distance to find nearest first
                        Collections.sort(hostiles, Comparator.comparingDouble(m -> m.distanceTo(player)));
                        for (Monster target : hostiles) {
                            // Skip if target already dead or out of range
                            if (!target.isAlive() || target.distanceTo(player) > 100.0) continue;

                            // Cancel current path and chase this target
                            baritone.getPathingBehavior().cancelEverything();
                            BlockPos targetPos = target.blockPosition();
                            baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetPos.getX(), targetPos.getY(), targetPos.getZ()));

                            // Wait until the target is dead or out of range
                            while (target.isAlive() && player.distanceTo(target) <= 100.0) {
                                // Update goal if target has moved
                                BlockPos newPos = target.blockPosition();
                                if (!newPos.equals(targetPos)) {
                                    targetPos = newPos;
                                    baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetPos.getX(), targetPos.getY(), targetPos.getZ()));
                                }
                                // Sleep briefly
                                Thread.sleep(100);
                            }
                        }
                        // After killing all detected mobs, restore original goal
                        baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(x, z));
                    } else {
                        // No mobs detected, sleep before checking again
                        Thread.sleep(500);
                    }
                }
            } catch (InterruptedException ignored) {
                // Thread interrupted; exit
            }
        }).start();
    }

    @Override
public Stream<String> tabComplete(String label, IArgConsumer args) {
    return Stream.of();
}

    @Override
    public String getShortDesc() {
        return "Move to XZ coordinates, killing hostiles within 100 blocks.";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of("Usage: #mgoto <x> <z> - path to X/Z, pausing to chase and kill hostile mobs within 100 block radius.");
    }
}
