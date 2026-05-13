package com.banno.gordon

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.variant.AndroidTest
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaBasePlugin.VERIFICATION_GROUP
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import java.util.Locale

class GordonPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val androidPlugin = project.androidPlugin()
            ?: error("Gordon plugin must be applied after applying the application or library Android plugin")

        val gordonExtension = project.extensions.create<GordonExtension>("gordon")

        fun registerGordonTask(
            androidTestVariant: AndroidTest,
            configuration: (GordonTestTask) -> Unit
        ) {
            val testBuildType = androidTestVariant.buildType.orEmpty()

            val variantTaskName = androidTestVariant.name
                .capitalize(Locale.ROOT)
                .replace(Regex("AndroidTest$"), "")
                .replace(Regex("${testBuildType.capitalize(Locale.ROOT)}$"), "")

            project.tasks.register<GordonTestTask>("gordon$variantTaskName") {
                group = VERIFICATION_GROUP
                val variantDescription = variantTaskName.takeIf { it.isNotBlank() }?.let { " for $it" } ?: ""
                description = "Installs and runs instrumentation tests$variantDescription."
                configuration(this)
            }
        }

        fun configureGordonTask(
            task: GordonTestTask,
            androidTestVariant: AndroidTest,
            applicationPackage: Provider<String>?,
            dynamicFeatureModuleManifest: Provider<RegularFile>?,
            dynamicFeatureModuleName: String?,
            applicationAab: Provider<RegularFile>?,
            applicationSigningConfig: Provider<ApkSigningConfig>?,
            animationsDisabled: Boolean
        ) {
            task.rootProjectBuildDirectory.set(project.rootProject.layout.buildDirectory)

            task.instrumentationApkDir.set(androidTestVariant.artifacts.get(SingleArtifact.APK))
            task.instrumentationPackage.set(androidTestVariant.applicationId)

            applicationPackage?.let(task.applicationPackage::set)

            dynamicFeatureModuleManifest?.let(task.dynamicFeatureModuleManifest::set)
            dynamicFeatureModuleName?.let(task.dynamicFeatureModuleName::set)

            applicationAab?.let(task.applicationAab::set)

            applicationSigningConfig?.map { it.storeFile }?.let(task.signingKeystoreFile::fileProvider)
            applicationSigningConfig
                ?.map { SigningConfigCredentials(storePassword = it.storePassword, keyAlias = it.keyAlias, keyPassword = it.keyPassword) }
                ?.let(task.signingConfigCredentials::set)
                ?: task.signingConfigCredentials.set(SigningConfigCredentials(null, null, null))

            task.androidInstrumentationRunnerOptions.set(
                androidTestVariant.instrumentationRunner
                    .zip(androidTestVariant.instrumentationRunnerArguments) { instrumentationRunner, instrumentationRunnerArguments ->
                        InstrumentationRunnerOptions(
                            testInstrumentationRunner = instrumentationRunner,
                            testInstrumentationRunnerArguments = instrumentationRunnerArguments,
                            animationsDisabled = animationsDisabled
                        )
                    }
            )

            task.poolingStrategy.set(gordonExtension.poolingStrategy)
            task.tabletShortestWidthDp.set(gordonExtension.tabletShortestWidthDp)
            task.retryQuota.set(gordonExtension.retryQuota)
            task.installTimeoutMillis.set(gordonExtension.installTimeoutMillis)
            task.testTimeoutMillis.set(gordonExtension.testTimeoutMillis)
            task.extensionTestFilter.set(gordonExtension.testFilter)
            task.extensionTestInstrumentationRunner.set(gordonExtension.testInstrumentationRunner)
            task.ignoreProblematicDevices.set(gordonExtension.ignoreProblematicDevices)
            task.leaveApksInstalledAfterRun.set(gordonExtension.leaveApksInstalledAfterRun)
        }

        when (androidPlugin) {
            is AndroidPlugin.App -> androidPlugin.componentsExtension.onVariants { applicationVariant ->
                applicationVariant.androidTest?.let { androidTestVariant ->
                    registerGordonTask(androidTestVariant) { gordonTask ->
                        configureGordonTask(
                            task = gordonTask,
                            androidTestVariant = androidTestVariant,
                            applicationPackage = applicationVariant.applicationId,
                            dynamicFeatureModuleManifest = null,
                            dynamicFeatureModuleName = null,
                            applicationAab = applicationVariant.artifacts.get(SingleArtifact.BUNDLE),
                            applicationSigningConfig = androidTestVariant.buildType
                                ?.let(androidPlugin.androidExtension.buildTypes::named)
                                ?.map { it.signingConfig },
                            animationsDisabled = androidPlugin.androidExtension.testOptions.animationsDisabled,
                        )
                    }
                }
            }

//            is AndroidPlugin.DynamicFeature -> androidPlugin.componentsExtension.onVariants { dynamicFeatureVariant ->
//                dynamicFeatureVariant.androidTest?.let { androidTestVariant ->
//                    registerGordonTask(androidTestVariant) { gordonTask ->
//                        val testedVariant = testedExtension.testVariants.single { it.name == androidTestVariant.name }.testedVariant as ApkVariant
//
//                        val (appProject, appVariant) = appDependencyOfFeature(project, testedVariant)
//                        gordonTask.dependsOn(appProject.tasks.named("bundle${appVariant.name.capitalize(Locale.ROOT)}"))
//                        val applicationAab = appVariant.aabOutputFile(appProject)
//                        val applicationSigningConfig = appVariant.signingConfig
//
//                        configureGordonTask(
//                            task = gordonTask,
//                            androidTestVariant = androidTestVariant,
//                            applicationPackage = dynamicFeatureVariant.applicationId,
//                            dynamicFeatureModuleManifest = dynamicFeatureVariant.artifacts.get(SingleArtifact.MERGED_MANIFEST),
//                            dynamicFeatureModuleName = project.name,
//                            applicationAab = applicationAab,
//                            applicationSigningConfig = applicationSigningConfig,
//                            animationsDisabled = androidPlugin.androidExtension.testOptions.animationsDisabled,
//                        )
//                    }
//                }
//            }

            is AndroidPlugin.Library -> androidPlugin.componentsExtension.onVariants { libraryVariant ->
                libraryVariant.androidTest?.let { androidTestVariant ->
                    registerGordonTask(androidTestVariant) { gordonTask ->
                        configureGordonTask(
                            task = gordonTask,
                            androidTestVariant = androidTestVariant,
                            applicationPackage = null,
                            dynamicFeatureModuleManifest = null,
                            dynamicFeatureModuleName = null,
                            applicationAab = null,
                            applicationSigningConfig = null,
                            animationsDisabled = androidPlugin.androidExtension.testOptions.animationsDisabled,
                        )
                    }
                }
            }
        }
    }

