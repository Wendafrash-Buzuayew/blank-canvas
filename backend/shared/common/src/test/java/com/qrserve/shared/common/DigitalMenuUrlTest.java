package com.qrserve.shared.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DigitalMenuUrlTest {

    private final DigitalMenuUrl url = new DigitalMenuUrl("menu.safaricom.et", "https");

    @Test
    void buildsTheCanonicalBranchUrl() {
        assertEquals("https://menu.safaricom.et/m/sunrise-coffee/main", url.branchUrl("sunrise-coffee", "main"));
    }

    @Test
    void buildsTheMerchantShortLink() {
        assertEquals("https://menu.safaricom.et/m/sunrise-coffee", url.merchantUrl("sunrise-coffee"));
    }

    @Test
    void encodesSlugsThatNeedIt() {
        assertEquals("https://menu.safaricom.et/m/joe%27s-diner/main",
                url.branchUrl("joe's-diner", "main"));
    }
}
