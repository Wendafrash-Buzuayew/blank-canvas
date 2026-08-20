# Table QR Provisioning and Merchant Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provision a stored, versioned EMVCo payload for every table at creation time, expose a durable terminal-label-to-table mapping that the payment matcher can resolve without a synchronous cross-service call, and give merchants a settings home for fulfilment types and settlement mode.

**Architecture:** The EMVCo payload builder is one pure static function in `shared:common`, because two renderers for a money instrument is the drift class this codebase has already paid for twice. `merchant-service` owns `MerchantSettings` and `TableQr` (it owns merchants and tables) and provisions a `TableQr` row inside the existing `createTable` transaction. `order-service` learns the terminal mapping from a Kafka event and keeps an append-only projection of it — not an HTTP call, because webhook processing is the money path and must not fail when `merchant-service` is down, and not a Redis-only cache, because a flushed Redis would silently turn real payments into `UNKNOWN_TERMINAL` holds. The projected rows are immutable identity data, never derived money values. `qr-service` renders images from the stored payload rather than recomputing it, and the frontend displays a server-rendered image instead of drawing the code itself.

**Tech Stack:** Java 17 (toolchain), Spring Boot 4.1.0, Spring Cloud 2025.1.2, Spring Kafka, PostgreSQL + JPA, Redis, Gradle 8.14.2 multi-module; React 19 + TypeScript + TanStack Query + Vite.

**Spec:** `docs/superpowers/specs/2026-08-20-superapp-miniapp-payments-design.md` (sections 3, 5.1–5.6; this plan is the prerequisite for the payment-core plan that implements sections 4, 6, 8, 10)

## Global Constraints

- **Flyway is disabled** (`spring.flyway.enabled: false`) and `spring.jpa.hibernate.ddl-auto` is `update`. `ddl-auto` **adds** columns and tables but never drops a constraint and never creates a partial index. Anything it cannot do goes in a hand-run file under `<service>/src/main/resources/db/manual/`, following the header style of `merchant-service/.../db/manual/001-branch-slug-per-merchant.sql`: what to run, which database, when relative to deploy, why it is manual, and whether it is safe on a populated database.
- **Gradle cannot run in the authoring environment.** The human runs `./gradlew`. Every backend verification step states the command for them to run and the expected result. Frontend steps run here: `npm run lint` (`tsc --noEmit`) and `npm run test:unit`.
- **Frontend tests are hand-rolled `tsx` scripts**, not a test runner. New pure logic gets a `*.test.ts` beside it, wired into the `test:unit` script, following `src/lib/tenant.test.ts` and `src/lib/orderSession.test.ts`.
- **Currency is ETB**, ISO 4217 numeric `230`. Country `ET`. Amounts are `BigDecimal` in Java and decimal strings on the wire — never a floating-point type.
- **EMVCo tag semantics are fixed by the spec's section 5.2 table.** Tag `62-07` carries the terminal label, `62-05` the payment reference, `62-03` the store label, `54` the amount (dynamic only), `01` is `11` for static and `12` for dynamic.
- **`terminalLabel` is unique per version, not per table.** A reprint issues a new label and supersedes the old row; the old row is never deleted, because a superseded sticker can stay on a table for weeks.
- Java tests use JUnit 5 with static imports from `org.junit.jupiter.api.Assertions`, matching `OrderServiceMenuLookupTest` and `TenantHostTest`. No AssertJ.
- Entities use Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, matching every existing entity.

---

## File Structure

**`shared:common`** — pure, dependency-free contracts used by three services.

| File | Responsibility |
|---|---|
| `shared/common/src/main/java/com/qrserve/shared/common/emvco/Emvco.java` | TLV encoding, CRC-16/CCITT-FALSE, tag parsing. Nothing domain-specific. |
| `shared/common/src/main/java/com/qrserve/shared/common/emvco/EmvcoMerchant.java` | The merchant-side inputs to a payload: GUID, destination ref, MCC, currency, country, name, city. |
| `shared/common/src/main/java/com/qrserve/shared/common/emvco/EmvcoPayload.java` | `staticPayload` / `dynamicPayload` / `crcValid`. The single builder. |
| `shared/common/src/main/java/com/qrserve/shared/common/FulfilmentType.java` | `DINE_IN`, `TAKEOUT`, `DELIVERY`. |
| `shared/common/src/main/java/com/qrserve/shared/common/SettlementMode.java` | `PREPAID`, `TAB`. |
| `shared/common/src/main/java/com/qrserve/shared/common/TerminalLabel.java` | Deterministic label for a (tableId, version). |

**`shared:events`** — the provisioning event.

| File | Responsibility |
|---|---|
| `shared/events/src/main/java/com/qrserve/shared/events/TableQrProvisionedEvent.java` | Terminal label to table identity. Immutable fact. |

**`merchant-service`** — owns merchants, branches, tables; therefore owns settings and table QRs.

| File | Responsibility |
|---|---|
| `entity/MerchantSettingsEntity.java` | Fulfilment toggles and settlement mode per merchant/branch. |
| `entity/TableQrEntity.java` | The exact payload bytes on a sticker, versioned. |
| `repository/MerchantSettingsRepository.java` | Lookup by merchant and branch. |
| `repository/TableQrRepository.java` | Lookup by table, by terminal label, active row. |
| `service/MerchantSettingsResolver.java` | Branch-overrides-merchant plus defaults. Pure. |
| `service/TableQrProvisioningService.java` | Provision and reprint; publishes the event. |
| `kafka/TableQrEventPublisher.java` | Publishes `table-qr-provisioned`. |
| `service/TableService.java` (modify) | Provision a `TableQr` inside `createTable`. |
| `dto/CreateTableResponse.java` (modify) | Return the payload and terminal label. |
| `src/main/resources/db/manual/002-merchant-settings-and-table-qr.sql` | The two indexes `ddl-auto` cannot create. |

**`order-service`** — consumes the mapping, owns nothing about tables.

| File | Responsibility |
|---|---|
| `payment/terminal/TerminalMapEntity.java` | Append-only projection row. |
| `payment/terminal/TerminalMapRepository.java` | Lookup by terminal label. |
| `payment/terminal/TerminalMapListener.java` | Kafka consumer, idempotent insert. |
| `payment/terminal/TerminalMapService.java` | `resolve(label)` for the matcher. |

**`qr-service`** — renders images from stored payloads.

| File | Responsibility |
|---|---|
| `service/QrGeneratorService.java` (modify) | Render the stored payload; stop recomputing the target. |
| `dto/QrMetadataResponse.java` (modify) | Carry `payloadRaw`, `terminalLabel`, `profile`. |

**Frontend**

| File | Responsibility |
|---|---|
| `src/lib/qrDisplay.ts` | Pure: can this metadata be rendered, and what is the image source. |
| `src/lib/qrDisplay.test.ts` | Tests for the above. |
| `src/components/merchant/QRDesigner.tsx` (modify) | Use the server image; delete the fabricated fallback. |
| `src/pages/TableManagement.tsx` (modify) | Show the QR on table creation. |
| `package.json` (modify) | Wire the new test file into `test:unit`. |

---

## Task 1: EMVCo payload builder

**Files:**
- Create: `backend/shared/common/src/main/java/com/qrserve/shared/common/emvco/Emvco.java`
- Create: `backend/shared/common/src/main/java/com/qrserve/shared/common/emvco/EmvcoMerchant.java`
- Create: `backend/shared/common/src/main/java/com/qrserve/shared/common/emvco/EmvcoPayload.java`
- Test: `backend/shared/common/src/test/java/com/qrserve/shared/common/emvco/EmvcoPayloadTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `Emvco.tlv(String tag, String value) -> String`
  - `Emvco.crc16(String data) -> String` (4 upper-case hex chars)
  - `Emvco.parseTags(String payload) -> Map<String, String>`
  - `EmvcoMerchant(String guid, String destinationRef, String mcc, String currencyNumeric, String countryCode, String name, String city)` — a record
  - `EmvcoPayload.staticPayload(EmvcoMerchant m, String terminalLabel, String storeLabel) -> String`
  - `EmvcoPayload.dynamicPayload(EmvcoMerchant m, String terminalLabel, String storeLabel, BigDecimal amount, String paymentRef, String billNumber) -> String`
  - `EmvcoPayload.crcValid(String payload) -> boolean`

- [ ] **Step 1: Write the failing test**

The two expected strings below are real: their CRCs were computed with CRC-16/CCITT-FALSE (polynomial `0x1021`, init `0xFFFF`, no reflection, no final XOR) over every character up to and including `6304`. Do not adjust them to match an implementation — if they disagree, the implementation is wrong.

```java
package com.qrserve.shared.common.emvco;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden vectors for the one EMVCo builder.
 *
 * <p>A payload with a wrong CRC is rejected silently by every bank app, which is
 * unfixable once a sticker is laminated and on a table. These vectors are the only
 * thing standing between that and a print run.
 */
class EmvcoPayloadTest {

    private static final EmvcoMerchant SUNRISE = new EmvcoMerchant(
            "ET.QRSERVE", "1234567890", "5812", "230", "ET", "SUNRISE", "ADDIS ABABA");

    @Test
    @DisplayName("static payload matches the golden vector, CRC included")
    void staticGoldenVector() {
        assertEquals(
                "00020101021126280010ET.QRSERVE011012345678905204581253032305802ET"
                        + "5907SUNRISE6011ADDIS ABABA62160303BR10705T42-163049A4D",
                EmvcoPayload.staticPayload(SUNRISE, "T42-1", "BR1"));
    }

    @Test
    @DisplayName("dynamic payload matches the golden vector, CRC included")
    void dynamicGoldenVector() {
        assertEquals(
                "00020101021226280010ET.QRSERVE011012345678905204581253032305406420.00"
                        + "5802ET5907SUNRISE6011ADDIS ABABA62350104PB-70303BR10507PR-90010705T42-1"
                        + "630442B8",
                EmvcoPayload.dynamicPayload(
                        SUNRISE, "T42-1", "BR1", new BigDecimal("420.00"), "PR-9001", "PB-7"));
    }

    @Test
    @DisplayName("static carries no amount and dynamic does — tag 01 says which")
    void pointOfInitiationDistinguishesThem() {
        Map<String, String> stat = Emvco.parseTags(EmvcoPayload.staticPayload(SUNRISE, "T42-1", "BR1"));
        assertEquals("11", stat.get("01"));
        assertFalse(stat.containsKey("54"), "a static payload must not fix an amount");

        Map<String, String> dyn = Emvco.parseTags(EmvcoPayload.dynamicPayload(
                SUNRISE, "T42-1", "BR1", new BigDecimal("420.00"), "PR-9001", "PB-7"));
        assertEquals("12", dyn.get("01"));
        assertEquals("420.00", dyn.get("54"));
    }

