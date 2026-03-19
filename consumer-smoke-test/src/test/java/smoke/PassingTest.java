package smoke;

import org.testng.Assert;
import org.testng.annotations.Test;

public class PassingTest {

    @Test(description = "should verify basic addition")
    public void testAddition() {
        Assert.assertEquals(2 + 3, 5, "2 + 3 should equal 5");
    }

    @Test(description = "should verify string is not empty")
    public void testStringNotEmpty() {
        String value = "Hello QA Touch";
        Assert.assertFalse(value.isEmpty(), "String should not be empty");
    }

    @Test(description = "should verify list contains item")
    public void testListContains() {
        java.util.List<String> items = java.util.Arrays.asList("alpha", "beta", "gamma");
        Assert.assertTrue(items.contains("beta"), "List should contain 'beta'");
    }
}
