package com.lifetracker.sms;

import com.lifetracker.sms.dto.ResolveUnparsedSmsRequest;
import com.lifetracker.sms.dto.SmsIngestRequest;
import com.lifetracker.sms.dto.SmsIngestResponse;
import com.lifetracker.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sms")
public class SmsController {

    private final SmsService smsService;

    public SmsController(SmsService smsService) {
        this.smsService = smsService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<SmsIngestResponse> ingest(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody SmsIngestRequest request) {
        SmsIngestResponse response = smsService.ingestMessages(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/unparsed/{id}/resolve")
    public ResponseEntity<TransactionResponse> resolve(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id,
            @Valid @RequestBody ResolveUnparsedSmsRequest request) {
        TransactionResponse response = smsService.resolveUnparsedSms(userId, id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/unparsed/{id}/discard")
    public ResponseEntity<Void> discard(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID id) {
        smsService.discardUnparsedSms(userId, id);
        return ResponseEntity.noContent().build();
    }
}
