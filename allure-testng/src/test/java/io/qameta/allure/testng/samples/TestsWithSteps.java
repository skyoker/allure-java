package io.qameta.allure.testng.samples;

import io.qameta.allure.Step;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Egor Borisov ehborisov@gmail.com
 */
public class TestsWithSteps {

    @BeforeSuite
    public void beforeSuite() throws Exception {

    }

    @BeforeTest
    public void beforeTest() throws Exception {

    }

    @BeforeClass
    public void beforeClass() throws Exception {

    }

    @BeforeMethod
    public void beforeMethod() throws Exception {

    }

    @Test
    public void testWithOneStep() {
        stepOne();
    }

    @Step("Sample step one")
    private void stepOne() {
    }

    @Step("Failing step")
    private void failingStep() {
        assertThat(2).isEqualTo(1);
    }

    @Test
    public void failingByAssertion() {
        stepOne();
        failingStep();
    }

    @Test
    public void skipped() {
        stepOne();
        skipThisTest();
    }

    @Step
    private void skipThisTest() {
        throw new SkipException("Skipped");
    }

    @Test
    public void brokenTest() {
        stepOne();
        broken();
    }

    @Test
    public void brokenTestWithoutMessage() {
        stepOne();
        brokenWithoutMessage();
    }

    @Step
    private void broken() {
        throw new RuntimeException("Exception");
    }

    @Step
    private void brokenWithoutMessage() {
        throw new RuntimeException();
    }
}
