package vn.vinfast.vfqc.api.target.dto;

import vn.vinfast.vfqc.api.target.enums.SecretBindingLocation;

public record SecretBindingCandidateResponse(SecretBindingLocation location, String keyOrPath) {}
