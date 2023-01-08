package me.itzg.helpers.curseforge.model;

import com.fasterxml.jackson.annotation.JsonValue;

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
