package tasks

import (
	"testing"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
)

// =============================================================================
// Order 测试
// =============================================================================

// layerOf 返回任务所在的层号，找不到返回 -1。
func layerOf(layers [][]string, name string) int {
	for i, layer := range layers {
		for _, n := range layer {
			if n == name {
				return i
			}
		}
	}
	return -1
}

func TestOrder_AfterChain(t *testing.T) {
	// init → migrate → gateway
	tasks := []deliveryv1.ProductTask{
		{ObjectMeta: meta("init")},
		{ObjectMeta: meta("migrate"), Spec: deliveryv1.ProductTaskSpec{
			TaskOrder: &deliveryv1.TaskOrder{After: []string{"init"}},
		}},
		{ObjectMeta: meta("gateway"), Spec: deliveryv1.ProductTaskSpec{
			TaskOrder: &deliveryv1.TaskOrder{After: []string{"migrate"}},
		}},
	}

	layers, err := Order(tasks)
	if err != nil {
		t.Fatalf("Order failed: %v", err)
	}
	if len(layers) != 3 {
		t.Fatalf("expected 3 layers, got %d: %v", len(layers), layers)
	}
	if layerOf(layers, "init") >= layerOf(layers, "migrate") {
		t.Errorf("init must precede migrate, got %v", layers)
	}
	if layerOf(layers, "migrate") >= layerOf(layers, "gateway") {
		t.Errorf("migrate must precede gateway, got %v", layers)
	}
}

func TestOrder_BeforeIsSymmetricToAfter(t *testing.T) {
	// 用 before 表达与 after 相同的顺序，结果应一致
	tasks := []deliveryv1.ProductTask{
		{ObjectMeta: meta("init"), Spec: deliveryv1.ProductTaskSpec{
			TaskOrder: &deliveryv1.TaskOrder{Before: []string{"gateway"}},
		}},
		{ObjectMeta: meta("gateway")},
	}

	layers, err := Order(tasks)
	if err != nil {
		t.Fatalf("Order failed: %v", err)
	}
	if layerOf(layers, "init") >= layerOf(layers, "gateway") {
		t.Errorf("init must precede gateway, got %v", layers)
	}
}

// 关键回归：同一对先后关系被双方各声明一次时只算一条边，不能误判成环。
func TestOrder_DoubleDeclaredEdgeIsNotACycle(t *testing.T) {
	tasks := []deliveryv1.ProductTask{
		{ObjectMeta: meta("A"), Spec: deliveryv1.ProductTaskSpec{
			TaskOrder: &deliveryv1.TaskOrder{Before: []string{"B"}},
		}},
		{ObjectMeta: meta("B"), Spec: deliveryv1.ProductTaskSpec{
			TaskOrder: &deliveryv1.TaskOrder{After: []string{"A"}},
		}},
	}

	layers, err := Order(tasks)
	if err != nil {
		t.Fatalf("重复声明的同一条边不应判为环，却报错: %v", err)
	}
	if layerOf(layers, "A") >= layerOf(layers, "B") {
		t.Errorf("A must precede B, got %v", layers)
	}
}

func TestOrder_IndependentTasksShareLayer(t *testing.T) {
	tasks := []deliveryv1.ProductTask{
		{ObjectMeta: meta("A")},
		{ObjectMeta: meta("B")},
		{ObjectMeta: meta("C"), Spec: deliveryv1.ProductTaskSpec{
			TaskOrder: &deliveryv1.TaskOrder{After: []string{"A", "B"}},
		}},
	}

	layers, err := Order(tasks)
	if err != nil {
		t.Fatalf("Order failed: %v", err)
	}
	if len(layers) != 2 {
		t.Fatalf("expected 2 layers, got %d: %v", len(layers), layers)
	}
	if layerOf(layers, "A") != layerOf(layers, "B") {
		t.Errorf("A and B should share layer 0, got %v", layers)
	}
}

func TestOrder_DetectsCycle(t *testing.T) {
	tasks := []deliveryv1.ProductTask{
		{ObjectMeta: meta("A"), Spec: deliveryv1.ProductTaskSpec{
			TaskOrder: &deliveryv1.TaskOrder{After: []string{"B"}},
		}},
		{ObjectMeta: meta("B"), Spec: deliveryv1.ProductTaskSpec{
			TaskOrder: &deliveryv1.TaskOrder{After: []string{"A"}},
		}},
	}

	if _, err := Order(tasks); err == nil {
		t.Error("expected circular task order error, got nil")
	}
}

