package com.pspswitch.orchestrator.kafka;

/**
 * Local topic names for NPCI integration. Kept minimal to avoid wider refactors.
 */
public final class NpciTopics {
    private NpciTopics() {}

    public static final String NPCI_OUTBOUND_REQUEST = "npci.outbound.request";
    public static final String NPCI_INBOUND_RESPONSE = "npci.inbound.response";
}

