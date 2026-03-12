package dto.youtube;

import java.time.Instant;

public class VideoInput {
    public String videoId;
    public String title;
    public String subtitle;   // 例: channel/titleUrl/shorts由来名 など
    public Instant watchedAt; // 無い場合は null
  }