package kernitus.plugin.OldCombatMechanics;

import be.seeseemelk.mockbukkit.ServerMock;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.UUID;

public class OCMPlayerMockFactory {

    private final ServerMock server;
    private final Random random = new Random();
    private int currentNameIndex = 0;

    public OCMPlayerMockFactory(@NotNull ServerMock server) {
        this.server = server;
    }

    @NotNull
    private String getUniqueRandomName() {
        int var10002 = this.currentNameIndex++;
        String name = "Player" + var10002;
        if (name.length() > 16) {
            throw new IllegalStateException("Maximum number of player names reached");
        } else {
            return name;
        }
    }

    @NotNull
    public OCMPlayerMock createRandomPlayer() {
        String name = this.getUniqueRandomName();
        UUID uuid = new UUID(this.random.nextLong(), this.random.nextLong());
        return new OCMPlayerMock(this.server, name, uuid);
    }
}
