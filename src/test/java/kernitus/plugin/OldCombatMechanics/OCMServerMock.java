package kernitus.plugin.OldCombatMechanics;

import be.seeseemelk.mockbukkit.ServerMock;

public class OCMServerMock extends ServerMock {

    private final OCMPlayerMockFactory playerFactory = new OCMPlayerMockFactory(this);

    public OCMPlayerMock addPlayer() {
        this.assertMainThread();
        OCMPlayerMock player = playerFactory.createRandomPlayer();
        this.addPlayer(player);
        return player;
    }

}
