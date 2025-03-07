package io.split.client;

import junit.framework.TestCase;
import org.junit.Test;

public class ApiKeyCounterTest extends TestCase {

    private static final String FIRST_KEY = "KEYNUMBER1";
    private static final String SECOND_KEY = "KEYNUMBER2";

    @Test
    public void testAddingNewToken() {
        ApiKeyCounter.getApiKeyCounterInstance().add(FIRST_KEY);
        assertTrue(ApiKeyCounter.getApiKeyCounterInstance().isApiKeyPresent(FIRST_KEY));

        ApiKeyCounter.getApiKeyCounterInstance().remove(FIRST_KEY);
    }

    @Test
    public void testAddingExistingToken() {
        ApiKeyCounter.getApiKeyCounterInstance().add(FIRST_KEY);
        ApiKeyCounter.getApiKeyCounterInstance().add(FIRST_KEY);

        assertTrue(ApiKeyCounter.getApiKeyCounterInstance().isApiKeyPresent(FIRST_KEY));
        assertEquals(2, ApiKeyCounter.getApiKeyCounterInstance().getCount(FIRST_KEY));
        ApiKeyCounter.getApiKeyCounterInstance().remove(FIRST_KEY);
        ApiKeyCounter.getApiKeyCounterInstance().remove(FIRST_KEY);
    }

    @Test
    public void testRemovingToken() {
        ApiKeyCounter.getApiKeyCounterInstance().add(FIRST_KEY);
        ApiKeyCounter.getApiKeyCounterInstance().remove(FIRST_KEY);

        assertFalse(ApiKeyCounter.getApiKeyCounterInstance().isApiKeyPresent(FIRST_KEY));
        assertEquals(0, ApiKeyCounter.getApiKeyCounterInstance().getCount(FIRST_KEY));
    }

    @Test
    public void testAddingNonExistingToken() {
        ApiKeyCounter.getApiKeyCounterInstance().add(FIRST_KEY);
        ApiKeyCounter.getApiKeyCounterInstance().add(SECOND_KEY);

        assertTrue(ApiKeyCounter.getApiKeyCounterInstance().isApiKeyPresent(FIRST_KEY));
        assertEquals(1, ApiKeyCounter.getApiKeyCounterInstance().getCount(FIRST_KEY));
        assertEquals(1, ApiKeyCounter.getApiKeyCounterInstance().getCount(SECOND_KEY));
        ApiKeyCounter.getApiKeyCounterInstance().remove(FIRST_KEY);
        ApiKeyCounter.getApiKeyCounterInstance().remove(SECOND_KEY);
    }
}
