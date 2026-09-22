package tasks

import (
	"fmt"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
)

// Order 按 ProductTask 的 before/after 声明解出拓扑层次，同层可并行。
//
// 与 pkg/deps.LayerSort 同源（Kahn 入度法），区别是这里的节点是任务名称、
// 边来自 before/after 而不是组件依赖。
//
// 语义：
//   - task.taskOrder.after[]  → 那些任务必须先完成（那些 -> 本任务）
//   - task.taskOrder.before[] → 本任务必须先完成（本任务 -> 那些）
//
// 同一对先后关系被双方各声明一次（A.before=[B] 且 B.after=[A]）只算一条边，
// 否则入度会被重复累加而误判成环。
func Order(tasks []deliveryv1.ProductTask) ([][]string, error) {
	known := make(map[string]bool, len(tasks))
	for i := range tasks {
		known[tasks[i].Name] = true
	}

	graph := make(map[string][]string, len(tasks))
	inDegree := make(map[string]int, len(tasks))
	for i := range tasks {
		name := tasks[i].Name
		if _, ok := graph[name]; !ok {
			graph[name] = []string{}
		}
		if _, ok := inDegree[name]; !ok {
			inDegree[name] = 0
		}
	}

	// edges 去重：map[from]map[to]struct{}
	edges := make(map[string]map[string]bool)
	addEdge := func(from, to string) {
		if !known[from] || !known[to] || from == to {
			return
		}
		if edges[from] == nil {
			edges[from] = make(map[string]bool)
		}
		if edges[from][to] {
			return
		}
		edges[from][to] = true
		graph[from] = append(graph[from], to)
		inDegree[to]++
	}

	for i := range tasks {
		name := tasks[i].Name
		ord := tasks[i].Spec.TaskOrder
		if ord == nil {
			continue
		}
		for _, dep := range ord.After {
			addEdge(dep, name)
		}
		for _, nxt := range ord.Before {
			addEdge(name, nxt)
		}
	}

	// 第一层：入度为 0（无前置）
	queue := make([]string, 0, len(tasks))
	for i := range tasks {
		if inDegree[tasks[i].Name] == 0 {
			queue = append(queue, tasks[i].Name)
		}
	}

	layers := make([][]string, 0, len(tasks))
	ordered := 0
	for len(queue) > 0 {
		layers = append(layers, append([]string{}, queue...))
		ordered += len(queue)

		next := make([]string, 0)
		for _, node := range queue {
			for _, nb := range graph[node] {
				inDegree[nb]--
				if inDegree[nb] == 0 {
					next = append(next, nb)
				}
			}
		}
		queue = next
	}

	if ordered != len(tasks) {
		return nil, fmt.Errorf("circular task order detected: ordered %d/%d tasks", ordered, len(tasks))
	}
	return layers, nil
}

// ByName 把任务列表转成按名称索引的 map。
func ByName(tasks []deliveryv1.ProductTask) map[string]deliveryv1.ProductTask {
	out := make(map[string]deliveryv1.ProductTask, len(tasks))
	for i := range tasks {
		out[tasks[i].Name] = tasks[i]
	}
	return out
}

// ReadyToRun 判断任务的所有前置（after）是否都已 Succeeded。
// 参考了不存在的任务名时视为未满足——宁可挡住，也不放过顺序。
func ReadyToRun(task *deliveryv1.ProductTask, byName map[string]deliveryv1.ProductTask) (bool, string) {
	if task.Spec.TaskOrder == nil {
		return true, ""
	}
	for _, dep := range task.Spec.TaskOrder.After {
		t, ok := byName[dep]
		if !ok {
			return false, fmt.Sprintf("前置任务 %s 不存在", dep)
		}
		if t.Status.Phase != deliveryv1.TaskPhaseSucceeded {
			return false, fmt.Sprintf("等待前置任务 %s（当前 %s）", dep, phaseOrPending(&t))
		}
	}
	return true, ""
}

// GatesComponent 判断是否存在以 (namespace, component) 为 refComponent 且尚未 Succeeded 的任务。
// 返回 true 表示该 CloudComponent 应被挡住、等待任务完成。
func GatesComponent(tasks []deliveryv1.ProductTask, namespace, component string) (bool, string) {
	for i := range tasks {
		ref := tasks[i].Spec.RefComponent
		if ref == nil || ref.Component == "" || ref.Component != component {
			continue
		}
		if ref.Namespace != "" && ref.Namespace != namespace {
			continue
		}
		if tasks[i].Status.Phase != deliveryv1.TaskPhaseSucceeded {
			return true, fmt.Sprintf("被 ProductTask %s 挡住（当前 %s）", tasks[i].Name, phaseOrPending(&tasks[i]))
		}
	}
	return false, ""
}

// phaseOrPending 返回任务的阶段，未设置时按 Pending 处理。
func phaseOrPending(t *deliveryv1.ProductTask) deliveryv1.TaskPhase {
	if t.Status.Phase == "" {
		return deliveryv1.TaskPhasePending
	}
	return t.Status.Phase
}
