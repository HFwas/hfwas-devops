package deps

import (
	"fmt"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
)

// TopologicalSort 对组件进行拓扑排序。
// 使用 Kahn 算法（BFS 入度法）。
// 返回按依赖排序的组件名称列表（无依赖的先出）。
// 如果存在循环依赖，返回错误。
func TopologicalSort(components []deliveryv1.ComponentDef) ([]string, error) {
	// 构建名称索引
	nameIndex := make(map[string]int)
	for i, comp := range components {
		nameIndex[comp.Name] = i
	}

	// 构建邻接表和入度表
	graph := make(map[string][]string)
	inDegree := make(map[string]int)

	for _, comp := range components {
		if _, ok := graph[comp.Name]; !ok {
			graph[comp.Name] = []string{}
		}
		if _, ok := inDegree[comp.Name]; !ok {
			inDegree[comp.Name] = 0
		}
	}

	// 处理依赖关系
	for _, comp := range components {
		for _, dep := range comp.Dependencies {
			if dep.Type != deliveryv1.DependencyOptional {
				// dep.Component 必须先于 comp.Name
				graph[dep.Component] = append(graph[dep.Component], comp.Name)
				inDegree[comp.Name]++
			}
		}
	}

	// BFS：入度为 0 的节点先出
	queue := make([]string, 0)
	for _, comp := range components {
		if inDegree[comp.Name] == 0 {
			queue = append(queue, comp.Name)
		}
	}

	result := make([]string, 0)
	for len(queue) > 0 {
		node := queue[0]
		queue = queue[1:]
		result = append(result, node)

		for _, neighbor := range graph[node] {
			inDegree[neighbor]--
			if inDegree[neighbor] == 0 {
				queue = append(queue, neighbor)
			}
		}
	}

	// 检查是否所有节点都已排序（检测环）
	if len(result) != len(components) {
		return nil, fmt.Errorf("circular dependency detected: sorted %d/%d components",
			len(result), len(components))
	}

	return result, nil
}

// LayerSort 返回分层排序结果（同层可并行）。
func LayerSort(components []deliveryv1.ComponentDef) ([][]string, error) {
	nameIndex := make(map[string]int)
	for i, comp := range components {
		nameIndex[comp.Name] = i
	}

	graph := make(map[string][]string)
	inDegree := make(map[string]int)
	depth := make(map[string]int)

	for _, comp := range components {
		if _, ok := graph[comp.Name]; !ok {
			graph[comp.Name] = []string{}
		}
		if _, ok := inDegree[comp.Name]; !ok {
			inDegree[comp.Name] = 0
		}
		depth[comp.Name] = 0
	}

	for _, comp := range components {
		for _, dep := range comp.Dependencies {
			if dep.Type != deliveryv1.DependencyOptional {
				graph[dep.Component] = append(graph[dep.Component], comp.Name)
				inDegree[comp.Name]++
			}
		}
	}

	// 第一层：入度为 0
	queue := make([]string, 0)
	for _, comp := range components {
		if inDegree[comp.Name] == 0 {
			queue = append(queue, comp.Name)
			depth[comp.Name] = 0
		}
	}

	for len(queue) > 0 {
		node := queue[0]
		queue = queue[1:]

		for _, neighbor := range graph[node] {
			inDegree[neighbor]--
			if depth[neighbor] < depth[node]+1 {
				depth[neighbor] = depth[node] + 1
			}
			if inDegree[neighbor] == 0 {
				queue = append(queue, neighbor)
			}
		}
	}

	// 按 depth 分组
	maxDepth := 0
	for _, d := range depth {
		if d > maxDepth {
			maxDepth = d
		}
	}

	layers := make([][]string, maxDepth+1)
	for compName, d := range depth {
		layers[d] = append(layers[d], compName)
	}

	return layers, nil
}

// ResolveDependencies 检查给定组件的所有依赖是否已满足。
func ResolveDependencies(
	component *deliveryv1.ComponentDef,
	componentPhases map[string]deliveryv1.Phase,
) (bool, string) {
	for _, dep := range component.Dependencies {
		phase, exists := componentPhases[dep.Component]
		if !exists {
			if dep.Type == deliveryv1.DependencyOptional {
				continue
			}
			return false, fmt.Sprintf("dependency %s not found", dep.Component)
		}

		switch dep.Condition {
		case deliveryv1.DependencyReady:
			if phase != deliveryv1.PhaseReady {
				return false, fmt.Sprintf("dependency %s is not ready (phase: %s)", dep.Component, phase)
			}
		case deliveryv1.DependencyDeployed:
			if phase == deliveryv1.PhasePending || phase == deliveryv1.PhaseUnknown {
				return false, fmt.Sprintf("dependency %s is not deployed (phase: %s)", dep.Component, phase)
			}
		}
	}
	return true, ""
}