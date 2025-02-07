package com.hfwas.devops.language.java;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author houfei
 * @package com.hfwas.devops.language.java
 * @date 2025/2/6
 */
@Data
public class MavenDependencyGraph {
    private Depgraph depGraph;

    private PackageCache cache;

    private Map<String, DepgraphArtifact> packageUrlToArtifact;

    private List<JavaPackage> directDependencies;

    public MavenDependencyGraph(Depgraph depGraph) {
        this.depGraph = depGraph;
        this.cache = new PackageCache();
        this.packageUrlToArtifact = new HashMap<>();
        this.directDependencies = new ArrayList<>();

        parseDependencies();
    }

    public JavaManifest createManifest(String filePath) {
        JavaManifest manifest;
        if (filePath != null && !filePath.isEmpty()) {
            manifest = new JavaManifest("getProjectName()", filePath);
        } else {
            manifest = new JavaManifest("getProjectName()", null);
        }
        // 对每个直接依赖，添加直接依赖及其传递依赖
        for (JavaPackage depPackage : directDependencies) {
            // 根据 packageURL 的字符串形式查找对应的 artifact 对象
            DepgraphArtifact artifact = packageUrlToArtifact.get(depPackage.getPackageURL().toString());
            DependencyScope scope = getDependencyScopeForMavenScope(artifact.getScopes());
            manifest.addDirectDependency(depPackage, scope);
            // 递归添加传递依赖
            addTransitiveDeps(manifest, depPackage.getDependencies());
        }
        return manifest;
    }

    /**
     * 递归处理传递依赖，将每个传递依赖添加为间接依赖
     *
     * @param manifest     Manifest 对象
     * @param dependencies 传递依赖列表
     */
    private void addTransitiveDeps(JavaManifest manifest, List<JavaPackage> dependencies) {
        if (dependencies != null) {
            for (JavaPackage transitiveDep : dependencies) {
                DepgraphArtifact transitiveDepArtifact = packageUrlToArtifact.get(transitiveDep.getPackageURL().toString());
                DependencyScope transitiveDepScope = getDependencyScopeForMavenScope(transitiveDepArtifact.getScopes());
                manifest.addIndirectDependency(transitiveDep, transitiveDepScope);
                // 继续递归处理子依赖
                addTransitiveDeps(manifest, transitiveDep.getDependencies());
            }
        }
    }

    /**
     * 根据 Maven 的 scope 列表映射到对应的 DependencyScope
     * 逻辑为：如果 scopes 中包含 "test"，则返回 DEVELOPMENT，否则返回 RUNTIME
     *
     * @param mavenScopes Maven 中的 scope 列表（可能为 null）
     * @return 映射后的 DependencyScope
     */
    private DependencyScope getDependencyScopeForMavenScope(List<String> mavenScopes) {
        if (mavenScopes != null && mavenScopes.contains("test")) {
            return DependencyScope.development;
        }
        return DependencyScope.runtime;
    }

    private Boolean checkForMultiModule(Depgraph depgraph) {
        List<DepgraphArtifact> artifacts = depgraph.getArtifacts();
        return null != artifacts && depgraph.getArtifacts().size() > 0;
    }

