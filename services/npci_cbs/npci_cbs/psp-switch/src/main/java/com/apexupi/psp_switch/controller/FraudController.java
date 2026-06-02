// package com.apexupi.psp_switch.controller;

// import com.apexupi.psp_switch.fraud.FraudDecision;
// import com.apexupi.psp_switch.fraud.FraudEngine;
// import com.apexupi.psp_switch.fraud.FraudProfileService;
// import com.apexupi.psp_switch.model.PaymentRequest;
// import lombok.RequiredArgsConstructor;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// import java.util.Map;
// import java.util.concurrent.atomic.AtomicLong;

// @RestController
// @RequestMapping("/fraud")
// @RequiredArgsConstructor
// public class FraudController {

//     private final FraudProfileService profileService;
//     private final FraudEngine fraudEngine;

//     // Global counters for stats
//     private final AtomicLong totalBlocked  = new AtomicLong();
//     private final AtomicLong totalReviewed = new AtomicLong();
//     private final AtomicLong totalAllowed  = new AtomicLong();

//     @PostMapping("/blacklist/{upiId}")
//     public ResponseEntity<Map<String, String>> blacklist(@PathVariable String upiId) {
//         profileService.blacklist(upiId);
//         return ResponseEntity.ok(Map.of("status", "BLACKLISTED", "upiId", upiId));
//     }

//     @DeleteMapping("/blacklist/{upiId}")
//     public ResponseEntity<Map<String, String>> removeBlacklist(@PathVariable String upiId) {
//         profileService.removeFromBlacklist(upiId);
//         return ResponseEntity.ok(Map.of("status", "REMOVED", "upiId", upiId));
//     }

//     @GetMapping("/profile/{upiId}")
//     public ResponseEntity<Map<String, Object>> getProfile(@PathVariable String upiId) {
//         return ResponseEntity.ok(profileService.getProfile(upiId));
//     }

//     @GetMapping("/stats")
//     public ResponseEntity<Map<String, Object>> getStats() {
//         return ResponseEntity.ok(Map.of(
//             "totalBlocked",  totalBlocked.get(),
//             "totalReviewed", totalReviewed.get(),
//             "totalAllowed",  totalAllowed.get()
//         ));
//     }

//     /** Manually evaluate a payment for fraud without processing it — great for demo */
//     @PostMapping("/evaluate")
//     public ResponseEntity<FraudDecision> evaluate(@RequestBody PaymentRequest request) {
//         FraudDecision decision = fraudEngine.evaluate(request);
//         switch (decision.getVerdict()) {
//             case BLOCK  -> totalBlocked.incrementAndGet();
//             case REVIEW -> totalReviewed.incrementAndGet();
//             case ALLOW  -> totalAllowed.incrementAndGet();
//         }
//         return ResponseEntity.ok(decision);
//     }
// }