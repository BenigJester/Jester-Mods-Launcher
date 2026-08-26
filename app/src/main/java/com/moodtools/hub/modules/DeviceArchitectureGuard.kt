package com.moodtools.hub.modules

import android.os.Build

/** The ABIs this Android system can actually run on the current device. */
internal object DeviceArchitectureGuard {
    fun supportedAbis(): Set<String> = Build.SUPPORTED_ABIS
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    fun supports(addOnAbis: Iterable<String>): Boolean =
        isDeviceArchitectureSupported(addOnAbis, supportedAbis())
}

internal fun isDeviceArchitectureSupported(
    addOnAbis: Iterable<String>,
    deviceAbis: Iterable<String>
): Boolean {
    val supportedByDevice = deviceAbis.toSet()
    return addOnAbis.any(supportedByDevice::contains)
}
