package com.example.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

enum class LicenseTier(val value: String) {
    FAMILY_VIP("FAMILY_VIP"),
    PRO_BUYER("PRO_BUYER"),
    TRIAL("TRIAL");

    companion object {
        fun fromStoredValue(value: String?): LicenseTier? =
            entries.firstOrNull { it.value == value }
    }
}

enum class ProUnlockType(
    val label: String,
    val licenseTier: String?
) {
    NONE("Not Activated", null),
    TRIAL("3-Day Free Trial", LicenseTier.TRIAL.value),
    FAMILY_VIP("Family VIP Lifetime", LicenseTier.FAMILY_VIP.value),
    PRO_CODE("Pro Licensed", LicenseTier.PRO_BUYER.value)
}

data class ProLicenseState(
    val unlockType: ProUnlockType = ProUnlockType.NONE,
    val activatedCode: String = "",
    val trialStartedAtMs: Long = 0L,
    val hasSeenActivation: Boolean = false,
    /** The exact tier stored in DataStore: FAMILY_VIP, PRO_BUYER, or TRIAL. */
    val licenseTier: String? = unlockType.licenseTier,
    /** Stored separately from the tier so the activation state can be read explicitly. */
    val isUnlocked: Boolean = !licenseTier.isNullOrBlank() || unlockType != ProUnlockType.NONE
) {
    private val effectiveUnlockType: ProUnlockType
        get() = when (LicenseTier.fromStoredValue(licenseTier)) {
            LicenseTier.FAMILY_VIP -> ProUnlockType.FAMILY_VIP
            LicenseTier.PRO_BUYER -> ProUnlockType.PRO_CODE
            LicenseTier.TRIAL -> ProUnlockType.TRIAL
            null -> unlockType
        }

    /**
     * Resolves the tier for both current records and older installations that only stored
     * ProUnlockType. This keeps an existing ALT-FAMILY-VIP activation developer-authorized.
     */
    fun resolvedLicenseTier(): String? =
        LicenseTier.fromStoredValue(licenseTier)?.value ?: effectiveUnlockType.licenseTier

    fun isAccessGranted(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!isUnlocked) return false
        return when (effectiveUnlockType) {
            ProUnlockType.FAMILY_VIP, ProUnlockType.PRO_CODE -> true
            ProUnlockType.TRIAL -> remainingTrialMs(nowMs) > 0L
            ProUnlockType.NONE -> false
        }
    }

    fun isLifetimePro(): Boolean {
        if (!isUnlocked) return false
        val tier = resolvedLicenseTier()
        return tier == LicenseTier.FAMILY_VIP.value || tier == LicenseTier.PRO_BUYER.value
    }

    fun isTrialExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        effectiveUnlockType == ProUnlockType.TRIAL && remainingTrialMs(nowMs) <= 0L

    fun remainingTrialMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (effectiveUnlockType != ProUnlockType.TRIAL || trialStartedAtMs <= 0L) return 0L
        val elapsed = (nowMs - trialStartedAtMs).coerceAtLeast(0L)
        return (ProLicenseManager.TRIAL_DURATION_MS - elapsed).coerceAtLeast(0L)
    }

    fun shouldShowActivationGate(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (isLifetimePro()) return false
        if (!hasSeenActivation) return true
        return !isAccessGranted(nowMs)
    }
}

sealed interface ProActivationResult {
    data class Success(
        val unlockType: ProUnlockType,
        val normalizedCode: String,
        val licenseTier: String = unlockType.licenseTier.orEmpty()
    ) : ProActivationResult
    data object InvalidCode : ProActivationResult
}

object ProLicenseManager {
    const val MASTER_FAMILY_CODE = "ALT-FAMILY-VIP"
    const val BUYER_CODE_PREFIX = "ALT-PRO-"
    const val TRIAL_DURATION_MS = 3L * 24L * 60L * 60L * 1000L

    private const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val CHECKSUM_SALT = "ALT_PLAYER_OFFLINE_PRO_SECRET_2026"
    private val random = SecureRandom()

    fun normalizeCode(raw: String): String =
        raw.trim().uppercase(Locale.US).replace(" ", "")

    fun validateCode(rawInput: String): ProActivationResult {
        val code = normalizeCode(rawInput)
        if (code == MASTER_FAMILY_CODE) {
            return ProActivationResult.Success(
                unlockType = ProUnlockType.FAMILY_VIP,
                normalizedCode = code,
                licenseTier = LicenseTier.FAMILY_VIP.value
            )
        }
        if (isValidBuyerCode(code)) {
            return ProActivationResult.Success(
                unlockType = ProUnlockType.PRO_CODE,
                normalizedCode = code,
                licenseTier = LicenseTier.PRO_BUYER.value
            )
        }
        return ProActivationResult.InvalidCode
    }

    /** Infers the canonical tier from an activation code kept by an older installation. */
    fun licenseTierForActivatedCode(rawCode: String): String? {
        val code = normalizeCode(rawCode)
        return when {
            code == MASTER_FAMILY_CODE -> LicenseTier.FAMILY_VIP.value
            isValidBuyerCode(code) -> LicenseTier.PRO_BUYER.value
            else -> null
        }
    }

    fun isValidBuyerCode(rawCode: String): Boolean {
        val code = normalizeCode(rawCode)
        if (!code.startsWith(BUYER_CODE_PREFIX)) return false
        val suffix = code.removePrefix(BUYER_CODE_PREFIX)
        if (suffix.length != 4) return false
        if (suffix.any { it !in CODE_ALPHABET }) return false
        val payload = suffix.substring(0, 2)
        val checksum = suffix.substring(2, 4)
        return computeChecksumPair(payload) == checksum
    }

    fun generateBuyerCode(seedPayload: String? = null): String {
        val payload = if (seedPayload != null &&
            seedPayload.length == 2 &&
            seedPayload.all { it in CODE_ALPHABET }
        ) {
            seedPayload
        } else {
            buildString {
                append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)])
                append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)])
            }
        }
        return "$BUYER_CODE_PREFIX$payload${computeChecksumPair(payload)}"
    }

    fun computeChecksumPair(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$CHECKSUM_SALT:$payload".toByteArray(Charsets.UTF_8))
        val c1 = CODE_ALPHABET[(digest[0].toInt() and 0xFF) % CODE_ALPHABET.length]
        val c2 = CODE_ALPHABET[(digest[1].toInt() and 0xFF) % CODE_ALPHABET.length]
        return "$c1$c2"
    }

    fun formatRemainingTrial(remainingMs: Long): String {
        if (remainingMs <= 0L) return "Expired"
        val totalHours = remainingMs / (60L * 60L * 1000L)
        val days = totalHours / 24L
        val hours = totalHours % 24L
        return when {
            days > 0L -> "${days}d ${hours}h left"
            totalHours > 0L -> "${totalHours}h left"
            else -> {
                val mins = (remainingMs / (60L * 1000L)).coerceAtLeast(1L)
                "${mins}m left"
            }
        }
    }
}
