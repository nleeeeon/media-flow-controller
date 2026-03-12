package dto.anime;

import java.util.List;


public record AnimeOpEd(
        String animeName,
        List<Theme> themes
) {}