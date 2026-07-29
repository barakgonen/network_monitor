package com.example.monitor.schema;

/**
 * Shared mode/protocol/host compatibility rule for {@link InterfaceConfig#getMode()}, used both
 * at config-load time ({@link TrafficToolConfigLoader}) and at runtime reconfiguration time
 * ({@code com.example.monitor.interfaces.InterfaceRuntimeState#configure}) so the rule lives in
 * exactly one place.
 */
public final class InterfaceModeValidator {

    private InterfaceModeValidator() {
    }

    /**
     * @param context human-readable identifier of the interface being validated, prefixed onto
     *                any thrown message (e.g. {@code "interfaces[key=fruit]"} or
     *                {@code "interface fruit"})
     * @throws IllegalArgumentException if {@code mode} isn't {@code SERVER}/{@code CLIENT}, or if
     *                                   {@code mode=CLIENT} is paired with a non-TCP protocol or a
     *                                   blank host
     */
    public static void validate(String mode, String protocol, String host, String context) {
        if (mode == null || !("SERVER".equalsIgnoreCase(mode) || "CLIENT".equalsIgnoreCase(mode))) {
            throw new IllegalArgumentException(context + " mode must be SERVER or CLIENT, was: " + mode);
        }

        if ("CLIENT".equalsIgnoreCase(mode)) {
            if (!"TCP".equalsIgnoreCase(protocol)) {
                throw new IllegalArgumentException(context + " mode=CLIENT requires protocol=TCP");
            }
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException(context + " mode=CLIENT requires a non-blank host");
            }
        }
    }
}
