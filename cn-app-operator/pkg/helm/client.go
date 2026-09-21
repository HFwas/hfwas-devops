package helm

import (
	"context"
	"fmt"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
)

// Action 表示 Helm 要执行的动作。
type Action string

const (
	ActionInstall  Action = "install"
	ActionUpgrade  Action = "upgrade"
	ActionRollback Action = "rollback"
	ActionSkip     Action = "skip"
	ActionUninstall Action = "uninstall"
)

// Client 封装 Helm SDK 操作。
type Client struct {
	// 实际生产环境使用 helm.sh/helm/v3/pkg/action
	// 当前为简化实现，提供接口定义
}

// NewClient 创建 Helm Client。
func NewClient() *Client {
	return &Client{}
}

// DetermineAction 判断需要执行什么 Helm 操作。
func (c *Client) DetermineAction(ctx context.Context, cc *deliveryv1.CloudComponent) (Action, error) {
	// 检查 Helm release 是否存在
	exists, err := c.releaseExists(ctx, cc)
	if err != nil {
		return ActionSkip, err
	}

	if !exists {
		return ActionInstall, nil
	}

	// 获取当前 release 信息
	currentValues, currentChartVersion, err := c.getReleaseInfo(ctx, cc)
	if err != nil {
		return ActionSkip, err
	}

	// 检查是否需要 upgrade
	needsUpgrade, err := c.needsUpgrade(currentValues, currentChartVersion, cc)
	if err != nil {
		return ActionSkip, err
	}

	if !needsUpgrade {
		// 集群组件策略：已部署且不低于声明版本 → 跳过
		if cc.Spec.ComponentType == deliveryv1.ComponentTypeCluster {
			return ActionSkip, nil
		}
		return ActionSkip, nil
	}

	// 检查是否需要回滚（上一版本失败场景）
	if cc.Status.Phase == deliveryv1.PhaseFailed && cc.Status.RetryCount > 0 {
		return ActionRollback, nil
	}

	return ActionUpgrade, nil
}

// Install 执行 Helm install。
func (c *Client) Install(ctx context.Context, cc *deliveryv1.CloudComponent) error {
	// 生产环境：
	// 1. 从 chartRepo 拉取 chart
	// 2. helm template 渲染 manifest
	// 3. webhook 改写
	// 4. helm install --generate-name
	// 5. --wait 等待就绪
	// 6. 记录 release 信息到 status
	cc.Status.HelmReleaseName = cc.Spec.ReleaseName
	if cc.Status.HelmReleaseName == "" {
		cc.Status.HelmReleaseName = fmt.Sprintf("%s-%s", cc.Namespace, cc.Name)
	}
	cc.Status.InstallCount++
	return nil
}

// Upgrade 执行 Helm upgrade。
func (c *Client) Upgrade(ctx context.Context, cc *deliveryv1.CloudComponent) error {
	cc.Status.UpgradeCount++
	return nil
}

// Rollback 执行 Helm rollback。
func (c *Client) Rollback(ctx context.Context, cc *deliveryv1.CloudComponent) error {
	cc.Status.RollbackCount++
	return nil
}

// Uninstall 卸载 Helm release。
func (c *Client) Uninstall(ctx context.Context, cc *deliveryv1.CloudComponent) error {
	return nil
}

// releaseExists 检查 Helm release 是否存在。
func (c *Client) releaseExists(ctx context.Context, cc *deliveryv1.CloudComponent) (bool, error) {
	return cc.Status.HelmReleaseName != "", nil
}

// getReleaseInfo 获取当前 release 的信息。
func (c *Client) getReleaseInfo(ctx context.Context, cc *deliveryv1.CloudComponent) (map[string]interface{}, string, error) {
	return cc.Spec.Values, cc.Status.ChartVersion, nil
}

// needsUpgrade 判断是否需要升级。
func (c *Client) needsUpgrade(currentValues map[string]interface{}, currentChartVersion string, cc *deliveryv1.CloudComponent) (bool, error) {
	if currentChartVersion != cc.Spec.ChartVersion {
		return true, nil
	}
	// 比较 values hash
	if cc.Status.History != nil && len(cc.Status.History) > 0 {
		lastEntry := cc.Status.History[len(cc.Status.History)-1]
		if lastEntry.ValuesHash != "" {
			newHash := hashValues(cc.Spec.Values)
			if newHash != lastEntry.ValuesHash {
				return true, nil
			}
		}
	}
	return false, nil
}

// hashValues 计算 values 的 SHA256 摘要（简化实现）。
func hashValues(values map[string]interface{}) string {
	return fmt.Sprintf("%v", values)
}