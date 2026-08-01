package io.docpilot.core.incremental.specification.snapshot

import io.docpilot.core.model.*
import io.docpilot.core.specification.ProjectSpecificationValidator

public class JsonSpecificationSnapshotCodec {
    public fun encode(specification: ProjectSpecification): String {
        val formatVersion = when (specification.schemaVersion) {
            SpecificationSnapshotFormat.LEGACY_DIR_SCHEMA_VERSION -> SpecificationSnapshotFormat.LEGACY_VERSION
            SpecificationSnapshotFormat.DIR_0_4_SCHEMA_VERSION -> SpecificationSnapshotFormat.DIR_0_4_VERSION
            SpecificationSnapshotFormat.DIR_0_5_SCHEMA_VERSION -> SpecificationSnapshotFormat.DIR_0_5_VERSION
            else -> require(false) { "Unsupported DIR schema version: ${specification.schemaVersion}" }
        }
        val payload = encodePayload(specification)
        val integrity = SpecificationSnapshotIntegrity.sha256(payload)
        return buildString {
            append("{\n")
            append("  \"snapshotFormatVersion\": ").append(formatVersion).append(",\n")
            append("  \"dirSchemaVersion\": ").appendJsonString(specification.schemaVersion).append(",\n")
            append("  \"projectIdentity\": {\"projectId\": ").appendJsonString(specification.project.id).append("},\n")
            append("  \"specification\": ").append(payload).append(",\n")
            append("  \"integrity\": {\"algorithm\": \"SHA-256\", \"payloadSha256\": ")
                .appendJsonString(integrity).append("}\n")
            append("}\n")
        }
    }

    public fun decode(value: String, expectedProjectId: String): SpecificationSnapshotLoadResult {
        return try {
        val root = JsonParser(value).parseRootObject()
        val version = root.requiredLong("snapshotFormatVersion").toIntExact("snapshotFormatVersion")
        if (version !in setOf(SpecificationSnapshotFormat.LEGACY_VERSION, SpecificationSnapshotFormat.DIR_0_4_VERSION, SpecificationSnapshotFormat.DIR_0_5_VERSION)) {
            return SpecificationSnapshotLoadResult.Invalid(
                SnapshotValidationFailure.UNSUPPORTED_VERSION,
                "Unsupported specification snapshot format version: $version",
            )
        }
        val dirVersion = root.requiredString("dirSchemaVersion")
        val expectedDirVersion = when (version) {
            SpecificationSnapshotFormat.LEGACY_VERSION -> SpecificationSnapshotFormat.LEGACY_DIR_SCHEMA_VERSION
            SpecificationSnapshotFormat.DIR_0_4_VERSION -> SpecificationSnapshotFormat.DIR_0_4_SCHEMA_VERSION
            else -> SpecificationSnapshotFormat.DIR_0_5_SCHEMA_VERSION
        }
        if (dirVersion != expectedDirVersion) {
            return SpecificationSnapshotLoadResult.Invalid(
                SnapshotValidationFailure.SCHEMA_MISMATCH,
                "Unsupported DIR schema version: $dirVersion",
            )
        }
        val identity = root.requiredObject("projectIdentity").requiredString("projectId")
        if (identity != expectedProjectId) {
            return SpecificationSnapshotLoadResult.Invalid(
                SnapshotValidationFailure.PROJECT_MISMATCH,
                "Snapshot project '$identity' does not match '$expectedProjectId'.",
            )
        }
        val specificationValue = root.requiredObject("specification")
        val specification = decodeSpecification(specificationValue)
        if (specification.schemaVersion != dirVersion) {
            return SpecificationSnapshotLoadResult.Invalid(
                SnapshotValidationFailure.SCHEMA_MISMATCH,
                "Snapshot envelope and specification DIR schema versions differ.",
            )
        }
        if (specification.project.id != identity) {
            return SpecificationSnapshotLoadResult.Invalid(
                SnapshotValidationFailure.PROJECT_MISMATCH,
                "Snapshot identity and specification project id differ.",
            )
        }
        try {
            ProjectSpecificationValidator.validate(specification)
        } catch (e: IllegalArgumentException) {
            return SpecificationSnapshotLoadResult.Invalid(
                SnapshotValidationFailure.INVALID_SPECIFICATION,
                e.message ?: "Invalid ProjectSpecification",
            )
        }
        val integrityObject = root.requiredObject("integrity")
        val algorithm = integrityObject.requiredString("algorithm")
        val checksum = integrityObject.requiredString("payloadSha256")
        if (algorithm != "SHA-256") {
            return SpecificationSnapshotLoadResult.Invalid(
                SnapshotValidationFailure.CORRUPTED,
                "Unsupported integrity algorithm: $algorithm",
            )
        }
        val canonicalPayload = encodePayload(specification)
        val actual = SpecificationSnapshotIntegrity.sha256(canonicalPayload)
        if (actual != checksum) {
            return SpecificationSnapshotLoadResult.Invalid(
                SnapshotValidationFailure.INTEGRITY_MISMATCH,
                "Specification snapshot integrity check failed.",
            )
        }
        SpecificationSnapshotLoadResult.Valid(
            StoredSpecificationSnapshot(
                snapshotFormatVersion = version,
                dirSchemaVersion = dirVersion,
                projectIdentity = SnapshotProjectIdentity(identity),
                specification = specification,
                integrity = SnapshotIntegrity(algorithm, checksum),
            ),
        )
    } catch (e: Exception) {
        SpecificationSnapshotLoadResult.Invalid(
            SnapshotValidationFailure.CORRUPTED,
            e.message ?: "Corrupted specification snapshot.",
        )
        }
    }

