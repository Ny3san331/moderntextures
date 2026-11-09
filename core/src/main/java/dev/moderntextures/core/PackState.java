package dev.moderntextures.core;

public enum PackState {
    SENT, ACCEPTED, DOWNLOADED, LOADED, DECLINED, FAILED, DISCARDED, TIMED_OUT;
    public boolean pending() { return this == SENT || this == ACCEPTED || this == DOWNLOADED; }
    public PackState receive(String status) {
        // Timeout is local; the same request can still finish downloading on the client.
        if (this == TIMED_OUT && status.equals("SUCCESSFULLY_LOADED")) return LOADED;
        if (this == LOADED && status.equals("DISCARDED")) return DISCARDED;
        if (this == LOADED && status.equals("FAILED_RELOAD")) return FAILED;
        if (!pending()) return this;
        return switch (status) {
            case "ACCEPTED" -> this == DOWNLOADED ? this : ACCEPTED;
            case "DOWNLOADED" -> DOWNLOADED;
            case "SUCCESSFULLY_LOADED" -> LOADED;
            case "DECLINED" -> DECLINED;
            case "DISCARDED" -> DISCARDED;
            case "FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD" -> FAILED;
            default -> this;
        };
    }
}
