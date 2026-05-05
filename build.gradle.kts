tasks.wrapper {
    distributionType = Wrapper.DistributionType.BIN
    gradleVersion = "latest"
    retries = 3
    retryBackOffMs = 1000
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
