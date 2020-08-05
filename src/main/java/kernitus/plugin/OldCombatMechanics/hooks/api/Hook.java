package kernitus.plugin.OldCombatMechanics.hooks.api;

import kernitus.plugin.OldCombatMechanics.OCMMain;

public interface Hook {
    void init(OCMMain plugin);

    void deinit(OCMMain plugin);
}
