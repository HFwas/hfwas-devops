package com.hfwas.devops.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hfwas.devops.language.java.JavaDependency;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author houfei
 * @package com.hfwas.devops.config
 * @date 2025/2/7
 */
@Configuration
public class GsonConfiguration {

    @Bean
    public Gson gson() {
        // 创建并注册自定义序列化器
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(JavaDependency.class, new JavaDependencySerializer())
                .create();
        return gson;
    }

}