    internal fun encodePayload(specification: ProjectSpecification): String = buildString {
        append('{')
        field("schemaVersion", specification.schemaVersion)
        append(','); field("project", encodeProject(specification.project), raw = true)
        append(','); field("modules", array(specification.modules.sortedBy { it.id }.map(::encodeModule)), raw = true)
        append(','); field("packages", array(specification.packages.sortedBy { it.id }.map(::encodePackage)), raw = true)
        append(','); field("components", array(specification.components.sortedBy { it.id }.map(::encodeComponent)), raw = true)
        append(','); field("relationships", array(specification.relationships.sortedBy { it.id }.map(::encodeRelationship)), raw = true)
        append(','); field("evidence", array(specification.evidence.sortedBy { it.id }.map(::encodeEvidence)), raw = true)
        append(','); field("unresolved", array(specification.unresolved.sortedBy { it.id }.map(::encodeUnresolved)), raw = true)
        if (specification.schemaVersion in setOf(SpecificationSnapshotFormat.DIR_0_4_SCHEMA_VERSION, SpecificationSnapshotFormat.DIR_0_5_SCHEMA_VERSION)) {
            append(','); field("features", array(specification.features.sortedBy { it.id }.map(::encodeFeature)), raw = true)
            append(','); field("entryPoints", array(specification.entryPoints.sortedBy { it.id }.map(::encodeEntryPoint)), raw = true)
            append(','); field(
                "scenarios",
                array(specification.scenarios.sortedWith(compareBy({ it.featureId }, { it.id })).map(::encodeScenario)),
                raw = true,
            )
        }
        if (specification.schemaVersion == SpecificationSnapshotFormat.DIR_0_5_SCHEMA_VERSION) {
            append(','); field("contracts", array(specification.contracts.sortedBy { it.id }.map(::encodeContract)), raw = true)
        }
        append('}')
    }

