package baritone.command.defaults;

import baritone.api.BaritoneAPI;
import baritone.api.command.AbstractCommand;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.stream.Collectors;

public class MGotoCommand extends AbstractCommand {

    private Goal originalGoal = null;
    private boolean hunting = false;

public MGotoCommand(IBaritone baritone) {
    super(baritone, "mgoto");
}

    @Override
    public void execute(String label, String[] args) {
        if (args.length < 3) {
            log("Usage: #mgoto <x> <y> <z>");
            return;
        }

        int x = Integer.parseInt(args[0]);
        int y = Integer.parseInt(args[1]);
        int z = Integer.parseInt(args[2]);
        
        this.originalGoal = new GoalBlock(x, y, z);

        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(originalGoal);

        // Start monitoring thread
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);

                    if (hunting) continue;

                    List<Entity> hostiles = mc.world.getEntities().stream()
                        .filter(e -> e instanceof HostileEntity)
                        .filter(e -> e.squaredDistanceTo(mc.player) < 100 * 100)
                        .collect(Collectors.toList());

                    if (!hostiles.isEmpty()) {
                        Entity target = hostiles.get(0); // Get first hostile
                        log("Hostile mob detected at " + target.getBlockPos() + ". Hunting it now.");

                        hunting = true;

                        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior()
                                .setGoalAndPath(new GoalBlock(target.getBlockPos()));

                        // Wait until mob is dead or very close
                        while (target.isAlive() && mc.player.squaredDistanceTo(target) > 2) {
                            Thread.sleep(500);
                        }

                        log("Target neutralized. Returning to original path.");

                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                                .setGoalAndPath(originalGoal);

                        hunting = false;
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }).start();
    }

    @Override
    public String getShortDesc() {
        return "Goes to a position but prioritizes hunting hostile mobs en route.";
    }

    @Override
    public String getLongDesc() {
        return "Usage: #mgoto <x> <y> <z>. Walks to the target position, but if a hostile mob is detected within 100 blocks, it diverts to hunt it down first.";
    }
}
