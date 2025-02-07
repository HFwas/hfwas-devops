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
public class PackageCache {
    private Map<String, JavaPackage> database;

    public PackageCache() {
        this.database = new HashMap<>();
    }

    public JavaPackage packages(PackageURL identifier) {
        JavaPackage javaPackage = this.lookupPackage(identifier);
        if (null != javaPackage) {
            return javaPackage;
        }
        JavaPackage javaPackage1 = new JavaPackage(identifier);
        this.addPackage(javaPackage1);
        return javaPackage1;
    }

    /**
     * getOrCreatePackage will create and add a new Package to the PackageCache if no package with a
     * matching identifier exists, or return an existing Package if a match is found.
     *
     * @param identifier a PackageURL identifying the package.
     * @return the Package corresponding to the identifier.
     */
    public JavaPackage getOrCreatePackage(PackageURL identifier) {
        JavaPackage existingDep = lookupPackage(identifier);
        if (existingDep != null) {
            return existingDep;
        }
        JavaPackage dep = new JavaPackage(identifier);
        addPackage(dep);
        return dep;
    }

    /**
     * Overloaded version that accepts a String identifier. The string must match the package URL format.
     *
     * @param identifier a String representation of the package URL.
     * @return the Package corresponding to the identifier.
     */
   public JavaPackage getOrCreatePackage(String identifier) {
       PackageURL purl = PackageURL.fromString(identifier);
       return getOrCreatePackage(purl);
   }

    /**
     * Returns all packages matching the given fields. Any parameter may be null to indicate that
     * it should not be used as a filter.
     *
     * @param namespace the namespace to match, or null.
     * @param name the name to match, or null.
     * @param version the version to match, or null.
     * @return a list of Packages that match the specified criteria.
     */
   public List<JavaPackage> packagesMatching(String namespace, String name, String version) {
       List<JavaPackage> result = new ArrayList<>();
       for (JavaPackage pkg : database.values()) {
           if (pkg.matching(namespace, name, version)) {
               result.add(pkg);
           }
       }
       return result;
   }

    /**
     * Adds a package to the cache.
     *
     * @param pkg the Package to add.
     */
    public void addPackage(JavaPackage pkg) {
        database.put(pkg.getPackageURL().toString(), pkg);
    }

    /**
     * Removes a package from the cache.
     *
     * @param pkg the Package to remove.
     */
    public void removePackage(JavaPackage pkg) {
        database.remove(pkg.getPackageURL().toString());
    }

    /**
     * Looks up and returns a package with a matching PackageURL, if one exists.
     *
     * @param identifier the PackageURL identifier.
     * @return the Package if found, or null otherwise.
     */
    public JavaPackage lookupPackage(PackageURL identifier) {
        return database.get(identifier.toString());
    }

    /**
     * Overloaded lookupPackage that accepts a String identifier.
     *
     * @param identifier a String representation of the package URL.
     * @return the Package if found, or null otherwise.
     */
   public JavaPackage lookupPackage(String identifier) {
       PackageURL purl = PackageURL.fromString(identifier);
       return lookupPackage(purl);
   }

    /**
     * Returns true if a package with a matching identifier exists in the cache.
     *
     * @param identifier the PackageURL identifier.
     * @return true if the package exists, false otherwise.
     */
    public boolean hasPackage(PackageURL identifier) {
        return lookupPackage(identifier) != null;
    }

    /**
     * Overloaded hasPackage that accepts a String identifier.
     *
     * @param identifier a String representation of the package URL.
     * @return true if the package exists, false otherwise.
     */
   public boolean hasPackage(String identifier) {
       return lookupPackage(identifier) != null;
   }

    /**
     * Returns the total number of packages tracked in the cache.
     *
     * @return the number of packages.
     */
    public int countPackages() {
        return database.size();
    }
}
