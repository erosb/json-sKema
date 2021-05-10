package com.github.erosb.jsonschema

import java.net.URI

internal fun parseUri(raw: String): Uri {
    val rawUri = URI(raw);
    val poundIdx = raw.indexOf('#');
    return if (poundIdx == -1)
        Uri(rawUri, "")
    else
        Uri(URI(raw.substring(0, poundIdx)), raw.substring(poundIdx))
}

internal data class Uri(val toBeQueried: URI, val fragment: String)