    private fun encodeProject(v: ProjectDescriptor) = obj(
        "id" to str(v.id), "name" to str(v.name), "description" to nullable(v.description),
        "platforms" to strings(v.platforms), "languages" to strings(v.languages), "buildSystems" to strings(v.buildSystems),
    )
    private fun encodeModule(v: ModuleSpecification) = obj(
        "id" to str(v.id), "name" to str(v.name), "path" to nullable(v.path), "description" to nullable(v.description),
        "sourceSets" to strings(v.sourceSets), "evidenceRefs" to strings(v.evidenceRefs),
    )
    private fun encodePackage(v: PackageSpecification) = obj(
        "id" to str(v.id), "name" to str(v.name), "qualifiedName" to str(v.qualifiedName), "moduleId" to str(v.moduleId),
        "description" to nullable(v.description), "evidenceRefs" to strings(v.evidenceRefs),
    )
    private fun encodeComponent(v: ComponentSpecification) = obj(
        "id" to str(v.id), "name" to str(v.name), "moduleId" to str(v.moduleId), "kind" to str(v.kind), "role" to str(v.role),
        "packageId" to nullable(v.packageId), "qualifiedName" to nullable(v.qualifiedName), "visibility" to nullable(v.visibility),
        "modifiers" to strings(v.modifiers), "annotations" to array(v.annotations.map(::str)),
        "typeParameters" to array(v.typeParameters.map(::str)), "superTypes" to array(v.superTypes.map(::str)),
        "responsibilities" to array(v.responsibilities.map(::str)), "dependencyIds" to strings(v.dependencyIds),
        "apis" to array(v.apis.sortedBy { it.id }.map(::encodeApi)), "properties" to array(v.properties.sortedBy { it.id }.map(::encodeProperty)),
        "evidenceRefs" to strings(v.evidenceRefs),
    )
    private fun encodeApi(v: ApiSpecification) = obj(
        "id" to str(v.id), "name" to str(v.name), "kind" to str(v.kind), "signature" to nullable(v.signature),
        "visibility" to nullable(v.visibility), "receiverType" to nullable(v.receiverType), "returnType" to nullable(v.returnType),
        "parameters" to array(v.parameters.map(::encodeParameter)), "modifiers" to strings(v.modifiers),
        "annotations" to array(v.annotations.map(::str)), "purpose" to nullable(v.purpose), "evidenceRefs" to strings(v.evidenceRefs),
    )
    private fun encodeParameter(v: ParameterSpecification) = obj(
        "name" to str(v.name), "type" to nullable(v.type), "hasDefaultValue" to v.hasDefaultValue.toString(),
    )
    private fun encodeProperty(v: PropertySpecification) = obj(
        "id" to str(v.id), "name" to str(v.name), "type" to nullable(v.type), "visibility" to nullable(v.visibility),
        "mutable" to nullableBoolean(v.mutable), "hasInitializer" to nullableBoolean(v.hasInitializer), "modifiers" to strings(v.modifiers),
        "annotations" to array(v.annotations.map(::str)), "purpose" to nullable(v.purpose), "evidenceRefs" to strings(v.evidenceRefs),
    )
    private fun encodeRelationship(v: RelationshipSpecification) = obj(
        "id" to str(v.id), "type" to str(v.type), "sourceId" to str(v.sourceId), "targetId" to str(v.targetId),
        "description" to nullable(v.description), "evidenceRefs" to strings(v.evidenceRefs),
    )
    private fun encodeEvidence(v: Evidence) = obj(
        "id" to str(v.id), "type" to str(v.type), "file" to nullable(v.file), "symbol" to nullable(v.symbol),
        "lineStart" to nullableInt(v.lineStart), "lineEnd" to nullableInt(v.lineEnd), "summary" to str(v.summary),
        "confidence" to str(v.confidence.name),
    )
    private fun encodeUnresolved(v: UnresolvedItem) = obj(
        "id" to str(v.id), "subject" to str(v.subject), "question" to str(v.question), "requiredAction" to nullable(v.requiredAction),
    )
    private fun encodeFeature(v: FeatureSpecification) = obj(
        "id" to str(v.id), "name" to str(v.name), "description" to nullable(v.description),
        "ownerComponentId" to str(v.ownerComponentId), "participantComponentIds" to strings(v.participantComponentIds),
        "entryPointIds" to array(v.entryPointIds.map(::str)), "scenarioIds" to array(v.scenarioIds.map(::str)),
        "evidenceRefs" to strings(v.evidenceRefs), "unresolvedRefs" to strings(v.unresolvedRefs),
    )
    private fun encodeEntryPoint(v: EntryPointSpecification) = obj(
        "id" to str(v.id), "name" to str(v.name), "kind" to str(v.kind),
        "ownerComponentId" to str(v.ownerComponentId), "apiId" to nullable(v.apiId),
        "evidenceRefs" to strings(v.evidenceRefs), "unresolvedRefs" to strings(v.unresolvedRefs),
    )
    private fun encodeScenario(v: ScenarioSpecification) = obj(
        "id" to str(v.id), "featureId" to str(v.featureId), "name" to str(v.name),
        "entryPointId" to nullable(v.entryPointId),
        "steps" to array(v.steps.sortedWith(compareBy({ it.order }, { it.id })).map(::encodeScenarioStep)),
        "evidenceRefs" to strings(v.evidenceRefs), "unresolvedRefs" to strings(v.unresolvedRefs),
    )
    private fun encodeScenarioStep(v: ScenarioStepSpecification) = obj(
        "id" to str(v.id), "order" to v.order.toString(), "action" to str(v.action),
        "ownerComponentId" to str(v.ownerComponentId), "targetComponentId" to nullable(v.targetComponentId),
        "apiId" to nullable(v.apiId), "evidenceRefs" to strings(v.evidenceRefs),
        "unresolvedRefs" to strings(v.unresolvedRefs),
    )
    private fun encodeType(v: ContractTypeReference): String = obj(
        "kind" to str(v.kind.name), "stableId" to nullable(v.stableId), "displayName" to str(v.displayName),
        "nullable" to v.nullable.toString(), "arguments" to array(v.arguments.map { encodeType(it) }),
        "evidenceRefs" to strings(v.evidenceRefs), "unresolvedRefs" to strings(v.unresolvedRefs),
    )
    private fun encodeValue(v: ContractValue) = obj(
        "id" to str(v.id), "semanticKey" to str(v.semanticKey), "name" to str(v.name), "type" to encodeType(v.type),
        "direction" to str(v.direction.name), "cardinality" to str(v.cardinality.name),
        "sourceEntityStableIds" to strings(v.sourceEntityStableIds), "evidenceRefs" to strings(v.evidenceRefs),
        "unresolvedRefs" to strings(v.unresolvedRefs), "semanticOrder" to nullableInt(v.semanticOrder),
    )
    private fun encodeMember(v: ContractMember) = obj(
        "id" to str(v.id), "semanticKey" to str(v.semanticKey), "name" to str(v.name), "type" to encodeType(v.type),
        "cardinality" to str(v.cardinality.name), "sourceEntityStableIds" to strings(v.sourceEntityStableIds),
        "evidenceRefs" to strings(v.evidenceRefs), "unresolvedRefs" to strings(v.unresolvedRefs),
        "semanticOrder" to nullableInt(v.semanticOrder),
    )
    private fun encodeContractRelationship(v: ContractRelationship) = obj(
        "id" to str(v.id), "kind" to str(v.kind.name), "targetStableId" to str(v.targetStableId),
        "evidenceRefs" to strings(v.evidenceRefs), "unresolvedRefs" to strings(v.unresolvedRefs),
    )
    private fun encodeContract(v: ContractSpecification) = obj(
        "id" to str(v.id), "semanticKey" to str(v.semanticKey), "displayName" to str(v.displayName), "kind" to str(v.kind.name), "role" to str(v.role.name),
        "owner" to obj("kind" to str(v.owner.kind.name), "stableId" to str(v.owner.stableId)),
        "sourceEntityStableIds" to strings(v.sourceEntityStableIds), "inputs" to array(v.inputs.map(::encodeValue)),
        "outputs" to array(v.outputs.map(::encodeValue)), "members" to array(v.members.map(::encodeMember)),
        "relationships" to array(v.relationships.sortedBy { it.id }.map(::encodeContractRelationship)),
        "evidenceRefs" to strings(v.evidenceRefs), "unresolvedRefs" to strings(v.unresolvedRefs),
    )

