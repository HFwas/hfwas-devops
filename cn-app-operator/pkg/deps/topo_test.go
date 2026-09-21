package deps

import (
	"testing"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
)

// =============================================================================
// TopologicalSort 测试
// =============================================================================

func TestTopologicalSort_SimpleDependency(t *testing.T) {
	// A → B (B depends on A), A → C (C depends on A)
	components := []deliveryv1.ComponentDef{
		{Name: "A"},
		{
			Name: "B",
			Dependencies: []deliveryv1.Dependency{
				{Component: "A", Type: deliveryv1.DependencyHard},
			},
		},
		{
			Name: "C",
			Dependencies: []deliveryv1.Dependency{
				{Component: "A", Type: deliveryv1.DependencyHard},
			},
		},
	}

	result, err := TopologicalSort(components)
	if err != nil {
		t.Fatalf("TopologicalSort failed: %v", err)
	}

	// A 必须在 B 和 C 之前
	if !comesBefore(result, "A", "B") {
		t.Errorf("A should come before B, got %v", result)
	}
	if !comesBefore(result, "A", "C") {
		t.Errorf("A should come before C, got %v", result)
	}
}

func TestTopologicalSort_ChainDependency(t *testing.T) {
	// A → B → C → D
	components := []deliveryv1.ComponentDef{
		{Name: "A"},
		{
			Name: "B",
			Dependencies: []deliveryv1.Dependency{
				{Component: "A", Type: deliveryv1.DependencyHard},
			},
		},
		{
			Name: "C",
			Dependencies: []deliveryv1.Dependency{
				{Component: "B", Type: deliveryv1.DependencyHard},
			},
		},
		{
			Name: "D",
			Dependencies: []deliveryv1.Dependency{
				{Component: "C", Type: deliveryv1.DependencyHard},
			},
		},
	}

	result, err := TopologicalSort(components)
	if err != nil {
		t.Fatalf("TopologicalSort failed: %v", err)
	}

	if !comesBefore(result, "A", "B") {
		t.Errorf("A should come before B, got %v", result)
	}
	if !comesBefore(result, "B", "C") {
		t.Errorf("B should come before C, got %v", result)
	}
	if !comesBefore(result, "C", "D") {
		t.Errorf("C should come before D, got %v", result)
	}
}

func TestTopologicalSort_NoDependencies(t *testing.T) {
	components := []deliveryv1.ComponentDef{
		{Name: "A"},
		{Name: "B"},
		{Name: "C"},
	}

	result, err := TopologicalSort(components)
	if err != nil {
		t.Fatalf("TopologicalSort failed: %v", err)
	}

	if len(result) != 3 {
		t.Errorf("expected 3 components, got %d: %v", len(result), result)
	}
}

func TestTopologicalSort_OptionalDependency(t *testing.T) {
	// B 对 A 的依赖是 Optional，不阻塞排序
	components := []deliveryv1.ComponentDef{
		{
			Name: "B",
			Dependencies: []deliveryv1.Dependency{
				{Component: "A", Type: deliveryv1.DependencyOptional},
			},
		},
	}

	result, err := TopologicalSort(components)
	if err != nil {
		t.Fatalf("TopologicalSort with optional dependency failed: %v", err)
	}
	if len(result) != 1 || result[0] != "B" {
		t.Errorf("expected [B], got %v", result)
	}
}

func TestTopologicalSort_CircularDependency(t *testing.T) {
	// A → B → A (cycle)
	components := []deliveryv1.ComponentDef{
		{
			Name: "A",
			Dependencies: []deliveryv1.Dependency{
				{Component: "B", Type: deliveryv1.DependencyHard},
			},
		},
		{
			Name: "B",
			Dependencies: []deliveryv1.Dependency{
				{Component: "A", Type: deliveryv1.DependencyHard},
			},
		},
	}

	_, err := TopologicalSort(components)
	if err == nil {
		t.Fatal("expected circular dependency error, got nil")
	}
}

func TestTopologicalSort_SingleComponent(t *testing.T) {
	components := []deliveryv1.ComponentDef{
		{Name: "redis"},
	}

	result, err := TopologicalSort(components)
	if err != nil {
		t.Fatalf("TopologicalSort failed: %v", err)
	}
	if len(result) != 1 || result[0] != "redis" {
		t.Errorf("expected [redis], got %v", result)
	}
}

// =============================================================================
// LayerSort 测试
// =============================================================================

