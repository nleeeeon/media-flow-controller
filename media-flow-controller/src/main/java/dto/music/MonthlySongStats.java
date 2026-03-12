package dto.music;

import java.time.Instant;

public record MonthlySongStats(
        String ym,
        String videoId,
        int plays,
        int daysActive,
        Instant firstSeen,
        Instant lastSeen
) {}