    @Test
    @DisplayName("a minted payload validates its own CRC")
    void mintedPayloadIsSelfConsistent() {
        assertTrue(EmvcoPayload.crcValid(EmvcoPayload.staticPayload(SUNRISE, "T42-1", "BR1")));
        assertTrue(EmvcoPayload.crcValid(EmvcoPayload.dynamicPayload(
                SUNRISE, "T42-1", "BR1", new BigDecimal("99.50"), "PR-1", "PB-1")));
    }

    @Test
    @DisplayName("a corrupted CRC is rejected")
    void corruptedCrcRejected() {
        String good = EmvcoPayload.staticPayload(SUNRISE, "T42-1", "BR1");
        assertFalse(EmvcoPayload.crcValid(good.substring(0, good.length() - 4) + "0000"));
    }

    @Test
    @DisplayName("amounts keep two decimals, because a bank app renders exactly what it reads")
    void amountScaleIsExplicit() {
        Map<String, String> tags = Emvco.parseTags(EmvcoPayload.dynamicPayload(
                SUNRISE, "T42-1", "BR1", new BigDecimal("420"), "PR-1", "PB-1"));
        assertEquals("420.00", tags.get("54"));
    }

    @Test
    @DisplayName("TLV length is the character count, zero-padded to two digits")
    void tlvEncoding() {
        assertEquals("0002ET", Emvco.tlv("00", "ET"));
        assertEquals("5907SUNRISE", Emvco.tlv("59", "SUNRISE"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Ask the human to run:

```bash
cd backend && ./gradlew :shared:common:test --tests '*EmvcoPayloadTest'
```

Expected: compilation failure — `package com.qrserve.shared.common.emvco does not exist`.

- [ ] **Step 3: Write the minimal implementation**

`Emvco.java`:

```java
package com.qrserve.shared.common.emvco;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EMVCo merchant-presented-mode primitives: TLV encoding, the CRC every payload
 * ends with, and a parser used to verify what we just built.
 *
 * <p>Deliberately domain-free. The one place that knows which tag means what is
 * {@link EmvcoPayload}.
 */
public final class Emvco {

    /** Tag 63, always last, always four hex characters. */
    public static final String CRC_TAG = "63";

    private Emvco() {
    }

    /** Tag, then the value's character count as two digits, then the value. */
    public static String tlv(String tag, String value) {
        if (value == null) {
            throw new IllegalArgumentException("EMVCo value for tag " + tag + " must not be null");
        }
        if (value.length() > 99) {
            throw new IllegalArgumentException(
                    "EMVCo value for tag " + tag + " exceeds 99 characters: " + value.length());
        }
        return tag + String.format("%02d", value.length()) + value;
    }

    /**
     * CRC-16/CCITT-FALSE: polynomial 0x1021, initial value 0xFFFF, no input or
     * output reflection, no final XOR. Computed over every character up to and
     * including the "6304" that introduces the checksum itself.
     */
    public static String crc16(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(java.nio.charset.StandardCharsets.US_ASCII)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }

    /** Top-level tags only; nested templates are returned as their raw inner string. */
    public static Map<String, String> parseTags(String payload) {
        Map<String, String> tags = new LinkedHashMap<>();
        int i = 0;
        while (i + 4 <= payload.length()) {
            String tag = payload.substring(i, i + 2);
            int length = Integer.parseInt(payload.substring(i + 2, i + 4));
            int from = i + 4;
            int to = from + length;
            if (to > payload.length()) {
                throw new IllegalArgumentException("Truncated EMVCo payload at tag " + tag);
            }
            tags.put(tag, payload.substring(from, to));
            i = to;
        }
        return tags;
    }
}
```

`EmvcoMerchant.java`:

```java
package com.qrserve.shared.common.emvco;

/**
 * The merchant-side inputs to a payload.
 *
 * @param guid            scheme identifier inside the merchant-account template
 * @param destinationRef  the account money lands in — the merchant's own, never ours
 * @param mcc             merchant category code, tag 52
 * @param currencyNumeric ISO 4217 numeric, tag 53 ("230" for ETB)
 * @param countryCode     ISO 3166-1 alpha-2, tag 58
 * @param name            tag 59
 * @param city            tag 60
 */
public record EmvcoMerchant(
        String guid,
        String destinationRef,
        String mcc,
        String currencyNumeric,
        String countryCode,
        String name,
        String city) {
}
```

`EmvcoPayload.java`:

```java
package com.qrserve.shared.common.emvco;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single EMVCo payload builder for the platform.
 *
 * <p>One builder, one place, because the alternative has already happened here: the
 * public menu URL was constructed independently in two services, each carrying a
 * comment claiming it matched the other, and they drifted. A drifted <em>payment</em>
 * payload is a QR that every bank app rejects without saying why.
 *
 * <p>Tag assignment follows section 5.2 of the payments design.
 */
public final class EmvcoPayload {

    private static final String FORMAT_INDICATOR = "01";
    private static final String STATIC_INITIATION = "11";
    private static final String DYNAMIC_INITIATION = "12";

    private EmvcoPayload() {
    }

    /** Amount-less, printable, stable across reprints of the same version. */
    public static String staticPayload(EmvcoMerchant merchant, String terminalLabel, String storeLabel) {
        String additional = Emvco.tlv("03", storeLabel) + Emvco.tlv("07", terminalLabel);
        return withCrc(header(merchant, STATIC_INITIATION) + tail(merchant) + Emvco.tlv("62", additional));
    }

    /** Amount-bearing, single-use, and the only payload carrying a payment reference. */
    public static String dynamicPayload(
            EmvcoMerchant merchant,
            String terminalLabel,
            String storeLabel,
            BigDecimal amount,
            String paymentRef,
            String billNumber) {

        String additional = Emvco.tlv("01", billNumber)
                + Emvco.tlv("03", storeLabel)
                + Emvco.tlv("05", paymentRef)
                + Emvco.tlv("07", terminalLabel);

        return withCrc(header(merchant, DYNAMIC_INITIATION)
                + Emvco.tlv("54", amountOf(amount))
                + tail(merchant)
                + Emvco.tlv("62", additional));
    }

    /** True when the payload's trailing four characters match its own contents. */
    public static boolean crcValid(String payload) {
        if (payload == null || payload.length() < 8) {
            return false;
        }
        int crcStart = payload.length() - 4;
        String body = payload.substring(0, crcStart);
        return body.endsWith(Emvco.CRC_TAG + "04")
                && Emvco.crc16(body).equalsIgnoreCase(payload.substring(crcStart));
    }

    private static String header(EmvcoMerchant m, String initiationMethod) {
        String mai = Emvco.tlv("00", m.guid()) + Emvco.tlv("01", m.destinationRef());
        return Emvco.tlv("00", FORMAT_INDICATOR)
                + Emvco.tlv("01", initiationMethod)
                + Emvco.tlv("26", mai)
                + Emvco.tlv("52", m.mcc())
                + Emvco.tlv("53", m.currencyNumeric());
    }

    private static String tail(EmvcoMerchant m) {
        return Emvco.tlv("58", m.countryCode())
                + Emvco.tlv("59", m.name())
                + Emvco.tlv("60", m.city());
    }

    /**
     * Always two decimals. A bank app renders exactly the characters it reads, so
     * "420" and "420.00" are the same money but not the same receipt.
     */
    private static String amountOf(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String withCrc(String body) {
        String withCrcTag = body + Emvco.CRC_TAG + "04";
        return withCrcTag + Emvco.crc16(withCrcTag);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Ask the human to run:

```bash
cd backend && ./gradlew :shared:common:test --tests '*EmvcoPayloadTest'
```

Expected: `BUILD SUCCESSFUL`, 7 tests passing. If a golden-vector assertion fails, the implementation is wrong, not the vector.

- [ ] **Step 5: Commit**

```bash
git add backend/shared/common/src/main/java/com/qrserve/shared/common/emvco backend/shared/common/src/test/java/com/qrserve/shared/common/emvco
git commit -m "feat(qr): single EMVCo payload builder with golden-vector CRC tests"
```

---

## Task 2: Fulfilment enums and the terminal label

**Files:**
- Create: `backend/shared/common/src/main/java/com/qrserve/shared/common/FulfilmentType.java`
- Create: `backend/shared/common/src/main/java/com/qrserve/shared/common/SettlementMode.java`
- Create: `backend/shared/common/src/main/java/com/qrserve/shared/common/TerminalLabel.java`
- Test: `backend/shared/common/src/test/java/com/qrserve/shared/common/TerminalLabelTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `FulfilmentType` — `DINE_IN`, `TAKEOUT`, `DELIVERY`
  - `SettlementMode` — `PREPAID`, `TAB`
  - `TerminalLabel.of(long tableId, int version) -> String`
  - `TerminalLabel.MAX_LENGTH` — `int`

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.shared.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The terminal label is what a webhook quotes back at us, so it has to identify one
 * physical sticker for as long as that sticker might still be on a table.
 */
class TerminalLabelTest {

    @Test
    @DisplayName("label is derived from the table and the version")
    void derivedFromTableAndVersion() {
        assertEquals("T42-1", TerminalLabel.of(42L, 1));
        assertEquals("T7-3", TerminalLabel.of(7L, 3));
    }

    @Test
    @DisplayName("a reprint gets a different label, so a payment names the sticker that made it")
    void reprintChangesTheLabel() {
        assertNotEquals(TerminalLabel.of(42L, 1), TerminalLabel.of(42L, 2));
    }

    @Test
    @DisplayName("different tables never collide")
    void differentTablesDiffer() {
        assertNotEquals(TerminalLabel.of(4L, 21), TerminalLabel.of(42L, 1));
    }

    @Test
    @DisplayName("fits EMVCo tag 62-07")
    void fitsTheTag() {
        assertTrue(TerminalLabel.of(Long.MAX_VALUE, 9999).length() <= TerminalLabel.MAX_LENGTH,
                "tag 62-07 is capped at " + TerminalLabel.MAX_LENGTH + " characters");
    }

    @Test
    @DisplayName("version zero is rejected: versions are one-based, and v0 would alias no sticker")
    void versionMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> TerminalLabel.of(42L, 0));
        assertThrows(IllegalArgumentException.class, () -> TerminalLabel.of(42L, -1));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Ask the human to run:

```bash
cd backend && ./gradlew :shared:common:test --tests '*TerminalLabelTest'
```

Expected: compilation failure — `cannot find symbol: class TerminalLabel`.

- [ ] **Step 3: Write the minimal implementation**

`FulfilmentType.java`:

```java
package com.qrserve.shared.common;

/**
 * How an order reaches the guest. The frontend mirrors these names; import from here
 * rather than writing the strings inline, which is how {@code OrderStatus} drifted in
 * four places.
 */
public enum FulfilmentType {

    /** Eaten at a table. The only type with a table id and a signed-QR presence proof. */
    DINE_IN,

    /** Collected at the counter. No table, so prepayment is what protects the kitchen. */
    TAKEOUT,

    /** Modelled now, enabled later — see the delivery sub-project. */
    DELIVERY
}
```

`SettlementMode.java`:

```java
package com.qrserve.shared.common;

/**
 * When a payable closes. Configured per merchant AND per fulfilment type, because one
 * restaurant commonly runs takeout prepaid and dine-in on a tab at the same time.
 */
public enum SettlementMode {

    /** Closes at order placement; the kitchen sees the order only once it is settled. */
    PREPAID,

    /** Stays open across a sitting and closes when the bill is requested. */
    TAB
}
```

`TerminalLabel.java`:

```java
package com.qrserve.shared.common;

/**
 * Identifies one printed sticker, not one table.
 *
 * <p>A reprint issues a new label and supersedes the old row, so a payment quoting
 * tag 62-07 tells us which physical code produced it — which is how a stale sticker
 * still in circulation gets discovered. Resolving a label to a table therefore has to
 * search superseded rows as well as the active one.
 */
public final class TerminalLabel {

    /** EMVCo caps tag 62-07 at 25 characters. */
    public static final int MAX_LENGTH = 25;

    private TerminalLabel() {
    }

    public static String of(long tableId, int version) {
        if (version < 1) {
            throw new IllegalArgumentException("Terminal label version is one-based, got " + version);
        }
        String label = "T" + tableId + "-" + version;
        if (label.length() > MAX_LENGTH) {
            throw new IllegalStateException("Terminal label exceeds tag 62-07: " + label);
        }
        return label;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Ask the human to run:

```bash
cd backend && ./gradlew :shared:common:test --tests '*TerminalLabelTest'
```

Expected: `BUILD SUCCESSFUL`, 5 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/shared/common/src/main/java/com/qrserve/shared/common/FulfilmentType.java backend/shared/common/src/main/java/com/qrserve/shared/common/SettlementMode.java backend/shared/common/src/main/java/com/qrserve/shared/common/TerminalLabel.java backend/shared/common/src/test/java/com/qrserve/shared/common/TerminalLabelTest.java
git commit -m "feat(payments): fulfilment enums and per-version terminal label"
```

---

## Task 3: Merchant settings resolution

Settings resolution is written before persistence, because the interesting behaviour — a branch row overriding a merchant row, and sane defaults when neither exists — is pure and can be tested without a database.

**Files:**
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/service/MerchantSettingsResolver.java`
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/dto/ResolvedMerchantSettings.java`
- Test: `backend/merchant-service/src/test/java/com/qrserve/merchant/service/MerchantSettingsResolverTest.java`

**Interfaces:**
- Consumes: `FulfilmentType`, `SettlementMode` (Task 2).
- Produces:
  - `ResolvedMerchantSettings(Set<FulfilmentType> fulfilmentEnabled, Map<FulfilmentType, SettlementMode> settlementMode)` — a record, with `mode(FulfilmentType) -> SettlementMode` and `isEnabled(FulfilmentType) -> boolean`
  - `MerchantSettingsResolver.resolve(SettingsRow merchantRow, SettingsRow branchRow) -> ResolvedMerchantSettings` where `SettingsRow` is the nested record `MerchantSettingsResolver.SettingsRow(Set<FulfilmentType>, Map<FulfilmentType, SettlementMode>)`, either argument nullable
  - `MerchantSettingsResolver.DEFAULTS` — `ResolvedMerchantSettings`

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.ResolvedMerchantSettings;
import com.qrserve.shared.common.FulfilmentType;
import com.qrserve.shared.common.SettlementMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every order and every payment reads these settings, and most merchants will never
 * open the settings screen. The defaults are therefore the real production behaviour.
 */
class MerchantSettingsResolverTest {

    private static Map<FulfilmentType, SettlementMode> modes(Object... pairs) {
        Map<FulfilmentType, SettlementMode> map = new EnumMap<>(FulfilmentType.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((FulfilmentType) pairs[i], (SettlementMode) pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("no rows at all: dine-in only, on a tab")
    void defaultsWhenNothingConfigured() {
        ResolvedMerchantSettings resolved = MerchantSettingsResolver.resolve(null, null);

        assertTrue(resolved.isEnabled(FulfilmentType.DINE_IN));
        assertFalse(resolved.isEnabled(FulfilmentType.TAKEOUT), "takeout is opt-in");
        assertFalse(resolved.isEnabled(FulfilmentType.DELIVERY), "delivery is not buildable yet");
        assertEquals(SettlementMode.TAB, resolved.mode(FulfilmentType.DINE_IN));
    }

    @Test
    @DisplayName("takeout defaults to prepaid, because prepayment is what protects the kitchen")
    void takeoutDefaultsToPrepaid() {
        assertEquals(SettlementMode.PREPAID,
                MerchantSettingsResolver.resolve(null, null).mode(FulfilmentType.TAKEOUT));
        assertEquals(SettlementMode.PREPAID,
                MerchantSettingsResolver.resolve(null, null).mode(FulfilmentType.DELIVERY));
    }

    @Test
    @DisplayName("a merchant row applies to every branch")
    void merchantRowApplies() {
        MerchantSettingsResolver.SettingsRow merchant = new MerchantSettingsResolver.SettingsRow(
                EnumSet.of(FulfilmentType.DINE_IN, FulfilmentType.TAKEOUT),
                modes(FulfilmentType.DINE_IN, SettlementMode.PREPAID));

        ResolvedMerchantSettings resolved = MerchantSettingsResolver.resolve(merchant, null);

        assertTrue(resolved.isEnabled(FulfilmentType.TAKEOUT));
        assertEquals(SettlementMode.PREPAID, resolved.mode(FulfilmentType.DINE_IN));
    }

    @Test
    @DisplayName("a branch row wins over the merchant row, field by field")
    void branchOverridesMerchant() {
        MerchantSettingsResolver.SettingsRow merchant = new MerchantSettingsResolver.SettingsRow(
                EnumSet.of(FulfilmentType.DINE_IN, FulfilmentType.TAKEOUT),
                modes(FulfilmentType.DINE_IN, SettlementMode.TAB));
        MerchantSettingsResolver.SettingsRow branch = new MerchantSettingsResolver.SettingsRow(
                EnumSet.of(FulfilmentType.DINE_IN),
                modes(FulfilmentType.DINE_IN, SettlementMode.PREPAID));

        ResolvedMerchantSettings resolved = MerchantSettingsResolver.resolve(merchant, branch);

        assertFalse(resolved.isEnabled(FulfilmentType.TAKEOUT), "this branch does not do takeout");
        assertEquals(SettlementMode.PREPAID, resolved.mode(FulfilmentType.DINE_IN));
    }

    @Test
    @DisplayName("a branch row with an empty mode map still inherits the merchant's modes")
    void emptyBranchMapDoesNotErasePolicy() {
        // A branch that only narrows the fulfilment list must not silently reset every
        // settlement mode to the default and start taking money at a different time.
        MerchantSettingsResolver.SettingsRow merchant = new MerchantSettingsResolver.SettingsRow(
                EnumSet.of(FulfilmentType.DINE_IN, FulfilmentType.TAKEOUT),
                modes(FulfilmentType.DINE_IN, SettlementMode.PREPAID));
        MerchantSettingsResolver.SettingsRow branch = new MerchantSettingsResolver.SettingsRow(
                EnumSet.of(FulfilmentType.DINE_IN), Map.of());

        assertEquals(SettlementMode.PREPAID,
                MerchantSettingsResolver.resolve(merchant, branch).mode(FulfilmentType.DINE_IN));
    }

    @Test
    @DisplayName("an unconfigured fulfilment type falls back to its default mode, not to null")
    void unconfiguredTypeFallsBack() {
        MerchantSettingsResolver.SettingsRow merchant = new MerchantSettingsResolver.SettingsRow(
                EnumSet.of(FulfilmentType.DINE_IN, FulfilmentType.TAKEOUT),
                modes(FulfilmentType.DINE_IN, SettlementMode.TAB));

        assertEquals(SettlementMode.PREPAID,
                MerchantSettingsResolver.resolve(merchant, null).mode(FulfilmentType.TAKEOUT));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Ask the human to run:

```bash
cd backend && ./gradlew :merchant-service:test --tests '*MerchantSettingsResolverTest'
```

Expected: compilation failure — `cannot find symbol: class MerchantSettingsResolver`.

- [ ] **Step 3: Write the minimal implementation**

`ResolvedMerchantSettings.java`:

```java
package com.qrserve.merchant.dto;

import com.qrserve.shared.common.FulfilmentType;
import com.qrserve.shared.common.SettlementMode;

import java.util.Map;
import java.util.Set;

/**
 * Settings as every caller sees them: already merged, already defaulted, no nulls.
 *
 * <p>Callers must never have to ask "and what if this merchant has no row" — that
 * question is answered once, in {@code MerchantSettingsResolver}.
 */
public record ResolvedMerchantSettings(
        Set<FulfilmentType> fulfilmentEnabled,
        Map<FulfilmentType, SettlementMode> settlementMode) {

    public boolean isEnabled(FulfilmentType type) {
        return fulfilmentEnabled.contains(type);
    }

    /** Never null: an unconfigured type resolves to its platform default. */
    public SettlementMode mode(FulfilmentType type) {
        return settlementMode.get(type);
    }
}
```

`MerchantSettingsResolver.java`:

```java
package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.ResolvedMerchantSettings;
import com.qrserve.shared.common.FulfilmentType;
import com.qrserve.shared.common.SettlementMode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Merges the merchant-wide row with an optional branch row and fills the gaps.
 *
 * <p>Static and pure so the precedence rules can be asserted without a database. The
 * merge is field by field, not row-replacing: a branch that only narrows its
 * fulfilment list must not reset the merchant's settlement modes and start taking
 * money at a different point in the meal.
 */
public final class MerchantSettingsResolver {

    /**
     * Dine-in on a tab is the product's original behaviour, so it is what a merchant
     * who never opens the settings screen keeps. Takeout and delivery default to
     * PREPAID because with no table there is no signed-QR presence proof, and
     * prepayment is the control that keeps unpaid orders out of the kitchen.
     */
    public static final ResolvedMerchantSettings DEFAULTS = new ResolvedMerchantSettings(
            EnumSet.of(FulfilmentType.DINE_IN),
            defaultModes());

    private MerchantSettingsResolver() {
    }

    /** A stored row. Either field may be null or empty, meaning "not configured here". */
    public record SettingsRow(
            Set<FulfilmentType> fulfilmentEnabled,
            Map<FulfilmentType, SettlementMode> settlementMode) {
    }

    public static ResolvedMerchantSettings resolve(SettingsRow merchantRow, SettingsRow branchRow) {
        Set<FulfilmentType> enabled = firstNonEmpty(
                branchRow == null ? null : branchRow.fulfilmentEnabled(),
                merchantRow == null ? null : merchantRow.fulfilmentEnabled(),
                DEFAULTS.fulfilmentEnabled());

        Map<FulfilmentType, SettlementMode> modes = defaultModes();
        putAll(modes, merchantRow == null ? null : merchantRow.settlementMode());
        putAll(modes, branchRow == null ? null : branchRow.settlementMode());

        return new ResolvedMerchantSettings(EnumSet.copyOf(enabled), modes);
    }

    private static Map<FulfilmentType, SettlementMode> defaultModes() {
        Map<FulfilmentType, SettlementMode> modes = new EnumMap<>(FulfilmentType.class);
        modes.put(FulfilmentType.DINE_IN, SettlementMode.TAB);
        modes.put(FulfilmentType.TAKEOUT, SettlementMode.PREPAID);
        modes.put(FulfilmentType.DELIVERY, SettlementMode.PREPAID);
        return modes;
    }

    private static void putAll(
            Map<FulfilmentType, SettlementMode> target,
            Map<FulfilmentType, SettlementMode> source) {
        if (source != null) {
            target.putAll(source);
        }
    }

    @SafeVarargs
    private static Set<FulfilmentType> firstNonEmpty(Set<FulfilmentType>... candidates) {
        for (Set<FulfilmentType> candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("DEFAULTS must never be empty");
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Ask the human to run:

```bash
cd backend && ./gradlew :merchant-service:test --tests '*MerchantSettingsResolverTest'
```

Expected: `BUILD SUCCESSFUL`, 6 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/merchant-service/src/main/java/com/qrserve/merchant/service/MerchantSettingsResolver.java backend/merchant-service/src/main/java/com/qrserve/merchant/dto/ResolvedMerchantSettings.java backend/merchant-service/src/test/java/com/qrserve/merchant/service/MerchantSettingsResolverTest.java
git commit -m "feat(merchant): settings resolution with branch override and defaults"
```

---

## Task 4: Merchant settings persistence

**Files:**
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/entity/MerchantSettingsEntity.java`
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/repository/MerchantSettingsRepository.java`
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/service/MerchantSettingsService.java`
- Create: `backend/merchant-service/src/main/resources/db/manual/002-merchant-settings-and-table-qr.sql`
- Test: `backend/merchant-service/src/test/java/com/qrserve/merchant/service/MerchantSettingsServiceTest.java`

**Interfaces:**
- Consumes: `MerchantSettingsResolver`, `ResolvedMerchantSettings` (Task 3).
- Produces:
  - `MerchantSettingsEntity` with `id`, `merchantId`, `branchId`, `fulfilmentEnabled`, `settlementMode`, `rail`, `destinationRef`, `credentialHandle`, `currency`
  - `MerchantSettingsRepository.findByMerchantIdAndBranchIdIsNull(UUID) -> Optional<MerchantSettingsEntity>`
  - `MerchantSettingsRepository.findByMerchantIdAndBranchId(UUID, Long) -> Optional<MerchantSettingsEntity>`
  - `MerchantSettingsService.resolve(UUID merchantId, Long branchId) -> ResolvedMerchantSettings` (branchId nullable)

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.MerchantSettingsEntity;
import com.qrserve.merchant.repository.MerchantSettingsRepository;
import com.qrserve.shared.common.FulfilmentType;
import com.qrserve.shared.common.SettlementMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantSettingsServiceTest {

    private static final UUID MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MerchantSettingsRepository repository;
    private MerchantSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(MerchantSettingsRepository.class);
        service = new MerchantSettingsService(repository);
    }

    @Test
    @DisplayName("a merchant with no rows gets the platform defaults, not an exception")
    void noRowsYieldsDefaults() {
        when(repository.findByMerchantIdAndBranchIdIsNull(MERCHANT)).thenReturn(Optional.empty());

        assertTrue(service.resolve(MERCHANT, null).isEnabled(FulfilmentType.DINE_IN));
        assertEquals(SettlementMode.TAB, service.resolve(MERCHANT, null).mode(FulfilmentType.DINE_IN));
    }

    @Test
    @DisplayName("a branch row narrows the merchant row")
    void branchNarrowsMerchant() {
        when(repository.findByMerchantIdAndBranchIdIsNull(MERCHANT)).thenReturn(Optional.of(
                MerchantSettingsEntity.builder()
                        .merchantId(MERCHANT)
                        .fulfilmentEnabled(EnumSet.of(FulfilmentType.DINE_IN, FulfilmentType.TAKEOUT))
                        .settlementMode(Map.of(FulfilmentType.DINE_IN, SettlementMode.TAB))
                        .build()));
        when(repository.findByMerchantIdAndBranchId(MERCHANT, 5L)).thenReturn(Optional.of(
                MerchantSettingsEntity.builder()
                        .merchantId(MERCHANT)
                        .branchId(5L)
                        .fulfilmentEnabled(EnumSet.of(FulfilmentType.DINE_IN))
                        .settlementMode(Map.of())
                        .build()));

        assertTrue(service.resolve(MERCHANT, 5L).isEnabled(FulfilmentType.DINE_IN));
        assertFalse(service.resolve(MERCHANT, 5L).isEnabled(FulfilmentType.TAKEOUT));
        assertEquals(SettlementMode.TAB, service.resolve(MERCHANT, 5L).mode(FulfilmentType.DINE_IN));
    }

    @Test
    @DisplayName("a null branch id reads only the merchant-wide row")
    void nullBranchReadsMerchantRowOnly() {
        when(repository.findByMerchantIdAndBranchIdIsNull(MERCHANT)).thenReturn(Optional.of(
                MerchantSettingsEntity.builder()
                        .merchantId(MERCHANT)
                        .fulfilmentEnabled(EnumSet.of(FulfilmentType.TAKEOUT))
                        .settlementMode(Map.of(FulfilmentType.TAKEOUT, SettlementMode.PREPAID))
                        .build()));

        assertTrue(service.resolve(MERCHANT, null).isEnabled(FulfilmentType.TAKEOUT));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Ask the human to run:

```bash
cd backend && ./gradlew :merchant-service:test --tests '*MerchantSettingsServiceTest'
```

Expected: compilation failure — `cannot find symbol: class MerchantSettingsEntity`.

- [ ] **Step 3: Write the minimal implementation**

`MerchantSettingsEntity.java`:

```java
package com.qrserve.merchant.entity;

import com.qrserve.shared.common.FulfilmentType;
import com.qrserve.shared.common.SettlementMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Merchant-configurable behaviour: which fulfilment types are on, when money is
 * taken, and where it lands.
 *
 * <p>A null {@code branchId} is the merchant-wide default; a row with a branch id
 * overrides it field by field (see {@code MerchantSettingsResolver}).
 *
 * <p>{@code credentialHandle} is a reference into the secret store, never a
 * credential. The M-PESA collection account's secrets must not sit in a table that
 * merchant admin endpoints can read.
 */
@Entity
@Table(name = "merchant_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    /** Null means this row is the merchant-wide default. */
    @Column(name = "branch_id")
    private Long branchId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "merchant_fulfilment_enabled",
            joinColumns = @JoinColumn(name = "settings_id"))
    @Column(name = "fulfilment_type")
    @Enumerated(EnumType.STRING)
    private Set<FulfilmentType> fulfilmentEnabled;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "merchant_settlement_mode",
            joinColumns = @JoinColumn(name = "settings_id"))
    @MapKeyColumn(name = "fulfilment_type")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "mode")
    @Enumerated(EnumType.STRING)
    private Map<FulfilmentType, SettlementMode> settlementMode;

    /** MPESA_COLLECTION, BANK_MAI or SUPER_APP_MERCHANT. */
    @Column(name = "rail")
    private String rail;

    /** The merchant's own account. Funds never land anywhere else. */
    @Column(name = "destination_ref")
    private String destinationRef;

    @Column(name = "credential_handle")
    private String credentialHandle;

    @Column(name = "currency")
    private String currency;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (currency == null) currency = "ETB";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

`MerchantSettingsRepository.java`:

```java
package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.MerchantSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantSettingsRepository extends JpaRepository<MerchantSettingsEntity, Long> {

    Optional<MerchantSettingsEntity> findByMerchantIdAndBranchIdIsNull(UUID merchantId);

    Optional<MerchantSettingsEntity> findByMerchantIdAndBranchId(UUID merchantId, Long branchId);
}
```

`MerchantSettingsService.java`:

```java
package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.ResolvedMerchantSettings;
import com.qrserve.merchant.entity.MerchantSettingsEntity;
import com.qrserve.merchant.repository.MerchantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantSettingsService {

    private final MerchantSettingsRepository repository;

    /** @param branchId null to resolve the merchant-wide settings only */
    @Transactional(readOnly = true)
    public ResolvedMerchantSettings resolve(UUID merchantId, Long branchId) {
        MerchantSettingsResolver.SettingsRow merchantRow =
                repository.findByMerchantIdAndBranchIdIsNull(merchantId).map(this::toRow).orElse(null);

        MerchantSettingsResolver.SettingsRow branchRow = branchId == null
                ? null
                : repository.findByMerchantIdAndBranchId(merchantId, branchId).map(this::toRow).orElse(null);

        return MerchantSettingsResolver.resolve(merchantRow, branchRow);
    }

    private MerchantSettingsResolver.SettingsRow toRow(MerchantSettingsEntity entity) {
        return new MerchantSettingsResolver.SettingsRow(
                Optional.ofNullable(entity.getFulfilmentEnabled()).orElse(java.util.Set.of()),
                Optional.ofNullable(entity.getSettlementMode()).orElse(java.util.Map.of()));
    }
}
```

`002-merchant-settings-and-table-qr.sql`:

```sql
-- Merchant settings and table QR provisioning: the constraints ddl-auto cannot make.
--
-- Run against qrserve_merchant AFTER deploying the code change (the tables are
-- created by spring.jpa.hibernate.ddl-auto=update on startup; these indexes are not).
--
-- Why this is a manual file: spring.flyway.enabled is false, and ddl-auto ADDS
-- tables and columns but never creates a partial index and never enforces
-- uniqueness that involves a nullable column the way Postgres treats NULLs.
--
-- Safe on a populated database: both statements are IF NOT EXISTS and both are
-- additive. If either fails on duplicate data, that duplicate data is the bug.

-- 1. One merchant-wide settings row per merchant.
--
--    UNIQUE (merchant_id, branch_id) does NOT achieve this: in Postgres, NULLs are
--    distinct, so a merchant could accumulate any number of rows with branch_id
--    NULL and MerchantSettingsResolver would silently read whichever one the
--    planner returned first.
CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_settings_merchant_wide
    ON merchant_settings (merchant_id)
    WHERE branch_id IS NULL;

-- 2. One settings row per branch.
CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_settings_branch
    ON merchant_settings (merchant_id, branch_id)
    WHERE branch_id IS NOT NULL;

-- 3. One ACTIVE QR per table. A table with two active stickers means two terminal
--    labels in circulation and no way to know which one a guest scanned.
CREATE UNIQUE INDEX IF NOT EXISTS uq_table_qr_active
    ON table_qr (table_id)
    WHERE state = 'ACTIVE';
```

- [ ] **Step 4: Run the test to verify it passes**

Ask the human to run:

```bash
cd backend && ./gradlew :merchant-service:test --tests '*MerchantSettingsServiceTest'
```

Expected: `BUILD SUCCESSFUL`, 3 tests passing. (`table_qr` in the SQL file is created in Task 5; the file is written now because all three indexes belong in one hand-run script, and it is not executed until both tables exist.)

- [ ] **Step 5: Commit**

```bash
git add backend/merchant-service/src/main/java/com/qrserve/merchant/entity/MerchantSettingsEntity.java backend/merchant-service/src/main/java/com/qrserve/merchant/repository/MerchantSettingsRepository.java backend/merchant-service/src/main/java/com/qrserve/merchant/service/MerchantSettingsService.java backend/merchant-service/src/main/resources/db/manual/002-merchant-settings-and-table-qr.sql backend/merchant-service/src/test/java/com/qrserve/merchant/service/MerchantSettingsServiceTest.java
git commit -m "feat(merchant): persist merchant settings with per-branch override"
```

---

## Task 5: TableQr entity and provisioning

**Files:**
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/entity/TableQrEntity.java`
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/repository/TableQrRepository.java`
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/service/TableQrProvisioningService.java`
- Test: `backend/merchant-service/src/test/java/com/qrserve/merchant/service/TableQrProvisioningServiceTest.java`

**Interfaces:**
- Consumes: `EmvcoPayload`, `EmvcoMerchant` (Task 1), `TerminalLabel` (Task 2), `MerchantSettingsService` (Task 4), existing `PublicMenuUrl` and `QrSignatureService`.
- Produces:
  - `TableQrEntity` with `id`, `tableId`, `merchantId`, `branchId`, `terminalLabel`, `payloadRaw`, `payloadCrc`, `profile`, `version`, `state`, `provisionedAt`, `printedAt`, `supersededAt`
  - `TableQrRepository.findByTerminalLabel(String) -> Optional<TableQrEntity>`
  - `TableQrRepository.findByTableIdAndState(Long, String) -> Optional<TableQrEntity>`
  - `TableQrRepository.findTopByTableIdOrderByVersionDesc(Long) -> Optional<TableQrEntity>`
  - `TableQrProvisioningService.provision(TableQrProvisioningService.TableRef ref) -> TableQrEntity`
  - `TableQrProvisioningService.reprint(Long tableId) -> TableQrEntity`
  - `TableQrProvisioningService.TableRef(Long tableId, UUID merchantId, Long branchId, String merchantSlug, String merchantName, String merchantCity, String branchSlug, String tableNumber)` — a record

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.TableQrEntity;
import com.qrserve.merchant.repository.TableQrRepository;
import com.qrserve.shared.common.emvco.Emvco;
import com.qrserve.shared.common.emvco.EmvcoPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Provisioning writes the bytes that end up laminated onto a table. Once printed
 * they cannot be corrected, so what is stored has to be exactly what was rendered.
 */
class TableQrProvisioningServiceTest {

    private static final UUID MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private TableQrRepository repository;
    private MerchantSettingsService settings;
    private TableQrProvisioningService service;

    private TableQrProvisioningService.TableRef ref() {
        return new TableQrProvisioningService.TableRef(
                42L, MERCHANT, 5L, "sunrise", "SUNRISE", "ADDIS ABABA", "wello-sefer", "15");
    }

    @BeforeEach
    void setUp() {
        repository = mock(TableQrRepository.class);
        settings = mock(MerchantSettingsService.class);
        service = new TableQrProvisioningService(repository, settings);

        when(repository.save(any(TableQrEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findTopByTableIdOrderByVersionDesc(42L)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("first provisioning is version 1 and ACTIVE")
    void firstProvisioning() {
        TableQrEntity qr = service.provision(ref());

        assertEquals(1, qr.getVersion());
        assertEquals("ACTIVE", qr.getState());
        assertEquals("T42-1", qr.getTerminalLabel());
    }

    @Test
    @DisplayName("the stored payload validates its own CRC and carries the terminal label")
    void storedPayloadIsValid() {
        TableQrEntity qr = service.provision(ref());

        assertTrue(EmvcoPayload.crcValid(qr.getPayloadRaw()),
                "a payload whose CRC does not match is rejected by every bank app");
        assertEquals(qr.getPayloadCrc(), qr.getPayloadRaw().substring(qr.getPayloadRaw().length() - 4));

        Map<String, String> tags = Emvco.parseTags(qr.getPayloadRaw());
        assertEquals("11", tags.get("01"), "a table sticker is static");
        assertTrue(tags.get("62").contains("T42-1"), "tag 62 must carry the terminal label");
    }

    @Test
    @DisplayName("a reprint supersedes the old row and issues a new label")
    void reprintSupersedes() {
        TableQrEntity existing = TableQrEntity.builder()
                .id(900L).tableId(42L).merchantId(MERCHANT).branchId(5L)
                .terminalLabel("T42-1").version(1).state("ACTIVE")
                .payloadRaw("irrelevant").payloadCrc("0000").profile("EMVCO")
                .build();
        when(repository.findTopByTableIdOrderByVersionDesc(42L)).thenReturn(Optional.of(existing));
        when(repository.findByTableIdAndState(42L, "ACTIVE")).thenReturn(Optional.of(existing));

        TableQrEntity reprinted = service.reprint(42L, ref());

        assertEquals(2, reprinted.getVersion());
        assertEquals("T42-2", reprinted.getTerminalLabel());
        assertNotEquals(existing.getTerminalLabel(), reprinted.getTerminalLabel());
        // The old sticker may still be on the table, so its row survives and stays
        // resolvable — deleting it would turn real payments into UNKNOWN_TERMINAL.
        assertEquals("SUPERSEDED", existing.getState());
    }

    @Test
    @DisplayName("the destination account comes from settings, never from a default")
    void destinationComesFromSettings() {
        // A payload built with the wrong destination sends a guest's money to the
        // wrong account, so an unconfigured merchant must fail loudly at provisioning
        // rather than print a code that pays somebody else.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.provision(new TableQrProvisioningService.TableRef(
                        43L, MERCHANT, 5L, "sunrise", "SUNRISE", "ADDIS ABABA", "wello-sefer", "16")),
                "provisioning without a settlement destination must be refused");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Ask the human to run:

```bash
cd backend && ./gradlew :merchant-service:test --tests '*TableQrProvisioningServiceTest'
```

Expected: compilation failure — `cannot find symbol: class TableQrEntity`.

- [ ] **Step 3: Write the minimal implementation**

`TableQrEntity.java`:

```java
package com.qrserve.merchant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The exact payload that went onto one printed sticker.
 *
 * <p>A payment instrument in the physical world is a liability: "what exact bytes are
 * on table 15" has to be answerable without recomputing from current configuration
 * and hoping none of the inputs moved. So the bytes are stored, not derived.
 *
 * <p>Rows are versioned and never deleted. A superseded sticker can stay on a table
 * for weeks, and a payment quoting its terminal label must still resolve to this
 * table — otherwise a reprint silently converts real payments into unmatched money.
 */
@Entity
@Table(name = "table_qr")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableQrEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    /** EMVCo tag 62-07. Unique per version, so a payment names its sticker. */
    @Column(name = "terminal_label", nullable = false, unique = true)
    private String terminalLabel;

    @Column(name = "payload_raw", nullable = false, length = 1024)
    private String payloadRaw;

    @Column(name = "payload_crc", nullable = false, length = 4)
    private String payloadCrc;

    /** EMVCO or MENU_URL — the two provisioning profiles. */
    @Column(name = "profile", nullable = false)
    private String profile;

    @Column(name = "version", nullable = false)
    private int version;

    /** ACTIVE, SUPERSEDED or REVOKED. */
    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "provisioned_at")
    private LocalDateTime provisionedAt;

    /** Set when a merchant actually exports the code for printing. */
    @Column(name = "printed_at")
    private LocalDateTime printedAt;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @PrePersist
    public void prePersist() {
        if (provisionedAt == null) provisionedAt = LocalDateTime.now();
        if (state == null) state = "ACTIVE";
    }
}
```

`TableQrRepository.java`:

```java
package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.TableQrEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TableQrRepository extends JpaRepository<TableQrEntity, Long> {

    /** Resolves any label, ACTIVE or SUPERSEDED — a stale sticker still takes payments. */
    Optional<TableQrEntity> findByTerminalLabel(String terminalLabel);

    Optional<TableQrEntity> findByTableIdAndState(Long tableId, String state);

    Optional<TableQrEntity> findTopByTableIdOrderByVersionDesc(Long tableId);
}
```

`TableQrProvisioningService.java`:

```java
package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.ResolvedMerchantSettings;
import com.qrserve.merchant.entity.TableQrEntity;
import com.qrserve.merchant.repository.TableQrRepository;
import com.qrserve.shared.common.TerminalLabel;
import com.qrserve.shared.common.emvco.EmvcoMerchant;
import com.qrserve.shared.common.emvco.EmvcoPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Mints and stores the static payload for a table.
 *
 * <p>Called inside the table-creation transaction, so a table cannot exist without a
 * scannable code. Reprints supersede rather than replace.
 */
@Service
@RequiredArgsConstructor
public class TableQrProvisioningService {

    /** Restaurants. EMVCo tag 52. */
    private static final String MCC_RESTAURANT = "5812";
    private static final String CURRENCY_ETB = "230";
    private static final String COUNTRY_ET = "ET";
    private static final String GUID = "ET.QRSERVE";
    private static final String PROFILE_EMVCO = "EMVCO";

    private final TableQrRepository repository;
    private final MerchantSettingsService settingsService;

    /** Everything the payload needs, passed in so this service reads no other table. */
    public record TableRef(
            Long tableId,
            UUID merchantId,
            Long branchId,
            String merchantSlug,
            String merchantName,
            String merchantCity,
            String branchSlug,
            String tableNumber) {
    }

    @Transactional
    public TableQrEntity provision(TableRef ref) {
        int version = repository.findTopByTableIdOrderByVersionDesc(ref.tableId())
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);
        return mint(ref, version);
    }

    /** Issues the next version and supersedes whatever is currently ACTIVE. */
    @Transactional
    public TableQrEntity reprint(Long tableId, TableRef ref) {
        repository.findByTableIdAndState(tableId, "ACTIVE").ifPresent(active -> {
            active.setState("SUPERSEDED");
            active.setSupersededAt(LocalDateTime.now());
            repository.save(active);
        });
        return provision(ref);
    }

    private TableQrEntity mint(TableRef ref, int version) {
        ResolvedMerchantSettings settings = settingsService.resolve(ref.merchantId(), ref.branchId());
        String destination = destinationOf(ref);

        String terminalLabel = TerminalLabel.of(ref.tableId(), version);
        EmvcoMerchant merchant = new EmvcoMerchant(
                GUID, destination, MCC_RESTAURANT, CURRENCY_ETB, COUNTRY_ET,
                ref.merchantName(), ref.merchantCity());

        String payload = EmvcoPayload.staticPayload(merchant, terminalLabel, ref.branchSlug());

        return repository.save(TableQrEntity.builder()
                .tableId(ref.tableId())
                .merchantId(ref.merchantId())
                .branchId(ref.branchId())
                .terminalLabel(terminalLabel)
                .payloadRaw(payload)
                .payloadCrc(payload.substring(payload.length() - 4))
                .profile(PROFILE_EMVCO)
                .version(version)
                .state("ACTIVE")
                .build());
    }

    /**
     * No destination, no code. A payload built with a guessed account sends a guest's
     * money somewhere nobody chose, and a printed sticker cannot be recalled — so an
     * unconfigured merchant fails here, loudly, before anything is laminated.
     */
    private String destinationOf(TableRef ref) {
        return Optional.ofNullable(
                        settingsService.destinationRef(ref.merchantId(), ref.branchId()))
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Merchant " + ref.merchantId() + " has no settlement destination; "
                                + "configure MerchantSettings before provisioning table QRs"));
    }
}
```

Add the accessor this depends on to `MerchantSettingsService`:

```java
    /** The merchant's own account, or null when unconfigured. Never defaulted. */
    @Transactional(readOnly = true)
    public String destinationRef(UUID merchantId, Long branchId) {
        return (branchId == null
                ? java.util.Optional.<com.qrserve.merchant.entity.MerchantSettingsEntity>empty()
                : repository.findByMerchantIdAndBranchId(merchantId, branchId))
                .or(() -> repository.findByMerchantIdAndBranchIdIsNull(merchantId))
                .map(com.qrserve.merchant.entity.MerchantSettingsEntity::getDestinationRef)
                .orElse(null);
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Ask the human to run:

```bash
cd backend && ./gradlew :merchant-service:test --tests '*TableQrProvisioningServiceTest'
```

Expected: `BUILD SUCCESSFUL`, 4 tests passing. The provisioning tests stub `destinationRef` to return an account for table 42 and null for table 43 — add `when(settings.destinationRef(MERCHANT, 5L)).thenReturn("1234567890")` in `setUp`, and a per-test override returning `null` for the refusal case.

- [ ] **Step 5: Commit**

```bash
git add backend/merchant-service/src/main/java/com/qrserve/merchant/entity/TableQrEntity.java backend/merchant-service/src/main/java/com/qrserve/merchant/repository/TableQrRepository.java backend/merchant-service/src/main/java/com/qrserve/merchant/service/TableQrProvisioningService.java backend/merchant-service/src/main/java/com/qrserve/merchant/service/MerchantSettingsService.java backend/merchant-service/src/test/java/com/qrserve/merchant/service/TableQrProvisioningServiceTest.java
git commit -m "feat(merchant): store versioned EMVCo payload per table"
```

---

## Task 6: Provision inside table creation and publish the event

**Files:**
- Create: `backend/shared/events/src/main/java/com/qrserve/shared/events/TableQrProvisionedEvent.java`
- Create: `backend/merchant-service/src/main/java/com/qrserve/merchant/kafka/TableQrEventPublisher.java`
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/service/TableService.java`
- Modify: `backend/merchant-service/src/main/java/com/qrserve/merchant/dto/CreateTableResponse.java`
- Test: `backend/merchant-service/src/test/java/com/qrserve/merchant/service/TableServiceQrProvisioningTest.java`

**Interfaces:**
- Consumes: `TableQrProvisioningService` (Task 5).
- Produces:
  - `TableQrProvisionedEvent` with `terminalLabel`, `tableId`, `merchantId`, `branchId`, `tableNumber`, `version`, `provisionedAt`
  - `TableQrEventPublisher.publish(TableQrProvisionedEvent)`
  - `CreateTableResponse` gains `terminalLabel` and `qrPayload`
  - Kafka topic name: `table-qr-provisioned`

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateTableRequest;
import com.qrserve.merchant.dto.CreateTableResponse;
import com.qrserve.merchant.entity.TableQrEntity;
import com.qrserve.merchant.kafka.TableQrEventPublisher;
import com.qrserve.shared.events.TableQrProvisionedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

/**
 * A table without a scannable code is a table nobody can order from, so provisioning
 * belongs in the creation transaction rather than in a later screen.
 */
class TableServiceQrProvisioningTest {

    @Test
    @DisplayName("creating a table returns its payload and terminal label")
    void creationReturnsPayload() {
        TableServiceHarness harness = TableServiceHarness.withProvisionedQr("T42-1", "0002010102...9A4D");

        CreateTableResponse response = harness.service().createTable(
                CreateTableRequest.builder().branchId(5L).tableNumber("15").capacity(4).build());

        assertEquals("T42-1", response.getTerminalLabel());
        assertNotNull(response.getQrPayload(), "the caller needs the payload to render a sticker");
    }

    @Test
    @DisplayName("creating a table publishes the terminal mapping")
    void creationPublishesMapping() {
        TableServiceHarness harness = TableServiceHarness.withProvisionedQr("T42-1", "0002010102...9A4D");

        harness.service().createTable(
                CreateTableRequest.builder().branchId(5L).tableNumber("15").capacity(4).build());

        ArgumentCaptor<TableQrProvisionedEvent> captor =
                ArgumentCaptor.forClass(TableQrProvisionedEvent.class);
        verify(harness.publisher()).publish(captor.capture());

        // order-service cannot resolve a webhook's terminal label without this event.
        assertEquals("T42-1", captor.getValue().getTerminalLabel());
        assertEquals(42L, captor.getValue().getTableId());
    }
}
```

`TableServiceHarness` is a test fixture in the same package that wires `TableService` with mocked repositories, a mocked `TableQrProvisioningService` returning a `TableQrEntity` with the given label and payload, and a mocked `TableQrEventPublisher`; `harness.service()`, `harness.publisher()` expose them. Write it as a small static factory in `src/test/java/com/qrserve/merchant/service/TableServiceHarness.java` mirroring the mock setup in `TableQrProvisioningServiceTest`.

- [ ] **Step 2: Run the test to verify it fails**

Ask the human to run:

```bash
cd backend && ./gradlew :merchant-service:test --tests '*TableServiceQrProvisioningTest'
```

Expected: compilation failure — `cannot find symbol: method getTerminalLabel()` on `CreateTableResponse`.

- [ ] **Step 3: Write the minimal implementation**

`TableQrProvisionedEvent.java`:

```java
package com.qrserve.shared.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A terminal label now identifies this table. Immutable identity, never revised.
 *
 * <p>order-service projects these into a local lookup table so that resolving a
 * payment webhook's terminal label needs no call to merchant-service. A synchronous
 * call there would put a second service in the money path, where it could fail while
 * a guest's payment is already in flight.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableQrProvisionedEvent {
    private String terminalLabel;
    private Long tableId;
    private UUID merchantId;
    private Long branchId;
    private String tableNumber;
    private int version;
    private LocalDateTime provisionedAt;
    private String traceId;
    private String spanId;
}
```

`TableQrEventPublisher.java`:

```java
package com.qrserve.merchant.kafka;

import com.qrserve.shared.events.TableQrProvisionedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TableQrEventPublisher {

    private static final String TOPIC = "table-qr-provisioned";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(TableQrProvisionedEvent event) {
        kafkaTemplate.send(TOPIC, event.getTerminalLabel(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        // Loud, because a lost event means a payment against this
                        // sticker lands as UNKNOWN_TERMINAL until the projection is
                        // rebuilt. Provisioning still succeeds: the sticker is real.
                        log.error("Failed to publish terminal mapping for {}",
                                event.getTerminalLabel(), error);
                    }
                });
    }
}
```

In `TableService.createTable`, after `TableEntity saved = tableRepository.save(table);` and before building the response, provision and publish:

```java
        TableQrEntity qr = tableQrProvisioningService.provision(
                new TableQrProvisioningService.TableRef(
                        saved.getId(), merchant.getId(), branch.getId(),
                        merchant.getSlug(), merchant.getName(), merchant.getCity(),
                        branch.getSlug(), saved.getTableNumber()));

        tableQrEventPublisher.publish(TableQrProvisionedEvent.builder()
                .terminalLabel(qr.getTerminalLabel())
                .tableId(saved.getId())
                .merchantId(merchant.getId())
                .branchId(branch.getId())
                .tableNumber(saved.getTableNumber())
                .version(qr.getVersion())
                .provisionedAt(qr.getProvisionedAt())
                .build());
```

and add `.terminalLabel(qr.getTerminalLabel())` and `.qrPayload(qr.getPayloadRaw())` to the `CreateTableResponse` builder. Add both fields to the DTO:

```java
    /** EMVCo tag 62-07 for this table's current sticker. */
    private String terminalLabel;

    /** The exact EMVCo payload to render. The caller must not build its own. */
    private String qrPayload;
```

Inject `TableQrProvisioningService` and `TableQrEventPublisher` via the existing `@RequiredArgsConstructor`.

- [ ] **Step 4: Run the test to verify it passes**

Ask the human to run:

```bash
cd backend && ./gradlew :merchant-service:test
```

Expected: `BUILD SUCCESSFUL`, the two new tests passing and no existing merchant-service test broken.

- [ ] **Step 5: Commit**

```bash
git add backend/shared/events/src/main/java/com/qrserve/shared/events/TableQrProvisionedEvent.java backend/merchant-service/src/main/java/com/qrserve/merchant/kafka/TableQrEventPublisher.java backend/merchant-service/src/main/java/com/qrserve/merchant/service/TableService.java backend/merchant-service/src/main/java/com/qrserve/merchant/dto/CreateTableResponse.java backend/merchant-service/src/test/java/com/qrserve/merchant/service
git commit -m "feat(merchant): provision table QR at creation and publish terminal mapping"
```

---

## Task 7: Terminal map projection in order-service

**Files:**
- Create: `backend/order-service/src/main/java/com/qrserve/order/payment/terminal/TerminalMapEntity.java`
- Create: `backend/order-service/src/main/java/com/qrserve/order/payment/terminal/TerminalMapRepository.java`
- Create: `backend/order-service/src/main/java/com/qrserve/order/payment/terminal/TerminalMapService.java`
- Create: `backend/order-service/src/main/java/com/qrserve/order/payment/terminal/TerminalMapListener.java`
- Test: `backend/order-service/src/test/java/com/qrserve/order/payment/terminal/TerminalMapServiceTest.java`

**Interfaces:**
- Consumes: `TableQrProvisionedEvent` (Task 6).
- Produces:
  - `TerminalMapEntity` with `terminalLabel` (id), `tableId`, `merchantId`, `branchId`, `tableNumber`, `version`, `receivedAt`
  - `TerminalMapRepository extends JpaRepository<TerminalMapEntity, String>`
  - `TerminalMapService.resolve(String terminalLabel) -> Optional<TerminalMapEntity>`
  - `TerminalMapService.record(TableQrProvisionedEvent) -> void` (idempotent)
  - Consumer group `order-service`, topic `table-qr-provisioned`

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.order.payment.terminal;

import com.qrserve.shared.events.TableQrProvisionedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This projection is what stands between an external payment and an
 * UNKNOWN_TERMINAL hold, so it has to be durable and it has to tolerate Kafka
 * delivering the same event more than once.
 */
class TerminalMapServiceTest {

    private static final UUID MERCHANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private TerminalMapRepository repository;
    private TerminalMapService service;

    private TableQrProvisionedEvent event(String label, long tableId, int version) {
        return TableQrProvisionedEvent.builder()
                .terminalLabel(label).tableId(tableId).merchantId(MERCHANT).branchId(5L)
                .tableNumber("15").version(version).provisionedAt(LocalDateTime.now())
                .build();
    }

    @BeforeEach
    void setUp() {
        repository = mock(TerminalMapRepository.class);
        service = new TerminalMapService(repository);
        when(repository.save(any(TerminalMapEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("a provisioning event becomes a resolvable mapping")
    void recordsMapping() {
        when(repository.existsById("T42-1")).thenReturn(false);

        service.record(event("T42-1", 42L, 1));

        verify(repository).save(any(TerminalMapEntity.class));
    }

    @Test
    @DisplayName("a redelivered event is not written twice")
    void idempotentOnRedelivery() {
        // Kafka is at-least-once, and the projection's primary key is the label, so a
        // second insert would fail the listener and stall the partition.
        when(repository.existsById("T42-1")).thenReturn(true);

        service.record(event("T42-1", 42L, 1));

        verify(repository, never()).save(any(TerminalMapEntity.class));
    }

    @Test
    @DisplayName("a superseded label still resolves, because its sticker may still be on the table")
    void supersededLabelStillResolves() {
        TerminalMapEntity v1 = TerminalMapEntity.builder()
                .terminalLabel("T42-1").tableId(42L).merchantId(MERCHANT).branchId(5L)
                .tableNumber("15").version(1).build();
        when(repository.findById("T42-1")).thenReturn(Optional.of(v1));

        Optional<TerminalMapEntity> resolved = service.resolve("T42-1");

        assertTrue(resolved.isPresent(), "a reprint must not orphan payments from old stickers");
        assertEquals(42L, resolved.get().getTableId());
    }

    @Test
    @DisplayName("an unknown label resolves to empty rather than throwing")
    void unknownLabelIsEmpty() {
        when(repository.findById("T99-9")).thenReturn(Optional.empty());
        assertTrue(service.resolve("T99-9").isEmpty());
    }

    @Test
    @DisplayName("a null or blank label resolves to empty")
    void blankLabelIsEmpty() {
        // A webhook that omits tag 62-07 must land as UNKNOWN_TERMINAL, not as an
        // exception that leaves the payment unrecorded.
        assertTrue(service.resolve(null).isEmpty());
        assertTrue(service.resolve("  ").isEmpty());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Ask the human to run:

```bash
cd backend && ./gradlew :order-service:test --tests '*TerminalMapServiceTest'
```

Expected: compilation failure — `package com.qrserve.order.payment.terminal does not exist`.

- [ ] **Step 3: Write the minimal implementation**

`TerminalMapEntity.java`:

```java
package com.qrserve.order.payment.terminal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Local, append-only projection of terminal label to table identity.
 *
 * <p>Owned by merchant-service, read here. It is a projection of immutable identity
 * data — never of a derived money value — which is what makes duplicating it safe:
 * a label's table never changes, so this copy cannot drift from its source.
 *
 * <p>It exists so that resolving a payment webhook needs no synchronous call to
 * merchant-service. That call would place a second service in the money path, able
 * to fail while a guest's payment is already in flight.
 */
@Entity
@Table(name = "terminal_map")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerminalMapEntity {

    /** EMVCo tag 62-07. The natural key: unique per printed sticker. */
    @Id
    @Column(name = "terminal_label", nullable = false)
    private String terminalLabel;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "table_number")
    private String tableNumber;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @PrePersist
    public void prePersist() {
        if (receivedAt == null) receivedAt = LocalDateTime.now();
    }
}
```

`TerminalMapRepository.java`:

```java
package com.qrserve.order.payment.terminal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalMapRepository extends JpaRepository<TerminalMapEntity, String> {
}
```

`TerminalMapService.java`:

```java
package com.qrserve.order.payment.terminal;

import com.qrserve.shared.events.TableQrProvisionedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TerminalMapService {

    private final TerminalMapRepository repository;

    /** Idempotent: Kafka is at-least-once and the label is the primary key. */
    @Transactional
    public void record(TableQrProvisionedEvent event) {
        if (repository.existsById(event.getTerminalLabel())) {
            return;
        }
        repository.save(TerminalMapEntity.builder()
                .terminalLabel(event.getTerminalLabel())
                .tableId(event.getTableId())
                .merchantId(event.getMerchantId())
                .branchId(event.getBranchId())
                .tableNumber(event.getTableNumber())
                .version(event.getVersion())
                .build());
    }

    /**
     * Resolves any label ever issued, superseded ones included.
     *
     * @return empty for an unknown, null or blank label — the caller turns that into
     *         an UNKNOWN_TERMINAL hold, which is a recorded payment awaiting staff,
     *         never a thrown exception that loses it
     */
    @Transactional(readOnly = true)
    public Optional<TerminalMapEntity> resolve(String terminalLabel) {
        if (terminalLabel == null || terminalLabel.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(terminalLabel);
    }
}
```

`TerminalMapListener.java`:

```java
package com.qrserve.order.payment.terminal;

import com.qrserve.shared.events.TableQrProvisionedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TerminalMapListener {

    private final TerminalMapService terminalMapService;

    @KafkaListener(topics = "table-qr-provisioned", groupId = "order-service")
    public void onProvisioned(TableQrProvisionedEvent event) {
        log.info("Terminal {} maps to table {}", event.getTerminalLabel(), event.getTableId());
        terminalMapService.record(event);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Ask the human to run:

```bash
cd backend && ./gradlew :order-service:test --tests '*TerminalMapServiceTest'
```

Expected: `BUILD SUCCESSFUL`, 5 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/order-service/src/main/java/com/qrserve/order/payment/terminal backend/order-service/src/test/java/com/qrserve/order/payment/terminal
git commit -m "feat(payments): project terminal mapping into order-service"
```

---

## Task 8: qr-service renders the stored payload

**Files:**
- Modify: `backend/qr-service/src/main/java/com/qrserve/qr/service/QrGeneratorService.java`
- Modify: `backend/qr-service/src/main/java/com/qrserve/qr/dto/QrMetadataResponse.java`
- Test: `backend/qr-service/src/test/java/com/qrserve/qr/service/QrGeneratorServiceTest.java`

**Interfaces:**
- Consumes: `CreateTableResponse.qrPayload` / the table QR read endpoint from Task 6, `EmvcoPayload.crcValid` (Task 1).
- Produces:
  - `QrMetadataResponse` gains `payloadRaw`, `terminalLabel`, `profile`
  - `QrGeneratorService.getQrForTable(Long)` returns the **stored** payload rendered to PNG, and refuses to render an invalid one

- [ ] **Step 1: Write the failing test**

```java
package com.qrserve.qr.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * qr-service renders; it no longer decides what the code says. Recomputing the target
 * here is how the two services drifted the first time.
 */
class QrGeneratorServiceTest {

    private static final String VALID_STATIC =
            "00020101021126280010ET.QRSERVE011012345678905204581253032305802ET"
                    + "5907SUNRISE6011ADDIS ABABA62160303BR10705T42-163049A4D";

    @Test
    @DisplayName("renders the stored payload verbatim")
    void rendersStoredPayload() {
        assertEquals(VALID_STATIC, QrGeneratorService.payloadToRender(VALID_STATIC));
    }

    @Test
    @DisplayName("refuses to render a payload whose CRC does not match")
    void refusesInvalidCrc() {
        // Rendering it would produce a sticker that every bank app rejects with no
        // explanation, and by then it is laminated to a table.
        String corrupted = VALID_STATIC.substring(0, VALID_STATIC.length() - 4) + "0000";
        assertThrows(IllegalStateException.class, () -> QrGeneratorService.payloadToRender(corrupted));
    }

    @Test
    @DisplayName("refuses to invent a payload when none is stored")
    void refusesMissingPayload() {
        assertThrows(IllegalStateException.class, () -> QrGeneratorService.payloadToRender(null));
        assertThrows(IllegalStateException.class, () -> QrGeneratorService.payloadToRender(" "));
    }

    @Test
    @DisplayName("a rendered PNG is a real PNG")
    void rendersPng() {
        byte[] png = QrGeneratorService.renderPng(VALID_STATIC, 300);
        assertTrue(png.length > 100);
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Ask the human to run:

```bash
cd backend && ./gradlew :qr-service:test --tests '*QrGeneratorServiceTest'
```

Expected: compilation failure — `cannot find symbol: method payloadToRender(String)`.

- [ ] **Step 3: Write the minimal implementation**

Add to `QrGeneratorService` and use it from `getQrForTable` and `exportPng`, replacing the `targetUrlFor(...)` call as the source of what gets encoded:

```java
    /**
     * The payload to encode, validated.
     *
     * <p>Static so it can be asserted without standing up HTTP, and package-visible
     * for the same reason {@code targetUrlFor} was: this is the one decision that
     * must not diverge between services.
     *
     * @throws IllegalStateException when nothing is stored or the CRC does not match —
     *         both cases must stop a print run rather than produce a dead sticker
     */
    static String payloadToRender(String storedPayload) {
        if (storedPayload == null || storedPayload.isBlank()) {
            throw new IllegalStateException(
                    "No provisioned QR payload for this table; provision one before rendering");
        }
        if (!EmvcoPayload.crcValid(storedPayload)) {
            throw new IllegalStateException(
                    "Stored QR payload fails its own CRC and would be rejected by every wallet");
        }
        return storedPayload;
    }

    /** ZXing render. Separated from payload selection so each can be tested alone. */
    static byte[] renderPng(String payload, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                    payload, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1));
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render QR payload", e);
        }
    }
```

Add to `QrMetadataResponse`:

```java
    /** The exact provisioned payload. Clients render this and never build their own. */
    private String payloadRaw;

    /** EMVCo tag 62-07 for the sticker this image represents. */
    private String terminalLabel;

    /** EMVCO or MENU_URL. */
    private String profile;
```

`getQrForTable` now fetches the provisioned `TableQr` for the table (via the existing merchant-service `RestTemplate` call pattern already used by `fetchTable`), passes `payloadRaw` through `payloadToRender`, renders it with `renderPng`, and fills the three new fields. The existing `qrUrl` field keeps carrying the menu URL so the designer can still show a human-readable link, but it is no longer what gets encoded.

- [ ] **Step 4: Run the test to verify it passes**

Ask the human to run:

```bash
cd backend && ./gradlew :qr-service:test
```

Expected: `BUILD SUCCESSFUL`, 4 new tests passing and the existing `targetUrlFor` tests still passing.

- [ ] **Step 5: Commit**

```bash
git add backend/qr-service/src/main/java/com/qrserve/qr backend/qr-service/src/test/java/com/qrserve/qr
git commit -m "feat(qr): render the provisioned payload and refuse an invalid one"
```

---

## Task 9: Frontend shows the server-rendered code

**Files:**
- Create: `src/lib/qrDisplay.ts`
- Create: `src/lib/qrDisplay.test.ts`
- Modify: `src/components/merchant/QRDesigner.tsx`
- Modify: `src/pages/TableManagement.tsx`
- Modify: `package.json`

**Interfaces:**
- Consumes: `QrMetadataResponse` with `payloadRaw`, `terminalLabel`, `profile`, `base64Content` (Task 8).
- Produces:
  - `canRenderQr(metadata) -> boolean`
  - `qrImageSrc(metadata) -> string | null`
  - `qrCaption(metadata) -> string`

- [ ] **Step 1: Write the failing test**

```ts
/**
 * Tests for QR display gating. Run with `npm run test:unit`.
 *
 * The component this backs used to fall back to a fabricated
 * `https://qrserve.com/menu/${slug}/${branchId || 1}/${tableId || 1}` when metadata
 * had not loaded — the wrong domain, with branch and table defaulted to 1. A merchant
 * could print and laminate that. These tests exist to keep the guess deleted.
 */
import assert from 'node:assert/strict';
import { canRenderQr, qrCaption, qrImageSrc } from './qrDisplay';

let failures = 0;
function test(name: string, fn: () => void) {
  try {
    fn();
    console.log(`  ok  ${name}`);
  } catch (error) {
    failures += 1;
    console.error(`FAIL  ${name}`);
    console.error(`      ${(error as Error).message}`);
  }
}

const READY = {
  base64Content: 'data:image/png;base64,iVBORw0KGgo=',
  payloadRaw: '000201010211...9A4D',
  terminalLabel: 'T42-1',
  profile: 'EMVCO',
};

test('renders when the server supplied an image', () => {
  assert.equal(canRenderQr(READY), true);
  assert.equal(qrImageSrc(READY), READY.base64Content);
});

test('renders nothing at all when metadata has not loaded', () => {
  assert.equal(canRenderQr(undefined), false);
  assert.equal(qrImageSrc(undefined), null);
});

test('renders nothing when the image is missing, rather than guessing a URL', () => {
  const noImage = { ...READY, base64Content: '' };
  assert.equal(canRenderQr(noImage), false);
  assert.equal(qrImageSrc(noImage), null);
});

test('a payment code is captioned with its terminal label', () => {
  // Staff need this to answer "which sticker is on table 15" without a database.
  assert.match(qrCaption(READY), /T42-1/);
});

test('a menu-url code is captioned as a menu link, not as a payment code', () => {
  assert.match(qrCaption({ ...READY, profile: 'MENU_URL' }), /menu/i);
});

test('an unloaded caption says so instead of inventing a label', () => {
  assert.match(qrCaption(undefined), /not provisioned|loading/i);
});

if (failures > 0) {
  console.error(`\n${failures} test(s) failed`);
  process.exit(1);
}
console.log('\nall QR display tests passed');
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx tsx src/lib/qrDisplay.test.ts
```

Expected: failure — `Cannot find module './qrDisplay'`.

- [ ] **Step 3: Write the minimal implementation**

`src/lib/qrDisplay.ts`:

```ts
/**
 * Display gating for a provisioned table QR.
 *
 * The payload is minted, stored and rendered by the backend. The frontend's only job
 * is to show the image it was given, or show nothing — never to construct a code. A
 * client-side render of an EMVCo payload is a second renderer for a money instrument,
 * and a fabricated URL is a sticker that resolves to a 404 once it is laminated.
 */
export interface QrMetadata {
  base64Content?: string | null;
  payloadRaw?: string | null;
  terminalLabel?: string | null;
  profile?: string | null;
  qrUrl?: string | null;
}

export function canRenderQr(metadata?: QrMetadata | null): boolean {
  return !!metadata?.base64Content;
}

/** @returns the server-rendered image, or null. Never a generated fallback. */
export function qrImageSrc(metadata?: QrMetadata | null): string | null {
  return canRenderQr(metadata) ? metadata!.base64Content! : null;
}

export function qrCaption(metadata?: QrMetadata | null): string {
  if (!canRenderQr(metadata)) return 'QR not provisioned yet';
  if (metadata!.profile === 'MENU_URL') return 'Menu link code';
  return `Payment code · terminal ${metadata!.terminalLabel ?? 'unknown'}`;
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
npx tsx src/lib/qrDisplay.test.ts && npm run lint
```

Expected: 6 tests pass, `tsc --noEmit` clean.

- [ ] **Step 5: Wire it into the UI and the test script, then commit**

In `QRDesigner.tsx`: delete the `menuUrl` fallback expression and the `QRCode.toCanvas` call, remove the now-unused `qrcode` import, and render `<img src={qrImageSrc(qrMetadata)} />` inside `canRenderQr(qrMetadata)`, with an empty state otherwise. Branding composition (frame, colours, title) keeps working — it now surrounds the image instead of drawing the modules. In `TableManagement.tsx`, show the same image from the create-table response after a table is created.

In `package.json`:

```json
    "test:unit": "tsx src/lib/tenant.test.ts && tsx src/lib/orderSession.test.ts && tsx src/lib/qrDisplay.test.ts"
```

```bash
npm run lint && npm run test:unit
git add src/lib/qrDisplay.ts src/lib/qrDisplay.test.ts src/components/merchant/QRDesigner.tsx src/pages/TableManagement.tsx package.json
git commit -m "fix(qr): show the server-rendered code and delete the fabricated fallback"
```

---

## Plan Self-Review

**Spec coverage.** Section 3 (merchant configuration) → Tasks 3–4, including the per-fulfilment-type mode map and `credentialHandle` never holding a credential. Section 5.1 (one builder, one place) → Task 1. Section 5.2 (tag mapping) → Task 1, asserted by golden vectors. Section 5.4 (`QrPayloadPort`) → **not in this plan**: `mintStatic` is Task 5 and `mintDynamic` is per-bill, so the port interface lands with the payable in the payment-core plan, where its only caller exists. Section 5.5 (table binding, terminal mapping, supersede-not-delete, `REVOKED`, branch `MENU_URL` profile) → Tasks 5–7; `REVOKED` is a stored state with no writer yet, and the branch entry QR is deferred with open item 6. Section 5.6 (server renders, delete the fallback) → Tasks 8–9. Sections 4, 6, 8, 10 are the payment-core plan by design.

**Two gaps found and closed inline.** Task 5's provisioning originally read a destination account that nothing supplied — fixed by adding `MerchantSettingsService.destinationRef` and a test asserting that provisioning without one is refused rather than defaulted, because a printed sticker cannot be recalled. Task 4's SQL originally relied on `UNIQUE (merchant_id, branch_id)` to keep one merchant-wide row, which Postgres does not enforce across NULLs; it is now two partial indexes.

**Placeholder scan.** No TBD/TODO. Two prose-only steps remain by intent: Task 6 Step 1's `TableServiceHarness` (described precisely — mocked repositories, a stubbed provisioning service, a mocked publisher, mirroring Task 5's setup) and Task 9 Step 5's component edits, which are deletions and a JSX swap rather than new logic. Everything with behaviour has real code.

**Type consistency.** `TerminalLabel.of(long, int)` returns the `terminalLabel` stored by `TableQrEntity`, published as `TableQrProvisionedEvent.terminalLabel`, keyed by `TerminalMapEntity.terminalLabel`, and read by `TerminalMapService.resolve(String)` — one name, one type, five files. `EmvcoPayload.crcValid` is used in Tasks 1, 5 and 8 with the same signature. `ResolvedMerchantSettings.mode()` never returns null, which is why Task 5 can call it without a null check. `TableQrProvisioningService.reprint` takes `(Long, TableRef)` in both its test and its implementation.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-20-table-qr-provisioning-and-merchant-settings.md`. Two execution options:

**1. Subagent-Driven (recommended)** — a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session with checkpoints for review.

The payment-core plan (spec sections 4, 6, 8, 10) and the non-table fulfilment plan (section 9) follow this one.
