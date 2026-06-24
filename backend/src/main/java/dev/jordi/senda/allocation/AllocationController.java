package dev.jordi.senda.allocation;

import dev.jordi.senda.common.CurrentUser;
import dev.jordi.senda.common.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/allocation")
public class AllocationController {

    private final AllocationService allocationService;

    public AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @GetMapping("/envelopes")
    public List<EnvelopeResponse> list() {
        return allocationService.list(CurrentUser.id());
    }

    @PutMapping("/envelopes")
    public List<EnvelopeResponse> savePlan(@Valid @RequestBody EnvelopePlanRequest request) {
        return allocationService.savePlan(CurrentUser.id(), request);
    }

    @PostMapping("/distribute")
    public DistributionResponse distribute(@Valid @RequestBody DistributeRequest request) {
        return allocationService.distribute(CurrentUser.id(), request);
    }

    /**
     * Controller-local handler: business-rule violations of this module map to
     * 400 with a descriptive message (same pattern as InvalidTransactionException).
     */
    @ExceptionHandler(InvalidAllocationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAllocation(InvalidAllocationException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad Request", ex.getMessage()));
    }
}
