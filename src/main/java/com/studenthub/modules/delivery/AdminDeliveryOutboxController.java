package com.studenthub.modules.delivery;

import com.studenthub.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/delivery-outbox")
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('push:manage')")
@RequiredArgsConstructor
public class AdminDeliveryOutboxController {
    private final DeliveryOutboxService outbox;
    @GetMapping public Result<List<Map<String, Object>>> recent(@RequestParam(required = false) String status) {
        return Result.success(outbox.recent(status));
    }
    @PostMapping("/retry") public Result<Boolean> retry(@RequestParam String eventKey) {
        return Result.success(outbox.retryDead(eventKey));
    }
}
