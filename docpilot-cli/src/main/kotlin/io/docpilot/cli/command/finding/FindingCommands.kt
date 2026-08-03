package io.docpilot.cli.command.finding

import io.docpilot.cli.bootstrap.CliBootstrap
import io.docpilot.cli.bootstrap.ProjectKnowledgeLoader
import io.docpilot.cli.command.CliArguments
import io.docpilot.cli.io.ConsolePrinter
import io.docpilot.cli.io.OutputWriter
import io.docpilot.cli.logging.ProjectLogSession
import io.docpilot.core.document.DocumentRenderer
import io.docpilot.core.documentation.adr.AdrProposalRequestBuilder
import io.docpilot.core.documentation.adr.AiProposedAdrAdoption
import io.docpilot.core.documentation.adr.AiProposedAdrBuilder
import io.docpilot.core.documentation.advisory.ExecutiveSummaryBuilder
import io.docpilot.core.documentation.advisory.ExecutiveSummaryMarkdownRenderer
import io.docpilot.core.documentation.advisory.ExecutiveSummaryRequestBuilder
import io.docpilot.core.documentation.advisory.KnownIssuesRegisterBuilder
import io.docpilot.core.documentation.advisory.KnownIssuesRegisterMarkdownRenderer
import io.docpilot.core.documentation.backlog.ProductizationRoadmapBuilder
import io.docpilot.core.documentation.backlog.ProductizationRoadmapCurator
import io.docpilot.core.documentation.backlog.ProductizationRoadmapMarkdownRenderer
import io.docpilot.core.documentation.backlog.renderCuration
import io.docpilot.core.documentation.finding.FindingProposalBuilder
import io.docpilot.core.documentation.finding.FindingProposalRequestBuilder
import io.docpilot.core.documentation.finding.ProposedFinding
import io.docpilot.core.documentation.synthesis.SynthesisEngine
import io.docpilot.core.documentation.synthesis.SynthesisSource
import io.docpilot.core.incremental.specification.review.DocumentationReviewDecision
import io.docpilot.core.incremental.specification.review.DocumentationReviewDisposition
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.specification.DefaultSpecificationBuilder
import io.docpilot.core.specification.SpecificationBuildRequest
import io.docpilot.core.specification.finding.Finding
import io.docpilot.core.specification.finding.FindingFactory
import io.docpilot.core.specification.finding.FindingSeverity
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * RFC-0083: CLI wiring for RFC-0078 (Finding), RFC-0079 (Synthesis/Advisory tier), RFC-0080
 * (Executive Summary/Known Issues Register), RFC-0081 (Productization Roadmap/curation), and
 * RFC-0082 (AI-Proposed ADR) — all of which are core-library-only with no prior CLI entry point.
 */
