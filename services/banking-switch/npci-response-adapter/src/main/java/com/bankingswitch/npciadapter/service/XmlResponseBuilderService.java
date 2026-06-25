package com.bankingswitch.npciadapter.service;

import com.bankingswitch.npciadapter.model.NpciCallbackEvent;
import org.springframework.stereotype.Service;

@Service
public class XmlResponseBuilderService {

    public String buildResponseXml(NpciCallbackEvent event) {
        String result = "SUCCESS".equals(event.getStatus()) ? "SUCCESS" : "FAILURE";
        String errCode = event.getErrorCode() != null ? event.getErrorCode() : "";
        String balStr = event.getBalance() != null ? event.getBalance().toString() : "0.0";
        
        return String.format(
            "<%s>\n" +
            "  <Txn id=\"%s\" type=\"%s\"/>\n" +
            "  <Resp result=\"%s\" errCode=\"%s\"/>\n" +
            "  <BalDetail settlementAmount=\"%s\" settAmount=\"%s\"/>\n" +
            "</%s>",
            event.getTxnType(), event.getTxnId(), event.getTxnType(), result, errCode, balStr, balStr, event.getTxnType()
        );
    }
}
