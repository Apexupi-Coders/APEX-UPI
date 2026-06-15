package com.hpe.upi.npci.adapter;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Iso8583Codec — encodes and decodes ISO 8583 financial messages.
 *
 * ISO 8583 is the international standard for financial transaction messages.
 * It is the protocol used by ATM networks, credit card switches, and UPI's
 * underlying bank-to-bank communication.
 *
 * Message structure:
 *   [MTI 4 bytes][Primary Bitmap 8 bytes][Secondary Bitmap 8 bytes][Data Elements]
 *
 * MTI (Message Type Indicator) — 4-digit code identifying message type:
 *   0200 — Financial Transaction Request  (payment debit)
 *   0210 — Financial Transaction Response (payment debit ack)
 *   0400 — Reversal Request
 *   0410 — Reversal Response
 *   0800 — Network Management Request    (status/ping)
 *   0810 — Network Management Response
 *
 * Bitmap — 64 bits (8 bytes) where each set bit means that data element
 * is present in the message. Bit 1 set = secondary bitmap present (128 bits total).
 *
 * Key Data Elements used in UPI:
 *   DE02 — Primary Account Number (PAN / account number)
 *   DE03 — Processing Code (000000=purchase, 200000=reversal)
 *   DE04 — Transaction Amount (12 digits, implied 2 decimal places)
 *   DE07 — Transmission Date/Time (MMDDHHmmss)
 *   DE11 — System Trace Audit Number (STAN) — unique per message
 *   DE12 — Local Transaction Time (HHmmss)
 *   DE13 — Local Transaction Date (MMDD)
 *   DE32 — Acquiring Institution ID (bank routing code)
 *   DE37 — Retrieval Reference Number (RRN)
 *   DE39 — Response Code (00=approved, 51=insufficient funds, 91=timeout)
 *   DE41 — Card Acceptor Terminal ID (VPA in UPI context)
 *   DE42 — Card Acceptor ID (merchant/payee VPA)
 *   DE49 — Currency Code (356 = INR)
 *   DE102 — Account Identification 1 (payer account number)
 *   DE103 — Account Identification 2 (payee account number)
 *
 * HPE NonStop context:
 *   On NonStop platforms, ISO 8583 messages are processed by KMSF
 *   (Key Management Server Framework) and TANDEM message queues.
 *   This codec simulates that encoding/decoding layer.
 *
 * NOTE: This implementation covers the fields relevant to UPI transactions.
 * A full production ISO 8583 implementation would use a library like
 * jPOS (jpos.org) which handles all 128 data elements with full TLV support.
 */
@Component
public class Iso8583Codec {

    // ── MTI Constants ─────────────────────────────────────────────────────────
    public static final String MTI_TXN_REQUEST   = "0200";
    public static final String MTI_TXN_RESPONSE  = "0210";
    public static final String MTI_REVERSAL_REQ  = "0400";
    public static final String MTI_REVERSAL_RESP = "0410";
    public static final String MTI_NETWORK_REQ   = "0800";
    public static final String MTI_NETWORK_RESP  = "0810";

    // ── Processing Codes ──────────────────────────────────────────────────────
    public static final String PROC_PURCHASE  = "000000";
    public static final String PROC_REVERSAL  = "200000";
    public static final String PROC_CREDIT    = "100000";

    // ── Response Codes ────────────────────────────────────────────────────────
    public static final String RC_APPROVED           = "00";
    public static final String RC_INSUFFICIENT_FUNDS = "51";
    public static final String RC_INVALID_ACCOUNT    = "14";
    public static final String RC_SYSTEM_ERROR       = "96";
    public static final String RC_SWITCH_INOPERATIVE = "91";
    public static final String RC_DO_NOT_HONOUR      = "05";

    // ── Currency ─────────────────────────────────────────────────────────────
    public static final String CURRENCY_INR = "356";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("MMddHHmmss");

    // ── Encode: NpciRequest → ISO 8583 ───────────────────────────────────────

