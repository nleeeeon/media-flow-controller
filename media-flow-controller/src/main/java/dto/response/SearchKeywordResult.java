package dto.response;

public class SearchKeywordResult {
    private final String valueText;
    private final java.sql.Timestamp createdAt;

    public SearchKeywordResult(String valueText, java.sql.Timestamp createdAt) {
        this.valueText = valueText;
        this.createdAt = createdAt;
    }

    public String getValueText() {
        return valueText;
    }

    public java.sql.Timestamp getCreatedAt() {
        return createdAt;
    }
}
