package io.docpilot.core.specification.extraction

import io.docpilot.core.model.*
import io.docpilot.core.model.evidence.EvidenceCollection
import io.docpilot.core.model.knowledge.KnowledgeGraph
import io.docpilot.core.model.source.*
import io.docpilot.core.specification.ProjectSpecificationValidator
import io.docpilot.core.incremental.specification.DefaultSpecificationDiffer
import io.docpilot.core.incremental.specification.snapshot.JsonSpecificationSnapshotCodec
import io.docpilot.core.incremental.specification.snapshot.SpecificationSnapshotLoadResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeterministicContractExtractionEngineTest {
    @Test fun `extracts all nine evidence-backed roles and validates DIR 05`() {
        val fixture = fixture()
        val result = DeterministicContractExtractionEngine().extract(fixture.index, fixture.specification)
        val actual = result.contracts.mapTo(mutableSetOf()) { it.role }
        assertEquals(ContractRole.entries.toSet(), actual)
        val extracted = fixture.specification.copy(schemaVersion = "0.5", contracts = result.contracts,
            unresolved = result.unresolved)
        ProjectSpecificationValidator.validate(extracted)
        assertTrue(result.contracts.all { it.evidenceRefs.isNotEmpty() })
    }

    @Test fun `is invariant to file symbol annotation and collection order`() {
        val fixture = fixture()
        val engine = DeterministicContractExtractionEngine()
        val first = engine.extract(fixture.index, fixture.specification)
        val reversedIndex = fixture.index.copy(files = fixture.index.files.reversed().map { file ->
            file.copy(symbols = file.symbols.reversed().map { it.copy(annotations = it.annotations.reversed()) })
        })
        val reversedSpec = fixture.specification.copy(
            components = fixture.specification.components.reversed().map { it.copy(
                apis = it.apis.reversed(), properties = it.properties.reversed(), evidenceRefs = it.evidenceRefs.reversed().toSet()) },
            evidence = fixture.specification.evidence.reversed(), entryPoints = fixture.specification.entryPoints.reversed())
        val second = engine.extract(reversedIndex, reversedSpec)
        assertEquals(first.contracts, second.contracts)
        assertEquals(first.unresolved, second.unresolved)
    }

    @Test fun `does not infer business roles from names or simple annotations`() {
        val evidence = evidence("e:fake", "FakeRepository.kt", 1)
        val component = component("FakeRepository", "sample.FakeRepository", evidence.id,
            annotations = listOf("Repository", "Dto", "Entity", "Event"))
        val file = sourceFile("FakeRepository.kt", component, evidence, imports = emptyList())
        val result = DeterministicContractExtractionEngine().extract(index(file), specification(listOf(component), listOf(evidence)))
        assertFalse(result.contracts.any { it.role !in setOf(ContractRole.PUBLIC_API, ContractRole.CALLBACK) })
    }

    @Test fun `extracted DIR 05 round trips through snapshot 3 and produces stable diff`() {
        val fixture = fixture()
        val extraction = DeterministicContractExtractionEngine().extract(fixture.index, fixture.specification)
        val specification = fixture.specification.copy(schemaVersion = "0.5", contracts = extraction.contracts,
            unresolved = extraction.unresolved)
        val codec = JsonSpecificationSnapshotCodec()
        val bytes = codec.encode(specification)
        val loaded = codec.decode(bytes, specification.project.id) as SpecificationSnapshotLoadResult.Valid
        assertEquals(bytes, codec.encode(loaded.snapshot.specification))
        assertEquals(specification.contracts, loaded.snapshot.specification.contracts)
        assertFalse(DefaultSpecificationDiffer().diff(loaded.snapshot.specification, loaded.snapshot.specification).hasChanges)
        val changed = loaded.snapshot.specification.copy(contracts = loaded.snapshot.specification.contracts.dropLast(1))
        assertTrue(DefaultSpecificationDiffer().diff(loaded.snapshot.specification, changed).contractChanges.isNotEmpty())
    }

    @Test fun `ambiguous project type remains explicitly unresolved`() {
        val evidence = evidence("e:ambiguous", "Api.kt", 1)
        val apiOwner = component("Api", "sample.Api", evidence.id, apiType = "Result")
        val one = component("Result", "one.Result", evidence.id, withApi = false)
        val two = component("Result", "two.Result", evidence.id, withApi = false)
        val file = sourceFile("Api.kt", apiOwner, evidence)
        val result = DeterministicContractExtractionEngine().extract(index(file), specification(listOf(apiOwner, one, two), listOf(evidence)))
        val type = result.contracts.single { it.role == ContractRole.PUBLIC_API }.outputs.single().type
        assertEquals(ContractTypeKind.UNRESOLVED, type.kind)
        assertTrue(type.unresolvedRefs.isNotEmpty())
        assertTrue(result.unresolved.any { it.id in type.unresolvedRefs })
    }

    @Test fun `boundary keeps same-named parameters from distinct APIs`() {
        val evidence = evidence("e:boundary", "Boundary.kt", 1)
        val base = component("Boundary", "sample.Boundary", evidence.id,
            annotations = listOf("Repository"))
        val second = base.apis.single().copy(id = "api:sample.Boundary.other", name = "other")
        val component = base.copy(apis = base.apis + second)
        val symbolFile = sourceFile("Boundary.kt", component, evidence,
            imports = listOf(SourceImport("io.docpilot.contract.Repository")))
        val sourceSymbol = symbolFile.symbols.single()
        val file = symbolFile.copy(symbols = listOf(sourceSymbol.copy(children = sourceSymbol.children +
            sourceSymbol.children.single().copy(name = "other", id = "sample.Boundary.other", qualifiedName = "sample.Boundary.other"))))
        val result = DeterministicContractExtractionEngine().extract(index(file), specification(listOf(component), listOf(evidence)))
        val boundary = result.contracts.single { it.role == ContractRole.REPOSITORY_API }
        assertEquals(2, boundary.inputs.size)
        assertEquals(2, boundary.outputs.size)
    }

    private data class Fixture(val index: SourceIndex, val specification: ProjectSpecification)

    private fun fixture(): Fixture {
        val files = mutableListOf<SourceFile>()
        val components = mutableListOf<ComponentSpecification>()
        val evidences = mutableListOf<Evidence>()
        fun add(name: String, annotation: String?, role: String, apiType: String = "String") {
            val path = "$name.kt"; val ev = evidence("e:$role", path, 1)
            val c = component(name, "sample.$name", ev.id, annotations = annotation?.let(::listOf).orEmpty(), apiType = apiType)
            files += sourceFile(path, c, ev, annotation?.let { listOf(SourceImport(it)) }.orEmpty())
            components += c; evidences += ev
        }
        add("PublicApi", null, "public", "String")
        add("RepositoryBoundary", "io.docpilot.contract.Repository", "repository")
        add("DomainRecord", "io.docpilot.contract.DataModel", "data")
        add("WireRecord", "kotlinx.serialization.Serializable", "dto")
        add("MessageRecord", "io.docpilot.contract.Event", "event")
        add("CallbackApi", null, "callback", "Unit")
        components[5] = components[5].copy(apis = components[5].apis.map { it.copy(parameters = listOf(ParameterSpecification("listener", "(String) -> Unit")), returnType = "Unit") })
        files[5] = files[5].copy(symbols = files[5].symbols.map { symbol -> symbol.copy(children = symbol.children.map { it.copy(parameters = listOf(SourceParameter("listener", "(String) -> Unit")), type = "Unit") }) })
        add("StoredRecord", "androidx.room.Entity", "persistence")
        add("RemoteService", "retrofit2.http.GET", "external")

        val navEvidence = evidence("e:navigation", "Navigation.kt", 3, "COMPOSE_NAVIGATION_ARGUMENT")
        val navOwner = components.first()
        val navFile = SourceFile("Navigation.kt", SourceLanguage.KOTLIN, packageName = "sample", composeNavigation =
            ComposeNavigationSourceObservations(routeArguments = listOf(ComposeNavigationArgumentObservation(
                "arg:task", "taskId", ComposeNavigationArgumentSourceKind.ROUTE_PATH_PLACEHOLDER, "String", false,
                routePlaceholder = "taskId", ownerRegistrationId = "registration:task", location = SourceLocation("Navigation.kt", 3, 1, 3, 10)))))
        files += navFile; evidences += navEvidence
        val entry = EntryPointSpecification("entry-point:registration:task", "Task", EntryPointKind.COMPOSE_DESTINATION.name,
            navOwner.id, navOwner.apis.first().id, setOf(navEvidence.id))
        return Fixture(index(*files.toTypedArray()), specification(components, evidences, listOf(entry)))
    }

    private fun component(name: String, qualified: String, evidenceId: String, annotations: List<String> = emptyList(),
        apiType: String = "String", withApi: Boolean = true): ComponentSpecification {
        val apiId = "api:$qualified.call"
        val api = ApiSpecification(apiId, "call", "function", visibility = "PUBLIC", returnType = apiType,
            parameters = if (apiType == "Unit") emptyList() else listOf(ParameterSpecification("input", "String")), evidenceRefs = setOf(evidenceId))
        return ComponentSpecification("component:$qualified", name, "module:root", "CLASS", "fixture", "module:root:package:sample",
            qualified, "PUBLIC", annotations = annotations.map { it.substringAfterLast('.') }, apis = if (withApi) listOf(api) else emptyList(),
            properties = listOf(PropertySpecification("property:$qualified.value", "value", "String", evidenceRefs = setOf(evidenceId))), evidenceRefs = setOf(evidenceId))
    }

    private fun sourceFile(path: String, component: ComponentSpecification, evidence: Evidence,
        imports: List<SourceImport> = emptyList()): SourceFile {
        val api = component.apis.firstOrNull()
        return SourceFile(path, SourceLanguage.KOTLIN, "sample", imports, symbols = listOf(SourceSymbol(component.name,
            SourceSymbolKind.CLASS, SourceVisibility.PUBLIC, SourceLocation(path, 1, 1, 5, 1), component.annotations,
            children = api?.let { listOf(SourceSymbol(it.name, SourceSymbolKind.FUNCTION, SourceVisibility.PUBLIC,
                SourceLocation(path, 2, 1, 2, 20), id = it.id.removePrefix("api:"), qualifiedName = component.qualifiedName + ".call",
                parameters = it.parameters.map { p -> SourceParameter(p.name, p.type) }, type = it.returnType)) }.orEmpty(),
            id = component.id.removePrefix("component:"), qualifiedName = component.qualifiedName)))
    }

    private fun evidence(id: String, path: String, line: Int, type: String = "SYMBOL_DECLARATION") =
        Evidence(id, type, path, lineStart = line, lineEnd = line, summary = id, confidence = EvidenceConfidence.HIGH)

    private fun index(vararg files: SourceFile) = SourceIndex(files.toList(), emptyList())
    private fun specification(components: List<ComponentSpecification>, evidence: List<Evidence>, entries: List<EntryPointSpecification> = emptyList()) =
        ProjectSpecification("0.5", ProjectDescriptor("project:test", "test"), modules = listOf(ModuleSpecification("module:root", "root")),
            packages = listOf(PackageSpecification("module:root:package:sample", "sample", "sample", "module:root")),
            components = components, evidence = evidence, entryPoints = entries)
}
