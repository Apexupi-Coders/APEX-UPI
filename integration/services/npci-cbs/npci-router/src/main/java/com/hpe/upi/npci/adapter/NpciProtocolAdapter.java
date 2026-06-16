package com.hpe.upi.npci.adapter;

import java.util.Map;

/**
 * NpciProtocolAdapter — protocol translation boundary for the NPCI Router.
 *
 * Responsibilities:
 *  - Convert internal UPI transaction map → NpciRequest (outbound to NPCI network)
 *  - Convert NpciResponse → internal UPI transaction map (inbound from NPCI network)
 *
 * Why this exists:
 *  Real NPCI uses ISO 8583 or a proprietary XML wire format. This adapter is the
 *  single seam where that protocol mapping lives. Swapping to a real NPCI protocol
 *  only requires a new implementation of this interface — NpciRoutingService is untouched.
 *
 * HPE NonStop context:
 *  On NonStop, this adapter would translate to/from the TANDEM message format or
 *  Pathway ServerClass requests. The interface remains the same.
 */
public interface NpciProtocolAdapter {

    /**
     * Translate an internal UPI transaction into an outbound NPCI request.
     * Called before publishing to upi.cbs.debit or upi.cbs.credit.
     */
    NpciRequest toNpciRequest(Map<String, Object> internalTxn, NpciRequest.OperationType operation);

    /**
     * Translate an NPCI response back into the internal UPI transaction map.
     * Called after receiving a confirmation from CBS.
     */
    Map<String, Object> fromNpciResponse(NpciResponse response);

    /**
     * Validate an incoming transaction before routing begins.
     * Returns a ValidationResult with success flag and optional error reason.
     */
    ValidationResult validate(Map<String, Object> internalTxn);
}
