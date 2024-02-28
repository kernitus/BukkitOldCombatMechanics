package kernitus.plugin.OldCombatMechanics.scheduler.task;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class FoliaTaskWrapper implements ITaskWrapper {

    private final ScheduledTask scheduledTask;

    public FoliaTaskWrapper(ScheduledTask scheduledTask) {
        this.scheduledTask = scheduledTask;
    }

    @Override
    public void cancel() {
        scheduledTask.cancel();
    }

    @Override
    public @NotNull Plugin owner() {
        return scheduledTask.getOwningPlugin();
    }

    @Override
    public Integer taskId() {
        throw new UnsupportedOperationException("Folia task can not get task id");
    }

    @Override
    public boolean isCancelled() {
        return scheduledTask.isCancelled();
    }

    @Override
    public @NotNull ScheduledTask platformTask() {
        return scheduledTask;
    }

}
