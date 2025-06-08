package com.hfwas.devops.service;

import com.hfwas.devops.entity.Node;
import org.springframework.stereotype.Service;

/**
 * @author houfei
 * @package com.hfwas.devops.service
 * @date 2025/6/6
 */
@Service
public interface NodeService {

    boolean saveNode(Node node);

    boolean editNode(Node node);

    boolean deleteNode(Long id);

    boolean tree(Node node);

}
