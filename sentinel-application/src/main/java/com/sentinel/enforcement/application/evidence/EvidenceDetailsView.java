package com.sentinel.enforcement.application.evidence;

import com.sentinel.enforcement.domain.evidence.Evidence;
import com.sentinel.enforcement.domain.evidence.EvidenceVersion;

public record EvidenceDetailsView(Evidence evidence, EvidenceVersion latestVersion) {}
