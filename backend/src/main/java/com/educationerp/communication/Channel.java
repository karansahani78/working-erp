package com.educationerp.communication;

/**
 * How a message reaches a person. Only {@link #IN_APP} is written to a portal today; the
 * others exist so the schema and the templates do not have to change when a provider is added.
 */
public enum Channel {
    IN_APP,
    EMAIL,
    SMS,
    PUSH,
    WHATSAPP
}
