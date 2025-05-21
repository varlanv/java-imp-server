package com.varlanv.gradle.plugin;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import com.diffplug.spotless.LineEnding;
import com.github.benmanes.gradle.versions.VersionsPlugin;
import com.github.spotbugs.snom.SpotBugsExtension;
import com.github.spotbugs.snom.SpotBugsPlugin;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.quality.*;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.VariantVersionMappingStrategy;
import org.gradle.api.publish.maven.MavenPomDeveloper;
import org.gradle.api.publish.maven.MavenPomLicense;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.SigningPlugin;
import org.gradle.testing.base.TestingExtension;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
public final class InternalConventionPlugin implements Plugin<Project> {

    private static final String PLUGIN_GIT_URL = "https://github.com/varlanv/java-imp-server";

    private Action<MavenPomLicense> pluginLicense() {
        return license -> {
            license.getName().set("MIT License");
            license.getUrl().set("https://mit-license.org/");
        };
    }

    private Action<MavenPomDeveloper> pluginDeveloper() {
        return developer -> {
            developer.getId().set("varlanv96");
            developer.getName().set("Vladyslav Varlan");
            developer.getEmail().set("varlanv96@gmail.com");
        };
    }

    @Override
    public void apply(Project project) {
        // -------------------- Variables start --------------------
        var extensions = project.getExtensions();
        var providers = project.getProviders();
        var pluginManager = project.getPluginManager();
        var tasks = project.getTasks();
        var projectName = project.getName();
        var repositories = project.getRepositories();
        var dependencies = project.getDependencies();
        var components = project.getComponents();
        var configurations = project.getConfigurations();
        var rootDir = project.getRootDir().toPath();
        var projectLayout = project.getLayout();
        var projectPath = project.getPath();
        var internalEnvironment = Optional.ofNullable((InternalEnvironment) extensions.findByName(InternalEnvironment.name()))
            .orElseGet(() -> new InternalEnvironment(
                providers.environmentVariable("CI").isPresent(),
                false
            ));
        var internalProperties = Optional.ofNullable((InternalProperties) extensions.findByName(InternalProperties.name()))
            .orElseGet(() -> new InternalProperties(((VersionCatalogsExtension) extensions.getByName("versionCatalogs")).named("libs")));
        var internalConventionExtension = Optional.ofNullable((InternalConventionExtension) extensions.findByName(InternalConventionExtension.name()))
            .orElseGet(() -> extensions.create(InternalConventionExtension.name(), InternalConventionExtension.class));
        internalConventionExtension.getIntegrationTestName().convention("integrationTest");
        internalConventionExtension.getInternalModule().convention(false);
        var isGradlePlugin = projectName.endsWith("plugin");
        var javaVersion = 11;
        var jdkVersion = 21;
        var internalJavaVersion = 21;
        var jvmVendor = JvmVendorSpec.ADOPTIUM;
        var staticAnalyseFolder = rootDir.resolve(".config").resolve("static-analyse");
        // -------------------- Variables end --------------------


        // -------------------- Configure repositories start --------------------
        if (internalEnvironment.isLocal()) {
            repositories.add(repositories.mavenLocal());
        }
        repositories.add(repositories.mavenCentral());
        // -------------------- Configure repositories end --------------------

        project.afterEvaluate(ignore -> {
                // Need to run these things after a project evaluated, so that InternalConventionExtension values are initialized
                // -------------------- Configure Java start --------------------
                pluginManager.withPlugin(
                    "java",
                    plugin -> {
                        extensions.<JavaPluginExtension>configure("java", java -> {
                            java.withSourcesJar();
                            var isInternalModule = internalConventionExtension.getInternalModule().get();
                            java.toolchain((spec -> {
                                spec.getLanguageVersion().set(JavaLanguageVersion.of(isInternalModule ? internalJavaVersion : jdkVersion));
                                spec.getVendor().set(jvmVendor);
                            }));
                            var mainSourceSet = java.getSourceSets().getByName("main");

                            tasks.named(mainSourceSet.getCompileJavaTaskName(), JavaCompile.class).configure(javaCompile -> {
                                var compileOpts = javaCompile.getOptions();
                                if (!isInternalModule) {
                                    compileOpts.getRelease().set(javaVersion);
                                }
                                compileOpts.getCompilerArgs().addAll(List.of(
                                    "-Xlint:-processing",
                                    "-Xlint:all",
                                    "-Werror"
                                ));
                            });
                            tasks.named(mainSourceSet.getJavadocTaskName(), Javadoc.class).configure(javadoc -> {
                                var javadocOptions =(CoreJavadocOptions) javadoc.getOptions();
                                javadocOptions.addStringOption("Xdoclint:none", "-quiet");
                                extensions.<JavaToolchainService>configure("javaToolchains", javaToolchains -> {
                                    javadoc.getJavadocTool().set(javaToolchains.javadocToolFor(toolchainSpec -> {
                                        toolchainSpec.getLanguageVersion().set(JavaLanguageVersion.of(jdkVersion));
                                        toolchainSpec.getVendor().set(jvmVendor);
                                    }));
                                });
                            });
                        });

                        // -------------------- Add common dependencies start --------------------
                        var jetbrainsAnnotations = internalProperties.getLib("jetbrains-annotations");
                        dependencies.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, jetbrainsAnnotations);
                        dependencies.add(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME, jetbrainsAnnotations);
                        dependencies.add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, internalProperties.getLib("assertj-core"));
                        dependencies.add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, internalProperties.getLib("junit-jupiter-api"));
                        dependencies.add(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME, internalProperties.getLib("junit-platform-launcher"));

                        var jSpecify = internalProperties.getLib("jSpecify");
                        dependencies.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, jSpecify);
                        dependencies.add(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME, jSpecify);

                        var immutablesDependency = internalProperties.getLib("immutables-values");
                        dependencies.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, immutablesDependency);
                        dependencies.add(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME, immutablesDependency);
                        dependencies.add(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME, immutablesDependency);
                        dependencies.add(JavaPlugin.TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME, immutablesDependency);

