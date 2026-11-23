package com.callcontrol.app.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse
import android.telecom.TelecomManager
import com.callcontrol.app.CallControlApp
import com.callcontrol.domain.CallAction
import com.callcontrol.storage.IncomingCall
import com.callcontrol.storage.ScreeningProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Official Android call screening (Android 10+). Android binds this service only while the user has made
 * the app the "Caller ID & spam app" (ROLE_CALL_SCREENING).
 *
 * SAFETY: any failure (database, contacts, timeout, unexpected exception) ends in ALLOW.
 */
class CallControlScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val app = application as CallControlApp

        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val rawNumber = callDetails.handle?.schemeSpecificPart
        val incoming = IncomingCall(
            rawNumber = rawNumber,
            presentationRestricted = callDetails.handlePresentation == TelecomManager.PRESENTATION_RESTRICTED,
        )
        val processor = app.screeningProcessor

        app.appScope.launch {
            val result = try {
                processor.decide(incoming)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScreeningProcessor.failOpen(e::class.java.simpleName)
            }

            val deliveryError = try {
                respondToCall(callDetails, buildResponse(result.decision.action))
                null
            } catch (e: Exception) {
                e::class.java.simpleName
            }

            if (deliveryError == null && result.decision.action == CallAction.REJECT) {
                CallRejectedNotifier(this@CallControlScreeningService).show(rawNumber)
            }
            processor.record(result, deliveryError)
        }
    }

    private fun buildResponse(action: CallAction): CallResponse =
        when (action) {
            CallAction.ALLOW -> CallResponse.Builder().build()
            CallAction.REJECT -> CallResponse.Builder().setDisallowCall(true).setRejectCall(true).build()
            CallAction.SILENCE -> CallResponse.Builder().setSilenceCall(true).build()
        }
}
