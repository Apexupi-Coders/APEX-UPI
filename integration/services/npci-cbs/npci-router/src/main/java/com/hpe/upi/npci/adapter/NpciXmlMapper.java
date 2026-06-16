package com.hpe.upi.npci.adapter;

import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * NpciXmlMapper — generates and parses NPCI UPI XML messages.
 *
 * Real NPCI UPI uses a proprietary XML format based on ISO 20022 financial
 * messaging standards. Every request and response between PSP, NPCI, and
 * banks travels as structured XML over dedicated leased lines.
 *
 * This class implements that XML layer inside the adapter boundary.
 * The routing logic (NpciRoutingService) never sees XML — it only works
 * with NpciRequest / NpciResponse POJOs. This mapper is the only place
 * XML is produced or consumed.
 *
 * Message types implemented:
 *   ReqPay      — payment initiation request (PSP → NPCI)
 *   RespPay     — payment response (NPCI → PSP)
 *   ReqChkTxn  — transaction status check
 *   ReqRvsl     — reversal request
 *   RespRvsl    — reversal response
 *
 * XML namespace: urn:npci:upi:xsd:v2.0
 * Timestamp format: ISO 8601 with IST offset (+05:30)
 *
 * HPE NonStop context:
 *   On NonStop, this XML would be serialized into TANDEM message format
 *   and sent via Pathway ServerClass to the NPCI leased line interface.
 */
@Component
public class NpciXmlMapper {

    private static final String NAMESPACE = "urn:npci:upi:xsd:v2.0";
    private static final String ORG_ID    = "NPCI";
    private static final String API_VER   = "2.0";

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    // ── Outbound: Java → XML ──────────────────────────────────────────────────

    /**
     * Generate NPCI XML for a payment / debit / credit / reversal request.
     * The message type tag is determined by the operation type.
     *
     * Real NPCI equivalent: ReqPay, ReqCredit, ReqRvsl XML messages
     */
    public String toXml(NpciRequest req) {
        String ts = now();
        return switch (req.getOperation()) {
            case DEBIT    -> buildReqPay(req, ts);
            case CREDIT   -> buildReqCredit(req, ts);
            case REVERSAL -> buildReqRvsl(req, ts);
        };
    }

    /**
     * Generate NPCI XML for a response (success or failure).
     * Real NPCI equivalent: RespPay, RespRvsl XML messages
     */
    public String toResponseXml(NpciResponse response) {
        String ts = now();
        String tag = (response.getOperation() == NpciRequest.OperationType.REVERSAL)
            ? "RespRvsl" : "RespPay";

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="%s">
              <%s>
                <Head>
                  <Ver>%s</Ver>
                  <Ts>%s</Ts>
                  <OrgId>%s</OrgId>
                  <MsgId>%s</MsgId>
                </Head>
                <Txn>
                  <Id>%s</Id>
                  <RefId>%s</RefId>
                  <Ts>%s</Ts>
                  <NpciTxnId>%s</NpciTxnId>
                </Txn>
                <Resp>
                  <ReqMsgId>%s</ReqMsgId>
                  <Result>%s</Result>
                  <ErrCode>%s</ErrCode>
                </Resp>
              </%s>
            </Document>
            """.formatted(
                NAMESPACE, tag,
                API_VER, ts, ORG_ID,
                response.getMsgId() != null ? response.getMsgId() : "MSG-RESP-" + response.getTxnId(),
                response.getTxnId(),
                response.getRrn() != null ? response.getRrn() : "",
                ts,
                response.getNpciTransactionId() != null ? response.getNpciTransactionId() : "",
                response.getMsgId() != null ? response.getMsgId() : "",
                response.isSuccess() ? "SUCCESS" : "FAILURE",
                response.getResponseCode() != null ? response.getResponseCode() : "99",
                tag
        );
    }

    // ── Inbound: XML → Java ───────────────────────────────────────────────────

    /**
     * Parse an NPCI XML response into NpciResponse.
     *
     * Extracts: MsgId, RefId (RRN), Result, ErrCode, NpciTxnId
     * RespCode 00 or Result SUCCESS = success, anything else = failure.
     *
     * In production this would use JAXB or StAX for full XML parsing.
     * For this demo, tag extraction covers all required fields.
     */
    public NpciResponse fromXml(String xml, String txnId,
                                 NpciRequest.OperationType operation) {
        if (xml == null || xml.isBlank()) {
            return NpciResponse.failure("PARSE_ERR", txnId, "96",
                "Empty XML response", operation);
        }

        String msgId      = extractTag(xml, "MsgId");
        String rrn        = extractTag(xml, "RefId");
        String result     = extractTag(xml, "Result");
        String errCode    = extractTag(xml, "ErrCode");
        String npciTxnId  = extractTag(xml, "NpciTxnId");

        boolean success = "SUCCESS".equalsIgnoreCase(result)
            || "00".equals(errCode);

        if (success) {
            NpciResponse resp = NpciResponse.success(msgId, txnId, rrn, operation);
            resp.setNpciTransactionId(npciTxnId);
            return resp;
        } else {
            NpciResponse resp = NpciResponse.failure(
                msgId, txnId,
                errCode != null ? errCode : "99",
                "NPCI response: " + result,
                operation
            );
            return resp;
        }
    }

    // ── Private builders ──────────────────────────────────────────────────────

    private String buildReqPay(NpciRequest req, String ts) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="%s">
              <ReqPay>
                <Head>
                  <Ver>%s</Ver>
                  <Ts>%s</Ts>
                  <OrgId>%s</OrgId>
                  <MsgId>%s</MsgId>
                </Head>
                <Txn>
                  <Id>%s</Id>
                  <Note>UPI Payment</Note>
                  <RefId>%s</RefId>
                  <Ts>%s</Ts>
                  <RoutingCode>%s</RoutingCode>
                </Txn>
                <Payer addr="%s" code="00" type="ACCOUNT" seqNum="1">
                  <Info>
                    <Ac addrType="ACCOUNT">
                      <Detail name="ACCOUNTNO" value="%s"/>
                    </Ac>
                  </Info>
                  <Amount cur="INR" value="%.2f"/>
                  <Creds>
                    <Cred subType="PIN" type="PIN"/>
                  </Creds>
                </Payer>
                <Payees>
                  <Payee addr="%s" code="00" type="ACCOUNT" seqNum="1">
                    <Info>
                      <Ac addrType="ACCOUNT">
                        <Detail name="ACCOUNTNO" value="%s"/>
                      </Ac>
                    </Info>
                    <Amount cur="INR" value="%.2f"/>
                  </Payee>
                </Payees>
              </ReqPay>
            </Document>
            """.formatted(
                NAMESPACE,
                API_VER, ts, ORG_ID, req.getMsgId(),
                req.getTxnId(), req.getRrn(), ts,
                safe(req.getNpciRoutingCode()),
                safe(req.getPayerVpa()),
                safe(req.getPayerAccountNumber()),
                req.getAmount() != null ? req.getAmount().doubleValue() : 0.0,
                safe(req.getPayeeVpa()),
                safe(req.getPayeeAccountNumber()),
                req.getAmount() != null ? req.getAmount().doubleValue() : 0.0
        );
    }

