package helm

import (
	"context"
	"testing"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
)

var ctx = context.Background()

// =============================================================================
// DetermineAction 测试
// =============================================================================

func TestDetermineAction_Install(t *testing.T) {
	client := NewClient()
	cc := &deliveryv1.CloudComponent{}
	cc.Name = "redis"
	cc.Spec.ChartName = "redis"
	cc.Spec.ChartVersion = "17.3.0"
	cc.Spec.ComponentType = deliveryv1.ComponentTypeService
	cc.Status.HelmReleaseName = ""

	action, err := client.DetermineAction(ctx, cc)
	if err != nil {
		t.Fatalf("DetermineAction failed: %v", err)
	}
	if action != ActionInstall {
		t.Errorf("expected ActionInstall, got %s", action)
	}
}

func TestDetermineAction_Upgrade(t *testing.T) {
	client := NewClient()
	cc := &deliveryv1.CloudComponent{}
	cc.Name = "redis"
	cc.Spec.ChartVersion = "18.0.0"
	cc.Spec.ComponentType = deliveryv1.ComponentTypeService
	cc.Status.HelmReleaseName = "redis-release"
	cc.Status.ChartVersion = "17.3.0"

	action, err := client.DetermineAction(ctx, cc)
	if err != nil {
		t.Fatalf("DetermineAction failed: %v", err)
	}
	if action != ActionUpgrade {
		t.Errorf("expected ActionUpgrade, got %s", action)
	}
}

func TestDetermineAction_Skip_SameVersion(t *testing.T) {
	client := NewClient()
	cc := &deliveryv1.CloudComponent{}
	cc.Name = "redis"
	cc.Spec.ChartVersion = "17.3.0"
	cc.Spec.ComponentType = deliveryv1.ComponentTypeService
	cc.Status.HelmReleaseName = "redis-release"
	cc.Status.ChartVersion = "17.3.0"

	action, err := client.DetermineAction(ctx, cc)
	if err != nil {
		t.Fatalf("DetermineAction failed: %v", err)
	}
	if action != ActionSkip {
		t.Errorf("expected ActionSkip (same version), got %s", action)
	}
}

func TestDetermineAction_ClusterComponent_Skip(t *testing.T) {
	client := NewClient()
	cc := &deliveryv1.CloudComponent{}
	cc.Name = "ingress-nginx"
	cc.Spec.ChartVersion = "4.7.0"
	cc.Spec.ComponentType = deliveryv1.ComponentTypeCluster
	cc.Status.HelmReleaseName = "ingress-nginx"
	cc.Status.ChartVersion = "4.7.0"

	action, err := client.DetermineAction(ctx, cc)
	if err != nil {
		t.Fatalf("DetermineAction failed: %v", err)
	}
	if action != ActionSkip {
		t.Errorf("expected ActionSkip (cluster component, same version), got %s", action)
	}
}

func TestDetermineAction_ClusterComponent_Upgrade(t *testing.T) {
	client := NewClient()
	cc := &deliveryv1.CloudComponent{}
	cc.Name = "ingress-nginx"
	cc.Spec.ChartVersion = "4.8.0"
	cc.Spec.ComponentType = deliveryv1.ComponentTypeCluster
	cc.Status.HelmReleaseName = "ingress-nginx"
	cc.Status.ChartVersion = "4.7.0"

	action, err := client.DetermineAction(ctx, cc)
	if err != nil {
		t.Fatalf("DetermineAction failed: %v", err)
	}
	if action != ActionUpgrade {
		t.Errorf("expected ActionUpgrade (cluster component, higher version), got %s", action)
	}
}

func TestDetermineAction_Rollback(t *testing.T) {
	client := NewClient()
	cc := &deliveryv1.CloudComponent{}
	cc.Name = "redis"
	cc.Spec.ChartVersion = "17.4.0" // higher than installed, would normally upgrade
	cc.Spec.ComponentType = deliveryv1.ComponentTypeService
	cc.Status.HelmReleaseName = "redis-release"
	cc.Status.ChartVersion = "17.3.0" // currently on 17.3.0
	cc.Status.Phase = deliveryv1.PhaseFailed // but it failed, so rollback
	cc.Status.RetryCount = 1

	action, err := client.DetermineAction(ctx, cc)
	if err != nil {
		t.Fatalf("DetermineAction failed: %v", err)
	}
	if action != ActionRollback {
		t.Errorf("expected ActionRollback (failed phase with retries), got %s", action)
	}
}

// =============================================================================
// Action 方法测试
// =============================================================================

func TestInstall(t *testing.T) {
	client := NewClient()
	cc := &deliveryv1.CloudComponent{}
	cc.Name = "test"
	cc.Namespace = "test-ns"
	cc.Spec.ReleaseName = ""
	cc.Status.InstallCount = 0

	if err := client.Install(ctx, cc); err != nil {
		t.Fatalf("Install failed: %v", err)
	}

	if cc.Status.InstallCount != 1 {
		t.Errorf("expected InstallCount=1, got %d", cc.Status.InstallCount)
	}
	if cc.Status.HelmReleaseName == "" {
		t.Error("HelmReleaseName should be set after install")
	}
}

func TestUpgrade(t *testing.T) {
	client := NewClient()
	cc := &deliveryv1.CloudComponent{}
	cc.Status.UpgradeCount = 0

	if err := client.Upgrade(ctx, cc); err != nil {
		t.Fatalf("Upgrade failed: %v", err)
	}
	if cc.Status.UpgradeCount != 1 {
		t.Errorf("expected UpgradeCount=1, got %d", cc.Status.UpgradeCount)
	}
}

func TestRollback(t *testing.T) {
	client := NewClient()
	cc := &deliveryv1.CloudComponent{}
	cc.Status.RollbackCount = 0

	if err := client.Rollback(ctx, cc); err != nil {
		t.Fatalf("Rollback failed: %v", err)
	}
	if cc.Status.RollbackCount != 1 {
		t.Errorf("expected RollbackCount=1, got %d", cc.Status.RollbackCount)
	}
}

func TestUninstall(t *testing.T) {
	client := NewClient()
	if err := client.Uninstall(ctx, &deliveryv1.CloudComponent{}); err != nil {
		t.Fatalf("Uninstall failed: %v", err)
	}
}

// =============================================================================
// 辅助方法测试
// =============================================================================

func TestHashValues(t *testing.T) {
	v1 := map[string]interface{}{
		"key": "value",
		"num": 42,
	}
	v2 := map[string]interface{}{
		"key": "value",
		"num": 42,
	}
	v3 := map[string]interface{}{
		"key": "different",
	}

	h1 := hashValues(v1)
	h2 := hashValues(v2)
	h3 := hashValues(v3)

	if h1 == "" {
		t.Error("hash should not be empty")
	}
	if h1 != h2 {
		t.Error("same values should produce same hash")
	}
	if h1 == h3 {
		t.Error("different values should produce different hash")
	}
}