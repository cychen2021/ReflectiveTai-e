package pascal.taie.analysis.pta;

import org.junit.Test;
import pascal.taie.analysis.Tests;

public class SolarTest {
    private static final String DIR = "solar";

    private static final String[] OPTS = {"reflection:solar"};

    @Test
    public void testBasicEntry() {
        Tests.testPTA(DIR, "Basic", OPTS);
    }
}
