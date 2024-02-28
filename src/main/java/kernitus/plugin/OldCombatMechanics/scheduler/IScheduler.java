package kernitus.plugin.OldCombatMechanics.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import kernitus.plugin.OldCombatMechanics.scheduler.task.ITaskWrapper;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/**
 * 平台调度器接口
 */
public interface IScheduler {

    /**
     * 执行一个任务
     *
     * @param plugin 调用的插件
     * @param task   需要执行的任务
     */
    ITaskWrapper runTask(@NotNull Plugin plugin, @NotNull Runnable task);

    /**
     * 异步执行一个任务
     *
     * @param plugin 调用的插件
     * @param task   需要执行的任务
     */
    ITaskWrapper runTaskAsync(@NotNull Plugin plugin, @NotNull Runnable task);

    /**
     * 在延迟后执行一个任务
     *
     * @param plugin     调用的插件
     * @param task       需要执行的任务
     * @param delayTicks 延迟的时间
     */
    ITaskWrapper runTaskLater(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks);

    /**
     * 在延迟后异步执行一个任务
     *
     * @param plugin     调用的插件
     * @param task       需要执行的任务
     * @param delayTicks 延迟的时间，单位为tick
     */
    ITaskWrapper runTaskLaterAsync(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks);

    /**
     * 在延迟后循环执行任务
     *
     * @param plugin      调用的插件
     * @param task        需要执行的任务
     * @param delayTicks  延迟的时间，单位为tick
     * @param periodTicks 循环执行的间隔时间，单位为tick
     */
    ITaskWrapper runTaskTimer(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks, long periodTicks);

    /**
     * 在延迟后循环执行任务
     *
     * @param plugin      调用的插件
     * @param task        需要执行的任务
     * @param delayTicks  延迟的时间，单位为tick
     * @param periodTicks 循环执行的间隔时间，单位为tick
     */
    ITaskWrapper runTaskTimerAsync(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks, long periodTicks);

    /**
     * 取消某个任务
     *
     * @param task 需要取消的任务的包装
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
     * 在指定实体的调度器上执行任务
     * 当平台为非Folia时，效果等同于runTask
     *
     * @param plugin      执行的插件
     * @param entity      执行载体
     * @param task        执行的任务
     * @param retriedTask 执行任务失败时, 重新尝试的任务
     */
    ITaskWrapper runTaskOnEntity(Plugin plugin, Entity entity, Runnable task, Runnable retriedTask);

    /**
     * 在指定实体的调度器上延迟执行任务
     * 当平台为非Folia时，效果等同于runTaskLater
     *
     * @param plugin      执行的插件
     * @param entity      执行载体
     * @param task        执行的任务
     * @param retriedTask 执行任务失败时, 重新尝试的任务
     * @param delayTicks  延迟执行的时间
     */
    ITaskWrapper runTaskOnEntityLater(Plugin plugin, Entity entity, Runnable task, Runnable retriedTask, long delayTicks);

    /**
     * 在指定实体的调度器上延迟一段时间后重复执行任务
     * 当平台为非Folia时，效果等同于runTaskTimer
     *
     * @param plugin      执行的插件
     * @param entity      执行载体
     * @param task        执行的任务
     * @param retriedTask 执行任务失败时, 重新尝试的任务
     * @param delayTicks  延迟执行的时间
     * @param periodTicks 重复执行的间隔
     */
    ITaskWrapper runTaskOnEntityTimer(Plugin plugin, Entity entity, Runnable task, Runnable retriedTask, long delayTicks, long periodTicks);

    /**
     * 在指定坐标的调度器上执行任务
     * 当平台为非Folia时，效果等同于runTask
     *
     * @param plugin      执行的插件
     * @param location    执行载体
     * @param task        执行的任务
     */
    ITaskWrapper runTaskOnLocation(Plugin plugin, Location location, Runnable task);


    /**
     * 在指定实体的调度器上延迟执行任务
     * 当平台为非Folia时，效果等同于runTaskLater
     *
     * @param plugin      执行的插件
     * @param location    执行载体
     * @param task        执行的任务
     * @param delayTicks  延迟执行的时间
     */
    ITaskWrapper runTaskOnLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks);

    /**
     * 在指定实体的调度器上延迟一段时间后重复执行任务
     * 当平台为非Folia时，效果等同于runTaskTimer
     *
     * @param plugin      执行的插件
     * @param location    执行载体
     * @param task        执行的任务
     * @param delayTicks  延迟执行的时间
     * @param periodTicks 重复执行的间隔
     */
    ITaskWrapper runTaskOnLocationTimer(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks);

}
