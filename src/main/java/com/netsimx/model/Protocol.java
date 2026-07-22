package com.netsimx.model;

/** Transport-layer behavior model used by the simulation engine. */
public enum Protocol {
    /** Connection-oriented: requires ACKs, retransmits on loss/timeout, guarantees delivery order. */
    TCP,
    /** Connectionless: fire-and-forget, no ACKs or retransmission, may be lost silently. */
    UDP
}
