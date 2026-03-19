package smoke;

import org.testng.Assert;
import org.testng.annotations.Test;

public class FailureTest {

    @Test(description = "should fail with wrong expected value")
    public void testWrongExpectedValue() {
        // Deliberate failure: 10 * 5 = 50, not 99
        Assert.assertEquals(10 * 5, 99, "10 * 5 should equal 99 (intentional failure)");
    }

    @Test(description = "should fail with null pointer assertion")
    public void testNullAssertion() {
        String value = null;
        // Deliberate failure: null is not "expected"
        Assert.assertEquals(value, "expected", "Null should equal 'expected' (intentional failure)");
    }

    @Test(description = "should pass as a control test")
    public void testControlPass() {
        Assert.assertTrue(true, "This should always pass");
    }
}
