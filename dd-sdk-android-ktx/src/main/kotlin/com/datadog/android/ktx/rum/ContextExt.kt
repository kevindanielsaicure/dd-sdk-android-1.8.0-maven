/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.rum

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import androidx.annotation.RawRes
import com.datadog.android.rum.resource.RumResourceInputStream
import java.io.InputStream

/**
 * Open an asset, returning an InputStream to read its contents, tracked as a RUM Resource.
 *
 * This provides access to files that have been bundled with an application as assets -- that is,
 * files placed into the "assets" directory.
 *
 * @param fileName The name of the asset to open.  This name can be hierarchical.
 * @param accessMode Desired access mode for retrieving the data.
 *
 * @return [InputStream] access to the asset data
 *
 * @see [AssetManager.ACCESS_UNKNOWN]
 * @see [AssetManager.ACCESS_STREAMING]
 * @see [AssetManager.ACCESS_RANDOM]
 * @see [AssetManager.ACCESS_BUFFER]
 */
fun Context.getAssetAsRumResource(
    fileName: String,
    accessMode: Int = AssetManager.ACCESS_STREAMING
): InputStream {
    return RumResourceInputStream(
        assets.open(fileName, accessMode),
        "assets://$fileName"
    )
}

/**
 * Open a data stream for reading a raw resource, tracked as a RUM Resource.
 *
 * This can only be used with resources whose value is the name of an asset file -- that is,
 * it can be used to open drawable, sound, and raw resources; it will fail on string and color
 * resources.
 *
 * @param id the resource identifier to open, as generated by the aapt tool.
 *
 * @return [InputStream] Access to the resource data.
 *
 */
@Suppress("SwallowedException")
fun Context.getRawResAsRumResource(
    @RawRes id: Int
): InputStream {
    val resName = try {
        resources.getResourceName(id)
    } catch (e: Resources.NotFoundException) {
        "res/0x${id.toString(16)}"
    }

    return RumResourceInputStream(resources.openRawResource(id), resName)
}
