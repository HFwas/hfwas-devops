package controllers

import (
	"context"
	"fmt"
	"time"

	"k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/log"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
	"github.com/hfwas/cn-app-operator/pkg/helm"
	"github.com/hfwas/cn-app-operator/pkg/labelmarker"
	"github.com/hfwas/cn-app-operator/pkg/status"
)

// CloudComponentReconciler 调和 CloudComponent CR。
type CloudComponentReconciler struct {
	client.Client
	Scheme    *runtime.Scheme
	HelmClient *helm.Client
}

// +kubebuilder:rbac:groups=delivery.hfwas.io,resources=cloudcomponents,verbs=get;list;watch;create;update;patch;delete
// +kubebuilder:rbac:groups=delivery.hfwas.io,resources=cloudcomponents/status,verbs=get;update;patch
// +kubebuilder:rbac:groups=delivery.hfwas.io,resources=cloudcomponents/finalizers,verbs=update

// Reconcile 处理 CloudComponent 的调和循环。
func (r *CloudComponentReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
	logger := log.FromContext(ctx).WithValues("cloudcomponent", req.NamespacedName)
	logger.Info("Starting reconcile")

	// 1. 读取 CloudComponent CR
	cc := &deliveryv1.CloudComponent{}
	if err := r.Get(ctx, req.NamespacedName, cc); err != nil {
		if errors.IsNotFound(err) {
			return ctrl.Result{}, nil
		}
		return ctrl.Result{}, err
	}

	// 检查是否标记了删除
	if !cc.DeletionTimestamp.IsZero() {
		logger.Info("CloudComponent is being deleted")
		return r.handleDeletion(ctx, cc)
	}

	// 2. 解析依赖 → 检查前置依赖状态
	depReady, err := r.checkDependencies(ctx, cc)
	if err != nil {
		return ctrl.Result{}, err
	}
	if !depReady {
		logger.Info("Dependencies not ready, requeueing")
		_ = r.updateCondition(ctx, cc, deliveryv1.ConditionProgressing, "False",
			"DependenciesNotReady", "One or more dependencies are not ready")
		return ctrl.Result{RequeueAfter: 30 * time.Second}, nil
	}

	// 3. 判断 Helm Action
	action, err := r.HelmClient.DetermineAction(ctx, cc)
	if err != nil {
		logger.Error(err, "Failed to determine Helm action")
		_ = r.updateCondition(ctx, cc, deliveryv1.ConditionDegraded, "True",
			"HelmActionFailed", err.Error())
		return ctrl.Result{}, err
	}

	// 4. 执行 Helm 操作
	switch action {
	case helm.ActionInstall:
		logger.Info("Installing Helm release")
		_ = r.updatePhase(ctx, cc, deliveryv1.PhaseDeploying)
		if err := r.HelmClient.Install(ctx, cc); err != nil {
			return r.handleHelmFailure(ctx, cc, err)
		}

	case helm.ActionUpgrade:
		logger.Info("Upgrading Helm release")
		_ = r.updatePhase(ctx, cc, deliveryv1.PhaseDeploying)
		if err := r.HelmClient.Upgrade(ctx, cc); err != nil {
			return r.handleHelmFailure(ctx, cc, err)
		}

	case helm.ActionRollback:
		logger.Info("Rolling back Helm release")
		_ = r.updatePhase(ctx, cc, deliveryv1.PhaseDeploying)
		if err := r.HelmClient.Rollback(ctx, cc); err != nil {
			return r.handleHelmFailure(ctx, cc, err)
		}

	case helm.ActionSkip:
		logger.V(1).Info("No action needed, skipping")
	}

	// 5. 打标 + 聚合 Workload 状态
	if err := labelmarker.LabelResources(ctx, r.Client, cc); err != nil {
		logger.Error(err, "Failed to label resources")
	}

	// 6. 聚合 Workload 状态
	ws, err := status.AggregateWorkloads(ctx, r.Client, cc)
	if err != nil {
		logger.Error(err, "Failed to aggregate workload status")
	}
	cc.Status.WorkloadStatus = ws

	// 7. 更新 Phase
	if ws != nil && ws.TotalWorkloads > 0 {
		if ws.ReadyWorkloads == ws.TotalWorkloads {
			cc.Status.Phase = deliveryv1.PhaseReady
			_ = r.updateCondition(ctx, cc, deliveryv1.ConditionAvailable, "True",
				"AllWorkloadsReady", "All workloads are ready")
		} else if ws.ReadyWorkloads > 0 {
			cc.Status.Phase = deliveryv1.PhaseDegraded
			_ = r.updateCondition(ctx, cc, deliveryv1.ConditionDegraded, "True",
				"PartialWorkloadsReady", fmt.Sprintf("%d/%d workloads ready", ws.ReadyWorkloads, ws.TotalWorkloads))
		} else {
			cc.Status.Phase = deliveryv1.PhaseDeploying
		}
	}

	// 8. 更新 Status
	if err := r.Status().Update(ctx, cc); err != nil {
		logger.Error(err, "Failed to update status")
		return ctrl.Result{}, err
	}

	logger.Info("Reconcile complete", "phase", cc.Status.Phase)
	return ctrl.Result{RequeueAfter: 10 * time.Minute}, nil
}

