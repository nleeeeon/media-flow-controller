package dto.anime;

import java.util.List;

public record Theme(
        String type,          // "OP" / "ED"
        String songTitle,
        List<String> artists,
        int entryCount
) {}