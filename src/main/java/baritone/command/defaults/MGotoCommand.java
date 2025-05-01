package baritone.command.defaults;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Box;

import java.util.List;

public class MGotoCommand extends Command {

    public MGotoCommand() {
        super("mgoto", "Vai para uma posição X/Z, atacando mobs hostis (Monster) no raio de 100 blocos.");
    }

    @Override
    public void execute(String label, String[] args) {
        if (args.length < 3) {
            logDirect("Uso: mgoto <x> <z>");
            return;
        }
        // Parse coordenadas de destino (X, Z)
        double targetX = Double.parseDouble(args[1]);
        double targetZ = Double.parseDouble(args[2]);

        // Obtém instância do Baritone e do jogador/mundo do Minecraft
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        MinecraftClient mc = MinecraftClient.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            logDirect("Mundo não carregado ou jogador nulo.");
            return;
        }

        // Atualização: Cancela quaisquer caminhos/processos anteriores antes de definir novo objetivo
        baritone.getPathingBehavior().cancelEverything();

        // Define objetivo principal (coordenadas X/Z)
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(targetX, targetZ));

        // Loop para monitorar mobs hostis enquanto o bot caminha
        // (Em prática, isso deveria rodar em outra thread/async no mod real, este é apenas exemplo)
        while (!baritone.getPathingBehavior().isPathingPaused()) {
            // Cria caixa de busca ao redor do jogador com raio de 100 blocos em cada direção
            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();
            Box searchBox = new Box(px - 100, py - 100, pz - 100, px + 100, py + 100, pz + 100);

            // Atualização: Em 1.21 use getEntitiesOfClass para listar entidades
            List<LivingEntity> hostiles = mc.level.getEntitiesOfClass(
                    LivingEntity.class,
                    searchBox,
                    e -> (e instanceof Monster) && e.isAlive() // Filtra entidades tipo Monster vivas
            );

            // Se encontrou algum mob hostil, direciona o Baritone para atacá-lo
            if (!hostiles.isEmpty()) {
                // Escolhe um alvo (por exemplo, o primeiro da lista ou o mais próximo)
                LivingEntity alvo = hostiles.get(0);
                // Opcional: poderia selecionar o mais próximo via distanceTo

                // Atualização: Cancele qualquer caminho antes de ir ao mob
                baritone.getPathingBehavior().cancelEverything();

                // Novo objetivo: mover até as coordenadas do mob
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(alvo.getX(), alvo.getZ()));

                // Espera até que o mob seja eliminado (isAlive == false)
                // (Em código real, cuidado com loops bloqueantes; aqui simplificado)
                while (alvo.isAlive()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Tratar interrupção adequadamente em código real
                    }
                }

                // Após matar o mob, voltar ao objetivo original
                baritone.getPathingBehavior().cancelEverything();
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(targetX, targetZ));
            }

            // Breve pausa para evitar sobrecarga do loop (simulação de atualização do tick)
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}