// handleDeletion 处理 CloudComponent 删除。
func (r *CloudComponentReconciler) handleDeletion(ctx context.Context, cc *deliveryv1.CloudComponent) (ctrl.Result, error) {
	if err := r.HelmClient.Uninstall(ctx, cc); err != nil {
		return ctrl.Result{}, err
	}
	return ctrl.Result{}, nil
}

// checkDependencies 检查所有前置依赖是否就绪。
func (r *CloudComponentReconciler) checkDependencies(ctx context.Context, cc *deliveryv1.CloudComponent) (bool, error) {
	for _, dep := range cc.Spec.Dependencies {
		if dep.Type != deliveryv1.DependencyHard {
			continue
		}
		depCC := &deliveryv1.CloudComponent{}
		depName := dep.Component
		if dep.ServiceRef != "" {
			depName = dep.ServiceRef + "-" + dep.Component
		}
		if err := r.Get(ctx, client.ObjectKey{
			Namespace: cc.Namespace,
			Name:      depName,
		}, depCC); err != nil {
			if errors.IsNotFound(err) {
				return false, nil
			}
			return false, err
		}
		if depCC.Status.Phase != deliveryv1.PhaseReady {
			return false, nil
		}
	}
	return true, nil
}

// handleHelmFailure 处理 Helm 操作失败。
func (r *CloudComponentReconciler) handleHelmFailure(ctx context.Context, cc *deliveryv1.CloudComponent, err error) (ctrl.Result, error) {
	logger := log.FromContext(ctx)
	logger.Error(err, "Helm operation failed")

	cc.Status.FailureMessage = err.Error()
	cc.Status.FailureReason = "HelmOperationFailed"
	cc.Status.RetryCount++

	strategy := cc.Spec.DeploymentStrategy
	if strategy != nil && strategy.Retry != nil && cc.Status.RetryCount < strategy.Retry.MaxRetries {
		cc.Status.Phase = deliveryv1.PhaseDeploying
		_ = r.updateCondition(ctx, cc, deliveryv1.ConditionProgressing, "False",
			"RetryingHelm", fmt.Sprintf("Retry %d/%d", cc.Status.RetryCount+1, strategy.Retry.MaxRetries))

		if err := r.Status().Update(ctx, cc); err != nil {
			return ctrl.Result{}, err
		}

		backoff := calculateBackoff(cc.Status.RetryCount, strategy.Retry.BackoffStrategy)
		return ctrl.Result{RequeueAfter: backoff}, nil
	}

	// 达到最大重试次数
	cc.Status.Phase = deliveryv1.PhaseFailed
	_ = r.updateCondition(ctx, cc, deliveryv1.ConditionAvailable, "False",
		"MaxRetriesExceeded", fmt.Sprintf("Helm operation failed after %d retries: %s", cc.Status.RetryCount, err.Error()))

	if err := r.Status().Update(ctx, cc); err != nil {
		return ctrl.Result{}, err
	}
	return ctrl.Result{}, nil
}

// updatePhase 更新 Phase 并写回 Status。
func (r *CloudComponentReconciler) updatePhase(ctx context.Context, cc *deliveryv1.CloudComponent, phase deliveryv1.Phase) error {
	cc.Status.Phase = phase
	return r.Status().Update(ctx, cc)
}

// updateCondition 更新 Condition。
func (r *CloudComponentReconciler) updateCondition(ctx context.Context, cc *deliveryv1.CloudComponent,
	condType deliveryv1.ConditionType, status metav1.ConditionStatus,
	reason, message string) error {

	now := metav1.Now()
	found := false
	for i, c := range cc.Status.Conditions {
		if c.Type == condType {
			cc.Status.Conditions[i].Status = status
			cc.Status.Conditions[i].Reason = reason
			cc.Status.Conditions[i].Message = message
			cc.Status.Conditions[i].LastTransitionTime = now
			cc.Status.Conditions[i].ObservedGeneration = cc.Generation
			found = true
			break
		}
	}
	if !found {
		cc.Status.Conditions = append(cc.Status.Conditions, deliveryv1.Condition{
			Type:               condType,
			Status:             status,
			Reason:             reason,
			Message:            message,
			LastTransitionTime: now,
			ObservedGeneration: cc.Generation,
		})
	}
	return r.Status().Update(ctx, cc)
}

// calculateBackoff 计算重试间隔。
func calculateBackoff(retryCount int, strategy string) time.Duration {
	baseDelay := 15 * time.Second
	switch strategy {
	case "Exponential":
		return baseDelay * (1 << retryCount) // 15s, 30s, 60s, ...
	case "Linear":
		return baseDelay * time.Duration(retryCount+1)
	default:
		return baseDelay
	}
}

// SetupWithManager 注册 Controller 到 Manager。
func (r *CloudComponentReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&deliveryv1.CloudComponent{}).
		Complete(r)
}