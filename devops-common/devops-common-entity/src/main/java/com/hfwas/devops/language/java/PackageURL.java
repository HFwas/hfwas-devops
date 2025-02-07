package com.hfwas.devops.language.java;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author houfei
 * @package com.hfwas.devops.language.java
 * @date 2025/2/6
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageURL {
    private String             type;
    
    private String             namespace;

    private String             name;
    
    private String             version;

    private Map<String,String> qualifiers;

    private String             subpath;

    /**
     * Parses a purl string into a PackageURL instance.
     *
     * @param purl the package URL string (must start with "pkg:")
     * @return a new PackageURL instance
     * @throws IllegalArgumentException if the purl is invalid
     */
    public static PackageURL fromString(String purl) {
        if (purl == null || purl.isEmpty()) {
            throw new IllegalArgumentException("purl must not be null or empty.");
        }
        if (!purl.startsWith("pkg:")) {
            throw new IllegalArgumentException("Invalid purl: must start with 'pkg:'");
        }

        // Remove "pkg:" prefix.
        String remainder = purl.substring(4);
        String subpath = null;
        // Check for subpath indicated by a '#' character.
        int hashIndex = remainder.indexOf('#');
        if (hashIndex != -1) {
            subpath = remainder.substring(hashIndex + 1);
            remainder = remainder.substring(0, hashIndex);
        }

        // Process qualifiers if present (after a '?').
        Map<String, String> qualifiers = new LinkedHashMap<>();
        int qIndex = remainder.indexOf('?');
        if (qIndex != -1) {
            String qualifiersStr = remainder.substring(qIndex + 1);
            remainder = remainder.substring(0, qIndex);
            // Qualifiers are key=value pairs separated by '&'
            String[] pairs = qualifiersStr.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    qualifiers.put(kv[0], kv[1]);
                } else {
                    qualifiers.put(kv[0], "");
                }
            }
        }

        // At this point, remainder should be: type/namespace/name@version
        // Find the first '/' to separate the type.
        int slashIndex = remainder.indexOf('/');
        if (slashIndex == -1) {
            throw new IllegalArgumentException("Invalid purl: missing '/' separator after type");
        }
        String type = remainder.substring(0, slashIndex);
        String pathAndVersion = remainder.substring(slashIndex + 1);

        // Check for version (indicated by '@')
        String version = null;
        int atIndex = pathAndVersion.lastIndexOf('@');
        if (atIndex != -1) {
            version = pathAndVersion.substring(atIndex + 1);
            pathAndVersion = pathAndVersion.substring(0, atIndex);
        }

        // Determine namespace and name.
        String namespace = null;
        String name;
        int lastSlashIndex = pathAndVersion.lastIndexOf('/');
        if (lastSlashIndex != -1) {
            namespace = pathAndVersion.substring(0, lastSlashIndex);
            name = pathAndVersion.substring(lastSlashIndex + 1);
        } else {
            name = pathAndVersion;
        }

        return new PackageURL(type, namespace, name, version, qualifiers, subpath);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("pkg:");
        sb.append(type);
        sb.append("/");
        if (namespace != null && !namespace.isEmpty()) {
            sb.append(namespace);
            sb.append("/");
        }
        sb.append(name);
        if (version != null && !version.isEmpty()) {
            sb.append("@");
            sb.append(version);
        }
        if (qualifiers != null && !qualifiers.isEmpty()) {
            sb.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : qualifiers.entrySet()) {
                if (!first) {
                    sb.append("&");
                }
                sb.append(entry.getKey());
                sb.append("=");
                sb.append(entry.getValue());
                first = false;
            }
        }
        if (subpath != null && !subpath.isEmpty()) {
            sb.append("#");
            sb.append(subpath);
        }
        return sb.toString();
    }

}
