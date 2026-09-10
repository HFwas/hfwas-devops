package com.hfwas.devops.container.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.container.dto.ImageSearchVO;
import com.hfwas.devops.container.service.registry.ImageSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for cross-registry image search and listing.
 * <p>
 * Provides a global view of images across all configured registries,
 * independent of the registry-scoped browsing in ImageController.
 * </p>
 */
@RestController
@RequestMapping("/container/images")
@RequiredArgsConstructor
public class ImageSearchController {

    private final ImageSearchService imageSearchService;

    @GetMapping("/search")
    public BaseResult<IPage<ImageSearchVO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return BaseResult.ok(imageSearchService.search(keyword, page, pageSize));
    }
}