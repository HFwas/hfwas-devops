package com.hfwas.devops.language.java;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author houfei
 * @package com.hfwas.devops.language.java
 * @date 2025/2/6
 */
@Data
public class JavaPackage {

    private PackageURL        packageURL;

    private List<JavaPackage> dependencies;

    public JavaPackage dependsOn(JavaPackage javaPackage) {
        this.dependencies.add(javaPackage);
        return this;
    }

    public JavaPackage(PackageURL javaPackage) {
        this.packageURL = javaPackage;
        this.dependencies = new ArrayList<>();
    }

    public String packageID() {
        return this.packageURL.toString();
    }

    public List<String> getPackageDependencyIDs() {
        return dependencies.stream()
                .map(JavaPackage::packageID)
                .collect(Collectors.toList());
    }

    /**
     * 确定包是否符合给定的条件
     *
     * @param matcherNamespace namespace
     * @param matcherName      name
     * @param matcherVersion   version
     * @return
     */
    public boolean matching(String matcherNamespace, String matcherName, String matcherVersion) {
        if (matcherNamespace != null && !Objects.equals(packageURL.getNamespace(), matcherNamespace)) {
            return false;
        }
        if (matcherName != null && !Objects.equals(packageURL.getName(), matcherName)) {
            return false;
        }
        if (matcherVersion != null && !Objects.equals(packageURL.getVersion(), matcherVersion)) {
            return false;
        }
        return true;
    }


}