    private String buildReqCredit(NpciRequest req, String ts) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="%s">
              <ReqCredit>
                <Head>
                  <Ver>%s</Ver>
                  <Ts>%s</Ts>
                  <OrgId>%s</OrgId>
                  <MsgId>%s</MsgId>
                </Head>
                <Txn>
                  <Id>%s</Id>
                  <RefId>%s</RefId>
                  <Ts>%s</Ts>
                  <RoutingCode>%s</RoutingCode>
                </Txn>
                <Payee addr="%s" code="00" type="ACCOUNT">
                  <Info>
                    <Ac addrType="ACCOUNT">
                      <Detail name="ACCOUNTNO" value="%s"/>
                    </Ac>
                  </Info>
                  <Amount cur="INR" value="%.2f"/>
                </Payee>
              </ReqCredit>
            </Document>
            """.formatted(
                NAMESPACE,
                API_VER, ts, ORG_ID, req.getMsgId(),
                req.getTxnId(), req.getRrn(), ts,
                safe(req.getNpciRoutingCode()),
                safe(req.getPayeeVpa()),
                safe(req.getPayeeAccountNumber()),
                req.getAmount() != null ? req.getAmount().doubleValue() : 0.0
        );
    }

    private String buildReqRvsl(NpciRequest req, String ts) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="%s">
              <ReqRvsl>
                <Head>
                  <Ver>%s</Ver>
                  <Ts>%s</Ts>
                  <OrgId>%s</OrgId>
                  <MsgId>%s</MsgId>
                </Head>
                <Txn>
                  <Id>%s</Id>
                  <RefId>%s</RefId>
                  <Ts>%s</Ts>
                  <RoutingCode>%s</RoutingCode>
                  <RvslReason>%s</RvslReason>
                </Txn>
                <Payer addr="%s" code="00" type="ACCOUNT">
                  <Info>
                    <Ac addrType="ACCOUNT">
                      <Detail name="ACCOUNTNO" value="%s"/>
                    </Ac>
                  </Info>
                  <Amount cur="INR" value="%.2f"/>
                </Payer>
              </ReqRvsl>
            </Document>
            """.formatted(
                NAMESPACE,
                API_VER, ts, ORG_ID, req.getMsgId(),
                req.getTxnId(), req.getRrn(), ts,
                safe(req.getNpciRoutingCode()),
                safe(req.getReversalReason()),
                safe(req.getPayerVpa()),
                safe(req.getPayerAccountNumber()),
                req.getAmount() != null ? req.getAmount().doubleValue() : 0.0
        );
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Extract content between <tag>...</tag> — first occurrence only */
    String extractTag(String xml, String tag) {
        String open  = "<"  + tag + ">";
        String close = "</" + tag + ">";
        int start = xml.indexOf(open);
        int end   = xml.indexOf(close);
        if (start < 0 || end < 0) return null;
        return xml.substring(start + open.length(), end).trim();
    }

    private String now() {
        return ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(TS_FMT);
    }

    private String safe(String s) {
        return s != null ? s : "";
    }
}
