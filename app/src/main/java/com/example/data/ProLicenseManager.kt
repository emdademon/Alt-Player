package com.example.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

enum class ProUnlockType(val label: String) {
    NONE("Not Activated"),
    TRIAL("3-Day Free Trial"),
    FAMILY_VIP("Family VIP Lifetime"),
    PRO_CODE("Pro Licensed")
}

data class ProLicenseState(
    val unlockType: ProUnlockType = ProUnlockType.NONE,
    val activatedCode: String = "",
    val trialStartedAtMs: Long = 0L,
    val hasSeenActivation: Boolean = false
) {
    fun isAccessGranted(nowMs: Long = System.currentTimeMillis()): Boolean {
        return when (unlockType) {
            ProUnlockType.FAMILY_VIP, ProUnlockType.PRO_CODE -> true
            ProUnlockType.TRIAL -> remainingTrialMs(nowMs) > 0L
            ProUnlockType.NONE -> false
        }
    }

    fun isLifetimePro(): Boolean =
        unlockType == ProUnlockType.FAMILY_VIP || unlockType == ProUnlockType.PRO_CODE

    fun isTrialExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        unlockType == ProUnlockType.TRIAL && remainingTrialMs(nowMs) <= 0L

    fun remainingTrialMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (unlockType != ProUnlockType.TRIAL || trialStartedAtMs <= 0L) return 0L
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
    data class Success(val unlockType: ProUnlockType, val normalizedCode: String) : ProActivationResult
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
            return ProActivationResult.Success(ProUnlockType.FAMILY_VIP, code)
        }
        if (isValidBuyerCode(code)) {
            return ProActivationResult.Success(ProUnlockType.PRO_CODE, code)
        }
        return ProActivationResult.InvalidCode
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
