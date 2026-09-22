package controllers

import (
	"context"
	"fmt"
	"time"

	"k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/log"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
	"github.com/hfwas/cn-app-operator/pkg/deps"
)

// CloudServiceReconciler 调和 CloudService CR。
type CloudServiceReconciler struct {
	client.Client
	Scheme *runtime.Scheme
}

// +kubebuilder:rbac:groups=delivery.hfwas.io,resources=cloudservices,verbs=get;list;watch;create;update;patch;delete
// +kubebuilder:rbac:groups=delivery.hfwas.io,resources=cloudservices/status,verbs=get;update;patch
// +kubebuilder:rbac:groups=delivery.hfwas.io,resources=cloudcomponents,verbs=get;list;watch;create;update;patch;delete

// Reconcile 处理 CloudService 的调和循环。
func (r *CloudServiceReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
	logger := log.FromContext(ctx).WithValues("cloudservice", req.NamespacedName)
	logger.Info("Starting reconcile")

	cs := &deliveryv1.CloudService{}
	if err := r.Get(ctx, req.NamespacedName, cs); err != nil {
		if errors.IsNotFound(err) {
			return ctrl.Result{}, nil
		}
		return ctrl.Result{}, err
	}

	if !cs.DeletionTimestamp.IsZero() {
		return ctrl.Result{}, nil
	}

	// 拓扑排序：计算组件调和顺序
	order, err := deps.TopologicalSort(cs.Spec.Components)
	if err != nil {
		logger.Error(err, "Failed to compute component order")
		cs.Status.ReconciliationOrder = nil
		_ = r.Status().Update(ctx, cs)
		return ctrl.Result{}, err
	}
	cs.Status.ReconciliationOrder = order

	// 确保每个组件都有对应的 CloudComponent CR
	if err := r.ensureCloudComponents(ctx, cs, order); err != nil {
		logger.Error(err, "Failed to ensure CloudComponents")
		return ctrl.Result{}, err
	}

	// 聚合组件状态
	cs.Status.ComponentCount = len(cs.Spec.Components)
	cs.Status.ReadyCount = 0

	for _, compName := range order {
		cc := &deliveryv1.CloudComponent{}
		if err := r.Get(ctx, client.ObjectKey{
			Namespace: cs.Namespace,
			Name:      componentCRName(cs.Name, compName),
		}, cc); err != nil {
			if !errors.IsNotFound(err) {
				logger.Error(err, "Failed to get CloudComponent", "component", compName)
			}
			continue
		}

		cs.Status.ComponentStatuses = append(cs.Status.ComponentStatuses, deliveryv1.ComponentStatus{
			Name:            compName,
			Phase:           cc.Status.Phase,
			ChartVersion:    cc.Spec.ChartVersion,
			DeployedVersion: cc.Status.ChartVersion,
			ErrorMessage:    cc.Status.FailureMessage,
		})

		if cc.Status.Phase == deliveryv1.PhaseReady {
			cs.Status.ReadyCount++
		}
	}

	// 聚合一阶段
	cs.Status.Phase = aggregateCloudServicePhase(cs.Status.ComponentStatuses)
	cs.Status.ObservedVersion = cs.Spec.Version

	if err := r.Status().Update(ctx, cs); err != nil {
		logger.Error(err, "Failed to update status")
		return ctrl.Result{}, err
	}

	logger.Info("Reconcile complete", "phase", cs.Status.Phase, "components", cs.Status.ComponentCount)
	return ctrl.Result{RequeueAfter: 5 * time.Minute}, nil
}

// ensureCloudComponents 确保每个组件都有对应的 CloudComponent CR。
func (r *CloudServiceReconciler) ensureCloudComponents(ctx context.Context, cs *deliveryv1.CloudService, order []string) error {
	for _, compName := range order {
		compDef := findComponentDef(cs.Spec.Components, compName)
		if compDef == nil {
			continue
		}

		cc := &deliveryv1.CloudComponent{}
		err := r.Get(ctx, client.ObjectKey{
			Namespace: cs.Namespace,
			Name:      componentCRName(cs.Name, compName),
		}, cc)

		if errors.IsNotFound(err) {
			// 创建 CloudComponent
			cc = &deliveryv1.CloudComponent{}
			cc.Name = componentCRName(cs.Name, compName)
			cc.Namespace = cs.Namespace
			cc.Spec = deliveryv1.CloudComponentSpec{
				DisplayName:        compDef.DisplayName,
				ComponentType:      compDef.ComponentType,
				ServiceRef:         cs.Name,
				ChartName:          compDef.Chart.Name,
				ChartVersion:       compDef.Chart.Version,
				ChartRepo:          compDef.Chart.Repository,
				ValuesOverlay:      compDef.DefaultValues,
				DeploymentStrategy: compDef.DeploymentStrategy,
				Dependencies:       compDef.Dependencies,
				HealthCheck:        compDef.HealthCheck,
				ClusterAffinity:    compDef.ClusterAffinity,
			}

			if err := r.Client.Create(ctx, cc); err != nil {
				return fmt.Errorf("failed to create CloudComponent %s: %w", cc.Name, err)
			}
		} else if err != nil {
			return err
		}
	}
	return nil
}

// aggregateCloudServicePhase 聚合组件阶段。
func aggregateCloudServicePhase(statuses []deliveryv1.ComponentStatus) deliveryv1.Phase {
	hasFailed := false
	hasDegraded := false
	hasDeploying := false
	hasPending := false
	allReady := true

	for _, s := range statuses {
		switch s.Phase {
		case deliveryv1.PhaseFailed:
			hasFailed = true
			allReady = false
		case deliveryv1.PhaseDegraded:
			hasDegraded = true
			allReady = false
		case deliveryv1.PhaseDeploying:
			hasDeploying = true
			allReady = false
		case deliveryv1.PhasePending:
			hasPending = true
			allReady = false
		case deliveryv1.PhaseReady:
			// OK
		default:
			allReady = false
		}
	}

	if len(statuses) == 0 {
		return deliveryv1.PhasePending
	}
	if hasFailed {
		return deliveryv1.PhaseFailed
	}
	if hasDegraded {
		return deliveryv1.PhaseDegraded
	}
	if hasDeploying || hasPending {
		return deliveryv1.PhaseDeploying
	}
	if allReady {
		return deliveryv1.PhaseReady
	}
	return deliveryv1.PhaseUnknown
}

// componentCRName 生成 CloudComponent 的名称。
func componentCRName(serviceName, compName string) string {
	return fmt.Sprintf("%s-%s", serviceName, compName)
}

// findComponentDef 根据名称查找组件定义。
func findComponentDef(comps []deliveryv1.ComponentDef, name string) *deliveryv1.ComponentDef {
	for i := range comps {
		if comps[i].Name == name {
			return &comps[i]
		}
	}
	return nil
}

// SetupWithManager 注册 Controller 到 Manager。
func (r *CloudServiceReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&deliveryv1.CloudService{}).
		Owns(&deliveryv1.CloudComponent{}).
		Complete(r)
}
