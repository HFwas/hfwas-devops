package com.hfwas.devops.image.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ImageBatchConvertRequest {

    @NotEmpty
    private List<String> sessionIds;

    @NotNull
    @Valid
    private ImageConvertRequest convert;
}
