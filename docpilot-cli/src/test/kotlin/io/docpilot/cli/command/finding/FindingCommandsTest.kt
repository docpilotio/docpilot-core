package io.docpilot.cli.command.finding

import io.docpilot.cli.bootstrap.ProjectKnowledgeLoader
import io.docpilot.cli.command.CliArguments
import io.docpilot.cli.command.GenerateCommand
import io.docpilot.cli.io.ConsolePrinter
import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition
import io.docpilot.core.model.EvidenceConfidence
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.specification.DefaultSpecificationBuilder
import io.docpilot.core.specification.SpecificationBuildRequest
import io.docpilot.core.specification.finding.Finding
import io.docpilot.core.specification.finding.FindingId
import io.docpilot.core.specification.finding.FindingSeverity
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FindingCommandsTest {
    @Test
    fun `findings validates evidence fail-closed against the real project`() {
        val project = realKotlinProject()
        val evidenceId = firstNonLowConfidenceEvidenceId(project)
        val inputFile = tempFile(
            """
            [{"subjectStableId": "component:Sample", "semanticKey": "missing-error-state",
              "category": "reliability", "severity": "HIGH", "summary": "Sample has an issue.",
              "evidenceRefs": ["$evidenceId"]}]
            """.trimIndent(),
        )
        val outputFile = project.resolve("out/findings.json")

        commands().findings(cliArgs("project" to project.toString(), "input" to inputFile.toString(), "output" to outputFile.toString()))

        val decoded = FindingsJsonCodec.decodeFindings(Files.readString(outputFile))
        assertEquals(1, decoded.size)
        assertEquals(FindingSeverity.HIGH, decoded[0].severity)
        assertEquals(setOf(evidenceId), decoded[0].evidenceRefs)
    }

    @Test
    fun `findings rejects an unresolvable evidence reference with the offending index`() {
        val project = realKotlinProject()
        val inputFile = tempFile(
            """
            [{"subjectStableId": "component:Sample", "semanticKey": "k", "category": "reliability",
              "severity": "HIGH", "summary": "Sample has an issue.", "evidenceRefs": ["evidence:does-not-exist"]}]
            """.trimIndent(),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            commands().findings(cliArgs(
                "project" to project.toString(), "input" to inputFile.toString(),
                "output" to project.resolve("out/findings.json").toString(),
            ))
        }
        assertTrue(exception.message!!.contains("index 0"), exception.message!!)
    }

    @Test
    fun `known-issues and roadmap render deterministically from a hand-authored findings file`() {
        val project = createTempDirectory("docpilot-finding-project")
        val findingsFile = tempFile(FindingsJsonCodec.encodeFindings(sampleFindings()))

        val knownIssuesOutput = project.resolve("out/known-issues.md")
        commands().knownIssues(cliArgs(
            "project" to project.toString(), "findings" to findingsFile.toString(), "output" to knownIssuesOutput.toString(),
        ))
        val knownIssuesText = Files.readString(knownIssuesOutput)
        assertTrue(knownIssuesText.contains("# Known Issues Register"))
        assertTrue(knownIssuesText.contains("critical summary"))

        val roadmapOutput = project.resolve("out/roadmap.md")
        commands().roadmap(cliArgs(
            "project" to project.toString(), "findings" to findingsFile.toString(), "output" to roadmapOutput.toString(),
        ))
        assertTrue(Files.readString(roadmapOutput).contains("# Productization Roadmap"))
    }

    @Test
    fun `roadmap applies curation decisions when --decisions is supplied`() {
        val project = createTempDirectory("docpilot-finding-project")
        val findings = sampleFindings()
        val findingsFile = tempFile(FindingsJsonCodec.encodeFindings(findings))
        val decisionsFile = tempFile(FindingsJsonCodec.encodeDecisions(listOf(
            DocumentationReviewDecision(findings[0].id.value, DocumentationReviewDisposition.ACCEPTED),
            DocumentationReviewDecision(findings[1].id.value, DocumentationReviewDisposition.REJECTED),
        )))
        val output = project.resolve("out/roadmap.md")

        commands().roadmap(cliArgs(
            "project" to project.toString(), "findings" to findingsFile.toString(),
            "decisions" to decisionsFile.toString(), "output" to output.toString(),
        ))

        val text = Files.readString(output)
        assertTrue(text.contains("Curation Outcome"))
        assertTrue(text.contains("## Adopted"))
    }

    @Test
    fun `executive-summary surfaces a synthesis rejection instead of writing partial output`() {
        val project = realKotlinProject()
        val findingsFile = tempFile(FindingsJsonCodec.encodeFindings(twoDistinctSubjectFindings(project)))
        val output = project.resolve("out/executive-summary.md")

        val exception = assertFailsWith<IllegalStateException> {
            commands().executiveSummary(cliArgs(
                "project" to project.toString(), "findings" to findingsFile.toString(),
                "provider" to "fixture", "model" to "fixture-model", "output" to output.toString(),
            ))
        }
        assertTrue(exception.message!!.contains("did not produce a usable draft"), exception.message!!)
        assertTrue(Files.notExists(output))
    }

    @Test
    fun `adr-adopt renders the accepted document without a second AI call and skips on rejection`() {
        val proposal = io.docpilot.core.documentation.adr.AiProposedAdr(
            proposalId = "documentation-synthesis:fixed",
            title = "Adopt caching", context = "Context text", decision = "Decision text",
            consequences = "Consequences text", alternatives = "Alternatives text",
            citedFindingIds = listOf("component:A", "component:B"),
            record = io.docpilot.core.documentation.synthesis.SynthesisRecord(
                synthesisStableId = "documentation-synthesis:fixed",
                providerId = "fixture", model = "fixture-model",
                canonicalInputIdentity = "hash1", promptTemplateIdentity = "template@1", promptTemplateVersion = 1,
                documentType = "ARCHITECTURE_DECISION_RECORD", sourceArtifactIds = listOf("component:A", "component:B"),
                evidenceRefs = listOf("evidence:e1"), unresolvedRefs = emptyList(), contentSha256 = "hash2",
                status = io.docpilot.core.documentation.enrichment.DocumentationEnrichmentStatus.APPLIED,
                providerInvoked = true, cached = false,
            ),
        )
        val proposalFile = tempFile(FindingsJsonCodec.encodeProposal(proposal))

        val acceptedOutput = tempDirFile("adopted.md")
        commands().adrAdopt(cliArgs("proposal" to proposalFile.toString(), "decision" to "accept", "output" to acceptedOutput.toString()))
        val accepted = Files.readString(acceptedOutput)
        assertTrue(accepted.contains("Adopt caching"))
        assertTrue(accepted.contains("Decision text"))

        val out = ByteArrayOutputStream()
        val rejectingCommands = FindingCommands(printer = ConsolePrinter(PrintStream(out), PrintStream(out)))
        rejectingCommands.adrAdopt(cliArgs("proposal" to proposalFile.toString(), "decision" to "reject"))
        assertTrue(out.toString().contains("rejected"))
    }

    @Test
    fun `propose-findings surfaces a rejection instead of writing partial output`() {
        val project = realKotlinProject()
        val output = project.resolve("out/candidates.json")

        val exception = assertFailsWith<IllegalStateException> {
            commands().proposeFindings(cliArgs(
                "project" to project.toString(), "provider" to "fixture", "model" to "fixture-model",
                "output" to output.toString(),
            ))
        }
        assertTrue(exception.message!!.contains("did not produce a usable batch"), exception.message!!)
        assertTrue(Files.notExists(output))
    }

    @Test
    fun `propose-findings rejects an unknown --artifact id`() {
        val project = realKotlinProject()

        val exception = assertFailsWith<IllegalArgumentException> {
            commands().proposeFindings(cliArgs(
                "project" to project.toString(), "provider" to "fixture", "model" to "fixture-model",
                "artifact" to "component:does-not-exist", "output" to project.resolve("out/candidates.json").toString(),
            ))
        }
        assertTrue(exception.message!!.contains("Unknown component id"), exception.message!!)
    }

    @Test
    fun `propose-findings rejects a non-positive --limit`() {
        val project = realKotlinProject()

        assertFailsWith<IllegalArgumentException> {
            commands().proposeFindings(cliArgs(
                "project" to project.toString(), "provider" to "fixture", "model" to "fixture-model",
                "limit" to "0", "output" to project.resolve("out/candidates.json").toString(),
            ))
        }
    }

    @Test
    fun `propose-findings requires at least two selected components`() {
        val project = createTempDirectory("docpilot-finding-single-component")
        val sourceFile = project.resolve("src/main/kotlin/com/example/Solo.kt")
        Files.createDirectories(sourceFile.parent)
        sourceFile.writeText("package com.example\n\nclass Solo\n")

        val exception = assertFailsWith<IllegalArgumentException> {
            commands().proposeFindings(cliArgs(
                "project" to project.toString(), "provider" to "fixture", "model" to "fixture-model",
                "output" to project.resolve("out/candidates.json").toString(),
            ))
        }
        assertTrue(exception.message!!.contains("at least two components"), exception.message!!)
    }

    @Test
    fun `GenerateCommand dispatches known-issues to FindingCommands`() {
        val project = createTempDirectory("docpilot-finding-project")
        val findingsFile = tempFile(FindingsJsonCodec.encodeFindings(sampleFindings()))
        val output = project.resolve("out/known-issues.md")

        val exitCode = GenerateCommand().execute(listOf(
            "known-issues", "--project", project.toString(), "--findings", findingsFile.toString(), "--output", output.toString(),
        ))

        assertEquals(0, exitCode)
        assertTrue(Files.isRegularFile(output))
    }

    private fun commands() = FindingCommands()

    private fun cliArgs(vararg pairs: Pair<String, String>): CliArguments =
        CliArguments.parse(pairs.flatMap { (k, v) -> listOf("--$k", v) })

    private fun tempFile(content: String): Path {
        val file = Files.createTempFile("docpilot-finding-test", ".json")
        file.writeText(content)
        return file
    }

    private fun tempDirFile(name: String): Path = createTempDirectory("docpilot-finding-test-out").resolve(name)

    private fun realKotlinProject(): Path {
        val project = createTempDirectory("docpilot-finding-project")
        val otherFile = project.resolve("src/main/kotlin/com/example/Other.kt")
        Files.createDirectories(otherFile.parent)
        otherFile.writeText(
            """
            package com.example

            class Other {
                fun greet(): String = "hi"
            }
            """.trimIndent(),
        )
        val sourceFile = project.resolve("src/main/kotlin/com/example/Sample.kt")
        Files.createDirectories(sourceFile.parent)
        sourceFile.writeText(
            """
            package com.example

            class Sample {
                fun run(): String = "ok"
            }
            """.trimIndent(),
        )
        return project
    }

    private fun analyze(project: Path): ProjectSpecification {
        val analysis = ProjectKnowledgeLoader().analyze(project)
        return DefaultSpecificationBuilder().build(SpecificationBuildRequest(analysis.project, analysis.knowledge, analysis.sourceIndex))
    }

    private fun firstNonLowConfidenceEvidenceId(project: Path): String {
        val specification = analyze(project)
        return specification.evidence.first { it.confidence != EvidenceConfidence.LOW }.id
    }

    private fun sampleFindings(): List<Finding> = listOf(
        Finding(
            id = FindingId("finding:critical"), subjectStableId = "component:A", semanticKey = "k1",
            category = "reliability", severity = FindingSeverity.CRITICAL, summary = "critical summary",
            evidenceRefs = setOf("evidence:e1"),
        ),
        Finding(
            id = FindingId("finding:low"), subjectStableId = "component:B", semanticKey = "k2",
            category = "reliability", severity = FindingSeverity.LOW, summary = "low summary",
            evidenceRefs = setOf("evidence:e2"),
        ),
    )

    private fun twoDistinctSubjectFindings(project: Path): List<Finding> {
        val evidenceId = firstNonLowConfidenceEvidenceId(project)
        return listOf(
            Finding(
                id = FindingId("finding:a"), subjectStableId = "component:Sample", semanticKey = "k1",
                category = "reliability", severity = FindingSeverity.HIGH, summary = "issue one",
                evidenceRefs = setOf(evidenceId),
            ),
            Finding(
                id = FindingId("finding:b"), subjectStableId = "component:Sample.run", semanticKey = "k2",
                category = "reliability", severity = FindingSeverity.MEDIUM, summary = "issue two",
                evidenceRefs = setOf(evidenceId),
            ),
        )
    }
}
