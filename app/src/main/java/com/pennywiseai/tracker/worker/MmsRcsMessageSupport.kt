package com.pennywiseai.tracker.worker

import android.util.Base64

/**
 * Pure helpers shared by the worker's proto-RCS and non-proto MMS/RCS paths.
 *
 * The provider-facing code stays in [OptimizedSmsReaderWorker]; extracting these
 * selection rules makes sender and text-part handling independently testable.
 */
internal object MmsRcsMessageSupport {

    private const val PROTO_PREFIX = "proto:"
    private const val INSERT_ADDRESS_TOKEN = "insert-address-token"

    /**
     * MMS rows with any non-blank transaction id are candidates for RCS/business
     * messages. Parser selection remains the financial-transaction safety boundary.
     */
    fun isRcsCandidate(trId: String?): Boolean = !trId.isNullOrBlank()

    fun isProtoRcs(trId: String): Boolean = trId.startsWith(PROTO_PREFIX)

    /** Preserves the legacy sender extraction behavior for proto: rows. */
    fun extractProtoSender(trId: String): String? = try {
        senderFromDecodedProtoPayload(
            String(Base64.decode(trId.removePrefix(PROTO_PREFIX), Base64.DEFAULT))
        )
    } catch (_: Exception) {
        null
    }

    /** The legacy sender pattern applied after a proto: payload has been decoded. */
    fun senderFromDecodedProtoPayload(decoded: String): String? {
        Regex("""([a-z_]+)_[a-z0-9]+_agent@rbm\.goog""").find(decoded)?.let { match ->
            return match.groupValues[1].split("_")
                .joinToString(" ") { token ->
                    if (token.isNotEmpty()) token.substring(0, 1).uppercase() + token.substring(1) else token
                }
        }
        Regex("""[\x12\x1a][\x00-\x20]([A-Za-z][A-Za-z\s]+)""").find(decoded)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.length in 4..49) return name
        }
        return null
    }

    /**
     * Reject provider placeholders before passing an MMS address to the parser factory.
     * Valid telephone numbers remain allowed here; unsupported senders are discarded by
     * the worker's normal parser lookup.
     */
    fun validMmsSender(address: String?): String? = address?.trim()?.takeIf { candidate ->
        candidate.isNotEmpty() && !candidate.equals(INSERT_ADDRESS_TOKEN, ignoreCase = true)
    }

    /** Returns inline text only for a usable text MMS part. */
    fun inlineTextPart(contentType: String?, text: String?): String? =
        text?.takeIf { it.isNotEmpty() && (contentType?.startsWith("text/", ignoreCase = true) == true ||
                contentType.equals("application/smil", ignoreCase = true)) }
}
