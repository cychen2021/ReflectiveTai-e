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

//    TODO: Solve the random temporary number problem
//    @Test
//    public void testArgsRefineEntry() {
//        Tests.testPTA(DIR, "ArgsRefine", OPTS);
//    }

    @Test
    public void testBasicFieldEntry() {
        Tests.testPTA(DIR, "BasicField", OPTS);
    }

    @Test
    public void testCastRecvEntry() {
        Tests.testPTA(DIR, "CastRecv", OPTS);
    }

//    TODO: Solve the random temporary number problem
//    @Test
//    public void testDummyCastEntry() {
//        Tests.testPTA(DIR, "DummyCast", OPTS);
//    }

    @Test
    public void testDuplicateNameEntry() {
        Tests.testPTA(DIR, "DuplicateName", OPTS);
    }

    @Test
    public void testFieldS2TEntry() {
        Tests.testPTA(DIR, "FieldS2T", OPTS);
    }

    @Test
    public void testFieldSigEntry() {
        Tests.testPTA(DIR, "FieldSig", OPTS);
    }

    @Test
    public void testFieldTpEntry() {
        Tests.testPTA(DIR, "FieldTp", OPTS);
    }

//    TODO: Solve OutOfMemoryError
//    @Test
//    public void testGetMethodsEntry() {
//        Tests.testPTA(DIR, "GetMethods", OPTS);
//    }

    @Test
    public void testInheritanceEntry() {
        Tests.testPTA(DIR, "Inheritance", OPTS);
    }

    @Test
    public void testKnownRecvEntry() {
        Tests.testPTA(DIR, "KnownRecv", OPTS);
    }

    @Test
    public void testLazyHeapFieldEntry() {
        Tests.testPTA(DIR, "LazyHeapField", OPTS);
    }

//    TODO: Solve the random temporary number problem
//    @Test
//    public void testMethodInferenceEntry() {
//        Tests.testPTA(DIR, "MethodInference", OPTS);
//    }

    @Test
    public void testRecvTypeEntry() {
        Tests.testPTA(DIR, "RecvType", OPTS);
    }

    @Test
    public void testUnknownClassNameEntry() {
        Tests.testPTA(DIR, "UnknownClassName", OPTS);
    }


//    TODO: Solve OutOfMemoryError
//    @Test
//    public void testUnknownMethodNameEntry() {
//        Tests.testPTA(DIR, "UnknownMethodName", OPTS);
//    }

    @Test
    public void testUnknownRecvEntry() {
        Tests.testPTA(DIR, "UnknownRecv", OPTS);
    }
}
