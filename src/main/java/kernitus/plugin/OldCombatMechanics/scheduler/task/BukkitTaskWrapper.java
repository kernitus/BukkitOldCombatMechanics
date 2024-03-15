package kernitus.plugin.OldCombatMechanics.scheduler.task;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class BukkitTaskWrapper implements ITaskWrapper {

    private final BukkitTask bukkitTask;

    public BukkitTaskWrapper(@NotNull BukkitTask bukkitTask) {
        this.bukkitTask = bukkitTask;
    }

    @Override
    public void cancel() {
        bukkitTask.cancel();
    }

    @Override
    @NotNull
    public Plugin owner() {
        return bukkitTask.getOwner();
    }

    @Override
    public Integer taskId() {
        return bukkitTask.getTaskId();
    }

    @Override
    public boolean isCancelled() {
        return bukkitTask.isCancelled();
    }

    @Override
    @NotNull
    public BukkitTask platformTask() {
        return bukkitTask;
    }

}
