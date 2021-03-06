package com.jdroid.android.sample.api

import com.jdroid.java.exception.UnexpectedException
import com.jdroid.java.http.parser.Parser

import java.io.InputStream
import java.net.SocketTimeoutException

class ConnectionExceptionParser : Parser {

    override fun parse(inputStream: InputStream): Any? {
        throw UnexpectedException(SocketTimeoutException())
    }

    override fun parse(input: String): Any? {
        throw UnexpectedException(SocketTimeoutException())
    }
}
