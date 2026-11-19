package org.oculix.report.selenium;

import java.lang.reflect.Method;

/**
 * Runtime dispatcher that wraps a user's {@code WebDriver} with our
 * {@link SeleniumReportingListener}. Picks the right Selenium API by
 * reflection:
 *
 * <ul>
 *   <li><b>Selenium 4+</b>: uses {@code EventFiringDecorator} with the
 *     modern {@code WebDriverListener} interface (non-deprecated).</li>
 *   <li><b>Selenium 3</b>: falls back to {@code EventFiringWebDriver} +
 *     {@code WebDriverEventListener}. Selenium 4.0 to 4.16 still ship both
 *     APIs; they take the Selenium 4 path above.</li>
 * </ul>
 *
 * <p>Entirely reflective so the Reporter jar does not impose a specific
 * Selenium version on callers. If neither API is on the classpath, a
 * clear exception is thrown.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class SeleniumWrap {

    private SeleniumWrap() {}

    public static Object wrap(Object driver) {
        if (driver == null) throw new IllegalArgumentException("driver must not be null");

        // --- Try Selenium 4 (modern API) ---
        try {
            Class<?> decoratorCls = Class.forName(
                "org.openqa.selenium.support.events.EventFiringDecorator");
            Class<?> listenerIface = Class.forName(
                "org.openqa.selenium.support.events.WebDriverListener");
            Class<?> webDriverCls = Class.forName("org.openqa.selenium.WebDriver");
            Object listener = new SeleniumReportingListener();

            // new EventFiringDecorator(new WebDriverListener[] { listener }):
            // the array must be a real WebDriverListener[], an Object[] is refused.
            Object listeners = java.lang.reflect.Array.newInstance(listenerIface, 1);
            java.lang.reflect.Array.set(listeners, 0, listener);
            Object decorator = decoratorCls
                .getDeclaredConstructor(listeners.getClass())
                .newInstance(listeners);

            // decorator.decorate(driver)
            Method decorate = decoratorCls.getMethod("decorate", webDriverCls);
            return decorate.invoke(decorator, driver);
        } catch (ClassNotFoundException ignored) {
            // Fall through to Selenium 3 path.
        } catch (Exception e) {
            throw new IllegalStateException(
                "Selenium 4 API present but wrap failed: " + e.getMessage(), e);
        }

        // --- Fallback: Selenium 3 or deprecated Selenium 4 ---
        try {
            Class<?> efwdCls = Class.forName(
                "org.openqa.selenium.support.events.EventFiringWebDriver");
            Class<?> webDriverCls = Class.forName("org.openqa.selenium.WebDriver");
            Class<?> eventListenerIface = Class.forName(
                "org.openqa.selenium.support.events.WebDriverEventListener");

            // Loaded by name: its interface is gone from Selenium 4.17 on, and
            // this branch is only reached when the Selenium 4 API is absent.
            Object listener = Class.forName("org.oculix.report.selenium.Selenium3ReportingListener")
                .getDeclaredConstructor().newInstance();

            // new EventFiringWebDriver(driver)
            Object efwd = efwdCls.getDeclaredConstructor(webDriverCls).newInstance(driver);
            // efwd.register(listener)
            Method register = efwdCls.getMethod("register", eventListenerIface);
            register.invoke(efwd, listener);
            return efwd;
        } catch (ClassNotFoundException noSelenium) {
            throw new IllegalStateException(
                "No Selenium on classpath — add selenium-support (3.x or 4.x) to use wrapDriver.",
                noSelenium);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Selenium 3 fallback wrap failed: " + e.getMessage(), e);
        }
    }
}
