package com.callcontrol.domain

/**
 * What the device actually does with an incoming call.
 *
 * This is deliberately separate from [Mode]: two different modes (BUSY, UNAVAILABLE)
 * map to the same device action (REJECT), because Android gives an app no way to tell
 * the network *which* "reason" to present to the caller.
 */
enum class CallAction {
    /** Let the call ring normally. */
    ALLOW,

    /** Reject the call on the device (CallResponse.Builder.setDisallowCall + setRejectCall). */
    REJECT,

    /** Let the call through but silence the ringer (CallResponse.Builder.setSilenceCall, API 29+). */
    SILENCE,
}

/**
 * The four user-facing states.
 *
 * Honest limits (documented so the UI never over-promises):
 *  - [BUSY] and [UNAVAILABLE] are both a device-side rejection. What the caller hears
 *    (busy tone, voicemail, "not reachable" message, forwarding) is decided by the carrier
 *    (e& Egypt) and must be verified on a real SIM; the app cannot force it.
 *  - [SILENT] does not reject: the caller keeps hearing a normal ringback tone while the
 *    phone does not ring.
 */
enum class Mode(val action: CallAction) {
    AVAILABLE(CallAction.ALLOW),
    BUSY(CallAction.REJECT),
    UNAVAILABLE(CallAction.REJECT),
    SILENT(CallAction.SILENCE),
}