    private fun decodeSpecification(o: JsonValue.ObjectValue): ProjectSpecification = ProjectSpecification(
        schemaVersion = o.requiredString("schemaVersion"),
        project = decodeProject(o.requiredObject("project")),
        modules = o.requiredArray("modules").mapObject(::decodeModule),
        packages = o.requiredArray("packages").mapObject(::decodePackage),
        components = o.requiredArray("components").mapObject(::decodeComponent),
        relationships = o.requiredArray("relationships").mapObject(::decodeRelationship),
        evidence = o.requiredArray("evidence").mapObject(::decodeEvidence),
        unresolved = o.requiredArray("unresolved").mapObject(::decodeUnresolved),
        features = o.optionalArray("features").mapObject(::decodeFeature),
        entryPoints = o.optionalArray("entryPoints").mapObject(::decodeEntryPoint),
        scenarios = o.optionalArray("scenarios").mapObject(::decodeScenario),
        contracts = if (o.requiredString("schemaVersion") == SpecificationSnapshotFormat.DIR_0_5_SCHEMA_VERSION) {
            o.requiredArray("contracts").mapObject(::decodeContract)
        } else {
            o.optionalArray("contracts").mapObject(::decodeContract)
        },
    )
    private fun decodeProject(o: JsonValue.ObjectValue) = ProjectDescriptor(
        o.requiredString("id"), o.requiredString("name"), o.optionalString("description"),
        o.stringSet("platforms"), o.stringSet("languages"), o.stringSet("buildSystems"),
    )
    private fun decodeModule(o: JsonValue.ObjectValue) = ModuleSpecification(
        o.requiredString("id"), o.requiredString("name"), o.optionalString("path"), o.optionalString("description"),
        o.stringSet("sourceSets"), o.stringSet("evidenceRefs"),
    )
    private fun decodePackage(o: JsonValue.ObjectValue) = PackageSpecification(
        o.requiredString("id"), o.requiredString("name"), o.requiredString("qualifiedName"), o.requiredString("moduleId"),
        o.optionalString("description"), o.stringSet("evidenceRefs"),
    )
    private fun decodeComponent(o: JsonValue.ObjectValue) = ComponentSpecification(
        id=o.requiredString("id"), name=o.requiredString("name"), moduleId=o.requiredString("moduleId"), kind=o.requiredString("kind"), role=o.requiredString("role"),
        packageId=o.optionalString("packageId"), qualifiedName=o.optionalString("qualifiedName"), visibility=o.optionalString("visibility"),
        modifiers=o.stringSet("modifiers"), annotations=o.stringList("annotations"), typeParameters=o.stringList("typeParameters"),
        superTypes=o.stringList("superTypes"), responsibilities=o.stringList("responsibilities"), dependencyIds=o.stringSet("dependencyIds"),
        apis=o.requiredArray("apis").mapObject(::decodeApi), properties=o.requiredArray("properties").mapObject(::decodeProperty), evidenceRefs=o.stringSet("evidenceRefs"),
    )
    private fun decodeApi(o: JsonValue.ObjectValue) = ApiSpecification(
        id=o.requiredString("id"), name=o.requiredString("name"), kind=o.requiredString("kind"), signature=o.optionalString("signature"),
        visibility=o.optionalString("visibility"), receiverType=o.optionalString("receiverType"), returnType=o.optionalString("returnType"),
        parameters=o.requiredArray("parameters").mapObject(::decodeParameter), modifiers=o.stringSet("modifiers"), annotations=o.stringList("annotations"),
        purpose=o.optionalString("purpose"), evidenceRefs=o.stringSet("evidenceRefs"),
    )
    private fun decodeParameter(o: JsonValue.ObjectValue) = ParameterSpecification(
        o.requiredString("name"), o.optionalString("type"), o.requiredBoolean("hasDefaultValue"),
    )
    private fun decodeProperty(o: JsonValue.ObjectValue) = PropertySpecification(
        id=o.requiredString("id"), name=o.requiredString("name"), type=o.optionalString("type"), visibility=o.optionalString("visibility"),
        mutable=o.optionalBoolean("mutable"), hasInitializer=o.optionalBoolean("hasInitializer"), modifiers=o.stringSet("modifiers"),
        annotations=o.stringList("annotations"), purpose=o.optionalString("purpose"), evidenceRefs=o.stringSet("evidenceRefs"),
    )
    private fun decodeRelationship(o: JsonValue.ObjectValue) = RelationshipSpecification(
        o.requiredString("id"), o.requiredString("type"), o.requiredString("sourceId"), o.requiredString("targetId"),
        o.optionalString("description"), o.stringSet("evidenceRefs"),
    )
    private fun decodeEvidence(o: JsonValue.ObjectValue) = Evidence(
        id=o.requiredString("id"), type=o.requiredString("type"), file=o.optionalString("file"), symbol=o.optionalString("symbol"),
        lineStart=o.optionalInt("lineStart"), lineEnd=o.optionalInt("lineEnd"), summary=o.requiredString("summary"),
        confidence=EvidenceConfidence.valueOf(o.requiredString("confidence")),
    )
    private fun decodeUnresolved(o: JsonValue.ObjectValue) = UnresolvedItem(
        o.requiredString("id"), o.requiredString("subject"), o.requiredString("question"), o.optionalString("requiredAction"),
    )
    private fun decodeFeature(o: JsonValue.ObjectValue) = FeatureSpecification(
        id=o.requiredString("id"), name=o.requiredString("name"), description=o.optionalString("description"),
        ownerComponentId=o.requiredString("ownerComponentId"), participantComponentIds=o.stringSet("participantComponentIds"),
        entryPointIds=o.stringList("entryPointIds"), scenarioIds=o.stringList("scenarioIds"),
        evidenceRefs=o.stringSet("evidenceRefs"), unresolvedRefs=o.stringSet("unresolvedRefs"),
    )
    private fun decodeEntryPoint(o: JsonValue.ObjectValue) = EntryPointSpecification(
        id=o.requiredString("id"), name=o.requiredString("name"), kind=o.requiredString("kind"),
        ownerComponentId=o.requiredString("ownerComponentId"), apiId=o.optionalString("apiId"),
        evidenceRefs=o.stringSet("evidenceRefs"), unresolvedRefs=o.stringSet("unresolvedRefs"),
    )
    private fun decodeScenario(o: JsonValue.ObjectValue) = ScenarioSpecification(
        id=o.requiredString("id"), featureId=o.requiredString("featureId"), name=o.requiredString("name"),
        entryPointId=o.optionalString("entryPointId"), steps=o.requiredArray("steps").mapObject(::decodeScenarioStep),
        evidenceRefs=o.stringSet("evidenceRefs"), unresolvedRefs=o.stringSet("unresolvedRefs"),
    )
    private fun decodeScenarioStep(o: JsonValue.ObjectValue) = ScenarioStepSpecification(
        id=o.requiredString("id"), order=o.requiredLong("order").toIntExact("order"), action=o.requiredString("action"),
        ownerComponentId=o.requiredString("ownerComponentId"), targetComponentId=o.optionalString("targetComponentId"),
        apiId=o.optionalString("apiId"), evidenceRefs=o.stringSet("evidenceRefs"), unresolvedRefs=o.stringSet("unresolvedRefs"),
    )
    private fun decodeType(o: JsonValue.ObjectValue): ContractTypeReference = ContractTypeReference(
        kind=ContractTypeKind.valueOf(o.requiredString("kind")), stableId=o.optionalString("stableId"),
        displayName=o.requiredString("displayName"), nullable=o.requiredBoolean("nullable"),
        arguments=o.requiredArray("arguments").mapObject { decodeType(it) }, evidenceRefs=o.stringSet("evidenceRefs"),
        unresolvedRefs=o.stringSet("unresolvedRefs"),
    )
    private fun decodeValue(o: JsonValue.ObjectValue) = ContractValue(
        id=o.requiredString("id"), semanticKey=o.requiredString("semanticKey"), name=o.requiredString("name"),
        type=decodeType(o.requiredObject("type")), direction=ContractDirection.valueOf(o.requiredString("direction")),
        cardinality=ContractCardinality.valueOf(o.requiredString("cardinality")), sourceEntityStableIds=o.stringSet("sourceEntityStableIds"),
        evidenceRefs=o.stringSet("evidenceRefs"), unresolvedRefs=o.stringSet("unresolvedRefs"), semanticOrder=o.optionalInt("semanticOrder"),
    )
    private fun decodeMember(o: JsonValue.ObjectValue) = ContractMember(
        id=o.requiredString("id"), semanticKey=o.requiredString("semanticKey"), name=o.requiredString("name"),
        type=decodeType(o.requiredObject("type")), cardinality=ContractCardinality.valueOf(o.requiredString("cardinality")),
        sourceEntityStableIds=o.stringSet("sourceEntityStableIds"), evidenceRefs=o.stringSet("evidenceRefs"),
        unresolvedRefs=o.stringSet("unresolvedRefs"), semanticOrder=o.optionalInt("semanticOrder"),
    )
    private fun decodeContractRelationship(o: JsonValue.ObjectValue) = ContractRelationship(
        id=o.requiredString("id"), kind=ContractRelationshipKind.valueOf(o.requiredString("kind")),
        targetStableId=o.requiredString("targetStableId"), evidenceRefs=o.stringSet("evidenceRefs"), unresolvedRefs=o.stringSet("unresolvedRefs"),
    )
    private fun decodeContract(o: JsonValue.ObjectValue) = ContractSpecification(
        id=o.requiredString("id"), semanticKey=o.requiredString("semanticKey"), displayName=o.requiredString("displayName"), kind=ContractKind.valueOf(o.requiredString("kind")),
        role=ContractRole.valueOf(o.requiredString("role")), owner=o.requiredObject("owner").let {
            ContractOwner(ContractOwnerKind.valueOf(it.requiredString("kind")), it.requiredString("stableId"))
        }, sourceEntityStableIds=o.stringSet("sourceEntityStableIds"), inputs=o.requiredArray("inputs").mapObject(::decodeValue),
        outputs=o.requiredArray("outputs").mapObject(::decodeValue), members=o.requiredArray("members").mapObject(::decodeMember),
        relationships=o.requiredArray("relationships").mapObject(::decodeContractRelationship), evidenceRefs=o.stringSet("evidenceRefs"),
        unresolvedRefs=o.stringSet("unresolvedRefs"),
    )

