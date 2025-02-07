package com.hfwas.devops.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.hfwas.devops.language.java.JavaDependency;

import java.lang.reflect.Type;

/**
 * @author houfei
 * @package com.hfwas.devops.config
 * @date 2025/2/7
 */
public class JavaDependencySerializer implements JsonSerializer<JavaDependency> {
    @Override
    public JsonElement serialize(JavaDependency javaDependency, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject json = javaDependency.toJSON();
        return json;
    }
}