//    private fun ApplicationVariant.aabOutputFile(appProject: Project) = appProject.extensions.getByType<BasePluginExtension>().archivesName.flatMap {
//        appProject.layout.buildDirectory.file("outputs/bundle/$name/$it-$baseName.aab")
//    }
//
//    private fun appDependencyOfFeature(
//        featureProject: Project,
//        featureVariant: ApkVariant
//    ): Pair<Project, ApplicationVariant> = featureProject
//        .configurations
//        .getByName("${featureVariant.name}RuntimeClasspath")
//        .incoming
//        .resolutionResult
//        .allComponents
//        .filter { it.id is ProjectComponentIdentifier }
//        .associateBy { featureProject.rootProject.project((it.id as ProjectComponentIdentifier).projectPath) }
//        .entries
//        .single { (project, _) -> project.plugins.hasPlugin("com.android.application") }
//        .let { (appProject, component) ->
//            val androidVariantAttributeKey = Attribute.of(VariantAttr.ATTRIBUTE.name, String::class.java)
//
//            val appVariantName = component
//                .variants
//                .mapNotNull { it.attributes.getAttribute(androidVariantAttributeKey) }
//                .single()
//
//            val appExtension = appProject.extensions.getByType<AppExtension>()
//            val appVariant = appExtension.applicationVariants.single { it.name == appVariantName }
//
//            appProject to appVariant
//        }
}
