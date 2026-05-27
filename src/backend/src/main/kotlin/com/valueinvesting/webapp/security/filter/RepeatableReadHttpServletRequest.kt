package com.valueinvesting.webapp.security.filter

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.ByteArrayInputStream

/**
 * Caches the request body so filters can inspect JSON (e.g. email for rate limits)
 * without consuming the stream before it reaches the controller.
 */
internal class RepeatableReadHttpServletRequest(
    request: HttpServletRequest,
) : HttpServletRequestWrapper(request) {

    private val cachedBody: ByteArray = request.inputStream.use { it.readBytes() }

    fun cachedBody(): ByteArray = cachedBody

    override fun getInputStream(): ServletInputStream =
        CachedBodyServletInputStream(cachedBody)

    private class CachedBodyServletInputStream(
        private val body: ByteArray,
    ) : ServletInputStream() {

        private val delegate = ByteArrayInputStream(body)

        override fun read(): Int = delegate.read()

        override fun isFinished(): Boolean = delegate.available() == 0

        override fun isReady(): Boolean = true

        override fun setReadListener(readListener: ReadListener?) = Unit
    }
}
