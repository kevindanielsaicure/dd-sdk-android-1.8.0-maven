/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.coroutine

import io.opentracing.Span
import kotlinx.coroutines.CoroutineScope

/**
 * An object that implements both [Span] and [CoroutineScope].
 */
interface CoroutineScopeSpan : CoroutineScope, Span
