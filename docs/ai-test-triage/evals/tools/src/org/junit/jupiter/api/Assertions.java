package org.junit.jupiter.api;

import org.junit.jupiter.api.function.Executable;
import org.opentest4j.AssertionFailedError;

/** Subconjunto de Assertions com as mensagens do JUnit 5 e a excecao real do opentest4j. */
public final class Assertions {
    private Assertions() {}

    private static AssertionFailedError notEqual(Object expected, Object actual) {
        return new AssertionFailedError("expected: <" + expected + "> but was: <" + actual + ">", expected, actual);
    }
    public static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) throw notEqual(expected, actual);
    }
    public static void assertEquals(int expected, int actual) { if (expected != actual) throw notEqual(expected, actual); }
    public static void assertEquals(long expected, long actual) { if (expected != actual) throw notEqual(expected, actual); }
    public static void assertEquals(double expected, double actual) {
        if (Double.doubleToLongBits(expected) != Double.doubleToLongBits(actual)) throw notEqual(expected, actual);
    }
    public static void assertTrue(boolean condition) { if (!condition) throw notEqual(true, false); }
    public static void assertFalse(boolean condition) { if (condition) throw notEqual(false, true); }
    public static <T extends Throwable> T assertThrows(Class<T> type, Executable executable) {
        try { executable.execute(); }
        catch (Throwable t) {
            if (type.isInstance(t)) return type.cast(t);
            throw new AssertionFailedError("Unexpected exception type thrown, expected: <" + type.getName() + "> but was: <" + t.getClass().getName() + ">", t);
        }
        throw new AssertionFailedError("Expected " + type.getCanonicalName() + " to be thrown, but nothing was thrown.");
    }
}
