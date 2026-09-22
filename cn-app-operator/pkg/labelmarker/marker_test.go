package labelmarker

import (
	"testing"

	"sigs.k8s.io/controller-runtime/pkg/client"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
)

func TestBuildLabels(t *testing.T) {
	cc := &deliveryv1.CloudComponent{}
	cc.Name = "redis"
	cc.Spec.ComponentType = deliveryv1.ComponentTypeService
	cc.Spec.ServiceRef = "order-core"
	cc.Status.CurrentRevision = "3"

	labels := BuildLabels(cc)

	tests := []struct {
		key      string
		expected string
	}{
		{LabelComponent, "redis"},
		{LabelComponentType, string(deliveryv1.ComponentTypeService)},
		{LabelManagedBy, ManagedByValue},
		{LabelService, "order-core"},
		{LabelRevision, "3"},
	}

	for _, tt := range tests {
		t.Run(tt.key, func(t *testing.T) {
			if labels[tt.key] != tt.expected {
				t.Errorf("labels[%q] = %q, want %q", tt.key, labels[tt.key], tt.expected)
			}
		})
	}
}

func TestBuildLabels_NoServiceRef(t *testing.T) {
	cc := &deliveryv1.CloudComponent{}
	cc.Name = "standalone-redis"
	cc.Spec.ComponentType = deliveryv1.ComponentTypeCluster
	cc.Status.CurrentRevision = ""

	labels := BuildLabels(cc)

	if _, ok := labels[LabelService]; ok {
		t.Error("LabelService should not be set when ServiceRef is empty")
	}
	if _, ok := labels[LabelRevision]; ok {
		t.Error("LabelRevision should not be set when CurrentRevision is empty")
	}
	if labels[LabelComponentType] != string(deliveryv1.ComponentTypeCluster) {
		t.Errorf("expected component type Cluster, got %q", labels[LabelComponentType])
	}
}

func TestMergeLabels(t *testing.T) {
	existing := Labels{
		"existing-key": "existing-value",
	}
	add := Labels{
		"new-key":      "new-value",
		"existing-key": "overridden",
	}

	result := MergeLabels(existing, add)

	if result["existing-key"] != "overridden" {
		t.Errorf("existing key should be overridden, got %q", result["existing-key"])
	}
	if result["new-key"] != "new-value" {
		t.Errorf("new key should be added, got %q", result["new-key"])
	}
}

func TestMergeLabels_NilExisting(t *testing.T) {
	add := Labels{
		"key": "value",
	}
	result := MergeLabels(nil, add)
	if result["key"] != "value" {
		t.Error("MergeLabels should handle nil existing labels")
	}
}

func TestHasManagedLabels(t *testing.T) {
	tests := []struct {
		name     string
		labels   map[string]string
		expected bool
	}{
		{"managed by operator", map[string]string{LabelManagedBy: ManagedByValue}, true},
		{"wrong value", map[string]string{LabelManagedBy: "other-operator"}, false},
		{"missing label", map[string]string{}, false},
		{"nil labels", nil, false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if HasManagedLabels(tt.labels) != tt.expected {
				t.Errorf("HasManagedLabels(%v) = %v, want %v", tt.labels, !tt.expected, tt.expected)
			}
		})
	}
}

func TestLabelSelectors(t *testing.T) {
	appSelector := AppLabelSelector("order-platform")
	if appSelector[LabelApp] != "order-platform" {
		t.Errorf("AppLabelSelector: expected app=order-platform, got %v", appSelector)
	}

	svcSelector := ServiceLabelSelector("order-core")
	if svcSelector[LabelService] != "order-core" {
		t.Errorf("ServiceLabelSelector: expected service=order-core, got %v", svcSelector)
	}

	compSelector := ComponentLabelSelector("redis")
	if compSelector[LabelComponent] != "redis" {
		t.Errorf("ComponentLabelSelector: expected component=redis, got %v", compSelector)
	}

	// 所有 selector 都应包含 managed-by
	selectors := []client.MatchingLabels{appSelector, svcSelector, compSelector}
	for i, sel := range selectors {
		if sel[LabelManagedBy] != ManagedByValue {
			t.Errorf("selector %d should include managed-by label", i)
		}
	}
}

func TestValidateLabels(t *testing.T) {
	cc := &deliveryv1.CloudComponent{}
	cc.Name = "redis"
	cc.Spec.ComponentType = deliveryv1.ComponentTypeService
	cc.Spec.ServiceRef = "order-core"

	goodLabels := map[string]string{
		LabelComponent:     "redis",
		LabelComponentType: "Service",
		LabelManagedBy:     ManagedByValue,
		LabelService:       "order-core",
	}

	if err := ValidateLabels(cc, goodLabels); err != nil {
		t.Errorf("ValidateLabels with correct labels should pass: %v", err)
	}

	badLabels := map[string]string{
		LabelComponent: "wrong-name",
		LabelManagedBy: ManagedByValue,
	}

	if err := ValidateLabels(cc, badLabels); err == nil {
		t.Error("ValidateLabels with incorrect labels should fail")
	}
}

func TestWorkloadKinds(t *testing.T) {
	expected := []string{
		"Deployment",
		"StatefulSet",
		"DaemonSet",
		"Job",
		"CronJob",
		"Service",
		"Ingress",
	}

	if len(WorkloadKinds) != len(expected) {
		t.Errorf("expected %d workload kinds, got %d", len(expected), len(WorkloadKinds))
	}

	for i, kind := range expected {
		if WorkloadKinds[i] != kind {
			t.Errorf("WorkloadKinds[%d] = %q, want %q", i, WorkloadKinds[i], kind)
		}
	}
}

func TestGetWorkloadListKey(t *testing.T) {
	cc := &deliveryv1.CloudComponent{}
	cc.Name = "redis"

	key := GetWorkloadListKey(cc)
	if key[LabelComponent] != "redis" {
		t.Errorf("expected component=redis, got %v", key)
	}
	if key[LabelManagedBy] != ManagedByValue {
		t.Errorf("expected managed-by=%s, got %v", ManagedByValue, key)
	}
}
