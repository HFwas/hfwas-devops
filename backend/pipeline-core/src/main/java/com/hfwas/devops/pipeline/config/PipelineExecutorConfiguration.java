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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class PipelineExecutorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutorConfiguration.class);

    /**
     * KubernetesClient bean — 仅当 kubeconfig 文件存在时才注册。
     * PodExecController / WebSocket Handler 也通过 ObjectProvider 注入此 bean。
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @Conditional(KubeconfigPresentCondition.class)
    public KubernetesClient kubernetesClient(
            @Value("${pipeline.kubeconfig:}") String kubeconfig
    ) throws IOException {
        Config config = Config.fromKubeconfig(Files.readString(Path.of(kubeconfig)));
        log.info("KubernetesClient initialized from {}", kubeconfig);
        return new KubernetesClientBuilder().withConfig(config).build();
    }

    @Bean
    public PipelineExecutor pipelineExecutor(
            ObjectProvider<KubernetesClient> kubernetesClients,
            @Value("${pipeline.namespace:hfwas-pipeline}") String namespace,
            @Value("${pipeline.git-http-proxy:}") String gitHttpProxy,
            @Value("${pipeline.git-docker-host:}") String gitDockerHost,
            @Value("${pipeline.api-endpoint:}") String apiEndpoint,
            PipelineMapper pipelineMapper,
            PipelineStageMapper stageMapper,
            PipelineJobMapper jobMapper,
            PipelineRunMapper runMapper,
            PipelineRunJobMapper runJobMapper,
            PipelineCredentialService credentialService,
            PipelineTaskKindMapper taskKindMapper
    ) {
        KubernetesClient client = kubernetesClients.getIfAvailable();
        if (client == null) {
            log.warn("kubeconfig 未配置或文件不存在，流水线暂不能真正执行");
            return new UnavailablePipelineExecutor();
        }
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
                gitDockerHost,
                apiEndpoint
        );
    }

    /**
     * 仅当 kubeconfig 属性非空且文件存在时注册 K8sClient bean。
     */
    static class KubeconfigPresentCondition implements org.springframework.context.annotation.Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String kubeconfig = context.getEnvironment().getProperty("pipeline.kubeconfig", "");
            if (!StringUtils.hasText(kubeconfig)) {
                return false;
            }
            return Files.isRegularFile(Path.of(kubeconfig));
        }
    }
}