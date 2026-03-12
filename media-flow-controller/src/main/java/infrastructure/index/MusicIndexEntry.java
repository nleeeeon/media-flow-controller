package infrastructure.index;

import domain.youtube.MusicKeyType;

public record MusicIndexEntry(long id, MusicKeyType type) {}