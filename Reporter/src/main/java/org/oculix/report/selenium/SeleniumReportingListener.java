package org.oculix.report.selenium;

import org.oculix.report.steps.StepRecorder;
import org.openqa.selenium.Alert;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.events.WebDriverListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

/**
 * Selenium 4 listener, plugged in by {@link SeleniumWrap} through
 * {@code EventFiringDecorator} for projects that call
 * {@code OculixReporter.wrapDriver(driver)}. Projects running the AspectJ
 * agent need no wrapping at all ({@link SeleniumStepsAspect}); when both are
 * present, the listener's step wraps the call and the aspect stays silent.
 *
 * <p>Implements only the Selenium 4 interface: Selenium removed the Selenium 3
 * one in 4.17, and a class implementing it could no longer be loaded there.
 * Selenium 3 goes through {@code Selenium3ReportingListener}.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class SeleniumReportingListener implements WebDriverListener {

    @Override
    public void beforeGet(WebDriver driver, String url) {
        StepRecorder.begin("get", "selenium.get", url);
    }

    @Override
    public void afterGet(WebDriver driver, String url) {
        StepRecorder.end("get");
    }

    @Override
    public void beforeTo(WebDriver.Navigation navigation, String url) {
        StepRecorder.begin("to", "selenium.navigate", url);
    }

    @Override
    public void afterTo(WebDriver.Navigation navigation, String url) {
        StepRecorder.end("to");
    }

    @Override
    public void beforeTo(WebDriver.Navigation navigation, URL url) {
        StepRecorder.begin("to", "selenium.navigate", String.valueOf(url));
    }

    @Override
    public void afterTo(WebDriver.Navigation navigation, URL url) {
        StepRecorder.end("to");
    }

    @Override
    public void beforeBack(WebDriver.Navigation navigation) {
        StepRecorder.begin("back", "selenium.navigate", "back");
    }

    @Override
    public void afterBack(WebDriver.Navigation navigation) {
        StepRecorder.end("back");
    }

    @Override
    public void beforeForward(WebDriver.Navigation navigation) {
        StepRecorder.begin("forward", "selenium.navigate", "forward");
    }

    @Override
    public void afterForward(WebDriver.Navigation navigation) {
        StepRecorder.end("forward");
    }

    @Override
    public void beforeRefresh(WebDriver.Navigation navigation) {
        StepRecorder.begin("refresh", "selenium.navigate", "refresh");
    }

    @Override
    public void afterRefresh(WebDriver.Navigation navigation) {
        StepRecorder.end("refresh");
    }

    @Override
    public void beforeClick(WebElement element) {
        StepRecorder.begin("click", "selenium.click", SeleniumNames.element(element));
    }

    @Override
    public void afterClick(WebElement element) {
        StepRecorder.end("click");
    }

    @Override
    public void beforeSubmit(WebElement element) {
        StepRecorder.begin("submit", "selenium.submit", SeleniumNames.element(element));
    }

    @Override
    public void afterSubmit(WebElement element) {
        StepRecorder.end("submit");
    }

    @Override
    public void beforeClear(WebElement element) {
        StepRecorder.begin("clear", "selenium.clear", SeleniumNames.element(element));
    }

    @Override
    public void afterClear(WebElement element) {
        StepRecorder.end("clear");
    }

    @Override
    public void beforeSendKeys(WebElement element, CharSequence... keysToSend) {
        String target = StepRecorder.isRecording()
            ? SeleniumNames.element(element) + " ← "
                + StepRecorder.quietly(() -> SeleniumNames.typed(element, new Object[] {keysToSend}))
            : "";
        StepRecorder.begin("sendKeys", "selenium.type", target);
    }

    @Override
    public void afterSendKeys(WebElement element, CharSequence... keysToSend) {
        StepRecorder.end("sendKeys");
    }

    @Override
    public void beforeAccept(Alert alert) {
        StepRecorder.begin("accept", "selenium.alert", "accept");
    }

    @Override
    public void afterAccept(Alert alert) {
        StepRecorder.end("accept");
    }

    @Override
    public void beforeDismiss(Alert alert) {
        StepRecorder.begin("dismiss", "selenium.alert", "dismiss");
    }

    @Override
    public void afterDismiss(Alert alert) {
        StepRecorder.end("dismiss");
    }

    @Override
    public void beforeExecuteScript(WebDriver driver, String script, Object[] args) {
        StepRecorder.begin("executeScript", "selenium.script", SeleniumNames.shorten(script));
    }

    @Override
    public void afterExecuteScript(WebDriver driver, String script, Object[] args, Object result) {
        StepRecorder.end("executeScript");
    }

    @Override
    public void beforeQuit(WebDriver driver) {
        StepRecorder.begin("quit", "selenium.quit", "");
    }

    @Override
    public void afterQuit(WebDriver driver) {
        StepRecorder.end("quit");
    }

    /** Closes, as failed, the step of the call that threw; other errors pass through. */
    @Override
    public void onError(Object target, Method method, Object[] args, InvocationTargetException e) {
        StepRecorder.fail(method.getName(), e.getCause() == null ? e : e.getCause());
    }
}