    private fun StringBuilder.field(name: String, value: String, raw: Boolean = false) {
        appendJsonString(name).append(':')
        if (raw) append(value) else appendJsonString(value)
    }
    private fun obj(vararg fields: Pair<String,String>) = fields.joinToString(prefix="{", postfix="}", separator=",") { (k,v) -> "${str(k)}:$v" }
    private fun array(values: List<String>) = values.joinToString(prefix="[", postfix="]", separator=",")
    private fun strings(values: Collection<String>) = array(values.sorted().map(::str))
    private fun str(v: String) = buildString { appendJsonString(v) }
    private fun nullable(v: String?) = v?.let(::str) ?: "null"
    private fun nullableBoolean(v: Boolean?) = v?.toString() ?: "null"
    private fun nullableInt(v: Int?) = v?.toString() ?: "null"
}

private fun StringBuilder.appendJsonString(value: String): StringBuilder {
    append('"')
    value.forEach { c -> when(c) {
        '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\u000C' -> append("\\f")
        '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
        else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
    }}
    return append('"')
}

private sealed interface JsonValue {
    data class ObjectValue(val values: Map<String, JsonValue>) : JsonValue
    data class ArrayValue(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val value: Long) : JsonValue
    data class BooleanValue(val value: Boolean) : JsonValue
    data object NullValue : JsonValue
}
private fun JsonValue.ObjectValue.requiredString(n:String)= (values[n] as? JsonValue.StringValue)?.value ?: error("Missing or invalid JSON string: $n")
private fun JsonValue.ObjectValue.optionalString(n:String)= when(val v=values[n]) { null, JsonValue.NullValue -> null; is JsonValue.StringValue -> v.value; else -> error("Invalid JSON string: $n") }
private fun JsonValue.ObjectValue.requiredLong(n:String)= (values[n] as? JsonValue.NumberValue)?.value ?: error("Missing or invalid JSON number: $n")
private fun JsonValue.ObjectValue.requiredBoolean(n:String)= (values[n] as? JsonValue.BooleanValue)?.value ?: error("Missing or invalid JSON boolean: $n")
private fun JsonValue.ObjectValue.optionalBoolean(n:String)= when(val v=values[n]) { null, JsonValue.NullValue -> null; is JsonValue.BooleanValue -> v.value; else -> error("Invalid JSON boolean: $n") }
private fun JsonValue.ObjectValue.optionalInt(n:String)= when(val v=values[n]) { null, JsonValue.NullValue -> null; is JsonValue.NumberValue -> v.value.toIntExact(n); else -> error("Invalid JSON number: $n") }
private fun JsonValue.ObjectValue.requiredObject(n:String)= values[n] as? JsonValue.ObjectValue ?: error("Missing or invalid JSON object: $n")
private fun JsonValue.ObjectValue.requiredArray(n:String)= (values[n] as? JsonValue.ArrayValue)?.values ?: error("Missing or invalid JSON array: $n")
private fun JsonValue.ObjectValue.optionalArray(n:String)= when(val v=values[n]) { null -> emptyList(); is JsonValue.ArrayValue -> v.values; else -> error("Invalid JSON array: $n") }
private fun JsonValue.ObjectValue.stringList(n:String)=requiredArray(n).mapIndexed { i,v -> (v as? JsonValue.StringValue)?.value ?: error("Invalid string at $n[$i]") }
private fun JsonValue.ObjectValue.stringSet(n:String)=stringList(n).toSet()
private fun <T> List<JsonValue>.mapObject(f:(JsonValue.ObjectValue)->T)=mapIndexed { i,v -> f(v as? JsonValue.ObjectValue ?: error("Expected object at index $i")) }
private fun Long.toIntExact(n:String):Int { require(this in Int.MIN_VALUE..Int.MAX_VALUE){"JSON number outside Int range: $n"}; return toInt() }

