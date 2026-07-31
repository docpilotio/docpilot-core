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

data class ComposeNavigationSourceObservations(
    val routes: List<ComposeRouteDeclarationObservation> = emptyList(),
    val registrations: List<ComposeNavigationRegistrationObservation> = emptyList(),
)
