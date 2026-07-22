package com.netsimx.model;

/**
 * QoS priority classes, ordered highest-priority first. Ordinal order is
 * used directly as the scheduling priority by {@link com.netsimx.simulation.QoSScheduler}.
 */
public enum PacketPriority {
    VOICE("Voice"),
    VIDEO("Video"),
    WEB("Web"),
    EMAIL("Email"),
    FILE_TRANSFER("File Transfer");

    private final String displayName;

    PacketPriority(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
