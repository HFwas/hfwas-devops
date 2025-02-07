package com.hfwas.devops.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.hfwas.devops.language.java.JavaDependency;
import com.hfwas.devops.language.java.JavaManifest;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * @author houfei
 * @package com.hfwas.devops.config
 * @date 2025/2/7
 */
public class JavaManifestSerializer implements JsonSerializer<JavaManifest> {
    @Override
    public JsonElement serialize(JavaManifest javaManifest, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject jsonObject = new JsonObject();

        // 处理 resolved 字段
        JsonObject resolvedJson = new JsonObject();
        for (Map.Entry<String, JavaDependency> entry : javaManifest.getResolved().entrySet()) {
            JavaDependency dependency = entry.getValue();
            JsonObject dependencyJSON = dependency.toJSON();
            resolvedJson.add(entry.getKey(), dependencyJSON);
        }
        jsonObject.add("resolved", resolvedJson);

        return jsonObject;
    }
}
