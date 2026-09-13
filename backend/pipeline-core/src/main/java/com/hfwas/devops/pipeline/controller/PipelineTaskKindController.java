package com.hfwas.devops.pipeline.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.pipeline.dto.TaskKindUpdateDTO;
import com.hfwas.devops.pipeline.dto.TaskKindVO;
import com.hfwas.devops.pipeline.service.PipelineTaskKindService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pipeline/task-kinds")
public class PipelineTaskKindController {

    private final PipelineTaskKindService taskKindService;

    public PipelineTaskKindController(PipelineTaskKindService taskKindService) {
        this.taskKindService = taskKindService;
    }

    @GetMapping
    public BaseResult<List<TaskKindVO>> list() {
        return BaseResult.ok(taskKindService.listAll());
    }

    @GetMapping("/{kind}")
    public BaseResult<TaskKindVO> get(@PathVariable("kind") String kind) {
        return BaseResult.ok(taskKindService.getByKind(kind));
    }

    @PutMapping("/{kind}")
    public BaseResult<Void> update(
            @PathVariable("kind") String kind,
            @Valid @RequestBody TaskKindUpdateDTO dto
    ) {
        taskKindService.update(kind, dto);
        return BaseResult.ok(null);
    }

    @PatchMapping("/{kind}/toggle")
    public BaseResult<Void> toggle(@PathVariable("kind") String kind) {
        taskKindService.toggle(kind);
        return BaseResult.ok(null);
    }

    @PostMapping("/{kind}/validate")
    public BaseResult<ValidationResult> validateTemplate(
            @PathVariable("kind") String kind,
            @RequestBody Map<String, String> body
    ) {
        String script = body != null ? body.get("script") : null;
        if (script == null || script.isBlank()) {
            return BaseResult.ok(new ValidationResult(true, List.of()));
        }
        return BaseResult.ok(validateShell(script));
    }

    private ValidationResult validateShell(String script) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("sh-validate-", ".sh");
            Files.writeString(tmp, script);
            Process process = new ProcessBuilder("bash", "-n", tmp.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean valid = process.waitFor() == 0;
            List<String> errors = valid ? List.of() :
                    Arrays.stream(output.split("\n"))
                            .filter(line -> line.contains("error:") || line.contains("line "))
                            .map(String::trim)
                            .toList();
            return new ValidationResult(valid, errors);
        } catch (IOException | InterruptedException e) {
            return new ValidationResult(false, List.of("验证失败: " + e.getMessage()));
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public record ValidationResult(boolean valid, List<String> errors) {}
}