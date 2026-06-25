package com.bankingswitch.npci.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "UpiResponse")
public class UpiResponse {
    
    @JacksonXmlProperty(localName = "Txn")
    private Txn txn;
    
    @JacksonXmlProperty(localName = "Resp")
    private Resp resp;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Txn {
        @JacksonXmlProperty(isAttribute = true)
        private String id;
        @JacksonXmlProperty(isAttribute = true)
        private String type;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Resp {
        @JacksonXmlProperty(isAttribute = true)
        private String result;
        @JacksonXmlProperty(isAttribute = true)
        private String errCode;
        @JacksonXmlProperty(isAttribute = true)
        private String customerName; // For ReqValAdd
        @JacksonXmlProperty(isAttribute = true)
        private String balance; // For ReqBalEnq
    }
}
