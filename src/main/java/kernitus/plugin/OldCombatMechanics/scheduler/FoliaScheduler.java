package kernitus.plugin.OldCombatMechanics.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import kernitus.plugin.OldCombatMechanics.scheduler.task.FoliaTaskWrapper;
import kernitus.plugin.OldCombatMechanics.scheduler.task.ITaskWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia平台的调度器
 */
public enum FoliaScheduler implements IScheduler {

    INSTANCE;

    @Override
    public ITaskWrapper runTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        return new FoliaTaskWrapper(Bukkit.getGlobalRegionScheduler().run(plugin, runnableToConsumer(task)));
    }

    @Override
    public ITaskWrapper runTaskAsync(@NotNull Plugin plugin, @NotNull Runnable task) {
        return new FoliaTaskWrapper(Bukkit.getAsyncScheduler().runNow(plugin, runnableToConsumer(task)));
    }

    @Override
    public ITaskWrapper runTaskLater(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks) {
        return new FoliaTaskWrapper(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, runnableToConsumer(task), delayTicks));
    }

    @Override
    public ITaskWrapper runTaskLaterAsync(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks) {
        return new FoliaTaskWrapper(Bukkit.getAsyncScheduler().runDelayed(plugin, runnableToConsumer(task), delayTicks * 50, TimeUnit.MILLISECONDS));
    }

    @Override
    public ITaskWrapper runTaskTimer(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks, long periodTicks) {
        return new FoliaTaskWrapper(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, runnableToConsumer(task), delayTicks, periodTicks));
    }

    @Override
    public ITaskWrapper runTaskTimerAsync(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks, long periodTicks) {
        return new FoliaTaskWrapper(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, runnableToConsumer(task), delayTicks * 50, periodTicks * 50, TimeUnit.MILLISECONDS));
    }

    @Override
    public void cancelTask(int taskId) {
        throw new UnsupportedOperationException("Folia can not cancel task with task id");
    }

    @Override
    public ITaskWrapper runTaskOnEntity(Plugin plugin, Entity entity, Runnable task, Runnable retriedTask) {
        return new FoliaTaskWrapper(entity.getScheduler().run(plugin, runnableToConsumer(task), retriedTask));
    }


    @Override
    public ITaskWrapper runTaskOnEntityLater(Plugin plugin, Entity entity, Runnable task, Runnable retriedTask, long delayTicks) {
        return new FoliaTaskWrapper(entity.getScheduler().runDelayed(plugin, runnableToConsumer(task), retriedTask, delayTicks));
    }

    @Override
    public ITaskWrapper runTaskOnEntityTimer(Plugin plugin, Entity entity, Runnable task, Runnable retriedTask, long delayTicks, long periodTicks) {
        return new FoliaTaskWrapper(entity.getScheduler().runAtFixedRate(plugin, runnableToConsumer(task), retriedTask, delayTicks, periodTicks));
    }

    @Override
    public ITaskWrapper runTaskOnLocation(Plugin plugin, Location location, Runnable task) {
        return new FoliaTaskWrapper(Bukkit.getRegionScheduler().run(plugin, location, runnableToConsumer(task)));
    }

    @Override
    public ITaskWrapper runTaskOnLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        return new FoliaTaskWrapper(Bukkit.getRegionScheduler().runDelayed(plugin, location, runnableToConsumer(task), delayTicks));
    }

    @Override
    public ITaskWrapper runTaskOnLocationTimer(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks) {
        return new FoliaTaskWrapper(Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, runnableToConsumer(task), delayTicks, periodTicks));
    }

    @Override
    public void cancelTasks(@NotNull Plugin plugin) {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    private Consumer<ScheduledTask> runnableToConsumer(Runnable runnable) {
        return (final ScheduledTask task) -> runnable.run();
    }

}
