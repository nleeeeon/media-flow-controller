package dto.youtube;
import java.util.List;

public record ShortVideoCheckTarget(
        int durationSec,
        String title,
        String description,
        List<String> tags
) {}