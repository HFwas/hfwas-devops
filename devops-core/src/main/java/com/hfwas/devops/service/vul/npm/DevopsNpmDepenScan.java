package com.hfwas.devops.service.vul.npm;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import com.hfwas.devops.service.vul.DepenScanFactory;
import com.hfwas.devops.tools.api.depency.NpmApi;
import feign.Response;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.vul.npm
 * @date 2025/1/13
 */
@Service("DevopsNpmDepenScan")
public class DevopsNpmDepenScan extends AbstractDepenScan implements InitializingBean {

    @Resource
    NpmApi npmApi;

    @Override
    public String language() {
        return "JavaScript";
    }

    @Override
    public String type() {
        return "npm";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        DepenScanFactory.register("npm", this);
    }

    @Override
    public List<DevopsVulCodeDependency> dependencys(MultipartFile multipartFile) throws IOException {
        List<DevopsVulCodeDependency> devopsVulDependencies = new ArrayList<>();
        byte[] bytes = multipartFile.getBytes();
        Gson gson = new Gson();
        JsonObject npmJson = gson.fromJson(new String(bytes), JsonObject.class);
        JsonObject asJsonObject = npmJson.getAsJsonObject("packages");

        JsonObject nullJsonObject = asJsonObject.getAsJsonObject("");
        if (nullJsonObject.has("devDependencies")) {
            JsonObject devDependencies = nullJsonObject.getAsJsonObject("devDependencies");
            Set<String> keySet = devDependencies.keySet();
        }

        Set<String> keySet = asJsonObject.keySet();
        for (String key : keySet) {
            if (key.equals("")) continue;
            String version = asJsonObject.getAsJsonObject(key).get("version").getAsString();
            if (key.startsWith("node_modules")) {
                DevopsVulCodeDependency devopsVulDependency = new DevopsVulCodeDependency();
                String substring = key.substring("node_modules".length()+1);
                boolean contains = substring.contains("/");
                if (contains) {
                    String[] split = substring.split("/");
                    devopsVulDependency.setCompany(split[0]);
                    devopsVulDependency.setDependencyName(split[1]);
                    devopsVulDependency.setVersion(version);
                    devopsVulDependency.setType(2);
                    devopsVulDependencies.add(devopsVulDependency);
                } else {
                    String[] split = substring.split("/");
                    devopsVulDependency.setCompany(split[0]);
                    devopsVulDependency.setDependencyName(null);
                    devopsVulDependency.setVersion(version);
                    devopsVulDependency.setType(2);
                    devopsVulDependencies.add(devopsVulDependency);
                }
            }
        }
        return devopsVulDependencies;
    }

    @Override
    public List<String> depenVersion(String depen, String version) {
        List<String> depenVersions = Lists.newArrayList();
        try {
            Gson gson = new Gson();
            Response response = npmApi.depenInfo(depen);
            byte[] bytes = response.body().asInputStream().readAllBytes();
            String s = new String(bytes);
            JsonObject jsonObject = gson.fromJson(s, JsonObject.class);
            Set<String> versions = jsonObject.getAsJsonObject("versions").keySet();
            depenVersions.addAll(versions);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        if (!Strings.isNullOrEmpty(version)) {
            String preVersion = depenVersions.get(depenVersions.indexOf(version) + 1);
            return Lists.newArrayList(preVersion);
        }
        return depenVersions;
    }
}
