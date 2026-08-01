package io.docpilot.core.model.source

data class ComposeRouteDeclarationObservation(
    val id: String,
    val symbolId: String,
    val qualifiedName: String,
    val kind: ComposeRouteDeclarationKind,
    val expression: String,
    val location: SourceLocation,
)

enum class ComposeRouteDeclarationKind {
    STRING_ROUTE,
    CONST_STRING_ROUTE,
    OBJECT_ROUTE,
    DATA_OBJECT_ROUTE,
    SERIALIZABLE_CLASS_ROUTE,
    SERIALIZABLE_OBJECT_ROUTE,
    TYPE_SAFE_ROUTE,
    UNKNOWN_ROUTE,
}

data class ComposeNavigationRegistrationObservation(
    val id: String,
    val apiKind: ComposeNavigationRegistrationKind,
    val calleeQualifiedName: String,
    val ownerSymbolId: String,
    val ownerQualifiedName: String,
    val routeExpression: String,
    val genericRouteType: String? = null,
    val destinationCalls: List<ComposeDestinationCallObservation> = emptyList(),
    val functionReferences: List<ComposeFunctionReferenceObservation> = emptyList(),
    val externalLambdaReference: String? = null,
    val arguments: List<ComposeNavigationArgumentObservation> = emptyList(),
    val ownerGraphId: String? = null,
    val location: SourceLocation,
)

enum class ComposeNavigationRegistrationKind {
    COMPOSABLE,
    NAVIGATION,
    DIALOG,
    BOTTOM_SHEET,
    ACTIVITY,
    CUSTOM_DESTINATION,
    UNKNOWN,
}

data class ComposeDestinationCallObservation(
    val calleeQualifiedName: String,
    val nestingDepth: Int,
    val location: SourceLocation,
)

data class ComposeFunctionReferenceObservation(
    val id: String,
    val expression: String,
    val referencedName: String,
    val receiverExpression: String? = null,
    val argumentName: String? = null,
    val argumentPosition: Int? = null,
    val kind: ComposeFunctionReferenceKind = ComposeFunctionReferenceKind.UNKNOWN,
    val ownerRegistrationId: String,
    val location: SourceLocation,
)

enum class ComposeFunctionReferenceKind {
    TOP_LEVEL_FUNCTION,
    STATIC_OR_OBJECT_MEMBER,
    BOUND_MEMBER,
    UNBOUND_MEMBER,
    CONSTRUCTOR_REFERENCE,
    UNKNOWN,
}

data class ComposeNavigationGraphObservation(
    val id: String,
    val kind: ComposeNavigationGraphKind,
    val routeExpression: String,
    val startDestinationExpression: String? = null,
    val parentGraphId: String? = null,
    val ownerSymbolId: String,
    val registrationId: String,
    val childRegistrationIds: List<String> = emptyList(),
    val location: SourceLocation,
)

enum class ComposeNavigationGraphKind {
    ROOT_NAV_HOST,
    NESTED_NAVIGATION_GRAPH,
    CUSTOM_GRAPH_BUILDER,
    UNKNOWN,
}

data class ComposeNavigationArgumentObservation(
    val id: String,
    val name: String,
    val sourceKind: ComposeNavigationArgumentSourceKind,
    val declaredType: String? = null,
    val nullable: Boolean? = null,
    val defaultValueExpression: String? = null,
    val routePlaceholder: String? = null,
    val ownerRegistrationId: String? = null,
    val ownerRouteId: String? = null,
    val location: SourceLocation,
)

data class ComposeNavigationArgumentLinkObservation(
    val id: String,
    val argumentId: String,
    val destinationSymbolId: String,
    val parameterName: String,
    val evidenceKind: ComposeNavigationArgumentLinkEvidenceKind,
)

enum class ComposeNavigationArgumentLinkEvidenceKind {
    TYPED_ROUTE_PARAMETER_SIGNATURE,
    DIRECT_ARGUMENT_EXPRESSION,
    UNKNOWN,
}

enum class ComposeNavigationArgumentSourceKind {
    TYPED_ROUTE_PROPERTY,
    ROUTE_PATH_PLACEHOLDER,
    ROUTE_QUERY_PLACEHOLDER,
    NAV_ARGUMENT_DECLARATION,
    REGISTRATION_ARGUMENT,
    UNKNOWN,
}

data class ComposeNavigationSourceObservations(
    val routes: List<ComposeRouteDeclarationObservation> = emptyList(),
    val registrations: List<ComposeNavigationRegistrationObservation> = emptyList(),
    val graphs: List<ComposeNavigationGraphObservation> = emptyList(),
    val routeArguments: List<ComposeNavigationArgumentObservation> = emptyList(),
)
