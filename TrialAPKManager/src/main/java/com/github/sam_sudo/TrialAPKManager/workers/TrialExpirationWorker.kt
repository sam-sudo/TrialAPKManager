package com.github.sam_sudo.TrialAPKManager.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.github.sam_sudo.TrialAPKManager.security.LicenseStatus
import com.github.sam_sudo.TrialAPKManager.security.TrialAPKManager

class TrialExpirationWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    val TAG = "TrialExpirationWorker"

    override fun doWork(): Result {
        Log.w(TAG,"DO WORK!")
        if (isTrialExpired()) {
            // 🔹 Actualizamos directamente el estado en TrialAPKManager
            TrialAPKManager.updateLicenseStatus(LicenseStatus.TRIAL_EXPIRED)
        }
        return Result.success()
    }

    private fun isTrialExpired(): Boolean {
        val startTime = TrialAPKManager.getTrialStartTime() ?: return true
        val trialDurationMs = inputData.getLong("trial_duration", 0L)
        val now = System.currentTimeMillis()
        return now >= startTime + trialDurationMs
    }
}