    private void parseDependencies() {
        Depgraph graph = this.depGraph;
        PackageCache cach = this.cache;

        List<DepgraphDependency> dependencies = graph.getDependencies();
        if (dependencies == null) {
            dependencies = new ArrayList<>();
        }
        List<String> rootArtifactIds = new ArrayList<>();
        Map<String, List<String>> dependencyIdMap = dependencyMap(dependencies);
        List<String> dependencyArtifactIdsWithParents = extractDependencyArtifactIdsWithParents(dependencies);
        Map<String, JavaPackage> idToPackageCachePackage = new HashMap<>();

        for (DepgraphArtifact artifact : graph.getArtifacts()) {
            PackageURL artifactUrl = artifactToPackageURL(artifact);
            JavaPackage pkg = cach.packages(artifactUrl); // Assuming getPackage returns a Package instance.
            idToPackageCachePackage.put(artifact.getId(), pkg);
            // Store a reference from the package URL (as String) to the original artifact.
            this.packageUrlToArtifact.put(artifactUrl.toString(), artifact);
            if (!dependencyArtifactIdsWithParents.contains(artifact.getId())) {
                rootArtifactIds.add(artifact.getId());
            }
        }

        // Process each dependency and link packages.
        for (String fromId : dependencyIdMap.keySet()) {
            JavaPackage pkg = idToPackageCachePackage.get(fromId);
            if (pkg == null) {
                throw new RuntimeException("Package '" + fromId + "' was not found in the cache.");
            }
            List<String> deps = dependencyIdMap.get(fromId);
            if (deps != null) {
                for (String dependencyId : deps) {
                    JavaPackage dependencyPkg = idToPackageCachePackage.get(dependencyId);
                    if (dependencyPkg == null) {
                        throw new RuntimeException("Failed to find a dependency package for '" + dependencyId + "'");
                    }
                    pkg.dependsOn(dependencyPkg);
                }
            }
        }

        // Determine direct dependencies.
        List<String> uniqueRootArtifactDependencies = new ArrayList<>();
        for (String rootArtifactId : rootArtifactIds) {
            List<String> dependencyIds = getDirectDependencies(rootArtifactId, dependencies);
            if (dependencyIds != null) {
                for (String dependencyId : dependencyIds) {
                    if (!uniqueRootArtifactDependencies.contains(dependencyId)) {
                        uniqueRootArtifactDependencies.add(dependencyId);
                    }
                }
            }
        }

        // Map each dependency id to a Package and store them.
        List<JavaPackage> directDeps = new ArrayList<>();
        for (String depId : uniqueRootArtifactDependencies) {
            JavaPackage pkg = idToPackageCachePackage.get(depId);
            directDeps.add(pkg);
        }
        this.directDependencies = directDeps;
    }

    // --- Helper methods ---

    private Map<String, List<String>> dependencyMap(List<DepgraphDependency> dependencies) {
        Map<String, List<String>> map = new HashMap<>();
        for (DepgraphDependency dependency : dependencies) {
            String from = dependency.getFrom();
            List<String> deps = map.get(from);
            if (deps == null) {
                deps = new ArrayList<>();
                map.put(from, deps);
            }
            deps.add(dependency.getTo());
        }
        return map;
    }

    /**
     * Returns a list of all dependency artifact IDs referenced as targets ("to") in the dependency list.
     */
    private List<String> extractDependencyArtifactIdsWithParents(List<DepgraphDependency> dependencies) {
        List<String> ids = new ArrayList<>();
        if (dependencies != null) {
            for (DepgraphDependency dependency : dependencies) {
                ids.add(dependency.getTo());
            }
        }
        return ids;
    }

    /**
     * Returns the list of direct dependency IDs for a given artifact id.
     */
    private List<String> getDirectDependencies(String artifactId, List<DepgraphDependency> dependencies) {
        List<String> directDeps = new ArrayList<>();
        for (DepgraphDependency dependency : dependencies) {
            if (artifactId.equals(dependency.getFrom())) {
                directDeps.add(dependency.getTo());
            }
        }
        return directDeps;
    }

    /**
     * Converts a DepgraphArtifact to a PackageURL.
     * (Assumes the existence of a corresponding method or constructor.)
     */
    private PackageURL artifactToPackageURL(DepgraphArtifact artifact) {
        // You may need to create or call an equivalent of the PackageURL constructor.
        // For example:
        Map<String, String> qualifiers = getArtifactQualifiers(artifact);
        return new PackageURL("maven",
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion(),
                qualifiers,
                null);
    }

    /**
     * Extracts qualifiers from the artifact (such as type and classifier).
     */
    private Map<String, String> getArtifactQualifiers(DepgraphArtifact artifact) {
        Map<String, String> qualifiers = new HashMap<>();
        if (artifact.getTypes() != null && !artifact.getTypes().isEmpty()) {
            qualifiers.put("type", artifact.getTypes().get(0));
        }
        if (artifact.getClassifiers() != null && !artifact.getClassifiers().isEmpty()) {
            qualifiers.put("classifier", artifact.getClassifiers().get(0));
        }
        return qualifiers.isEmpty() ? null : qualifiers;
    }
}