    /**
     * Encode an NpciRequest into an ISO 8583 message.
     * Returns an Iso8583Message containing:
     *   - MTI
     *   - Data elements map
     *   - Bitmap (hex string representation)
     *   - Raw hex string (what would go on the wire)
     */
    public Iso8583Message encode(NpciRequest req) {
        Iso8583Message msg = new Iso8583Message();

        // Set MTI based on operation
        msg.mti = switch (req.getOperation()) {
            case DEBIT    -> MTI_TXN_REQUEST;
            case CREDIT   -> MTI_TXN_REQUEST;
            case REVERSAL -> MTI_REVERSAL_REQ;
        };

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

        // ── Data Elements ─────────────────────────────────────────────────────
        // DE02 — Primary Account Number (payer account)
        if (req.getPayerAccountNumber() != null) {
            msg.dataElements.put(2, req.getPayerAccountNumber());
        }

        // DE03 — Processing Code
        msg.dataElements.put(3, switch (req.getOperation()) {
            case DEBIT    -> PROC_PURCHASE;
            case CREDIT   -> PROC_CREDIT;
            case REVERSAL -> PROC_REVERSAL;
        });

        // DE04 — Transaction Amount (12 digits, paise, no decimal)
        if (req.getAmount() != null) {
            long paise = req.getAmount()
                .multiply(new java.math.BigDecimal("100"))
                .longValue();
            msg.dataElements.put(4, String.format("%012d", paise));
        }

        // DE07 — Transmission Date/Time
        msg.dataElements.put(7, now.format(DT_FMT));

        // DE11 — System Trace Audit Number (6 digits from msgId)
        String stan = req.getMsgId() != null
            ? req.getMsgId().replaceAll("[^0-9]", "").substring(0, Math.min(6,
                req.getMsgId().replaceAll("[^0-9]", "").length()))
            : String.format("%06d", (int)(Math.random() * 999999));
        msg.dataElements.put(11, String.format("%6s", stan).replace(" ", "0"));

        // DE12 — Local Transaction Time
        msg.dataElements.put(12, now.format(TIME_FMT));

        // DE13 — Local Transaction Date
        msg.dataElements.put(13, now.format(DATE_FMT));

        // DE32 — Acquiring Institution ID (routing code, up to 11 chars)
        if (req.getNpciRoutingCode() != null) {
            msg.dataElements.put(32, req.getNpciRoutingCode());
        }

        // DE37 — Retrieval Reference Number (12 chars, right-padded)
        if (req.getRrn() != null) {
            msg.dataElements.put(37, String.format("%-12s", req.getRrn()).substring(0, 12));
        }

        // DE41 — Card Acceptor Terminal ID (payer VPA, 8 chars)
        if (req.getPayerVpa() != null) {
            String termId = req.getPayerVpa().replace("@", "_");
            msg.dataElements.put(41, String.format("%-8s", termId).substring(0, 8));
        }

        // DE42 — Card Acceptor ID (payee VPA, 15 chars)
        if (req.getPayeeVpa() != null) {
            msg.dataElements.put(42, String.format("%-15s", req.getPayeeVpa()).substring(0, 15));
        }

        // DE49 — Currency Code
        msg.dataElements.put(49, CURRENCY_INR);

        // DE102 — Account Identification 1 (payer account)
        if (req.getPayerAccountNumber() != null) {
            msg.dataElements.put(102, req.getPayerAccountNumber());
        }

        // DE103 — Account Identification 2 (payee account)
        if (req.getPayeeAccountNumber() != null) {
            msg.dataElements.put(103, req.getPayeeAccountNumber());
        }

        // Build bitmap
        msg.bitmap = buildBitmap(msg.dataElements.keySet());
        msg.rawHex  = buildRawHex(msg);

        return msg;
    }

    /**
     * Decode an ISO 8583 response into NpciResponse.
     * Parses MTI, response code (DE39), RRN (DE37), and STAN (DE11).
     */
    public NpciResponse decode(Iso8583Message msg, String txnId,
                                NpciRequest.OperationType operation) {
        String respCode = msg.dataElements.getOrDefault(39, RC_SYSTEM_ERROR);
        String rrn      = msg.dataElements.get(37);
        String stan     = msg.dataElements.get(11);

        boolean success = RC_APPROVED.equals(respCode);

        if (success) {
            NpciResponse resp = NpciResponse.success(msg.mti + "-" + stan, txnId, rrn, operation);
            return resp;
        } else {
            String message = responseCodeDescription(respCode);
            return NpciResponse.failure(msg.mti + "-" + stan, txnId, respCode, message, operation);
        }
    }

    // ── Bitmap ────────────────────────────────────────────────────────────────

