package org.oculix.report.selenium;

import org.aspectj.lang.JoinPoint;
import org.openqa.selenium.WebElement;

/**
 * How a Selenium call reads in the report. Works on the objects Selenium 3
 * and 4 both have, without depending on either version's internals.
 *
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 5.0.0
 */
final class SeleniumNames {

    static final String MASK = "••••";
    private static final int MAX = 120;

    private SeleniumNames() {}

    /** Argument {@code index} as text, or an empty string. */
    static String arg(JoinPoint jp, int index) {
        Object[] args = jp.getArgs();
        if (args == null || index >= args.length || args[index] == null) return "";
        return String.valueOf(args[index]);
    }

    /**
     * The locator an element was found with: Selenium keeps it in the
     * element's {@code toString()}, after {@code ->}
     * ({@code [[ChromeDriver: chrome on windows (…)] -> id: login]}).
     */
    static String element(Object element) {
        if (element == null) return "element";
        String text;
        try {
            text = String.valueOf(element);
        } catch (RuntimeException e) {
            return "element";
        }
        int arrow = text.lastIndexOf("-> ");
        if (arrow < 0) return "element";
        String locator = text.substring(arrow + 3);
        while (locator.endsWith("]")) {
            locator = locator.substring(0, locator.length() - 1);
        }
        return locator.trim();
    }

    /** What {@code sendKeys} types, masked when the field is a password field. */
    static String typed(Object element, Object[] args) {
        if (isPassword(element)) return MASK;
        StringBuilder sb = new StringBuilder();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof CharSequence[]) {
                    for (CharSequence cs : (CharSequence[]) arg) sb.append(cs);
                } else if (arg != null) {
                    sb.append(arg);
                }
            }
        }
        return shorten(sb.toString());
    }

    static String shorten(String s) {
        if (s == null) return "";
        return s.length() <= MAX ? s : s.substring(0, MAX) + "…";
    }

    private static boolean isPassword(Object element) {
        if (!(element instanceof WebElement)) return false;
        try {
            return "password".equalsIgnoreCase(((WebElement) element).getAttribute("type"));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
