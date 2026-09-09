package com.hfwas.devops.pipeline.config;

import com.hfwas.devops.pipeline.executor.PipelineExecutor;
import com.hfwas.devops.pipeline.executor.TektonPipelineExecutor;
import com.hfwas.devops.pipeline.executor.UnavailablePipelineExecutor;
import com.hfwas.devops.pipeline.mapper.PipelineJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunJobMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import com.hfwas.devops.pipeline.mapper.PipelineStageMapper;
import com.hfwas.devops.pipeline.mapper.PipelineTaskKindMapper;
import com.hfwas.devops.pipeline.service.PipelineCredentialService;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class PipelineExecutorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutorConfiguration.class);

    @Bean
    public PipelineExecutor pipelineExecutor(
            @Value("${pipeline.kubeconfig:}") String kubeconfig,
            @Value("${pipeline.namespace:hfwas-pipeline}") String namespace,
            @Value("${pipeline.git-http-proxy:}") String gitHttpProxy,
            @Value("${pipeline.git-docker-host:}") String gitDockerHost,
            PipelineMapper pipelineMapper,
            PipelineStageMapper stageMapper,
            PipelineJobMapper jobMapper,
            PipelineRunMapper runMapper,
            PipelineRunJobMapper runJobMapper,
            PipelineCredentialService credentialService,
            PipelineTaskKindMapper taskKindMapper
    ) {
        Path path = StringUtils.hasText(kubeconfig) ? Path.of(kubeconfig) : null;
        if (path == null || !Files.isRegularFile(path)) {
            return new UnavailablePipelineExecutor();
        }
        try {
            Config config = Config.fromKubeconfig(Files.readString(path));
            KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build();
            return new TektonPipelineExecutor(
                    client,
                    namespace,
                    pipelineMapper,
                    stageMapper,
                    jobMapper,
                    runMapper,
                    runJobMapper,
                    credentialService,
                    taskKindMapper,
                    gitHttpProxy,
                    gitDockerHost
            );
        } catch (Exception e) {
            log.error("无法初始化执行集群（{}），流水线暂不能真正执行", path, e);
            return new UnavailablePipelineExecutor();
        }
    }
}
