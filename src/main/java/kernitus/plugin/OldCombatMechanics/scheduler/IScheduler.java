package kernitus.plugin.OldCombatMechanics.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import kernitus.plugin.OldCombatMechanics.scheduler.task.ITaskWrapper;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/**
 * Platform scheduler interface
 */
public interface IScheduler {

    /**
     * Schedules this in server scheduler to run on next tick.
     *
     * @param plugin the reference to the plugin scheduling task
     * @param task   the task to be run
     */
    ITaskWrapper runTask(@NotNull Plugin plugin, @NotNull Runnable task);

    /**
     * Schedules this in server scheduler to run asynchronously.
     *
     * @param plugin the reference to the plugin scheduling task
     * @param task   the task to be run
     */
    ITaskWrapper runTaskAsync(@NotNull Plugin plugin, @NotNull Runnable task);

    /**
     * Schedules this to run after the specified number of server ticks.
     *
     * @param plugin     the reference to the plugin scheduling task
     * @param task       the task to be run
     * @param delayTicks the ticks to wait before running the task
     */
    ITaskWrapper runTaskLater(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks);

    /**
     * Schedules this to run asynchronously after the specified number of server ticks.
     *
     * @param plugin     the reference to the plugin scheduling task
     * @param task       the task to be run
     * @param delayTicks the ticks to wait before running the task
     */
    ITaskWrapper runTaskLaterAsync(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks);

    /**
     * Returns a task that will repeatedly run until cancelled, starting after the specified number of server ticks.
     *
     * @param plugin      the reference to the plugin scheduling task
     * @param task        the task to be run
     * @param delayTicks  the ticks to wait before running the task
     * @param periodTicks the ticks to wait between runs
     */
    ITaskWrapper runTaskTimer(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks, long periodTicks);

    /**
     * Returns a task that will repeatedly run asynchronously until cancelled, starting after the specified number of server ticks
     *
     * @param plugin      the reference to the plugin scheduling task
     * @param task        the task to be run
     * @param delayTicks  the ticks to wait before running the task
     * @param periodTicks the ticks to wait between runs
     */
    ITaskWrapper runTaskTimerAsync(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks, long periodTicks);

    /**
     * Removes task from scheduler.
     *
     * @param task the task to be removed
     */
    default void cancelTask(@NotNull ITaskWrapper task) {
        if (task instanceof BukkitTask) {
            ((BukkitTask) task).cancel();
        } else if (task instanceof ScheduledTask) {
            ((ScheduledTask) task).cancel();
        } else {
            throw new AssertionError("Illegal task class " + task);
        }
    }

    void cancelTask(int taskId);

    void cancelTasks(@NotNull Plugin plugin);

    /**
     * Schedules a task to execute on the next tick. If the task failed to schedule because the scheduler is retired (entity removed), then returns null. Otherwise, either the task callback will be invoked after the specified delay, or the retired callback will be invoked if the scheduler is retired. Note that the retired callback is invoked in critical code, so it should not attempt to remove the entity, remove other entities, load chunks, load worlds, modify ticket levels, etc.
     * It is guaranteed that the task and retired callback are invoked on the region which owns the entity.
     * When the platform is not Folia, the effect is equivalent to runTask
     *
     * @param plugin      the plugin that owns the task
     * @param entity      the task's owning entity
     * @param task        the task to be run
     * @param retriedTask retire callback to run if the entity is retired before the run callback can be invoked, may be null.
     */
    ITaskWrapper runTaskOnEntity(Plugin plugin, Entity entity, Runnable task, Runnable retriedTask);

    /**
     * Schedules a task with the given delay. If the task failed to schedule because the scheduler is retired (entity removed), then returns null. Otherwise, either the task callback will be invoked after the specified delay, or the retired callback will be invoked if the scheduler is retired. Note that the retired callback is invoked in critical code, so it should not attempt to remove the entity, remove other entities, load chunks, load worlds, modify ticket levels, etc.
     * It is guaranteed that the task and retired callback are invoked on the region which owns the entity.
     * When the platform is not Folia, the effect is equivalent to runTaskLater
     *
     * @param plugin      the plugin that owns the task
     * @param entity      the task's owning entity
     * @param task        the task to be run
     * @param retriedTask retire callback to run if the entity is retired before the run callback can be invoked, may be null.
     * @param delayTicks  the ticks to wait before running the task
     */
    ITaskWrapper runTaskOnEntityLater(Plugin plugin, Entity entity, Runnable task, Runnable retriedTask, long delayTicks);

    /**
     * Schedules a repeating task with the given delay and period. If the task failed to schedule because the scheduler is retired (entity removed), then returns null. Otherwise, either the task callback will be invoked after the specified delay, or the retired callback will be invoked if the scheduler is retired. Note that the retired callback is invoked in critical code, so it should not attempt to remove the entity, remove other entities, load chunks, load worlds, modify ticket levels, etc.
     * It is guaranteed that the task and retired callback are invoked on the region which owns the entity.
     * When the platform is not Folia, the effect is equivalent to runTaskTimer
     *
     * @param plugin      the plugin that owns the task
     * @param entity      the task's owning entity
     * @param task        the task to be run
     * @param retriedTask retire callback to run if the entity is retired before the run callback can be invoked, may be null.
     * @param delayTicks  the ticks to wait before running the task
     * @param periodTicks the ticks to wait between runs
     */
    ITaskWrapper runTaskOnEntityTimer(Plugin plugin, Entity entity, Runnable task, Runnable retriedTask, long delayTicks, long periodTicks);

    /**
     * Schedules a task to be executed on the region which owns the location on the next tick.
     * When the platform is not Folia, the effect is equivalent to runTask
     *
     * @param plugin      the plugin that owns the task
     * @param location    used to get the region which the task belongs
     * @param task        the task to be run
     */
    ITaskWrapper runTaskOnLocation(Plugin plugin, Location location, Runnable task);


    /**
     * Schedules a task to be executed on the region which owns the location after the specified delay in ticks.
     * When the platform is not Folia, the effect is equivalent to runTaskLater
     *
     * @param plugin      the plugin that owns the task
     * @param location    used to get the region which the task belongs
     * @param task        the task to be run
     * @param delayTicks  the ticks to wait before running the task
     */
    ITaskWrapper runTaskOnLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks);

    /**
     * Schedules a repeating task to be executed on the region which owns the location after the initial delay with the specified period.
     * When the platform is not Folia, the effect is equivalent to runTaskTimer
     *
     * @param plugin      the plugin that owns the task
     * @param location    used to get the region which the task belongs
     * @param task        the task to be run
     * @param delayTicks  the ticks to wait before running the task
     * @param periodTicks the ticks to wait between runs
     */
    ITaskWrapper runTaskOnLocationTimer(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks);

}
