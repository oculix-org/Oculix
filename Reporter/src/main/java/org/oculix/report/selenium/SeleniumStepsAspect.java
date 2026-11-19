package org.oculix.report.selenium;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.oculix.report.steps.StepRecorder;

/**
 * Every Selenium action of a test becomes a step of the report, with no
 * line in the test: the aspect is woven into Selenium's own classes at load
 * time by the AspectJ agent the build adds to the test JVM
 * ({@code -javaagent:aspectjweaver.jar}), as declared in
 * {@code META-INF/aop.xml}. Selenium 3 and 4 share these type names, so the
 * same aspect serves both.
 *
 * <p>Steps: navigation ({@code get}, {@code navigate().to/back/forward/refresh}),
 * element actions ({@code click}, {@code sendKeys}, {@code clear},
 * {@code submit}), {@code Select} choices, alerts, scripts, {@code quit}.
 * A lookup ({@code findElement}) only appears when it fails: the action that
 * follows already names the element. Text typed into a password field is
 * masked.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 5.0.0
 */
@Aspect
public class SeleniumStepsAspect {

    @Pointcut("execution(public * org.openqa.selenium.WebDriver+.get(String))")
    public void open() {
        // pointcut body, empty
    }

    @Pointcut("execution(public * org.openqa.selenium.WebDriver.Navigation+.to(..))"
        + " || execution(public * org.openqa.selenium.WebDriver.Navigation+.back())"
        + " || execution(public * org.openqa.selenium.WebDriver.Navigation+.forward())"
        + " || execution(public * org.openqa.selenium.WebDriver.Navigation+.refresh())")
    public void navigate() {
        // pointcut body, empty
    }

    @Pointcut("execution(public * org.openqa.selenium.WebElement+.click())"
        + " || execution(public * org.openqa.selenium.WebElement+.clear())"
        + " || execution(public * org.openqa.selenium.WebElement+.submit())")
    public void elementAction() {
        // pointcut body, empty
    }

    @Pointcut("execution(public * org.openqa.selenium.WebElement+.sendKeys(..))")
    public void type() {
        // pointcut body, empty
    }

    @Pointcut("execution(public * org.openqa.selenium.SearchContext+.findElement(..))")
    public void find() {
        // pointcut body, empty
    }

    @Pointcut("execution(public * org.openqa.selenium.support.ui.Select.selectBy*(..))"
        + " || execution(public * org.openqa.selenium.support.ui.Select.deselect*(..))")
    public void select() {
        // pointcut body, empty
    }

    @Pointcut("execution(public * org.openqa.selenium.Alert+.accept())"
        + " || execution(public * org.openqa.selenium.Alert+.dismiss())"
        + " || execution(public * org.openqa.selenium.Alert+.sendKeys(..))")
    public void alert() {
        // pointcut body, empty
    }

    @Pointcut("execution(public * org.openqa.selenium.JavascriptExecutor+.executeScript(..))"
        + " || execution(public * org.openqa.selenium.JavascriptExecutor+.executeAsyncScript(..))")
    public void script() {
        // pointcut body, empty
    }

    @Pointcut("execution(public void org.openqa.selenium.WebDriver+.quit())")
    public void quit() {
        // pointcut body, empty
    }

    @Around("open()")
    public Object onOpen(ProceedingJoinPoint jp) throws Throwable {
        return StepRecorder.record("selenium.get", SeleniumNames.arg(jp, 0), jp::proceed);
    }

    @Around("navigate()")
    public Object onNavigate(ProceedingJoinPoint jp) throws Throwable {
        String method = jp.getSignature().getName();
        String target = "to".equals(method) ? SeleniumNames.arg(jp, 0) : method;
        return StepRecorder.record("selenium.navigate", target, jp::proceed);
    }

    @Around("elementAction()")
    public Object onElementAction(ProceedingJoinPoint jp) throws Throwable {
        return StepRecorder.record("selenium." + jp.getSignature().getName(),
            SeleniumNames.element(jp.getTarget()), jp::proceed);
    }

    @Around("type()")
    public Object onType(ProceedingJoinPoint jp) throws Throwable {
        if (!StepRecorder.isRecording()) return jp.proceed();
        Object element = jp.getTarget();
        String typed = StepRecorder.quietly(() -> SeleniumNames.typed(element, jp.getArgs()));
        return StepRecorder.record("selenium.type",
            SeleniumNames.element(element) + " ← " + typed, jp::proceed);
    }

    @Around("find()")
    public Object onFind(ProceedingJoinPoint jp) throws Throwable {
        return StepRecorder.recordOnFailure("selenium.find", SeleniumNames.arg(jp, 0), jp::proceed);
    }

    @Around("select()")
    public Object onSelect(ProceedingJoinPoint jp) throws Throwable {
        return StepRecorder.record("selenium.select",
            jp.getSignature().getName() + " " + SeleniumNames.arg(jp, 0), jp::proceed);
    }

    @Around("alert()")
    public Object onAlert(ProceedingJoinPoint jp) throws Throwable {
        return StepRecorder.record("selenium.alert", jp.getSignature().getName(), jp::proceed);
    }

    @Around("script()")
    public Object onScript(ProceedingJoinPoint jp) throws Throwable {
        return StepRecorder.record("selenium.script", SeleniumNames.shorten(SeleniumNames.arg(jp, 0)), jp::proceed);
    }

    @Around("quit()")
    public Object onQuit(ProceedingJoinPoint jp) throws Throwable {
        return StepRecorder.record("selenium.quit", "", jp::proceed);
    }
}