internal class FindingCommands(
    private val bootstrap: CliBootstrap = CliBootstrap(),
    private val knowledgeLoader: ProjectKnowledgeLoader = ProjectKnowledgeLoader(),
    private val writer: OutputWriter = OutputWriter(),
    private val printer: ConsolePrinter = ConsolePrinter(),
) {
    fun findings(args: CliArguments) {
        val project = Path.of(args.required("project"))
        val log = ProjectLogSession.create(project)
        log.info("Findings validation started for ${project.toAbsolutePath().normalize()}.")
        val specification = specification(project)
        val inputs = FindingsJsonCodec.decodeFindingInputs(readFile(args.required("input")))
        require(inputs.isNotEmpty()) { "Findings input must contain at least one entry." }
        val findings = inputs.mapIndexed { index, input ->
            try {
                FindingFactory.create(
                    specification = specification,
                    subjectStableId = input.subjectStableId,
                    semanticKey = input.semanticKey,
                    category = input.category,
                    severity = parseSeverity(input.severity, index),
                    summary = input.summary,
                    evidenceRefs = input.evidenceRefs,
                    unresolvedRefs = input.unresolvedRefs,
                )
            } catch (exception: IllegalArgumentException) {
                throw IllegalArgumentException("Finding input at index $index is invalid: ${exception.message}")
            }
        }
        emit(FindingsJsonCodec.encodeFindings(findings), args.required("output"))
        log.info("Findings validation completed: ${findings.size} Finding(s) validated.")
    }

    fun proposeFindings(args: CliArguments) {
        val project = Path.of(args.required("project"))
        val log = ProjectLogSession.create(project)
        log.info("Finding proposal drafting started for ${project.toAbsolutePath().normalize()}.")
        val specification = specification(project)
        val providerId = args.required("provider")
        val model = args.required("model")
        val limit = args.optional("limit")?.let {
            it.toIntOrNull()?.takeIf { n -> n > 0 } ?: throw IllegalArgumentException("--limit must be a positive integer.")
        } ?: DEFAULT_PROPOSAL_LIMIT
        val requestedIds = args.optional("artifact")?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
        val byId = specification.components.associateBy { it.id }
        val selected = if (requestedIds != null) {
            val unknown = requestedIds - byId.keys
            require(unknown.isEmpty()) { "Unknown component id(s): ${unknown.sorted().joinToString()}" }
            requestedIds.map { byId.getValue(it) }.sortedBy { it.id }
        } else {
            specification.components.filter { it.evidenceRefs.isNotEmpty() }.sortedBy { it.id }.take(limit)
        }
        require(selected.size >= 2) {
            "Finding proposal requires at least two components with Evidence; found ${selected.size}."
        }

        val sources = selected.map { component ->
            SynthesisSource(
                artifactId = component.id,
                sourceKind = component.kind,
                sourceModelStableIds = listOf(component.id),
                evidenceRefs = component.evidenceRefs.sorted().take(MAX_EVIDENCE_REFS_PER_COMPONENT),
            )
        }
        // Deliberately compact: id/kind/name/count only, never full raw Evidence text — see RFC-0084's
        // "Part A" design notes on the oversized-prompt bug found while smoke-testing RFC-0083.
        val canonicalFacts = selected.joinToString("\n") { "${it.id} | ${it.kind} | ${it.name} | evidenceCount=${it.evidenceRefs.size}" }

        val engine = SynthesisEngine(log.logging(bootstrap.createProvider(providerId)))
        val request = FindingProposalRequestBuilder.request(sources, canonicalFacts, providerId, model)
        val result = engine.synthesize(specification, request)
        val proposals = FindingProposalBuilder.build(result, allowedSubjectIds = selected.mapTo(mutableSetOf()) { it.id })
            ?: throw IllegalStateException(
                "Finding proposal drafting did not produce a usable batch (${result.record.status}${result.record.diagnostic?.let { ": $it" }.orEmpty()}).",
            )

        val byComponentId = selected.associateBy { it.id }
        val usedKeys = mutableSetOf<String>()
        val inputs = proposals.map { proposal ->
            val component = byComponentId.getValue(proposal.subjectStableId)
            FindingInput(
                subjectStableId = proposal.subjectStableId,
                semanticKey = uniqueSemanticKey(proposal, usedKeys),
                category = proposal.category,
                severity = proposal.severity.name,
                summary = proposal.summary,
                evidenceRefs = component.evidenceRefs,
                unresolvedRefs = emptySet(),
            )
        }
        emit(FindingsJsonCodec.encodeFindingInputs(inputs), args.required("output"))
        printer.content("Proposed ${inputs.size} candidate Finding(s).")
        log.info("Finding proposal drafting completed: ${inputs.size} candidate(s).")
    }

    fun knownIssues(args: CliArguments) {
        val project = Path.of(args.required("project"))
        val log = ProjectLogSession.create(project)
        log.info("Known Issues Register generation started for ${project.toAbsolutePath().normalize()}.")
        val findings = readFindings(args.required("findings"))
        val document = KnownIssuesRegisterBuilder.build(findings)
        emit(KnownIssuesRegisterMarkdownRenderer.render(document, findings), args.required("output"))
        log.info("Known Issues Register generation completed.")
    }

    fun roadmap(args: CliArguments) {
        val project = Path.of(args.required("project"))
        val log = ProjectLogSession.create(project)
        log.info("Productization Roadmap generation started for ${project.toAbsolutePath().normalize()}.")
        val findings = readFindings(args.required("findings"))
        val document = ProductizationRoadmapBuilder.build(findings)
        val rendered = args.optional("decisions")?.let { decisionsPath ->
            val decisions = FindingsJsonCodec.decodeDecisions(readFile(decisionsPath))
            ProductizationRoadmapMarkdownRenderer.renderCuration(ProductizationRoadmapCurator.apply(document, decisions))
        } ?: ProductizationRoadmapMarkdownRenderer.render(document)
        emit(rendered, args.required("output"))
        log.info("Productization Roadmap generation completed.")
    }

    fun executiveSummary(args: CliArguments) {
        val project = Path.of(args.required("project"))
        val log = ProjectLogSession.create(project)
        log.info("Executive Summary synthesis started for ${project.toAbsolutePath().normalize()}.")
        val specification = specification(project)
        val findings = readFindings(args.required("findings"))
        val providerId = args.required("provider")
        val model = args.required("model")
        val engine = SynthesisEngine(log.logging(bootstrap.createProvider(providerId)))
        val request = ExecutiveSummaryRequestBuilder.request(synthesisSources(findings), canonicalNarrative(findings), providerId, model)
        val result = engine.synthesize(specification, request)
        val document = ExecutiveSummaryBuilder.build(result) ?: throw IllegalStateException(
            "Executive Summary synthesis did not produce a usable draft (${result.record.status}${result.record.diagnostic?.let { ": $it" }.orEmpty()}).",
        )
        emit(ExecutiveSummaryMarkdownRenderer.render(document), args.required("output"))
        log.info("Executive Summary synthesis completed.")
    }

    fun adrPropose(args: CliArguments) {
        val project = Path.of(args.required("project"))
        val log = ProjectLogSession.create(project)
        log.info("AI-Proposed ADR drafting started for ${project.toAbsolutePath().normalize()}.")
        val specification = specification(project)
        val findings = readFindings(args.required("findings"))
        val providerId = args.required("provider")
        val model = args.required("model")
        val engine = SynthesisEngine(log.logging(bootstrap.createProvider(providerId)))
        val request = AdrProposalRequestBuilder.request(synthesisSources(findings), canonicalNarrative(findings), providerId, model)
        val result = engine.synthesize(specification, request)
        val proposal = AiProposedAdrBuilder.build(result) ?: throw IllegalStateException(
            "AI-Proposed ADR drafting did not produce a usable draft (${result.record.status}${result.record.diagnostic?.let { ": $it" }.orEmpty()}).",
        )
        emit(FindingsJsonCodec.encodeProposal(proposal), args.required("output"))
        printer.content("Proposal ID: ${proposal.proposalId}")
        log.info("AI-Proposed ADR drafting completed: ${proposal.proposalId}.")
    }

    fun adrAdopt(args: CliArguments) {
        val log = args.optional("project")?.let { ProjectLogSession.create(Path.of(it)) }
        val proposal = FindingsJsonCodec.decodeProposal(readFile(args.required("proposal")))
        val disposition = when (args.required("decision").trim().lowercase()) {
            "accept" -> DocumentationReviewDisposition.ACCEPTED
            "reject" -> DocumentationReviewDisposition.REJECTED
            else -> throw IllegalArgumentException("--decision must be 'accept' or 'reject'.")
        }
        val decision = DocumentationReviewDecision(
            targetId = proposal.proposalId, disposition = disposition, comment = args.optional("comment"),
        )
        log?.info("ADR proposal ${proposal.proposalId} decision recorded: $disposition.")
        if (disposition == DocumentationReviewDisposition.REJECTED) {
            printer.content("Proposal rejected; no document produced.")
            return
        }
        val document = AiProposedAdrAdoption.adopt(proposal, decision)
        emit(DocumentRenderer().render(document), args.required("output"))
    }

    private fun specification(project: Path): ProjectSpecification {
        val analysis = knowledgeLoader.analyze(project)
        return DefaultSpecificationBuilder().build(SpecificationBuildRequest(analysis.project, analysis.knowledge, analysis.sourceIndex))
    }

    private fun readFindings(path: String): List<Finding> {
        val decoded = FindingsJsonCodec.decodeFindings(readFile(path))
        require(decoded.isNotEmpty()) { "Findings file must contain at least one Finding." }
        return decoded
    }

    private fun readFile(path: String): String = Files.readString(Path.of(path), StandardCharsets.UTF_8)

    private fun synthesisSources(findings: List<Finding>): List<SynthesisSource> =
        findings.groupBy { it.subjectStableId }.map { (subjectStableId, group) ->
            SynthesisSource(
                artifactId = subjectStableId,
                sourceKind = group.first().category,
                sourceModelStableIds = listOf(subjectStableId),
                evidenceRefs = group.flatMap { it.evidenceRefs }.distinct(),
                unresolvedRefs = group.flatMap { it.unresolvedRefs }.distinct(),
            )
        }.sortedBy { it.artifactId }

    private fun canonicalNarrative(findings: List<Finding>): String =
        findings.sortedBy { it.id.value }.joinToString("\n") { "${it.severity} | ${it.category} | ${it.summary}" }

    private fun parseSeverity(value: String, index: Int): FindingSeverity =
        runCatching { FindingSeverity.valueOf(value.trim().uppercase()) }.getOrElse {
            throw IllegalArgumentException(
                "Finding input at index $index has invalid severity '$value'. Expected one of: " +
                    FindingSeverity.entries.joinToString { it.name },
            )
        }

    private fun emit(content: String, output: String) {
        val path = writer.write(Path.of(output), content)
        printer.success("Generated $path")
    }

    private fun uniqueSemanticKey(proposal: ProposedFinding, used: MutableSet<String>): String {
        val base = "${proposal.category.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}-${sha256(proposal.summary).take(8)}"
        var key = base
        var suffix = 2
        while (!used.add(key)) { key = "$base-$suffix"; suffix++ }
        return key
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        const val DEFAULT_PROPOSAL_LIMIT = 20
        const val MAX_EVIDENCE_REFS_PER_COMPONENT = 20
    }
}
