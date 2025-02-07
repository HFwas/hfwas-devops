package com.hfwas.devops.language.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Data;

import java.util.List;

/**
 * @author houfei
 * @package com.hfwas.devops.language.java
 * @date 2025/2/7
 */
@Data
public class JavaDependency {
    private JavaPackage            depPackage;
    private DependencyRelationship relationship;
    private DependencyScope        scope;

    /**
     *
     * @param depPackage   依赖包
     * @param relationship 依赖关系
     * @param scope        依赖范围
     */
    public JavaDependency(JavaPackage depPackage, DependencyRelationship relationship, DependencyScope scope) {
        this.depPackage   = depPackage;
        this.relationship = relationship;
        this.scope        = scope;
    }

    public JsonObject toJSON() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("package_url", this.depPackage.getPackageURL().toString());
        jsonObject.addProperty("relationship", this.relationship.name());
        jsonObject.addProperty("scope", this.scope.name());
        List<String> packageDependencyIDs = this.depPackage.getPackageDependencyIDs();
        JsonArray jsonArray = new JsonArray();
        packageDependencyIDs.forEach(id -> jsonArray.add(id));
        jsonObject.add("dependencies", jsonArray);
        return jsonObject;
    }
}