    /**
     * Build a hex bitmap string from a set of present data element numbers.
     * Primary bitmap covers DE01-DE64.
     * Secondary bitmap covers DE65-DE128 (bit 1 of primary bitmap set when used).
     */
    String buildBitmap(Set<Integer> presentElements) {
        long primary   = 0L;
        long secondary = 0L;
        boolean needSecondary = presentElements.stream().anyMatch(de -> de > 64);

        if (needSecondary) primary |= (1L << 63); // set bit 1 = secondary bitmap present

        for (int de : presentElements) {
            if (de >= 1 && de <= 64) {
                primary |= (1L << (64 - de));
            } else if (de >= 65 && de <= 128) {
                secondary |= (1L << (128 - de));
            }
        }

        if (needSecondary) {
            return String.format("%016X%016X", primary, secondary);
        }
        return String.format("%016X", primary);
    }

    // ── Raw Hex ───────────────────────────────────────────────────────────────

    /**
     * Build the full ISO 8583 raw hex string:
     * [MTI][Bitmap][DE values concatenated]
     *
     * In production this would use proper TLV encoding with length prefixes.
     * For demo purposes we produce a readable hex representation.
     */
    private String buildRawHex(Iso8583Message msg) {
        StringBuilder sb = new StringBuilder();
        // MTI as ASCII hex
        sb.append(toHex(msg.mti));
        // Bitmap
        sb.append(msg.bitmap);
        // Data elements in order
        new TreeMap<>(msg.dataElements).forEach((de, value) -> {
            sb.append(toHex(value));
        });
        return sb.toString().toUpperCase();
    }

    private String toHex(String s) {
        return HexFormat.of().formatHex(s.getBytes(StandardCharsets.US_ASCII)).toUpperCase();
    }

    // ── Response code descriptions ────────────────────────────────────────────

    public static String responseCodeDescription(String code) {
        return switch (code) {
            case "00" -> "Approved";
            case "01" -> "Refer to card issuer";
            case "05" -> "Do not honour";
            case "14" -> "Invalid account number";
            case "51" -> "Insufficient funds";
            case "54" -> "Expired card";
            case "91" -> "Issuer or switch inoperative";
            case "96" -> "System malfunction";
            default   -> "Unknown response code: " + code;
        };
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    /**
     * Represents a decoded or pre-encode ISO 8583 message.
     * Contains the MTI, parsed data elements, bitmap, and raw hex.
     */
    public static class Iso8583Message {
        public String mti;
        public Map<Integer, String> dataElements = new TreeMap<>();
        public String bitmap;
        public String rawHex;

        /** Human-readable representation for logging */
        public String toPrettyString() {
            StringBuilder sb = new StringBuilder();
            sb.append("┌─── ISO 8583 Message ───────────────────────────────────┐\n");
            sb.append(String.format("│ MTI    : %-45s │\n", mti + " (" + mtiDescription(mti) + ")"));
            sb.append(String.format("│ Bitmap : %-45s │\n",
                bitmap != null && bitmap.length() > 45
                    ? bitmap.substring(0, 42) + "..." : bitmap));
            sb.append("│ Data Elements:                                          │\n");
            dataElements.forEach((de, value) -> {
                String label = deLabel(de);
                String display = value.length() > 30 ? value.substring(0, 27) + "..." : value;
                sb.append(String.format("│   DE%-3d %-18s: %-22s │\n", de, label, display));
            });
            sb.append("└─────────────────────────────────────────────────────────┘");
            return sb.toString();
        }

        private String mtiDescription(String mti) {
            return switch (mti) {
                case "0200" -> "Financial Txn Request";
                case "0210" -> "Financial Txn Response";
                case "0400" -> "Reversal Request";
                case "0410" -> "Reversal Response";
                case "0800" -> "Network Mgmt Request";
                case "0810" -> "Network Mgmt Response";
                default     -> "Unknown";
            };
        }

        private String deLabel(int de) {
            return switch (de) {
                case 2   -> "(PAN/Account)";
                case 3   -> "(Proc Code)";
                case 4   -> "(Amount)";
                case 7   -> "(Trans DateTime)";
                case 11  -> "(STAN)";
                case 12  -> "(Local Time)";
                case 13  -> "(Local Date)";
                case 32  -> "(Acq Inst ID)";
                case 37  -> "(RRN)";
                case 39  -> "(Resp Code)";
                case 41  -> "(Terminal ID)";
                case 42  -> "(Acceptor ID)";
                case 49  -> "(Currency)";
                case 102 -> "(Payer Acct)";
                case 103 -> "(Payee Acct)";
                default  -> "";
            };
        }
    }
}
