package io.github.shitikanth.dependencyanalyzer;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.analyzer.DefaultProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzerException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.component.annotations.Requirement;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A starter-aware project dependency analyzer that extends the default analyzer
 * to handle Spring Boot starter dependencies more intelligently.
 */
public class StarterAwareDependencyAnalyzer extends DefaultProjectDependencyAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(StarterAwareDependencyAnalyzer.class);
    private static final Pattern STARTER_PATTERN = Pattern.compile("^spring-boot-starter(-[a-z0-9-]+)?$");
    @Requirement
    private DependencyGraphBuilder dependencyGraphBuilder;

    private final Map<Artifact, Set<Artifact>> starterDependencies = new HashMap<>();

    @Override
    public ProjectDependencyAnalysis analyze(MavenProject project)
            throws ProjectDependencyAnalyzerException {
        LOGGER.debug("StarterAwareDependencyAnalyzer is invoked!");

		ProjectDependencyAnalysis analysis = super.analyze(project);
        try {
            findStarterDependencies(project);
        } catch (DependencyGraphBuilderException e) {
            throw new ProjectDependencyAnalyzerException("Could not build dependency graph", e);
        }
        return removeFalsePositives(analysis);
    }

    @Override
    public ProjectDependencyAnalysis analyze(MavenProject project, Collection<String> excludedClasses)
        throws ProjectDependencyAnalyzerException {
        LOGGER.debug("StarterAwareDependencyAnalyzer is invoked!");

        // For now, simply delegate to the superclass to keep the test failing
        ProjectDependencyAnalysis analysis = super.analyze(project, excludedClasses);
        try {
            findStarterDependencies(project);
        } catch (DependencyGraphBuilderException e) {
            throw new ProjectDependencyAnalyzerException("Could not build dependency graph", e);
        }

        return removeFalsePositives(analysis);
    }

    private void findStarterDependencies(MavenProject project) throws DependencyGraphBuilderException {
        ProjectBuildingRequest buildingRequest =
            new DefaultProjectBuildingRequest(project.getProjectBuildingRequest());
        buildingRequest.setProject(project);
        buildingRequest.setProcessPlugins(false);
        DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);
        processDependencies(rootNode,  null);

        for (Artifact starter : starterDependencies.keySet()) {
            LOGGER.debug("{}", starter);
            for (Artifact dependency : starterDependencies.get(starter)) {
                LOGGER.debug("   {}", dependency);
            }
        }
    }

    private @NonNull ProjectDependencyAnalysis removeFalsePositives(ProjectDependencyAnalysis analysis) {
        Set<Artifact> starterDeps = starterDependencies.values()
            .stream()
            .flatMap(Set::stream)
            .collect(Collectors.toSet());

        Set<Artifact> usedUndeclared = analysis.getUsedUndeclaredArtifacts()
            .stream()
            .filter(o -> !starterDeps.contains(o))
            .collect(Collectors.toSet());
        Set<Artifact> unusedDeclared = analysis.getUnusedDeclaredArtifacts()
            .stream()
            .filter(key -> !starterDependencies.containsKey(key))
            .collect(Collectors.toSet());

        return new ProjectDependencyAnalysis(
            analysis.getUsedDeclaredArtifacts(),
            usedUndeclared,
            unusedDeclared,
            analysis.getTestArtifactsWithNonTestScope()
        );
    }

    private void processDependencies(DependencyNode node, Artifact starter) {
        if (node == null) {
            return;
        }
        Artifact artifact = node.getArtifact();
        if (starter == null) {
            if (isStarter(artifact)) {
                starter = artifact;
            }
        } else {
            starterDependencies.computeIfAbsent(starter, k -> new HashSet<>())
                .add(artifact);
        }
        for (DependencyNode child : node.getChildren()) {
            processDependencies(child, starter);
        }

    }

    static boolean isStarter(Artifact artifact) {
        return artifact.getGroupId().equals("org.springframework.boot") &&
            STARTER_PATTERN.matcher(artifact.getArtifactId()).matches();
    }
}