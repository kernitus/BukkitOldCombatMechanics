package kernitus.plugin.OldCombatMechanics.scheduler;

import kernitus.plugin.OldCombatMechanics.scheduler.task.ITaskWrapper;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public abstract class ProxyRunnable implements Runnable {

    protected ITaskWrapper taskWrapper;

    @Override
    public abstract void run();

    public ITaskWrapper runTask(Plugin plugin) {
        checkTaskNotNull();
        return setTaskWrapper(SchedulerManager.INSTANCE.getScheduler().runTask(plugin, this));
    }

    public ITaskWrapper runTaskLater(Plugin plugin, long delayTicks) {
        checkTaskNotNull();
        return setTaskWrapper(SchedulerManager.INSTANCE.getScheduler().runTaskLater(plugin, this, delayTicks));
    }

    public ITaskWrapper runTaskTimer(Plugin plugin, long delayTicks, long periodTicks) {
        checkTaskNotNull();
        return setTaskWrapper(SchedulerManager.INSTANCE.getScheduler().runTaskTimer(plugin, this, delayTicks, periodTicks));
    }

    public ITaskWrapper runTaskAsync(Plugin plugin) {
        checkTaskNotNull();
        return setTaskWrapper(SchedulerManager.INSTANCE.getScheduler().runTaskAsync(plugin, this));
    }

    public ITaskWrapper runTaskLaterAsync(Plugin plugin, long delayTicks) {
        checkTaskNotNull();
        return setTaskWrapper(SchedulerManager.INSTANCE.getScheduler().runTaskLaterAsync(plugin, this, delayTicks));
    }

    public ITaskWrapper runTaskTimerAsync(Plugin plugin, long delayTicks, long periodTicks) {
        checkTaskNotNull();
        return setTaskWrapper(SchedulerManager.INSTANCE.getScheduler().runTaskTimerAsync(plugin, this, delayTicks, periodTicks));
    }

    public ITaskWrapper runTaskOnLocation(Plugin plugin, Location location) {
        checkTaskNotNull();
        return setTaskWrapper(SchedulerManager.INSTANCE.getScheduler().runTaskOnLocation(plugin, location, this));
    }

    public ITaskWrapper runTaskOnLocationLater(Plugin plugin, Location location, long delayTicks) {
        checkTaskNotNull();
        return setTaskWrapper(SchedulerManager.INSTANCE.getScheduler().runTaskOnLocationLater(plugin, location, this, delayTicks));
    }

    public ITaskWrapper runTaskOnLocationTimer(Plugin plugin, Location location, long delayTicks, long periodTicks) {
        checkTaskNotNull();
        return setTaskWrapper(SchedulerManager.INSTANCE.getScheduler().runTaskOnLocationTimer(plugin, location, this, delayTicks, periodTicks));
    }

    public ITaskWrapper runTaskOnEntity(Plugin plugin, Entity entity) {
        checkTaskNotNull();
        return setTaskWrapper(SchedulerManager.INSTANCE.getScheduler().runTaskOnEntity(plugin, entity, this, this));
    }

    public ITaskWrapper runTaskOnEntityLater(Plugin plugin, Entity entity, long delayTicks) {
        checkTaskNotNull();
        return setTaskWrapper(SchedulerManager.INSTANCE.getScheduler().runTaskOnEntityLater(plugin, entity, this, this, delayTicks));
    }

    public ITaskWrapper runTaskOnEntityTimer(Plugin plugin, Entity entity, long delayTicks, long periodTicks) {
        checkTaskNotNull();
        return setTaskWrapper(SchedulerManager.INSTANCE.getScheduler().runTaskOnEntityTimer(plugin, entity, this, this, delayTicks, periodTicks));
    }

    public void cancel() {
        checkTaskNull();
        this.taskWrapper.cancel();
    }

    public boolean isCancelled() {
        checkTaskNull();
        return this.taskWrapper.isCancelled();
    }

    protected ITaskWrapper setTaskWrapper(ITaskWrapper taskWrapper) {
        this.taskWrapper = taskWrapper;
        return this.taskWrapper;
    }

    protected void checkTaskNotNull() {
        if (this.taskWrapper != null) {
            throw new IllegalArgumentException("Runnable is null");
        }
    }

    protected void checkTaskNull() {
        if (this.taskWrapper == null) {
            throw new IllegalArgumentException("Task is null");
        }
    }
    
}
