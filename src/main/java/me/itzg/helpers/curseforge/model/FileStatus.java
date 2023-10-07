package me.itzg.helpers.curseforge.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * <a href="https://docs.curseforge.com/#tocS_FileStatus">Schema</a>
 */
public enum FileStatus {
    Processing,
    ChangesRequired,
    UnderReview,
    Approved,
    Rejected,
    MalwareDetected,
    Deleted,
    Archived,
    Testing,
    Released,
    ReadyForReview,
    Deprecated,
    Baking,
    AwaitingPublishing,
    FailedPublishing;

    @JsonValue
    public int toValue() {
        return this.ordinal()+1;
    }
}
