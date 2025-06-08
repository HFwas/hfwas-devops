package com.hfwas.devops.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.entity.Node;
import com.hfwas.devops.service.NodeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author houfei
 * @package com.hfwas.devops.controller
 * @date 2025/6/6
 */
@RestController("/nodes")
@AllArgsConstructor
public class NodeController {

    private NodeService nodeService;

    @PostMapping("/saveNode")
    public BaseResult saveNode(@RequestBody Node node) {
        nodeService.saveNode(node);
        return BaseResult.ok();
    }

    @PostMapping("/edit")
    public BaseResult editNode(@RequestBody Node node) {
        nodeService.editNode(node);
        return BaseResult.ok();
    }

    @PostMapping("/delete")
    public BaseResult deleteNode(@RequestParam Long id) {
        nodeService.deleteNode(id);
        return BaseResult.ok();
    }

    @PostMapping("/tree")
    public BaseResult treeNode(@RequestBody Node node) {
        nodeService.tree(node);
        return BaseResult.ok();
    }

}
