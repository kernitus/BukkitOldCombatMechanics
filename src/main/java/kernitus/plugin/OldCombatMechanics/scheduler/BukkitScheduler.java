package kernitus.plugin.OldCombatMechanics.scheduler;

import kernitus.plugin.OldCombatMechanics.scheduler.task.BukkitTaskWrapper;
import kernitus.plugin.OldCombatMechanics.scheduler.task.ITaskWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Bukkit平台的调度器
 */
public enum BukkitScheduler implements IScheduler {

    INSTANCE;

    @Override
    public ITaskWrapper runTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        return new BukkitTaskWrapper(Bukkit.getScheduler().runTask(plugin, task));
    }

    @Override
    public ITaskWrapper runTaskAsync(@NotNull Plugin plugin, @NotNull Runnable task) {
        return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    @Override
    public ITaskWrapper runTaskLater(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks) {
        return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public ITaskWrapper runTaskLaterAsync(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks) {
        return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks));
    }

    @Override
    public ITaskWrapper runTaskTimer(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks, long periodTicks) {
        return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public ITaskWrapper runTaskTimerAsync(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks, long periodTicks) {
        return new BukkitTaskWrapper(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public void cancelTask(int taskId) {
        Bukkit.getScheduler().cancelTask(taskId);
    }

    @Override
    public ITaskWrapper runTaskOnEntity(Plugin plugin, Entity entity, Runnable task, Runnable retriedTask) {
        return runTask(plugin, task);
    }

    @Override
    public ITaskWrapper runTaskOnEntityLater(Plugin plugin, Entity entity, Runnable task, Runnable retriedTask, long delayTicks) {
        return runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public ITaskWrapper runTaskOnEntityTimer(Plugin plugin, Entity entity, Runnable task, Runnable retriedTask, long delayTicks, long periodTicks) {
        return runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    @Override
    public ITaskWrapper runTaskOnLocation(Plugin plugin, Location location, Runnable task) {
        return runTask(plugin, task);
    }

    @Override
    public ITaskWrapper runTaskOnLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        return runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public ITaskWrapper runTaskOnLocationTimer(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks) {
        return runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    @Override
    public void cancelTasks(@NotNull Plugin plugin) {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

}