func TestOrder_IgnoresUnknownReferences(t *testing.T) {
	tasks := []deliveryv1.ProductTask{
		{ObjectMeta: meta("A"), Spec: deliveryv1.ProductTaskSpec{
			TaskOrder: &deliveryv1.TaskOrder{After: []string{"does-not-exist"}},
		}},
	}

	layers, err := Order(tasks)
	if err != nil {
		t.Fatalf("引用不存在的任务名不应报错: %v", err)
	}
	if layerOf(layers, "A") != 0 {
		t.Errorf("A should be in layer 0, got %v", layers)
	}
}

// =============================================================================
// ReadyToRun 测试
// =============================================================================

func TestReadyToRun(t *testing.T) {
	task := deliveryv1.ProductTask{
		ObjectMeta: meta("gateway"),
		Spec: deliveryv1.ProductTaskSpec{
			TaskOrder: &deliveryv1.TaskOrder{After: []string{"init"}},
		},
	}

	// 前置未完成
	byName := ByName([]deliveryv1.ProductTask{
		task,
		{ObjectMeta: meta("init"), Status: deliveryv1.ProductTaskStatus{Phase: deliveryv1.TaskPhaseRunning}},
	})
	if ready, _ := ReadyToRun(&task, byName); ready {
		t.Error("init is Running, gateway should not be ready")
	}

	// 前置已完成
	byName = ByName([]deliveryv1.ProductTask{
		task,
		{ObjectMeta: meta("init"), Status: deliveryv1.ProductTaskStatus{Phase: deliveryv1.TaskPhaseSucceeded}},
	})
	if ready, msg := ReadyToRun(&task, byName); !ready {
		t.Errorf("init is Succeeded, gateway should be ready, got %q", msg)
	}

	// 前置任务不存在
	byName = ByName([]deliveryv1.ProductTask{task})
	if ready, _ := ReadyToRun(&task, byName); ready {
		t.Error("missing prerequisite should block, not pass")
	}
}

func TestReadyToRun_NoOrderIsAlwaysReady(t *testing.T) {
	task := deliveryv1.ProductTask{ObjectMeta: meta("solo")}
	if ready, msg := ReadyToRun(&task, ByName(nil)); !ready {
		t.Errorf("task without taskOrder should be ready, got %q", msg)
	}
}

// =============================================================================
// GatesComponent 测试
// =============================================================================

func TestGatesComponent(t *testing.T) {
	tasks := []deliveryv1.ProductTask{
		{
			ObjectMeta: meta("init-bizstack"),
			Spec: deliveryv1.ProductTaskSpec{
				RefComponent: &deliveryv1.TaskComponentRef{Component: "bizgateway", Namespace: "dlv-order"},
			},
			Status: deliveryv1.ProductTaskStatus{Phase: deliveryv1.TaskPhaseRunning},
		},
	}

	blocked, msg := GatesComponent(tasks, "dlv-order", "bizgateway")
	if !blocked {
		t.Error("bizgateway should be gated while init task is Running")
	}
	if msg == "" {
		t.Error("expected a reason message")
	}

	// 其它组件不受影响
	if blocked, _ := GatesComponent(tasks, "dlv-order", "redis"); blocked {
		t.Error("redis should not be gated by a task referencing bizgateway")
	}

	// 同名的另一个 namespace 不受影响
	if blocked, _ := GatesComponent(tasks, "other-ns", "bizgateway"); blocked {
		t.Error("component in another namespace should not be gated")
	}
}

func TestGatesComponent_PassesWhenSucceeded(t *testing.T) {
	tasks := []deliveryv1.ProductTask{
		{
			ObjectMeta: meta("init-bizstack"),
			Spec: deliveryv1.ProductTaskSpec{
				RefComponent: &deliveryv1.TaskComponentRef{Component: "bizgateway"},
			},
			Status: deliveryv1.ProductTaskStatus{Phase: deliveryv1.TaskPhaseSucceeded},
		},
	}
	if blocked, _ := GatesComponent(tasks, "dlv-order", "bizgateway"); blocked {
		t.Error("succeeded task must not gate the component")
	}
}

// meta 构造只带名字的 ObjectMeta。
func meta(name string) metav1.ObjectMeta {
	return metav1.ObjectMeta{Name: name}
}
