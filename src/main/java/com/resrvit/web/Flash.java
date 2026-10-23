package com.resrvit.web;

import com.resrvit.service.BookingException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.function.Supplier;

/** Runs an action and turns rule violations into a flash message on the redirect target. */
public final class Flash {

    private Flash() {}

    public static String run(RedirectAttributes ra, String redirect, Runnable action, String success) {
        try {
            action.run();
            if (success != null) ra.addFlashAttribute("success", success);
        } catch (BookingException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + redirect;
    }

    public static String call(RedirectAttributes ra, String redirect, Supplier<String> action) {
        try {
            String msg = action.get();
            if (msg != null) ra.addFlashAttribute("success", msg);
        } catch (BookingException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + redirect;
    }

    /** Where to go back to: the Referer if it is one of our pages, else the fallback. */
    public static String back(String referer, String fallback) {
        if (referer == null || referer.isBlank()) return fallback;
        try {
            java.net.URI u = java.net.URI.create(referer);
            String path = u.getRawPath() + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
            return path.startsWith("/") && !path.startsWith("//") ? path : fallback;
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
