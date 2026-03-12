package dto.youtube;

import java.util.List;

public class AgeCheckResult {
    public final List<String> allowed;
    public final List<String> ageRestricted;
    public final List<String> notEmbeddable;

    public AgeCheckResult(List<String> allowed, List<String> ageRestricted, List<String> notEmbeddable) {
        this.allowed = allowed;
        this.ageRestricted = ageRestricted;
        this.notEmbeddable = notEmbeddable;
    }
}