package com.hfwas.devops.service.impl;

import com.hfwas.devops.entity.Node;
import com.hfwas.devops.mapper.NodeMapper;
import com.hfwas.devops.service.NodeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author houfei
 * @package com.hfwas.devops.service.impl
 * @date 2025/6/6
 */
@Service
@AllArgsConstructor
public class NodeServiceImpl implements NodeService {

    private NodeMapper nodeMapper;


    @Override
    public boolean saveNode(Node node) {
        return false;
    }

    @Override
    public boolean editNode(Node node) {
        return false;
    }

    @Override
    public boolean deleteNode(Long id) {
        return false;
    }

    @Override
    public boolean tree(Node node) {
        return false;
    }
}
