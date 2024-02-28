package kernitus.plugin.OldCombatMechanics.scheduler.task;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public interface ITaskWrapper {

    /**
     * 取消此任务
     */
    void cancel();

    /**
     * 获取此任务的所属插件
     *
     * @return 此任务的所属插件
     */
    @NotNull
    Plugin owner();

    /**
     * 获取此任务的Task Id,Folia平台不支持此方法
     *
     * @return 此任务的Task id
     */
    Integer taskId();

    /**
     * 获取此任务是否被取消
     *
     * @return 此任务是否被取消
     */
    boolean isCancelled();

    /**
     * 返回对应平台的原始Task类型
     *
     * @return 对应平台的原始Task类型
     */
    @NotNull
    Object platformTask();

}
