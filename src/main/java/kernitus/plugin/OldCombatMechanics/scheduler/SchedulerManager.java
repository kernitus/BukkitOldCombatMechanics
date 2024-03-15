package kernitus.plugin.OldCombatMechanics.scheduler;

public enum SchedulerManager {

    INSTANCE;
    private IScheduler scheduler;

    public void loadPlatform() {
        try {
            Class<?> pluginMetaClass = Class.forName("io.papermc.paper.plugin.configuration.PluginMeta");
            pluginMetaClass.getMethod("isFoliaSupported");
            scheduler = FoliaScheduler.INSTANCE;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            scheduler = BukkitScheduler.INSTANCE;
        }
    }

    public IScheduler getScheduler() {
        return scheduler;
    }

}
