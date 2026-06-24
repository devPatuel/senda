package dev.jordi.senda.allocation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Full plan body for PUT /api/allocation/envelopes. All existing envelopes
 * for the user are replaced atomically by this list.
 */
public record EnvelopePlanRequest(
        @NotNull @Valid List<EnvelopeLineRequest> envelopes) {
}
