package com.nextdigital.oncall.service;

import java.util.List;

import com.nextdigital.oncall.dto.CallRecordResponse;

public interface CallSyncService {

    /**
     * Returns call records for the given range and SIP.
     * Serves from local DB when the range is already covered; fetches from Zadarma
     * otherwise.
     */
    List<CallRecordResponse> getCallRecords(String start, String end, String sip);

    /**
     * Always fetches from Zadarma, persists, and returns — ignores local coverage.
     */
    List<CallRecordResponse> syncAndGet(String start, String end, String sip);
}