func TestLayerSort(t *testing.T) {
	// A → B, A → C, B → D
	components := []deliveryv1.ComponentDef{
		{Name: "A"},
		{
			Name: "B",
			Dependencies: []deliveryv1.Dependency{
				{Component: "A", Type: deliveryv1.DependencyHard},
			},
		},
		{
			Name: "C",
			Dependencies: []deliveryv1.Dependency{
				{Component: "A", Type: deliveryv1.DependencyHard},
			},
		},
		{
			Name: "D",
			Dependencies: []deliveryv1.Dependency{
				{Component: "B", Type: deliveryv1.DependencyHard},
			},
		},
	}

	layers, err := LayerSort(components)
	if err != nil {
		t.Fatalf("LayerSort failed: %v", err)
	}

	// Layer 0: A (无依赖)
	// Layer 1: B, C (依赖 A)
	// Layer 2: D (依赖 B)
	if len(layers) != 3 {
		t.Fatalf("expected 3 layers, got %d: %v", len(layers), layers)
	}

	if len(layers[0]) != 1 || layers[0][0] != "A" {
		t.Errorf("layer 0 should be [A], got %v", layers[0])
	}
	if len(layers[1]) != 2 {
		t.Errorf("layer 1 should have 2 items, got %v", layers[1])
	}
	if len(layers[2]) != 1 || layers[2][0] != "D" {
		t.Errorf("layer 2 should be [D], got %v", layers[2])
	}
}

// =============================================================================
// ResolveDependencies 测试
// =============================================================================

func TestResolveDependencies_Ready(t *testing.T) {
	component := &deliveryv1.ComponentDef{
		Name: "backend",
		Dependencies: []deliveryv1.Dependency{
			{Component: "postgresql", Type: deliveryv1.DependencyHard, Condition: deliveryv1.DependencyReady},
			{Component: "redis", Type: deliveryv1.DependencyHard, Condition: deliveryv1.DependencyReady},
		},
	}

	phases := map[string]deliveryv1.Phase{
		"postgresql": deliveryv1.PhaseReady,
		"redis":      deliveryv1.PhaseReady,
	}

	ready, reason := ResolveDependencies(component, phases)
	if !ready {
		t.Errorf("expected all dependencies ready, got reason: %s", reason)
	}
}

func TestResolveDependencies_NotReady(t *testing.T) {
	component := &deliveryv1.ComponentDef{
		Name: "backend",
		Dependencies: []deliveryv1.Dependency{
			{Component: "postgresql", Type: deliveryv1.DependencyHard, Condition: deliveryv1.DependencyReady},
		},
	}

	// postgresql is still deploying
	phases := map[string]deliveryv1.Phase{
		"postgresql": deliveryv1.PhaseDeploying,
	}

	ready, _ := ResolveDependencies(component, phases)
	if ready {
		t.Error("expected dependency not ready")
	}
}

func TestResolveDependencies_MissingDependency(t *testing.T) {
	component := &deliveryv1.ComponentDef{
		Name: "backend",
		Dependencies: []deliveryv1.Dependency{
			{Component: "missing-svc", Type: deliveryv1.DependencyHard},
		},
	}

	ready, _ := ResolveDependencies(component, map[string]deliveryv1.Phase{})
	if ready {
		t.Error("expected missing dependency to block")
	}
}

func TestResolveDependencies_OptionalMissing(t *testing.T) {
	component := &deliveryv1.ComponentDef{
		Name: "backend",
		Dependencies: []deliveryv1.Dependency{
			{Component: "optional-svc", Type: deliveryv1.DependencyOptional},
		},
	}

	ready, _ := ResolveDependencies(component, map[string]deliveryv1.Phase{})
	if !ready {
		t.Error("optional missing dependency should not block")
	}
}

func TestResolveDependencies_DeployedCondition(t *testing.T) {
	component := &deliveryv1.ComponentDef{
		Name: "backend",
		Dependencies: []deliveryv1.Dependency{
			{Component: "init-job", Type: deliveryv1.DependencyHard, Condition: deliveryv1.DependencyDeployed},
		},
	}

	tests := []struct {
		phase deliveryv1.Phase
		ready bool
	}{
		{deliveryv1.PhasePending, false},
		{deliveryv1.PhaseDeploying, true},
		{deliveryv1.PhaseReady, true},
		{deliveryv1.PhaseFailed, true},
	}

	for _, tt := range tests {
		t.Run(string(tt.phase), func(t *testing.T) {
			ready, _ := ResolveDependencies(component, map[string]deliveryv1.Phase{
				"init-job": tt.phase,
			})
			if ready != tt.ready {
				t.Errorf("phase %s: expected ready=%v, got %v", tt.phase, tt.ready, ready)
			}
		})
	}
}

// =============================================================================
// Benchmark
// =============================================================================

func BenchmarkTopologicalSort(b *testing.B) {
	// 构建 10 个节点的链式依赖
	components := make([]deliveryv1.ComponentDef, 10)
	for i := 0; i < 10; i++ {
		components[i] = deliveryv1.ComponentDef{Name: string(rune('A' + i))}
		if i > 0 {
			components[i].Dependencies = []deliveryv1.Dependency{
				{Component: string(rune('A' + i - 1)), Type: deliveryv1.DependencyHard},
			}
		}
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		_, _ = TopologicalSort(components)
	}
}

// =============================================================================
// Helper
// =============================================================================

func comesBefore(order []string, a, b string) bool {
	posA, posB := -1, -1
	for i, name := range order {
		if name == a {
			posA = i
		}
		if name == b {
			posB = i
		}
	}
	return posA >= 0 && posB >= 0 && posA < posB
}