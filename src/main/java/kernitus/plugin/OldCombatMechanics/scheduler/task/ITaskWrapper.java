package kernitus.plugin.OldCombatMechanics.scheduler.task;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public interface ITaskWrapper {

    /**
     * 取消此任务
     */
    void cancel();

    /**
     * returns the plugin that owns the task
     *
     * @return the plugin that owns the task
     */
    @NotNull
    Plugin owner();

    /**
     * Returns the Task ID of this task. The Folia platform does not support this method.
     *
     * @return the task id of this task
     */
    Integer taskId();

    /**
     * Returns whether this task has been canceled
     *
     */
    boolean isCancelled();

    /**
     * Returns the original Task object corresponding to the platform
     *
     * @return the original Task object corresponding to the platform
     */
    @NotNull
    Object platformTask();

}
