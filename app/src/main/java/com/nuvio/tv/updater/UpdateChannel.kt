package com.nuvio.tv.updater

enum class UpdateChannel(val storedValue: String) {
    STABLE("stable"),
    BETA("beta"),
    NIGHTLY("nightly");

    companion object {
        fun fromStoredValue(value: String?): UpdateChannel? = entries.firstOrNull {
            it.storedValue.equals(value, ignoreCase = true)
        }

        fun defaultForVersion(versionName: String): UpdateChannel =
            when {
                versionName.contains("nightly", ignoreCase = true) -> NIGHTLY
                VersionUtils.isPrerelease(versionName) -> BETA
                else -> STABLE
            }
    }
}
