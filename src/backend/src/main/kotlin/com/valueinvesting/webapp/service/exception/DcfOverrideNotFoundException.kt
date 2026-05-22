package com.valueinvesting.webapp.service.exception

class DcfOverrideNotFoundException(
    val ticker: String,
) : RuntimeException("No DCF override for ticker $ticker")
