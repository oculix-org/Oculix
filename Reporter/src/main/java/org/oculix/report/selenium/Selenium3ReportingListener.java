package org.oculix.report.selenium;

import org.oculix.report.steps.StepRecorder;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.events.WebDriverEventListener;

/**
 * Selenium 3 listener, registered by {@link SeleniumWrap} on an
 * {@code EventFiringWebDriver} when the project runs Selenium 3. Loaded only
 * by name in that case: its interface no longer exists from Selenium 4.17 on.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 5.0.0
 */
@SuppressWarnings("deprecation")
public final class Selenium3ReportingListener implements WebDriverEventListener {

    @Override
    public void beforeNavigateTo(String url, WebDriver driver) {
        StepRecorder.begin("to", "selenium.get", url);
    }

    @Override
    public void afterNavigateTo(String url, WebDriver driver) {
        StepRecorder.end("to");
    }

    @Override
    public void beforeNavigateBack(WebDriver driver) {
        StepRecorder.begin("back", "selenium.navigate", "back");
    }

    @Override
    public void afterNavigateBack(WebDriver driver) {
        StepRecorder.end("back");
    }

    @Override
    public void beforeNavigateForward(WebDriver driver) {
        StepRecorder.begin("forward", "selenium.navigate", "forward");
    }

    @Override
    public void afterNavigateForward(WebDriver driver) {
        StepRecorder.end("forward");
    }

    @Override
    public void beforeNavigateRefresh(WebDriver driver) {
        StepRecorder.begin("refresh", "selenium.navigate", "refresh");
    }

    @Override
    public void afterNavigateRefresh(WebDriver driver) {
        StepRecorder.end("refresh");
    }

    @Override
    public void beforeClickOn(WebElement element, WebDriver driver) {
        StepRecorder.begin("click", "selenium.click", SeleniumNames.element(element));
    }

    @Override
    public void afterClickOn(WebElement element, WebDriver driver) {
        StepRecorder.end("click");
    }

    /** Selenium 3 reports both {@code sendKeys} and {@code clear} here; {@code clear} has no keys. */
    @Override
    public void beforeChangeValueOf(WebElement element, WebDriver driver, CharSequence[] keysToSend) {
        if (keysToSend == null) {
            StepRecorder.begin("change", "selenium.clear", SeleniumNames.element(element));
            return;
        }
        String target = StepRecorder.isRecording()
            ? SeleniumNames.element(element) + " ← "
                + StepRecorder.quietly(() -> SeleniumNames.typed(element, new Object[] {keysToSend}))
            : "";
        StepRecorder.begin("change", "selenium.type", target);
    }

    @Override
    public void afterChangeValueOf(WebElement element, WebDriver driver, CharSequence[] keysToSend) {
        StepRecorder.end("change");
    }

    @Override
    public void beforeAlertAccept(WebDriver driver) {
        StepRecorder.begin("accept", "selenium.alert", "accept");
    }

    @Override
    public void afterAlertAccept(WebDriver driver) {
        StepRecorder.end("accept");
    }

    @Override
    public void beforeAlertDismiss(WebDriver driver) {
        StepRecorder.begin("dismiss", "selenium.alert", "dismiss");
    }

    @Override
    public void afterAlertDismiss(WebDriver driver) {
        StepRecorder.end("dismiss");
    }

    @Override
    public void beforeScript(String script, WebDriver driver) {
        StepRecorder.begin("script", "selenium.script", SeleniumNames.shorten(script));
    }

    @Override
    public void afterScript(String script, WebDriver driver) {
        StepRecorder.end("script");
    }

    /** Selenium 3 does not say which call failed: the last step opened is the one. */
    @Override
    public void onException(Throwable throwable, WebDriver driver) {
        StepRecorder.fail(null, throwable);
    }

    @Override public void beforeFindBy(By by, WebElement element, WebDriver driver) {}
    @Override public void afterFindBy(By by, WebElement element, WebDriver driver) {}
    @Override public void beforeSwitchToWindow(String windowName, WebDriver driver) {}
    @Override public void afterSwitchToWindow(String windowName, WebDriver driver) {}
    @Override public <X> void beforeGetScreenshotAs(OutputType<X> target) {}
    @Override public <X> void afterGetScreenshotAs(OutputType<X> target, X screenshot) {}
    @Override public void beforeGetText(WebElement element, WebDriver driver) {}
    @Override public void afterGetText(WebElement element, WebDriver driver, String text) {}
}
