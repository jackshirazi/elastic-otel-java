plugins {
    id("elastic-otel.java-conventions")
}

description = "An extension that logs the OpenTelemetry configuration at startup"

dependencies {
    implementation(platform(catalog.opentelemetryInstrumentationAlphaBom))
    implementation("io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")

    // @AutoService annotation
    compileOnly(catalog.autoservice.annotations)
    annotationProcessor(catalog.autoservice.processor)
}
