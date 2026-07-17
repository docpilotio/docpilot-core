package io.docpilot.cli.io

import java.io.PrintStream

class ConsolePrinter(
    private val out: PrintStream = System.out,
    private val err: PrintStream = System.err,
) {
    fun content(value: String) = out.println(value)

    fun success(message: String) = out.println("[OK] $message")

    fun error(message: String) = err.println("[ERROR] $message")
}