                        dependencies.add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, internalProperties.getLib("junit-jupiter-api"));
                        dependencies.add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, internalProperties.getLib("junit-platform-launcher"));
                        dependencies.add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, internalProperties.getLib("junit-platform-engine"));

                        if (!internalEnvironment.isTest() && !projectPath.equals(":common-test")) {
                            dependencies.add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, dependencies.project(Collections.singletonMap("path", ":common-test")));
                        }

                        // -------------------- Add common dependencies end --------------------
                        // -------------------- Apply common plugins start --------------------
                        if (isGradlePlugin) {
                            pluginManager.apply(JavaGradlePluginPlugin.class);
                        }
                        pluginManager.apply(PmdPlugin.class);
                        pluginManager.apply(CheckstylePlugin.class);
                        pluginManager.apply(VersionsPlugin.class);
                        if (internalEnvironment.isLocal()) {
                            pluginManager.apply(IdeaPlugin.class);
                            extensions.<IdeaModel>configure("idea", idea -> {
                                idea.getModule().setDownloadJavadoc(true);
                                idea.getModule().setDownloadSources(true);
                            });
                        }
                        pluginManager.apply(SpotBugsPlugin.class);
                        extensions.<SpotBugsExtension>configure("spotbugs", spotBugsExtension -> spotBugsExtension
                            .getExcludeFilter().set(staticAnalyseFolder.resolve("spotbug-exclude.xml").toFile()));

                        pluginManager.apply(SpotlessPlugin.class);
                        extensions.<SpotlessExtension>configure("spotless", spotlessExtension -> spotlessExtension
                            .java(spotlessJava -> {
                                spotlessJava.importOrder();
                                spotlessJava.removeUnusedImports();
                                spotlessJava.palantirJavaFormat();
                                spotlessJava.formatAnnotations();
                                spotlessJava.encoding("UTF-8");
                                spotlessJava.endWithNewline();
                                spotlessJava.setLineEndings(LineEnding.UNIX);
                                spotlessJava.trimTrailingWhitespace();
                                spotlessJava.cleanthat();
                            }));

                        // -------------------- Apply common plugins end --------------------
                    }
                );
                // -------------------- Configure Java end --------------------

                // -------------------- Configure libraries publishing start --------------------
                if (!isGradlePlugin) {
                    pluginManager.withPlugin(
                        "maven-publish",
                        plugin -> {
                            pluginManager.apply(SigningPlugin.class);
                            var publishingExtension = extensions.getByType(PublishingExtension.class);
                            var signingExtension = extensions.getByType(SigningExtension.class);

                            var javaPluginExtension = extensions.getByType(JavaPluginExtension.class);
                            javaPluginExtension.withJavadocJar();
                            javaPluginExtension.withSourcesJar();

                            var createdMavenPublication = publishingExtension.getPublications().create(
                                "mavenJava",
                                MavenPublication.class,
                                mavenPublication -> {
                                    mavenPublication.from(components.getByName("java"));
                                    mavenPublication.versionMapping(
                                        versionMappingStrategy -> {
                                            versionMappingStrategy.usage(
                                                "java-api",
                                                variantVersionMappingStrategy ->
                                                    variantVersionMappingStrategy.fromResolutionOf(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
                                            versionMappingStrategy.usage("java-runtime", VariantVersionMappingStrategy::fromResolutionResult);
                                        }
                                    );
                                    mavenPublication.pom(pom -> {
                                        pom.getUrl().set(PLUGIN_GIT_URL);
                                        pom.licenses(licenses -> {
                                            licenses.license(pluginLicense());
                                        });
                                        pom.developers(developers -> {
                                            developers.developer(pluginDeveloper());
                                        });
                                    });
                                }
                            );
                            signingExtension.sign(createdMavenPublication);
                        }
                    );
                }
                // -------------------- Configure publishing end --------------------

            // Prepare aggregate static analysis tasks for future configuration
            var staticAnalyseMain = tasks.register(
                "staticAnalyseMain",
                task -> {
                    task.setGroup("static analysis");
                    task.setDescription("Run static analysis on main sources");
                    task.dependsOn("spotbugsMain");
                }
            );
            var staticAnalyseTest = tasks.register(
                "staticAnalyseTest",
                task -> {
                    task.setGroup("static analysis");
                    task.setDescription("Run static analysis on test sources");
                }
            );
                // -------------------- Configure tests start --------------------
                pluginManager.withPlugin(
                    "java",
                    plugin -> {
                        var testing = (TestingExtension) extensions.getByName("testing");
                        var suites = testing.getSuites();
                        var integrationTestTaskName = internalConventionExtension.getIntegrationTestName().get();
                        var integrationTestSuite = suites.register(
                            integrationTestTaskName,
                            JvmTestSuite.class
                        );
                        suites.configureEach(
                            suite -> {
                                if (suite instanceof JvmTestSuite jvmTestSuite) {
                                    staticAnalyseTest.configure(task -> task.dependsOn("spotbugs" + capitalize(suite.getName())));
                                    jvmTestSuite.useJUnitJupiter();
                                    jvmTestSuite.dependencies(
                                        jvmComponentDependencies -> {
                                            var implementation = jvmComponentDependencies.getImplementation();
                                            implementation.add(jvmComponentDependencies.project());
                                        }
                                    );
                                    jvmTestSuite.sources(sourceSet -> {
                                        var compileJavaTaskName = sourceSet.getCompileJavaTaskName();
                                        tasks.named(compileJavaTaskName, JavaCompile.class).configure(compileTestJava -> {
                                            var compileOpts = compileTestJava.getOptions();
                                            compileOpts.getRelease().set(internalJavaVersion);
                                            compileOpts.getCompilerArgs().addAll(List.of(
                                                "-Xlint:-processing",
                                                "-Xlint:all",
                                                "-Werror"
                                            ));
                                            compileTestJava.finalizedBy("spotbugs" + capitalize(sourceSet.getName()));
                                        });
                                    });
                                    jvmTestSuite.getTargets().all(
                                        target -> target.getTestTask().configure(
                                            test -> {
                                                test.getOutputs().upToDateWhen(task -> false);
                                                test.testLogging(
                                                    logging -> {
                                                        logging.setShowStandardStreams(true);
                                                        logging.setShowStackTraces(true);
                                                    }
                                                );
                                                test.setFailFast(internalEnvironment.isCi());
                                                var environment = new HashMap<>(test.getEnvironment());
                                                environment.put("TESTCONTAINERS_REUSE_ENABLE", "true");
                                                test.setEnvironment(environment);
                                                var memory = test.getName().equals(JavaPlugin.TEST_TASK_NAME) ? "512m" : "1024m";
                                                test.setJvmArgs(
                                                    Stream.of(
                                                            test.getJvmArgs(),
                                                            Arrays.asList("-Xms" + memory, "-Xmx" + memory),
                                                            Arrays.asList("-XX:TieredStopAtLevel=1", "-noverify")
                                                        )
                                                        .flatMap(Collection::stream)
                                                        .collect(Collectors.toList())
                                                );
                                            }
                                        )
                                    );
                                }
                            }
                        );
                        tasks.named("check", task -> task.dependsOn(integrationTestSuite));

                        // configure integration test configurations
                        configurations.named(
                            integrationTestTaskName + "Implementation",
                            configuration -> configuration.extendsFrom(configurations.getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME))
                        );
                        configurations.named(
                            integrationTestTaskName + "AnnotationProcessor",
                            configuration -> configuration.extendsFrom(configurations.getByName(JavaPlugin.TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME))
                        );
                        configurations.named(
                            integrationTestTaskName + "CompileOnly",
                            configuration -> configuration.extendsFrom(configurations.getByName(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME))
                        );
                        configurations.named(
                            integrationTestTaskName + "RuntimeOnly",
                            configuration -> configuration.extendsFrom(configurations.getByName(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME))
                        );
                        // -------------------- Configure tests end --------------------


                        // -------------------- Configure static analysis start --------------------
                        tasks.register(
                            "staticAnalyseFull",
                            task -> {
                                task.setGroup("static analysis");
                                task.setDescription("Run static analysis on all sources");
                                task.dependsOn(staticAnalyseMain, staticAnalyseTest, "spotlessCheck");
                            }
                        );

                        tasks.named(
                            "check",
                            task -> {
                                task.dependsOn(staticAnalyseMain);
                            }
                        );

                        // Configure pmd
                        pluginManager.withPlugin(
                            "pmd",
                            pmd -> {
                                var pmdExtension = (PmdExtension) extensions.getByName("pmd");
                                pmdExtension.setRuleSetFiles(
                                    projectLayout.files(
                                        staticAnalyseFolder.resolve("pmd.xml")
                                    )
                                );
                                pmdExtension.setToolVersion(internalProperties.getVersion("pmdTool"));
                                var pmdMainTask = tasks.named(
                                    "pmdMain",
                                    Pmd.class,
                                    pmdTask -> pmdTask.setRuleSetFiles(
                                        projectLayout.files(
                                            staticAnalyseFolder.resolve("pmd.xml")
                                        )
                                    )
                                );
                                var pmdTestTasks = Stream.of(JavaPlugin.TEST_TASK_NAME, internalConventionExtension.getIntegrationTestName().get())
                                    .map(testTaskName -> "pmd" + capitalize(testTaskName))
                                    .map(
                                        taskName -> tasks.named(
                                            taskName,
                                            Pmd.class,
                                            pmdTask -> pmdTask.setRuleSetFiles(
                                                projectLayout.files(
                                                    staticAnalyseFolder.resolve("pmd-test.xml")
                                                )
                                            )
                                        )
                                    )
                                    .toList();
                                staticAnalyseMain.configure(task -> task.dependsOn(pmdMainTask));
                                staticAnalyseTest.configure(task -> task.dependsOn(pmdTestTasks));
                            }
                        );

                        // Configure checkstyle
                        pluginManager.withPlugin(
                            "checkstyle",
                            checkstyle -> {
                                var checkstyleExtension = extensions.getByType(CheckstyleExtension.class);
                                checkstyleExtension.setToolVersion(internalProperties.getVersion("checkstyleTool"));
                                checkstyleExtension.setMaxWarnings(0);
                                checkstyleExtension.setMaxErrors(0);
                                checkstyleExtension.setConfigFile(staticAnalyseFolder.resolve("checkstyle.xml").toFile());

                                var checkstyleMainTask = tasks.named("checkstyleMain");
                                var checkstyleTestTasks = Stream.of("test", internalConventionExtension.getIntegrationTestName().get())
                                    .map(string -> "checkstyle" + string.substring(0, 1).toUpperCase() + string.substring(1))
                                    .map(taskName -> tasks.named(taskName, Task.class))
                                    .collect(Collectors.toList());

                                staticAnalyseMain.configure(task -> task.dependsOn(checkstyleMainTask));
                                staticAnalyseTest.configure(task -> task.dependsOn(checkstyleTestTasks));
                            }
                        );
                        // -------------------- Configure static analysis end --------------------
                    }
                );
            }
        );
    }

    private String capitalize(String string) {
        var chars = string.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }
}