private class JsonParser(private val input:String) {
    private var i=0
    fun parseRootObject():JsonValue.ObjectValue { val v=parseValue() as? JsonValue.ObjectValue ?: error("JSON root must be an object"); skip(); require(i==input.length){"Unexpected trailing JSON content at index $i"}; return v }
    private fun parseValue():JsonValue { skip(); require(i<input.length){"Unexpected end of JSON"}; return when(input[i]) {
        '{'->parseObject(); '['->parseArray(); '"'->JsonValue.StringValue(parseString()); 'n'->{token("null");JsonValue.NullValue};
        't'->{token("true");JsonValue.BooleanValue(true)}; 'f'->{token("false");JsonValue.BooleanValue(false)};
        '-', in '0'..'9' -> JsonValue.NumberValue(parseLong()); else->error("Unexpected JSON character '${input[i]}' at index $i") }
    }
    private fun parseObject():JsonValue.ObjectValue { expect('{'); skip(); val m=linkedMapOf<String,JsonValue>(); if(peek('}')){expect('}');return JsonValue.ObjectValue(m)}; while(true){ val n=parseString(); expect(':'); require(m.put(n,parseValue())==null){"Duplicate JSON field: $n"}; skip(); if(peek(',')){expect(',')} else if(peek('}')){expect('}');return JsonValue.ObjectValue(m)} else error("Expected ',' or '}' at index $i") } }
    private fun parseArray():JsonValue.ArrayValue { expect('['); skip(); val a=mutableListOf<JsonValue>(); if(peek(']')){expect(']');return JsonValue.ArrayValue(a)}; while(true){a+=parseValue();skip();if(peek(',')){expect(',')}else if(peek(']')){expect(']');return JsonValue.ArrayValue(a)}else error("Expected ',' or ']' at index $i")}}
    private fun parseString():String { skip();expect('"');val b=StringBuilder();while(i<input.length){val c=input[i++];when{c=='"'->return b.toString();c=='\\'->b.append(escape());c.code<0x20->error("Unescaped control character");else->b.append(c)}};error("Unterminated JSON string") }
    private fun escape():Char { require(i<input.length){"Unterminated JSON escape"};return when(val e=input[i++]){'"'->'"';'\\'->'\\';'/'->'/';'b'->'\b';'f'->'\u000C';'n'->'\n';'r'->'\r';'t'->'\t';'u'->{val d=input.substring(i,i+4);require(d.all{it.isDigit()||it.lowercaseChar() in 'a'..'f'});i+=4;d.toInt(16).toChar()};else->error("Unsupported JSON escape: \\$e")}}
    private fun parseLong():Long {val s=i;if(peek('-'))i++;require(i<input.length&&input[i].isDigit());if(input[i]=='0'){i++;require(i>=input.length||!input[i].isDigit())}else while(i<input.length&&input[i].isDigit())i++;require(i>=input.length||input[i] !in listOf('.','e','E'));return input.substring(s,i).toLong()}
    private fun token(t:String){require(input.startsWith(t,i)){"Expected '$t' at index $i"};i+=t.length}
    private fun expect(c:Char){skip();require(i<input.length&&input[i]==c){"Expected '$c' at index $i"};i++}
    private fun peek(c:Char)=i<input.length&&input[i]==c
    private fun skip(){while(i<input.length&&input[i].isWhitespace())i++}
}
