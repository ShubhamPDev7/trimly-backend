package com.trimly.backend.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public final class Sanitizer {

    private Sanitizer() {}

    public static String clean(String input) {
        if (input == null) return null;
        return Jsoup.clean(input.trim(), Safelist.none());
    }
}