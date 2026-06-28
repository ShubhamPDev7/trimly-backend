package com.trimly.backend.dto.servicerecord;

import java.util.List;

public record ServiceRecordRequest(
        String notes,
        List<String> productsUsed,
        List<String> photoUrls
) {}