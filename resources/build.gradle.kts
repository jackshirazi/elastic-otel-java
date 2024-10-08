plugins {
  id("elastic-otel.java-conventions")
  alias(catalog.plugins.taskinfo)
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  // not included in the upstream agent
  implementation(catalog.contribResources)
}
