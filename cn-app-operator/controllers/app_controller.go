package controllers

import (
	"context"
	"time"

	"k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/runtime"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/log"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
)

// AppReconciler 调和 App CR。
type AppReconciler struct {
	client.Client
	Scheme *runtime.Scheme
}

// +kubebuilder:rbac:groups=delivery.hfwas.io,resources=apps,verbs=get;list;watch;create;update;patch;delete
// +kubebuilder:rbac:groups=delivery.hfwas.io,resources=apps/status,verbs=get;update;patch
// +kubebuilder:rbac:groups=delivery.hfwas.io,resources=cloudservices,verbs=get;list;watch

// Reconcile 处理 App 的调和循环。
func (r *AppReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
	logger := log.FromContext(ctx).WithValues("app", req.NamespacedName)
	logger.Info("Starting reconcile")

	app := &deliveryv1.App{}
	if err := r.Get(ctx, req.NamespacedName, app); err != nil {
		if errors.IsNotFound(err) {
			return ctrl.Result{}, nil
		}
		return ctrl.Result{}, err
	}

	if !app.DeletionTimestamp.IsZero() {
		return ctrl.Result{}, nil
	}

	// 聚合所有下属 CloudService 的状态
	summary := &deliveryv1.AggregatedSummary{}
	var serviceStatuses []deliveryv1.ServiceStatus

	for _, svcRef := range app.Spec.Services {
		cs := &deliveryv1.CloudService{}
		if err := r.Get(ctx, client.ObjectKey{
			Namespace: app.Namespace,
			Name:      svcRef.Name,
		}, cs); err != nil {
			if errors.IsNotFound(err) {
				serviceStatuses = append(serviceStatuses, deliveryv1.ServiceStatus{
					Name:         svcRef.Name,
					Phase:        deliveryv1.PhaseUnknown,
					ErrorMessage: "CloudService not found",
				})
				summary.FailedServices++
			} else {
				logger.Error(err, "Failed to get CloudService", "service", svcRef.Name)
				continue
			}
		} else {
			serviceStatuses = append(serviceStatuses, deliveryv1.ServiceStatus{
				Name:  svcRef.Name,
				Phase: cs.Status.Phase,
			})

			summary.TotalComponents += cs.Status.ComponentCount
			summary.ReadyComponents += cs.Status.ReadyCount

			switch cs.Status.Phase {
			case deliveryv1.PhaseReady:
				summary.ReadyServices++
			case deliveryv1.PhaseDegraded:
				summary.DegradedServices++
			case deliveryv1.PhaseFailed:
				summary.FailedServices++
			}
		}
		summary.TotalServices++
	}

	app.Status.AggregatedSummary = summary
	app.Status.ServiceStatuses = serviceStatuses
	app.Status.Phase = aggregateAppPhase(serviceStatuses)
	app.Status.ObservedGeneration = app.Generation

	if err := r.Status().Update(ctx, app); err != nil {
		logger.Error(err, "Failed to update status")
		return ctrl.Result{}, err
	}

	logger.Info("Reconcile complete", "phase", app.Status.Phase,
		"services", summary.TotalServices, "ready", summary.ReadyServices)
	return ctrl.Result{RequeueAfter: 5 * time.Minute}, nil
}

// aggregateAppPhase 聚合 App 阶段。
func aggregateAppPhase(statuses []deliveryv1.ServiceStatus) deliveryv1.Phase {
	hasFailed := false
	hasDegraded := false
	hasDeploying := false

	for _, s := range statuses {
		switch s.Phase {
		case deliveryv1.PhaseFailed:
			hasFailed = true
		case deliveryv1.PhaseDegraded:
			hasDegraded = true
		case deliveryv1.PhaseDeploying, deliveryv1.PhasePending:
			hasDeploying = true
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
	if hasDeploying {
		return deliveryv1.PhaseDeploying
	}
	return deliveryv1.PhaseReady
}

// SetupWithManager 注册 Controller 到 Manager。
func (r *AppReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&deliveryv1.App{}).
		Complete(r)
}
