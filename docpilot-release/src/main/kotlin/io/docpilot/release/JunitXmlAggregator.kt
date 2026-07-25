package io.docpilot.release

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

public class JunitXmlAggregator {
    public fun aggregate(
        roots: List<Path>,
        executionStartedMillis: Long,
        cached: Boolean = false,
    ): TestAggregate {
        val files = roots
            .flatMap { root ->
                if (!Files.isDirectory(root)) emptyList()
                else Files.walk(root).use { stream ->
                    stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".xml") }
                        .map { it.toAbsolutePath().normalize() }
                        .toList()
                }
            }
            .distinct()
            .sortedBy { it.toString().replace('\\', '/') }

        require(files.isNotEmpty()) { "No JUnit XML files found." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

        var tests = 0
        var failures = 0
        var errors = 0
        var skipped = 0
        var fresh = true
        files.forEach { path ->
            fresh = fresh && Files.getLastModifiedTime(path).toMillis() >= executionStartedMillis
            val root = Files.newInputStream(path).use { factory.newDocumentBuilder().parse(it).documentElement }
            require(root.tagName == "testsuite" || root.tagName == "testsuites") {
                "Unsupported JUnit XML root: ${root.tagName}"
            }
            if (root.tagName == "testsuite") {
                tests += root.intAttribute("tests")
                failures += root.intAttribute("failures")
                errors += root.intAttribute("errors")
                skipped += root.intAttribute("skipped")
            } else {
                val suites = root.childNodes
                for (index in 0 until suites.length) {
                    val node = suites.item(index)
                    if (node.nodeName == "testsuite") {
                        val element = node as org.w3c.dom.Element
                        tests += element.intAttribute("tests")
                        failures += element.intAttribute("failures")
                        errors += element.intAttribute("errors")
                        skipped += element.intAttribute("skipped")
                    }
                }
            }
        }
        return TestAggregate(files.size, tests, failures, errors, skipped, fresh, cached)
    }

    private fun org.w3c.dom.Element.intAttribute(name: String): Int {
        val raw = getAttribute(name).ifBlank { "0" }
        return raw.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("Invalid JUnit $name: $raw")
    }
}
