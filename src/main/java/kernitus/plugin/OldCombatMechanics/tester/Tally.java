package kernitus.plugin.OldCombatMechanics.tester;

public class Tally {
    private int passed = 0;
    private int failed = 0;

    public void passed() {
        passed++;
    }

    public void failed() {
        failed++;
    }

    public int getPassed() {
        return passed;
    }

    public int getFailed() {
        return failed;
    }

    public int getTotal() {
        return passed + failed;
    }
}
